package com.example.glucocare;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AiChatFragment extends Fragment {

    private static final String GEMINI_KEY = BuildConfig.GEMINI_KEY;

    private LinearLayout  chatContainer;
    private ScrollView    scrollView;
    private EditText      etMessage;
    private ImageButton   btnSend;
    private TextView      btnBack;

    // Maintain conversation history for multi-turn chat
    private final JSONArray conversationHistory = new JSONArray();

    public AiChatFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chatContainer = view.findViewById(R.id.chatContainer);
        scrollView    = view.findViewById(R.id.scrollView);
        etMessage     = view.findViewById(R.id.etMessage);
        btnSend       = view.findViewById(R.id.btnSend);
        btnBack       = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                etMessage.setText("");
                sendMessage(msg);
            }
        });

        // Greeting message
        addBubble("👋 Hi! I'm your GlucoCare AI assistant. Ask me anything about diabetes, " +
                "glucose management, medications, or your health.", false);
    }

    private void sendMessage(String userText) {
        // Show user bubble immediately
        addBubble(userText, true);

        // Show typing indicator
        TextView typing = addBubble("Typing...", false);

        new Thread(() -> {
            try {
                // Add user message to history
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                JSONArray userParts = new JSONArray();
                JSONObject userPart = new JSONObject();
                userPart.put("text", userText);
                userParts.put(userPart);
                userMsg.put("parts", userParts);
                conversationHistory.put(userMsg);

                // Build request with full history + system context
                JSONObject systemPart = new JSONObject();
                systemPart.put("text", "You are a helpful diabetes health assistant called GlucoCare AI. " +
                        "You help users understand their glucose readings, medications, and lifestyle tips. " +
                        "Keep responses concise, friendly, and medically responsible. " +
                        "Always remind users to consult their doctor for medical decisions.");

                JSONArray systemParts = new JSONArray();
                systemParts.put(systemPart);
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "user");
                systemMsg.put("parts", systemParts);

                // Prepend system context + history
                JSONArray contents = new JSONArray();
                contents.put(systemMsg);
                for (int i = 0; i < conversationHistory.length(); i++) {
                    contents.put(conversationHistory.get(i));
                }

                JSONObject body = new JSONObject();
                body.put("contents", contents);

                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + GEMINI_KEY);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush(); os.close();
                conn.connect();

                int code = conn.getResponseCode();
                BufferedReader br = (code >= 200 && code < 300)
                        ? new BufferedReader(new InputStreamReader(conn.getInputStream()))
                        : new BufferedReader(new InputStreamReader(conn.getErrorStream()));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                String aiText = new JSONObject(response.toString())
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text");

                // Add AI response to history
                JSONObject aiMsg = new JSONObject();
                aiMsg.put("role", "model");
                JSONArray aiParts = new JSONArray();
                JSONObject aiPart = new JSONObject();
                aiPart.put("text", aiText);
                aiParts.put(aiPart);
                aiMsg.put("parts", aiParts);
                conversationHistory.put(aiMsg);

                String finalText = aiText;
                if (getActivity() != null) requireActivity().runOnUiThread(() -> {
                    // Replace typing bubble with real response
                    chatContainer.removeView(typing);
                    addBubble(finalText, false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) requireActivity().runOnUiThread(() -> {
                    chatContainer.removeView(typing);
                    addBubble("Sorry, I couldn't connect. Please check your internet.", false);
                });
            }
        }).start();
    }

    private TextView addBubble(String text, boolean isUser) {
        TextView bubble = new TextView(getContext());
        if (isUser) {
            bubble.setText(text);
        } else {
            bubble.setText(formatMarkdown(text));
        }
        bubble.setTextSize(14f);
        bubble.setLineSpacing(0, 1.4f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 8;
        params.bottomMargin = 4;

        int pad = (int) (14 * getResources().getDisplayMetrics().density);

        if (isUser) {
            bubble.setBackgroundResource(R.drawable.bubble_user);
            bubble.setTextColor(0xFFFFFFFF);
            params.gravity = android.view.Gravity.END;
            params.leftMargin = (int) (60 * getResources().getDisplayMetrics().density);
            params.rightMargin = (int) (4 * getResources().getDisplayMetrics().density);
        } else {
            bubble.setBackgroundResource(R.drawable.bubble_ai);
            bubble.setTextColor(0xFF0F172A);
            params.gravity = android.view.Gravity.START;
            params.rightMargin = (int) (60 * getResources().getDisplayMetrics().density);
            params.leftMargin = (int) (4 * getResources().getDisplayMetrics().density);
        }

        bubble.setPadding(pad, pad - 4, pad, pad - 4);
        bubble.setLayoutParams(params);
        chatContainer.addView(bubble);

        // Auto scroll to bottom
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        return bubble;
    }

    /**
     * Converts simple markdown (**, *, #, numbered lists, bullet lists) into
     * a styled SpannableStringBuilder so the chat bubbles look clean.
     */
    private SpannableStringBuilder formatMarkdown(String raw) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = raw.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Skip empty lines but add a newline separator
            if (line.trim().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                continue;
            }

            // Detect heading lines (### ## #)
            boolean isHeading = false;
            if (line.startsWith("### ")) { line = line.substring(4); isHeading = true; }
            else if (line.startsWith("## ")) { line = line.substring(3); isHeading = true; }
            else if (line.startsWith("# "))  { line = line.substring(2); isHeading = true; }

            // Detect bullet lines (- or * or •)
            boolean isBullet = false;
            if (line.matches("^[-*•] .+")) {
                line = line.substring(2).trim();
                isBullet = true;
            }

            // Detect numbered list (1. 2. etc.)
            boolean isNumbered = false;
            String numberPrefix = "";
            if (line.matches("^\\d+\\. .+")) {
                int dotIdx = line.indexOf(". ");
                numberPrefix = line.substring(0, dotIdx + 2);
                line = line.substring(dotIdx + 2).trim();
                isNumbered = true;
            }

            // Build inline spans (bold and italic from ** and *)
            SpannableStringBuilder lineSb = applyInlineSpans(isNumbered ? numberPrefix + line : line);

            int start = sb.length();
            if (isBullet) {
                sb.append("  • ");
            }
            sb.append(lineSb);
            int end = sb.length();

            if (isHeading) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(1.08f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (i < lines.length - 1) sb.append("\n");
        }

        return sb;
    }

    private SpannableStringBuilder applyInlineSpans(String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                // Bold: find closing **
                int close = text.indexOf("**", i + 2);
                if (close == -1) { sb.append(text.substring(i)); break; }
                int start = sb.length();
                sb.append(text, i + 2, close);
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = close + 2;
            } else if (text.charAt(i) == '*') {
                // Italic: find closing *
                int close = text.indexOf("*", i + 1);
                if (close == -1) { sb.append(text.substring(i)); break; }
                int start = sb.length();
                sb.append(text, i + 1, close);
                sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = close + 1;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        return sb;
    }
}
