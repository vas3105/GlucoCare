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
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class InsightsFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;
    private TextView tvInsight, tvUrgency, tvTip, tvLastCheck;

    public InsightsFragment() {}

    public static InsightsFragment newInstance(String param1, String param2) {
        InsightsFragment fragment = new InsightsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

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

        // Show current time as "last check"
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        tvLastCheck.setText("Last check: " + time);

        callAI();
    }

    private void callAI() {
        new Thread(() -> {
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=AIzaSyA0nMy8DGfBS3gDrgLtrJUmsX4QFA7CqmA");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInput = "{\n" +
                        "  \"contents\": [{\n" +
                        "    \"parts\": [{\"text\": \"You are a diabetes assistant. A user has slightly high post-meal glucose. Give response EXACTLY in this format:\\nInsight: ...\\nUrgency: Low/Medium/High\\nTip: ...\"}]\n" +
                        "  }]\n" +
                        "}";

                OutputStream os = conn.getOutputStream();
                os.write(jsonInput.getBytes("UTF-8"));
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

                JSONObject jsonObject = new JSONObject(response.toString());
                JSONArray candidates = jsonObject.getJSONArray("candidates");
                JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                String aiText = parts.getJSONObject(0).getString("text");

                parseAndDisplay(aiText);

            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    tvInsight.setText("Could not load insight. Please try again.");
                    tvUrgency.setText("—");
                    tvTip.setText("");
                });
            }
        }).start();
    }

    private void parseAndDisplay(String aiText) {
        String insight = "", urgency = "", tip = "";

        for (String line : aiText.split("\n")) {
            if (line.toLowerCase().startsWith("insight"))
                insight = line.replaceFirst("(?i)insight:\\s*", "").trim();
            else if (line.toLowerCase().startsWith("urgency"))
                urgency = line.replaceFirst("(?i)urgency:\\s*", "").trim();
            else if (line.toLowerCase().startsWith("tip"))
                tip = line.replaceFirst("(?i)tip:\\s*", "").trim();
        }

        String finalInsight = insight;
        String finalUrgency = urgency;
        String finalTip     = tip;

        requireActivity().runOnUiThread(() -> {
            tvInsight.setText(finalInsight);
            tvUrgency.setText(finalUrgency);
            tvTip.setText(finalTip);

            if (finalUrgency.equalsIgnoreCase("High")) {
                tvUrgency.setTextColor(0xFFD32F2F);   // red
            } else if (finalUrgency.equalsIgnoreCase("Medium")) {
                tvUrgency.setTextColor(0xFFF9A825);   // amber
            } else {
                tvUrgency.setTextColor(0xFF1B7A5A);   // green
            }
        });
    }
}