package com.example.glucocare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OcrHelper — fully on-device OCR for glucometer screens.
 *
 * Primary:  Tesseract in digits-only mode
 *            → most accurate for LCD number displays
 *            → no internet, no quota, no API key
 *
 * Fallback: ML Kit text recognition
 *            → activates automatically if Tesseract fails
 *            → also on-device, also free
 *
 * Both run entirely on the device — no network calls at all.
 */
public class OcrHelper {

    private static final String TAG         = "OcrHelper";
    private static final int    MIN_GLUCOSE = 20;
    private static final int    MAX_GLUCOSE = 600;
    private static final int    MAX_WIDTH   = 1024; // larger = better for Tesseract
    private static final String TESS_DATA   = "tessdata";
    private static final String LANG        = "eng";

    private final ExecutorService executor;

    // ── Callback ─────────────────────────────────────────────────────────────

    public interface OcrCallback {
        void onSuccess(int glucoseValue);
        void onFailure(String errorMessage);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public OcrHelper() {
        executor = Executors.newSingleThreadExecutor();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void extractGlucoseValue(Context context, Uri imageUri, OcrCallback callback) {
        executor.execute(() -> {
            try {
                // Ensure tessdata is copied to internal storage
                String tessDataPath = prepareTessData(context);
                if (tessDataPath == null) {
                    Log.w(TAG, "Tesseract data not available → ML Kit fallback");
                    runMlKitOcr(context, imageUri, callback);
                    return;
                }

                // Load and pre-process the image
                Bitmap original = loadAndResize(context, imageUri);
                if (original == null) {
                    callback.onFailure("Could not load image.");
                    return;
                }

                // Run Tesseract on multiple image variants
                int result = runTesseractMultiPass(original, tessDataPath);

                if (result != -1) {
                    Log.d(TAG, "Tesseract detected: " + result + " mg/dL");
                    callback.onSuccess(result);
                } else {
                    // Tesseract failed → try ML Kit
                    Log.w(TAG, "Tesseract found nothing → ML Kit fallback");
                    runMlKitOcr(context, imageUri, callback);
                }

            } catch (Exception e) {
                Log.e(TAG, "OcrHelper error: " + e.getMessage(), e);
                runMlKitOcr(context, imageUri, callback);
            }
        });
    }

    // ── Tesseract multi-pass ──────────────────────────────────────────────────

    /**
     * Runs Tesseract on 3 image variants and returns the best result.
     *
     * Variant 1: High contrast grayscale  → standard LCD screens
     * Variant 2: Brightened               → dim/dark LCD screens
     * Variant 3: Inverted                 → white-on-black LCDs
     */
    private int runTesseractMultiPass(Bitmap original, String tessDataPath) {
        Bitmap[] variants = {
                processHighContrast(original),
                processBrighten(original),
                processInverted(original)
        };

        List<Integer> allCandidates = new ArrayList<>();

        for (int i = 0; i < variants.length; i++) {
            Bitmap variant = variants[i];
            if (variant == null) continue;

            int result = runTesseractOnBitmap(variant, tessDataPath, i + 1);
            if (result != -1) allCandidates.add(result);
        }

        return bestCandidate(allCandidates);
    }

    /**
     * Runs Tesseract on a single bitmap.
     * Uses DIGITS ONLY whitelist — only recognises 0123456789.
     */
    private int runTesseractOnBitmap(Bitmap bitmap, String tessDataPath, int variantNum) {
        TessBaseAPI tess = new TessBaseAPI();

        try {
            boolean init = tess.init(tessDataPath, LANG);
            if (!init) {
                Log.e(TAG, "Tesseract init failed for variant " + variantNum);
                return -1;
            }

            // DIGITS ONLY — the most important setting for glucometers
            // This tells Tesseract to only look for characters 0-9
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");
            tess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*()-=_+[]{}|;':\",./<>?");

            // PSM_SINGLE_BLOCK — treats image as a single block of text
            // Best for glucometer displays with one large number
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);

            tess.setImage(bitmap);

            String text = tess.getUTF8Text();
            int    conf = tess.meanConfidence();

            Log.d(TAG, "Tesseract variant " + variantNum
                    + " → '" + text + "' (confidence: " + conf + "%)");

            // Only accept results with reasonable confidence
            if (conf < 30) {
                Log.w(TAG, "Confidence too low (" + conf + "%) — skipping");
                return -1;
            }

            return parseDigitsOnly(text);

        } catch (Exception e) {
            Log.e(TAG, "Tesseract error variant " + variantNum + ": " + e.getMessage());
            return -1;
        } finally {
            tess.recycle(); // always release Tesseract resources
        }
    }

    // ── Tessdata preparation ──────────────────────────────────────────────────

    /**
     * Copies tessdata from assets to internal storage.
     * Tesseract requires the data file to be on the filesystem, not in assets.
     * Only copies once — checks if already exists.
     *
     * @return path to the folder containing tessdata/, or null on failure
     */
    private String prepareTessData(Context context) {
        File tessDir  = new File(context.getFilesDir(), TESS_DATA);
        File dataFile = new File(tessDir, LANG + ".traineddata");

        // Already copied — return immediately
        if (dataFile.exists() && dataFile.length() > 0) {
            Log.d(TAG, "Tessdata already prepared at: " + context.getFilesDir());
            return context.getFilesDir().getAbsolutePath();
        }

        // Create tessdata directory
        if (!tessDir.exists() && !tessDir.mkdirs()) {
            Log.e(TAG, "Could not create tessdata directory");
            return null;
        }

        // Copy from assets
        try {
            InputStream  in  = context.getAssets().open(TESS_DATA + "/" + LANG + ".traineddata");
            OutputStream out = new FileOutputStream(dataFile);

            byte[] buffer = new byte[4096];
            int    read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.flush();
            out.close();

            Log.d(TAG, "Tessdata copied successfully to: " + dataFile.getAbsolutePath());
            return context.getFilesDir().getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy tessdata: " + e.getMessage());
            // Delete partial file if copy failed
            if (dataFile.exists()) dataFile.delete();
            return null;
        }
    }

    // ── ML Kit fallback ───────────────────────────────────────────────────────

    /**
     * ML Kit fallback — runs 3 image variants and merges results.
     * Used when Tesseract is unavailable or finds nothing.
     */
    private void runMlKitOcr(Context context, Uri imageUri, OcrCallback callback) {
        Log.d(TAG, "Running ML Kit fallback");

        Bitmap original = loadAndResize(context, imageUri);
        if (original == null) {
            callback.onFailure("Could not load image.");
            return;
        }

        Bitmap[] variants = {
                processHighContrast(original),
                processBrighten(original),
                processInverted(original)
        };

        TextRecognizer    recognizer    = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS);
        List<Integer>     allCandidates = new ArrayList<>();
        final int[]       doneCount     = {0};
        final int         total         = variants.length;

        for (Bitmap variant : variants) {
            if (variant == null) {
                doneCount[0]++;
                if (doneCount[0] == total)
                    deliverResult(allCandidates, callback, "ML Kit");
                continue;
            }

            InputImage image = InputImage.fromBitmap(variant, 0);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String raw = visionText.getText();
                        Log.d(TAG, "ML Kit raw: " + raw);
                        synchronized (allCandidates) {
                            allCandidates.addAll(extractNumbers(raw));
                        }
                        doneCount[0]++;
                        if (doneCount[0] == total)
                            deliverResult(allCandidates, callback, "ML Kit");
                    })
                    .addOnFailureListener(e -> {
                        doneCount[0]++;
                        if (doneCount[0] == total)
                            deliverResult(allCandidates, callback, "ML Kit");
                    });
        }
    }

    private void deliverResult(List<Integer> candidates,
                               OcrCallback callback, String source) {
        int best = bestCandidate(candidates);
        if (best != -1) {
            Log.d(TAG, "✓ " + source + " detected: " + best + " mg/dL");
            callback.onSuccess(best);
        } else {
            callback.onFailure(
                    "Could not detect a glucose value.\n\n" +
                            "Tips for a better photo:\n" +
                            "• Hold phone 10–15 cm directly above display\n" +
                            "• Avoid glare — tilt glucometer slightly\n" +
                            "• Ensure the number is clearly lit\n" +
                            "• Fill the frame with the display\n\n" +
                            "You can also enter the value manually.");
        }
    }

    // ── Image processing variants ─────────────────────────────────────────────

    private Bitmap processHighContrast(Bitmap src) {
        // Strong contrast — best for standard LCD
        return applyColorMatrix(src, buildGrayscaleMatrix(2.0f, -80f));
    }

    private Bitmap processBrighten(Bitmap src) {
        // Brightened — best for dim LCD screens
        return applyColorMatrix(src, buildGrayscaleMatrix(1.3f, 50f));
    }

    private Bitmap processInverted(Bitmap src) {
        // Inverted — best for white-on-black LCD displays
        Bitmap gray = applyColorMatrix(src, buildGrayscaleMatrix(1.8f, -40f));
        if (gray == null) return null;
        return applyColorMatrix(gray, new float[]{
                -1,  0,  0, 0, 255,
                0, -1,  0, 0, 255,
                0,  0, -1, 0, 255,
                0,  0,  0, 1,   0
        });
    }

    private float[] buildGrayscaleMatrix(float contrast, float offset) {
        return new float[]{
                contrast*0.299f, contrast*0.587f, contrast*0.114f, 0, offset,
                contrast*0.299f, contrast*0.587f, contrast*0.114f, 0, offset,
                contrast*0.299f, contrast*0.587f, contrast*0.114f, 0, offset,
                0,               0,               0,               1, 0
        };
    }

    private Bitmap applyColorMatrix(Bitmap src, float[] matrix) {
        if (src == null) return null;
        try {
            Bitmap result = Bitmap.createBitmap(
                    src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            Paint  paint  = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(matrix)));
            canvas.drawBitmap(src, 0, 0, paint);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Number parsing ────────────────────────────────────────────────────────

    /**
     * Parses digits-only text from Tesseract.
     * Since we use a digit whitelist, text should be clean numbers.
     */
    private int parseDigitsOnly(String text) {
        if (text == null || text.trim().isEmpty()) return -1;

        // Remove all non-digit characters (spaces, newlines)
        String digitsOnly = text.replaceAll("[^0-9]", "").trim();
        Log.d(TAG, "Digits extracted: '" + digitsOnly + "'");

        if (digitsOnly.isEmpty()) return -1;

        // Try 3-digit numbers first (most glucose readings are 3 digits)
        if (digitsOnly.length() >= 3) {
            // Try all 3-digit substrings, pick the one in glucose range
            for (int i = 0; i <= digitsOnly.length() - 3; i++) {
                int v = safe(digitsOnly.substring(i, i + 3));
                if (inRange(v)) return v;
            }
        }

        // Try 2-digit numbers
        if (digitsOnly.length() >= 2) {
            for (int i = 0; i <= digitsOnly.length() - 2; i++) {
                int v = safe(digitsOnly.substring(i, i + 2));
                if (inRange(v)) return v;
            }
        }

        // Try the whole string as a number
        int v = safe(digitsOnly);
        if (inRange(v)) return v;

        return -1;
    }

    /** Extracts all plausible glucose numbers from general text (ML Kit) */
    private List<Integer> extractNumbers(String text) {
        List<Integer> results = new ArrayList<>();
        if (text == null) return results;

        // Apply LCD corrections for ML Kit (not needed for Tesseract)
        String corrected = text
                .replace("O", "0").replace("o", "0")
                .replace("I", "1").replace("l", "1")
                .replace("S", "5").replace("B", "8")
                .replace("G", "6").replace("Z", "2");

        Pattern p = Pattern.compile("(?<![\\d])(\\d{2,3})(?![\\d])");
        Matcher m = p.matcher(corrected);
        while (m.find()) {
            int v = safe(m.group(1));
            if (inRange(v)) results.add(v);
        }
        return results;
    }

    // ── Confidence scoring ────────────────────────────────────────────────────

    private int bestCandidate(List<Integer> candidates) {
        if (candidates.isEmpty()) return -1;

        int bestValue = -1;
        int bestScore = -1;

        for (int i = 0; i < candidates.size(); i++) {
            int v = candidates.get(i);
            int score = 0;

            // Range scoring — common glucose values score higher
            if      (v >= 70  && v <= 130) score += 50;
            else if (v >= 131 && v <= 300) score += 35;
            else if (v >= 40  && v <= 69)  score += 25;
            else if (v >= 301 && v <= 600) score += 15;

            // Frequency bonus — same value seen in multiple variants
            int freq = 0;
            for (int c : candidates) if (c == v) freq++;
            score += freq * 25;

            // Position bonus — earlier results more likely to be the main reading
            score -= i * 3;

            Log.d(TAG, "Candidate " + v + " score=" + score + " freq=" + freq);

            if (score > bestScore) {
                bestScore = score;
                bestValue = v;
            }
        }

        Log.d(TAG, "Best: " + bestValue + " (score=" + bestScore + ")");
        return bestValue;
    }

    // ── Image loading ─────────────────────────────────────────────────────────

    private Bitmap loadAndResize(Context context, Uri uri) {
        try {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            if (stream == null) return null;
            Bitmap original = BitmapFactory.decodeStream(stream);
            stream.close();
            if (original == null) return null;

            // Tesseract works better with larger images — only downscale if huge
            if (original.getWidth() > MAX_WIDTH) {
                float scale  = (float) MAX_WIDTH / original.getWidth();
                int   height = (int) (original.getHeight() * scale);
                return Bitmap.createScaledBitmap(original, MAX_WIDTH, height, true);
            }
            return original;

        } catch (Exception e) {
            Log.e(TAG, "loadAndResize: " + e.getMessage());
            return null;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int safe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private boolean inRange(int v) {
        return v >= MIN_GLUCOSE && v <= MAX_GLUCOSE;
    }
}