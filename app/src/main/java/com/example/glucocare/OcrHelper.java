package com.example.glucocare;

import android.content.Context;
<<<<<<< HEAD
import android.net.Uri;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OcrHelper — wraps Google ML Kit Text Recognition.
 *
 * Call {@link #extractGlucoseValue(Context, Uri, OcrCallback)} from LogsFragment.
 * Delivers results on the ML Kit background thread; switch to main thread in the callback.
 */
=======
import android.graphics.*;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.gson.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import okhttp3.*;

>>>>>>> origin/master
public class OcrHelper {

    private static final String TAG = "OcrHelper";

    private static final int MIN_GLUCOSE = 20;
    private static final int MAX_GLUCOSE = 600;
<<<<<<< HEAD

    // ── Callback ─────────────────────────────────────────────────────────────
=======
    private static final int MAX_WIDTH = 768;

    // ✅ Cooldown
    private static long lastCall = 0;
    private static final long COOLDOWN = 3000;

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
>>>>>>> origin/master

    public interface OcrCallback {
        void onSuccess(int glucoseValue);
        void onFailure(String errorMessage);
    }

<<<<<<< HEAD
    // ── Public API ───────────────────────────────────────────────────────────

    public void extractGlucoseValue(Context context, Uri imageUri, OcrCallback callback) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, imageUri);
        } catch (IOException e) {
            Log.e(TAG, "Failed to build InputImage", e);
            callback.onFailure("Could not read the image file.");
            return;
        }
=======
    public void extractGlucoseValue(Context context, Uri uri, OcrCallback callback) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                if (now - lastCall < COOLDOWN) {
                    callback.onFailure("Wait a few seconds before scanning again.");
                    return;
                }

                Bitmap bitmap = loadAndResize(context, uri);
                if (bitmap == null) {
                    callback.onFailure("Image load failed.");
                    return;
                }

                // Try Gemini once
                int result = tryGemini(bitmap);
                if (result != -1) {
                    callback.onSuccess(result);
                    return;
                }

                // Fallback ML Kit (multi-variant)
                runMlKit(bitmap, callback);

            } catch (Exception e) {
                callback.onFailure("Unexpected error.");
            }
        });
    }

    // ───────── GEMINI ─────────

    private int tryGemini(Bitmap bitmap) {
        try {
            lastCall = System.currentTimeMillis();

            String base64 = bitmapToBase64(bitmap);

            JsonObject text = new JsonObject();
            text.addProperty("text",
                    "Read ONLY the glucose number from this glucometer display. Return only number.");

            JsonObject img = new JsonObject();
            img.addProperty("mimeType", "image/jpeg");
            img.addProperty("data", base64);

            JsonObject inline = new JsonObject();
            inline.add("inlineData", img);

            JsonArray parts = new JsonArray();
            parts.add(text);
            parts.add(inline);

            JsonObject content = new JsonObject();
            content.add("parts", parts);

            JsonArray contents = new JsonArray();
            contents.add(content);

            JsonObject req = new JsonObject();
            req.add("contents", contents);

            Request request = new Request.Builder()
                    .url(ApiConstants.GEMINI_URL)
                    .post(RequestBody.create(req.toString(),
                            MediaType.parse("application/json")))
                    .build();

            Response response = client.newCall(request).execute();

            if (response.code() == 429) return -1;
            if (!response.isSuccessful()) return -1;

            String json = response.body().string();

            String textResp = JsonParser.parseString(json)
                    .getAsJsonObject()
                    .getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            return extractNumber(textResp);

        } catch (Exception e) {
            return -1;
        }
    }

    // ───────── ML KIT (IMPROVED BACK) ─────────

    private void runMlKit(Bitmap original, OcrCallback callback) {

        Bitmap[] variants = {
                enhance(original, 1.8f, -60f),
                enhance(original, 1.2f, 40f),
                invert(original)
        };
>>>>>>> origin/master

        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

<<<<<<< HEAD
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    int glucose = parseGlucoseFromText(visionText.getText());
                    if (glucose != -1) {
                        callback.onSuccess(glucose);
                    } else {
                        callback.onFailure("No glucose value found. Please enter manually.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "ML Kit error", e);
                    callback.onFailure(e.getMessage());
                });
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    /**
     * Two-pass glucose parser:
     *  1. Number immediately followed by "mg/dL" variant  → high confidence
     *  2. Any standalone 2-3 digit number in [20, 600]     → fallback
     */
    int parseGlucoseFromText(String rawText) {
        if (rawText == null || rawText.isEmpty()) return -1;
        String text = rawText.replaceAll("\\s+", " ").trim();

        // Pass 1 — "104 mg/dL", "104mg/dl", "104 MG/DL", "104 mg" etc.
        Pattern mgdl = Pattern.compile(
                "(\\d{2,3})\\s*(?:mg/?dL|mg/dl|MG/DL|mg)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = mgdl.matcher(text);
        while (m1.find()) {
            int v = safe(m1.group(1));
            if (inRange(v)) return v;
        }

        // Pass 2 — standalone 2-3 digit integer in glucose range
        Matcher m2 = Pattern.compile("(?<![\\d.])(\\d{2,3})(?![\\d.])").matcher(text);
        while (m2.find()) {
            int v = safe(m2.group(1));
            if (inRange(v)) return v;
        }

        return -1;
    }

    private int safe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private boolean inRange(int v) { return v >= MIN_GLUCOSE && v <= MAX_GLUCOSE; }
=======
        List<Integer> results = new ArrayList<>();
        final int[] done = {0};

        for (Bitmap bmp : variants) {

            InputImage img = InputImage.fromBitmap(bmp, 0);

            recognizer.process(img)
                    .addOnSuccessListener(res -> {
                        String txt = fixText(res.getText());
                        results.addAll(findNumbers(txt));

                        done[0]++;
                        if (done[0] == variants.length)
                            deliver(results, callback);
                    })
                    .addOnFailureListener(e -> {
                        done[0]++;
                        if (done[0] == variants.length)
                            deliver(results, callback);
                    });
        }
        Log.d("OCR_DEBUG", "Candidates: " + results);

    }

    private void deliver(List<Integer> nums, OcrCallback callback) {
        int best = bestCandidate(nums);
        if (best != -1) callback.onSuccess(best);
        else callback.onFailure("Could not detect value. Try clearer photo.");
    }

    // ───────── HELPERS ─────────

    private int extractNumber(String text) {
        if (text == null) return -1;

        text = text.replaceAll("[^0-9.]", " "); // remove junk

        Matcher m = Pattern.compile("\\d{2,3}").matcher(text);

        while (m.find()) {
            int val = Integer.parseInt(m.group());
            if (val >= MIN_GLUCOSE && val <= MAX_GLUCOSE) {
                return val;
            }
        }
        return -1;
    }

    private String fixText(String s) {
        return s.replace("O","0").replace("I","1").replace("S","5");
    }

    private List<Integer> findNumbers(String text) {
        List<Integer> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\d{2,3}").matcher(text);
        while (m.find()) {
            int v = Integer.parseInt(m.group());
            if (v >= MIN_GLUCOSE && v <= MAX_GLUCOSE)
                list.add(v);
        }
        return list;
    }

    private int bestCandidate(List<Integer> candidates) {
        if (candidates.isEmpty()) return -1;

        int best = -1;
        int bestScore = -1;

        for (int v : candidates) {
            int score = 0;

            // realistic glucose range gets higher score
            if (v >= 70 && v <= 140) score += 50;
            else if (v >= 141 && v <= 250) score += 30;
            else if (v >= 40 && v < 70) score += 20;
            else score += 5;

            // frequency boost
            int freq = 0;
            for (int x : candidates) if (x == v) freq++;
            score += freq * 10;

            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }

        return best;
    }

    private Bitmap enhance(Bitmap src, float contrast, float offset) {
        ColorMatrix cm = new ColorMatrix(new float[]{
                contrast,0,0,0,offset,
                0,contrast,0,0,offset,
                0,0,contrast,0,offset,
                0,0,0,1,0
        });
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src,0,0,p);
        return out;
    }

    private Bitmap invert(Bitmap src) {
        return enhance(src,-1,255);
    }

    private Bitmap loadAndResize(Context ctx, Uri uri) throws Exception {
        InputStream s = ctx.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(s);
        s.close();

        if (bmp.getWidth() > MAX_WIDTH) {
            float scale = (float) MAX_WIDTH / bmp.getWidth();
            return Bitmap.createScaledBitmap(bmp, MAX_WIDTH,
                    (int)(bmp.getHeight()*scale), true);
        }
        return bmp;
    }

    private String bitmapToBase64(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }
>>>>>>> origin/master
}