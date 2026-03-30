package com.example.glucocare;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class InsightsFragment extends Fragment {

    private TextView tvInsight, tvUrgency, tvTip, tvLastCheck;

    public InsightsFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_insights, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvInsight   = view.findViewById(R.id.tvInsight);
        tvUrgency   = view.findViewById(R.id.tvUrgency);
        tvTip       = view.findViewById(R.id.tvTip);
        tvLastCheck = view.findViewById(R.id.tvLastCheck);

        // Show current time as last check
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        tvLastCheck.setText("Last check: " + time);

        // Load today's readings from Room, then call AI
        loadTodayReadingsAndCallAI();
    }

    // ── Step 1: Query Room DB on background thread ────────────────────────────
    private void loadTodayReadingsAndCallAI() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());

            // Midnight of today in milliseconds
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();

            // Get all readings, filter to today
            List<GlucoseReading> allReadings = db.glucoseDao().getAllReadings();

            StringBuilder readingSummary = new StringBuilder();
            int count = 0;

            for (GlucoseReading r : allReadings) {
                if (r.timestamp >= startOfDay) {
                    String readingTime = new SimpleDateFormat("h:mm a", Locale.getDefault())
                            .format(new Date(r.timestamp));

                    readingSummary.append("- ")
                            .append(r.type)          // e.g. "Fasting", "After Meal"
                            .append(": ")
                            .append(r.level)         // e.g. 145.0
                            .append(" mg/dL at ")
                            .append(readingTime);    // e.g. "8:30 AM"

                    if (r.notes != null && !r.notes.trim().isEmpty()) {
                        readingSummary.append(" (note: ").append(r.notes.trim()).append(")");
                    }

                    readingSummary.append("\n");
                    count++;
                }
            }

            // Build personalized or fallback prompt
            String prompt;
            if (count == 0) {
                prompt = "You are a diabetes health assistant. " +
                        "The user has not logged any glucose readings today. " +
                        "Kindly remind them to log their readings and give one general " +
                        "healthy lifestyle tip for diabetics. " +
                        "Respond EXACTLY in this format (no extra text):\n" +
                        "Insight: ...\n" +
                        "Urgency: Low\n" +
                        "Tip: ...";
            } else {
                prompt = "You are a diabetes health assistant. " +
                        "Here are today's blood glucose readings for this user:\n" +
                        readingSummary.toString() +
                        "\nBased ONLY on these readings, provide a short personalized health insight (2-3 sentences). " +
                        "Note: Normal fasting range is 70-100 mg/dL, post-meal normal is 70-140 mg/dL. " +
                        "Readings above 180 mg/dL post-meal or above 130 mg/dL fasting are high. " +
                        "Below 70 mg/dL is low (hypoglycemia). " +
                        "Set urgency as High if any reading is severely out of range (>250 or <60), " +
                        "Medium if moderately out of range, Low if mostly normal. " +
                        "Respond EXACTLY in this format (no extra text, no markdown):\n" +
                        "Insight: ...\n" +
                        "Urgency: Low/Medium/High\n" +
                        "Tip: ...";
            }

            callAI(prompt);

        }).start();
    }

    // ── Step 2: Send prompt to Gemini API ─────────────────────────────────────
    private void callAI(String prompt) {
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=AIzaSyDJ3ihDuLQCO7dIFBI08G5R8jXjJ1PQyt4");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Build JSON safely using JSONObject (avoids escaping bugs)
            JSONObject part    = new JSONObject();
            part.put("text", prompt);

            JSONArray parts    = new JSONArray();
            parts.put(part);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject body    = new JSONObject();
            body.put("contents", contents);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            conn.connect();
            int responseCode = conn.getResponseCode();

            BufferedReader br = (responseCode >= 200 && responseCode < 300)
                    ? new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    : new BufferedReader(new InputStreamReader(conn.getErrorStream()));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);
            br.close();

            // Parse Gemini response
            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray  candidates = jsonObject.getJSONArray("candidates");
            JSONObject aiContent  = candidates.getJSONObject(0).getJSONObject("content");
            JSONArray  aiParts    = aiContent.getJSONArray("parts");
            String     aiText     = aiParts.getJSONObject(0).getString("text");

            parseAndDisplay(aiText);

        } catch (Exception e) {
            e.printStackTrace();
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    tvInsight.setText("Could not load insight. Please check your connection.");
                    tvUrgency.setText("—");
                    tvTip.setText("");
                });
            }
        }
    }

    // ── Step 3: Parse "Insight / Urgency / Tip" and update UI ────────────────
    private void parseAndDisplay(String aiText) {
        String insight = "", urgency = "", tip = "";

        for (String line : aiText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("insight"))
                insight = trimmed.replaceFirst("(?i)insight:\\s*", "").trim();
            else if (trimmed.toLowerCase().startsWith("urgency"))
                urgency = trimmed.replaceFirst("(?i)urgency:\\s*", "").trim();
            else if (trimmed.toLowerCase().startsWith("tip"))
                tip = trimmed.replaceFirst("(?i)tip:\\s*", "").trim();
        }

        String finalInsight = insight;
        String finalUrgency = urgency;
        String finalTip     = tip;

        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                tvInsight.setText(finalInsight.isEmpty() ? "No insight available." : finalInsight);
                tvUrgency.setText(finalUrgency.isEmpty() ? "—" : finalUrgency);
                tvTip.setText(finalTip);

                // Color-code urgency
                if (finalUrgency.equalsIgnoreCase("High")) {
                    tvUrgency.setTextColor(0xFFD32F2F);  // red
                } else if (finalUrgency.equalsIgnoreCase("Medium")) {
                    tvUrgency.setTextColor(0xFFF9A825);  // amber
                } else {
                    tvUrgency.setTextColor(0xFF1B7A5A);  // green
                }
            });
        }
    }
}