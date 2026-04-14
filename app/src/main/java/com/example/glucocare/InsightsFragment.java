package com.example.glucocare;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class InsightsFragment extends Fragment {

    private static final String GEMINI_KEY   = BuildConfig.GEMINI_KEY;
    private static final String NEWS_API_KEY = BuildConfig.NEWS_API_KEY;

    private TextView tvInsight, tvUrgency, tvTip, tvLastCheck;
    private TextView tvTip1Title, tvTip1Body, tvTip1Icon;
    private TextView tvTip2Title, tvTip2Body, tvTip2Icon;
    private LinearLayout educationalContainer;
    private TextView btnViewAll;

    private final List<Article> articleList = new ArrayList<>();

    public InsightsFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_insights, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvInsight            = view.findViewById(R.id.tvInsight);
        tvUrgency            = view.findViewById(R.id.tvUrgency);
        tvTip                = view.findViewById(R.id.tvTip);
        tvLastCheck          = view.findViewById(R.id.tvLastCheck);
        tvTip1Title          = view.findViewById(R.id.tvTip1Title);
        tvTip1Body           = view.findViewById(R.id.tvTip1Body);
        tvTip1Icon           = view.findViewById(R.id.tvTip1Icon);
        tvTip2Title          = view.findViewById(R.id.tvTip2Title);
        tvTip2Body           = view.findViewById(R.id.tvTip2Body);
        tvTip2Icon           = view.findViewById(R.id.tvTip2Icon);
        educationalContainer = view.findViewById(R.id.educationalContainer);
        btnViewAll           = view.findViewById(R.id.btnViewAll);

        tvLastCheck.setText("Last check: " +
                new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()));

        btnViewAll.setOnClickListener(v -> showAllArticles());

        loadTodayReadingsAndCallAI();
        fetchNewsArticles();
    }

    // ── AI Insight (unchanged logic) ─────────────────────────────────────────

    private void loadTodayReadingsAndCallAI() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();

            // getAllReadings() returns newest-first (DESC by timestamp)
            // Reverse so we send oldest→newest to AI so trend is clear
            List<GlucoseReading> allReadings = db.glucoseDao().getAllReadings();
            List<GlucoseReading> todayReadings = new ArrayList<>();
            for (GlucoseReading r : allReadings) {
                if (r.timestamp >= startOfDay) todayReadings.add(r);
            }
            // todayReadings is currently newest-first; reverse to oldest-first for trend narrative
            java.util.Collections.reverse(todayReadings);

            StringBuilder summary = new StringBuilder();
            int count = todayReadings.size();
            GlucoseReading latest = count > 0 ? todayReadings.get(todayReadings.size() - 1) : null;

            for (GlucoseReading r : todayReadings) {
                String t = new SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(new Date(r.timestamp));
                summary.append("- ").append(r.type).append(": ")
                        .append((int)r.level).append(" mg/dL at ").append(t);
                if (r.notes != null && !r.notes.trim().isEmpty())
                    summary.append(" (note: ").append(r.notes.trim()).append(")");
                summary.append("\n");
            }

            // Detect if trend is improving (last reading lower than first)
            String trendNote = "";
            if (count >= 2) {
                float first = todayReadings.get(0).level;
                float last  = todayReadings.get(count - 1).level;
                if (last < first - 20)       trendNote = "Note: The trend shows improvement — levels have come down over time.";
                else if (last > first + 20)  trendNote = "Note: The trend shows levels rising over time.";
                else                         trendNote = "Note: Levels have been relatively stable throughout the day.";
            }

            String latestInfo = latest != null
                    ? "Most recent reading: " + (int)latest.level + " mg/dL (" + latest.type + ")\n"
                    : "";

            String prompt = count == 0
                    ? "You are a diabetes health assistant. The user has not logged any glucose readings today.\n" +
                    "Respond EXACTLY in this format (no extra text, no markdown):\n" +
                    "Insight: <remind them to log readings, 1-2 sentences>\n" +
                    "Urgency: Low\n" +
                    "Tip: <one general healthy lifestyle tip for diabetics>\n" +
                    "Tip1Icon: 📋\nTip1Title: Log Your Readings\n" +
                    "Tip1Body: <short reason why logging is important>\n" +
                    "Tip2Icon: 🥗\nTip2Title: <general healthy habit title>\n" +
                    "Tip2Body: <short description of the habit>"
                    : "You are a diabetes health assistant.\n" +
                    "Today's blood glucose readings (listed oldest to most recent):\n" + summary +
                    latestInfo + trendNote + "\n\n" +
                    "IMPORTANT: Base your insight primarily on the MOST RECENT reading and the TREND, not just the highest value seen today.\n" +
                    "Normal fasting: 70–100 mg/dL. Post-meal normal: 70–140 mg/dL. High >180 post-meal or >130 fasting. Low <70.\n" +
                    "Urgency: High if CURRENT reading >250 or <60. Medium if current reading moderately out of range. Low if current reading is normal or improving toward normal.\n\n" +
                    "Respond EXACTLY in this format (no markdown, no extra text):\n" +
                    "Insight: <2-3 sentence insight focused on the TREND and most recent reading, not the worst reading>\n" +
                    "Urgency: Low/Medium/High\n" +
                    "Tip: <one actionable tip relevant to where levels stand RIGHT NOW>\n" +
                    "Tip1Icon: <single emoji>\nTip1Title: <short title>\nTip1Body: <1-2 sentence explanation>\n" +
                    "Tip2Icon: <single emoji>\nTip2Title: <short title>\nTip2Body: <1-2 sentence explanation>";

            callInsightAI(prompt);
        }).start();
    }

    private void callInsightAI(String prompt) {
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=" + GEMINI_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject part = new JSONObject(); part.put("text", prompt);
            JSONArray parts = new JSONArray(); parts.put(part);
            JSONObject content = new JSONObject(); content.put("parts", parts);
            JSONArray contents = new JSONArray(); contents.put(content);
            JSONObject body = new JSONObject(); body.put("contents", contents);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush(); os.close();
            // Note: conn.connect() removed — getOutputStream() already opens the connection

            int code = conn.getResponseCode();
            BufferedReader br = (code >= 200 && code < 300)
                    ? new BufferedReader(new InputStreamReader(conn.getInputStream()))
                    : new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);
            br.close();

            android.util.Log.d("GlucoCare", "Gemini raw response (code=" + code + "): " + response);
            String aiText = new JSONObject(response.toString())
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text");

            parseAndDisplayInsight(aiText);
        } catch (Exception e) {
            e.printStackTrace();
            if (getActivity() != null) requireActivity().runOnUiThread(() -> {
                tvInsight.setText("Could not load insight. Check your connection.");
                tvUrgency.setText("—");
            });
        }
    }

    private void parseAndDisplayInsight(String aiText) {
        String insight = "", urgency = "", tip = "";
        String tip1Icon = "🚶", tip1Title = "", tip1Body = "";
        String tip2Icon = "💡", tip2Title = "", tip2Body = "";

        for (String line : aiText.split("\n")) {
            String t = line.trim();
            if (t.toLowerCase().startsWith("insight:"))        insight   = t.replaceFirst("(?i)insight:\\s*", "");
            else if (t.toLowerCase().startsWith("urgency:"))   urgency   = t.replaceFirst("(?i)urgency:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip1icon:"))  tip1Icon  = t.replaceFirst("(?i)tip1icon:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip1title:")) tip1Title = t.replaceFirst("(?i)tip1title:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip1body:"))  tip1Body  = t.replaceFirst("(?i)tip1body:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip2icon:"))  tip2Icon  = t.replaceFirst("(?i)tip2icon:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip2title:")) tip2Title = t.replaceFirst("(?i)tip2title:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip2body:"))  tip2Body  = t.replaceFirst("(?i)tip2body:\\s*", "").trim();
            else if (t.toLowerCase().startsWith("tip:"))       tip       = t.replaceFirst("(?i)tip:\\s*", "").trim();
        }

        int urgencyColor = urgency.equalsIgnoreCase("High")   ? 0xFFD32F2F
                : urgency.equalsIgnoreCase("Medium") ? 0xFFF9A825
                :                                      0xFF00A36C;

        String fi = insight, fu = urgency, ft = tip;
        String f1i = tip1Icon, f1t = tip1Title, f1b = tip1Body;
        String f2i = tip2Icon, f2t = tip2Title, f2b = tip2Body;
        int fc = urgencyColor;

        if (getActivity() != null) requireActivity().runOnUiThread(() -> {
            tvInsight.setText(fi.isEmpty() ? "No insight available." : fi);
            tvTip.setText(ft);
            tvUrgency.setText(fu.isEmpty() ? "—" : fu);

            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.RECTANGLE);
            badge.setCornerRadius(60f);
            badge.setColor(fc);
            tvUrgency.setBackground(badge);
            tvUrgency.setTextColor(0xFFFFFFFF);

            tvTip1Icon.setText(f1i);
            tvTip1Title.setText(f1t.isEmpty() ? "Activity Tip" : f1t);
            tvTip1Body.setText(f1b);
            tvTip2Icon.setText(f2i);
            tvTip2Title.setText(f2t.isEmpty() ? "Health Tip" : f2t);
            tvTip2Body.setText(f2b);
        });
    }

    // ── Real News Articles via NewsAPI ────────────────────────────────────────

    static class Article {
        String source, title, description, url, publishedAt, imageUrl, category;
        Article(String source, String title, String description, String url,
                String publishedAt, String imageUrl, String category) {
            this.source = source; this.title = title; this.description = description;
            this.url = url; this.publishedAt = publishedAt;
            this.imageUrl = imageUrl; this.category = category;
        }
    }

    // Rotating search queries so each app-open shows different article topics
    private static final String[] NEWS_QUERIES = {
            "diabetes insulin management blood sugar",
            "type 2 diabetes glucose control diet",
            "diabetes medication A1C treatment",
            "insulin resistance metabolic health",
            "diabetes exercise lifestyle blood glucose"
    };

    // Category tags auto-assigned by keywords in title
    private static final String[][] CATEGORY_RULES = {
            {"Nutrition",  "diet", "food", "eat", "meal", "carb", "glycemic", "nutrition"},
            {"Exercise",   "exercise", "walk", "activity", "fitness", "movement"},
            {"Medication", "insulin", "metformin", "medication", "drug", "dose", "ozempic", "glp"},
            {"Research",   "study", "trial", "research", "clinical", "scientists", "found"},
            {"Lifestyle",  "sleep", "stress", "weight", "lifestyle", "habit"},
            {"Technology", "cgm", "pump", "sensor", "device", "app", "monitor", "wearable"},
    };

    private static final int[] CATEGORY_COLORS = {
            0xFF2E7D32,  // Nutrition → green
            0xFF1565C0,  // Exercise  → blue
            0xFF6A1B9A,  // Medication → purple
            0xFF00695C,  // Research  → teal
            0xFFE65100,  // Lifestyle → orange
            0xFF37474F,  // Technology → grey-blue
            0xFF1A2B4C,  // Default
    };

    private void fetchNewsArticles() {
        new Thread(() -> {
            try {
                // Pick a random query so topics vary on each visit
                String query = NEWS_QUERIES[(int)(Math.random() * NEWS_QUERIES.length)];
                String endpoint = "https://newsapi.org/v2/everything?"
                        + "q=" + query.replace(" ", "+")
                        + "&language=en"
                        + "&sortBy=publishedAt"
                        + "&pageSize=15"
                        + "&apiKey=" + NEWS_API_KEY;

                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "GlucoCareApp/1.0");
                conn.connect();

                int code = conn.getResponseCode();
                BufferedReader br = (code >= 200 && code < 300)
                        ? new BufferedReader(new InputStreamReader(conn.getInputStream()))
                        : new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                JSONObject json = new JSONObject(response.toString());
                articleList.clear();

                if ("ok".equals(json.optString("status"))) {
                    JSONArray articles = json.getJSONArray("articles");
                    for (int i = 0; i < articles.length() && articleList.size() < 8; i++) {
                        JSONObject a = articles.getJSONObject(i);
                        String title    = a.optString("title", "").trim();
                        String desc     = a.optString("description", "").trim();
                        String artUrl   = a.optString("url", "").trim();
                        String imgUrl   = a.optString("urlToImage", "").trim();
                        String pub      = a.optString("publishedAt", "").trim();
                        String srcName  = a.optJSONObject("source") != null
                                ? a.optJSONObject("source").optString("name", "") : "";

                        if (title.isEmpty() || title.equals("[Removed]") || artUrl.isEmpty()) continue;
                        if (desc.length() > 120) desc = desc.substring(0, 117) + "…";

                        String dateLabel = formatPublishedDate(pub);
                        String srcLine   = srcName.isEmpty() ? dateLabel : srcName + "  ·  " + dateLabel;
                        String category  = detectCategory(title + " " + desc);

                        articleList.add(new Article(srcLine, title, desc, artUrl, pub, imgUrl, category));
                    }
                }

                if (articleList.isEmpty()) useFallbackArticles();
                if (getActivity() != null) requireActivity().runOnUiThread(this::renderArticleCards);

            } catch (Exception e) {
                e.printStackTrace();
                useFallbackArticles();
                if (getActivity() != null) requireActivity().runOnUiThread(this::renderArticleCards);
            }
        }).start();
    }

    private String detectCategory(String text) {
        String lower = text.toLowerCase();
        for (String[] rule : CATEGORY_RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (lower.contains(rule[i])) return rule[0];
            }
        }
        return "Health";
    }

    private int categoryColor(String category) {
        String[] names = {"Nutrition","Exercise","Medication","Research","Lifestyle","Technology"};
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(category)) return CATEGORY_COLORS[i];
        }
        return CATEGORY_COLORS[6];
    }

    // Light pastel background matching each category pill color
    private int categoryBgColor(String category) {
        switch (category) {
            case "Nutrition":   return 0xFFE8F5E9;  // light green
            case "Exercise":    return 0xFFE3F2FD;  // light blue
            case "Medication":  return 0xFFF3E5F5;  // light purple
            case "Research":    return 0xFFE0F2F1;  // light teal
            case "Lifestyle":   return 0xFFFFF3E0;  // light orange
            case "Technology":  return 0xFFECEFF1;  // light grey-blue
            default:            return 0xFFF0F4FF;  // light indigo
        }
    }
    private String formatPublishedDate(String isoDate) {
        try {
            java.text.SimpleDateFormat iso =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            Date d = iso.parse(isoDate);
            long diff = (new Date().getTime() - d.getTime()) / (1000 * 60 * 60);

            if (diff < 1)  return "Just now";
            if (diff < 24) return diff + "h ago";

            long days = diff / 24;
            if (days == 1) return "Yesterday";
            if (days < 7)  return days + " days ago";

            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d);

        } catch (Exception e) {
            return "";
        }
    }

    private void useFallbackArticles() {
        articleList.clear();
        articleList.add(new Article("American Diabetes Association", "Managing Your Blood Sugar", "Daily habits that keep glucose in range: timing meals, staying active, and monitoring trends.", "https://diabetes.org/health-wellness/blood-glucose-management", "", "", "Health"));
        articleList.add(new Article("CDC", "The Glycemic Index Explained", "How different carbohydrates affect your blood sugar speed — and which ones to choose.", "https://www.cdc.gov/diabetes/library/features/diabetes-and-fiber.html", "", "", "Nutrition"));
        articleList.add(new Article("Mayo Clinic", "Insulin Therapy for Diabetes", "Types of insulin, how to inject, timing strategies, and adjusting doses with your doctor.", "https://www.mayoclinic.org/diseases-conditions/type-1-diabetes/in-depth/insulin-therapy/art-20044452", "", "", "Medication"));
        articleList.add(new Article("Healthline", "Exercise & Blood Sugar Control", "How aerobic exercise and strength training both lower A1C and improve insulin sensitivity.", "https://www.healthline.com/health/diabetes/exercise", "", "", "Exercise"));
        articleList.add(new Article("WebMD", "Sleep, Stress & Glucose Spikes", "Poor sleep and high cortisol directly raise fasting blood sugar — here's what to do about it.", "https://www.webmd.com/diabetes/sleep-affects-blood-sugar", "", "", "Lifestyle"));
    }

    private void renderArticleCards() {
        educationalContainer.removeAllViews();
        int previewCount = Math.min(3, articleList.size());
        for (int i = 0; i < previewCount; i++) {
            educationalContainer.addView(buildArticleCard(articleList.get(i)));
        }
        btnViewAll.setVisibility(articleList.size() > 3 ? View.VISIBLE : View.GONE);
    }

    /**
     * Google Discover-style card:
     * ┌────────────────────────────────────┐
     * │ [CATEGORY PILL]                    │
     * │ Title text (2 lines, bold)         │
     * │ Description (2 lines, muted)       │
     * │ ─────────────────────────────────  │
     * │ Source name            time ago    │
     * └────────────────────────────────────┘
     */
    private View buildArticleCard(Article article) {
        float dp = getResources().getDisplayMetrics().density;

        CardView card = new CardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int)(10 * dp);
        card.setLayoutParams(cardParams);
        card.setRadius(18 * dp);
        card.setCardElevation(2 * dp);
        card.setCardBackgroundColor(categoryBgColor(article.category));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout outer = new LinearLayout(requireContext());
        outer.setOrientation(LinearLayout.VERTICAL);
        int padH = (int)(16 * dp);
        int padV = (int)(14 * dp);
        outer.setPadding(padH, padV, padH, padV);
        outer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Category pill ──────────────────────────────────────────────────
        TextView categoryPill = new TextView(requireContext());
        categoryPill.setText(article.category);
        categoryPill.setTextSize(10f);
        categoryPill.setTypeface(null, android.graphics.Typeface.BOLD);
        categoryPill.setTextColor(0xFFFFFFFF);
        categoryPill.setLetterSpacing(0.04f);
        int pillPadH = (int)(10 * dp), pillPadV = (int)(4 * dp);
        categoryPill.setPadding(pillPadH, pillPadV, pillPadH, pillPadV);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(20 * dp);
        pillBg.setColor(categoryColor(article.category));
        categoryPill.setBackground(pillBg);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pillParams.bottomMargin = (int)(8 * dp);
        categoryPill.setLayoutParams(pillParams);
        outer.addView(categoryPill);

        // ── Title ──────────────────────────────────────────────────────────
        TextView titleView = new TextView(requireContext());
        // Clean " - Source Name" suffixes common in news titles
        String cleanTitle = article.title.replaceAll("\\s*[-–|]\\s*[^-–|]{1,40}$", "").trim();
        titleView.setText(cleanTitle);
        titleView.setTextSize(15f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(0xFF0F172A);
        titleView.setLineSpacing(0f, 1.25f);
        titleView.setMaxLines(3);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        outer.addView(titleView);

        // ── Description ────────────────────────────────────────────────────
        if (!article.description.isEmpty()) {
            TextView descView = new TextView(requireContext());
            descView.setText(article.description);
            descView.setTextSize(12f);
            descView.setTextColor(0xFF64748B);
            descView.setLineSpacing(0f, 1.4f);
            descView.setMaxLines(2);
            descView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = (int)(5 * dp);
            descView.setLayoutParams(descParams);
            outer.addView(descView);
        }

        // ── Divider ────────────────────────────────────────────────────────
        View divider = new View(requireContext());
        divider.setBackgroundColor(0xFFEEF0F3);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * dp));
        divParams.topMargin    = (int)(12 * dp);
        divParams.bottomMargin = (int)(10 * dp);
        divider.setLayoutParams(divParams);
        outer.addView(divider);

        // ── Footer: source left, time right ───────────────────────────────
        LinearLayout footer = new LinearLayout(requireContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Split source line back into source and date parts if possible
        String sourceName = article.source;
        String timeLabel  = "";
        if (article.source.contains("  ·  ")) {
            String[] parts = article.source.split("  ·  ", 2);
            sourceName = parts[0].trim();
            timeLabel  = parts[1].trim();
        }

        TextView sourceView = new TextView(requireContext());
        sourceView.setText(sourceName);
        sourceView.setTextSize(11f);
        sourceView.setTextColor(0xFF94A3B8);
        sourceView.setTypeface(null, android.graphics.Typeface.BOLD);
        sourceView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        footer.addView(sourceView);

        if (!timeLabel.isEmpty()) {
            TextView timeView = new TextView(requireContext());
            timeView.setText(timeLabel);
            timeView.setTextSize(11f);
            timeView.setTextColor(0xFFB0BEC5);
            footer.addView(timeView);
        }

        outer.addView(footer);
        card.addView(outer);

        String articleUrl = article.url;
        card.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)));
            } catch (Exception ignored) {}
        });

        return card;
    }

    private void showAllArticles() {
        educationalContainer.removeAllViews();
        for (Article article : articleList) {
            educationalContainer.addView(buildArticleCard(article));
        }
        btnViewAll.setVisibility(View.GONE);
    }
}
