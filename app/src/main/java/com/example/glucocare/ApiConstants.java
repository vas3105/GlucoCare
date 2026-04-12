package com.example.glucocare;

/**
 * ApiConstants — SAFE Gemini config
 *
 * Uses ONLY one model to prevent rate limit spam.
 */
public class ApiConstants {

    public static final String GEMINI_API_KEY = "AIzaSyA11qjxYmli_ov9THXc3QQRpwffHlDUg-Q";

    private static final String BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    // Use ONLY ONE model (fast + enough for OCR)
    public static final String GEMINI_URL =
            BASE + "gemini-1.5-flash-8b:generateContent?key=" + GEMINI_API_KEY;

    private ApiConstants() {}
}