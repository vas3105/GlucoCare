package com.example.glucocare;

import android.content.Context;
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
public class OcrHelper {

    private static final String TAG = "OcrHelper";

    private static final int MIN_GLUCOSE = 20;
    private static final int MAX_GLUCOSE = 600;

    // ── Callback ─────────────────────────────────────────────────────────────

    public interface OcrCallback {
        void onSuccess(int glucoseValue);
        void onFailure(String errorMessage);
    }

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

        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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
}