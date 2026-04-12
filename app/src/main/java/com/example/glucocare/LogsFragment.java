package com.example.glucocare;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.glucocare.repository.GlucoseRepository;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * LogsFragment — updated to use GlucoseRepository (Room + Firebase).
 *
 * All DB operations go through GlucoseRepository which handles
 * local Room storage and Firestore sync transparently.
 */
public class LogsFragment extends Fragment {

    // ── Views ────────────────────────────────────────────────────────────────
    private EditText    etGlucoseLevel, etNotes;
    private ImageButton btnIncrement, btnDecrement, btnScan;
    private Button      btnLiveAnalysis, btnSaveReading;
    private TextView    tvAiInsight, tvAiSuggestion, tvDateTime, tvDiscardEntry;

    // Timing rows
    private LinearLayout  rowFasting, rowBeforeMeal, rowAfterMeal;
    private ImageView     ivFastingCheck, ivBeforeMealCheck, ivAfterMealCheck;
    private String        selectedTiming = "Before Meal";

    // ── State ────────────────────────────────────────────────────────────────
    private Calendar selectedDateTime = Calendar.getInstance();
    private Uri      cameraImageUri;

    // ── Repository (replaces direct DAO access) ───────────────────────────────
    private GlucoseRepository glucoseRepository;
    private OcrHelper         ocrHelper;

    // ── Launchers ────────────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera(); else toast("Camera permission denied.");
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraImageUri != null)
                    runOcr(cameraImageUri);
            });

    private final ActivityResultLauncher<String> galleryPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchGallery(); else toast("Storage permission denied.");
            });

    private final ActivityResultLauncher<String> galleryPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) runOcr(uri);
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        glucoseRepository = new GlucoseRepository(requireContext());
        ocrHelper         = new OcrHelper();
        bindViews(view);
        updateDateTimeDisplay();
        setupListeners();
        selectTiming("Before Meal"); // default
    }

    // ── Bind ─────────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        etGlucoseLevel   = v.findViewById(R.id.etGlucoseLevel);
        btnIncrement     = v.findViewById(R.id.btnIncrement);
        btnDecrement     = v.findViewById(R.id.btnDecrement);
        btnScan          = v.findViewById(R.id.btnScan);
        btnLiveAnalysis  = v.findViewById(R.id.btnLiveAnalysis);
        btnSaveReading   = v.findViewById(R.id.btnSaveReading);
        tvAiInsight      = v.findViewById(R.id.tvAiInsight);
        tvAiSuggestion   = v.findViewById(R.id.tvAiSuggestion);
        tvDateTime       = v.findViewById(R.id.tvDateTime);
        tvDiscardEntry   = v.findViewById(R.id.tvDiscardEntry);
        etNotes          = v.findViewById(R.id.etNotes);
        rowFasting       = v.findViewById(R.id.rowFasting);
        rowBeforeMeal    = v.findViewById(R.id.rowBeforeMeal);
        rowAfterMeal     = v.findViewById(R.id.rowAfterMeal);
        ivFastingCheck   = v.findViewById(R.id.ivFastingCheck);
        ivBeforeMealCheck= v.findViewById(R.id.ivBeforeMealCheck);
        ivAfterMealCheck = v.findViewById(R.id.ivAfterMealCheck);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        btnIncrement.setOnClickListener(v -> setGlucose(Math.min(parseGlucose() + 1, 999)));
        btnDecrement.setOnClickListener(v -> setGlucose(Math.max(parseGlucose() - 1, 0)));

        etGlucoseLevel.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateAiInsight(); }
        });

        rowFasting.setOnClickListener(v    -> selectTiming("Fasting"));
        rowBeforeMeal.setOnClickListener(v -> selectTiming("Before Meal"));
        rowAfterMeal.setOnClickListener(v  -> selectTiming("After Meal"));

        btnScan.setOnClickListener(v -> showScanOptions());
        btnLiveAnalysis.setOnClickListener(v -> {
            if (parseGlucose() == 0) { toast("Enter a glucose level first."); return; }
            updateAiInsight();
        });

        requireView().findViewById(R.id.rowDateTime)
                .setOnClickListener(v -> showDatePicker());

        btnSaveReading.setOnClickListener(v -> saveReading());
        tvDiscardEntry.setOnClickListener(v -> confirmDiscard());
    }

    // ── Timing ────────────────────────────────────────────────────────────────

    private void selectTiming(String timing) {
        selectedTiming = timing;
        setRowSelected(rowFasting,    ivFastingCheck,    false);
        setRowSelected(rowBeforeMeal, ivBeforeMealCheck, false);
        setRowSelected(rowAfterMeal,  ivAfterMealCheck,  false);
        switch (timing) {
            case "Fasting":     setRowSelected(rowFasting,    ivFastingCheck,    true); break;
            case "Before Meal": setRowSelected(rowBeforeMeal, ivBeforeMealCheck, true); break;
            case "After Meal":  setRowSelected(rowAfterMeal,  ivAfterMealCheck,  true); break;
        }
        updateAiInsight();
    }

    private void setRowSelected(LinearLayout row, ImageView check, boolean selected) {
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
        row.setBackgroundResource(selected
                ? R.drawable.bg_timing_option_selected
                : R.drawable.bg_timing_option);
    }

    // ── Glucose helpers ───────────────────────────────────────────────────────

    private float parseGlucose() {
        try { return Float.parseFloat(etGlucoseLevel.getText().toString().trim()); }
        catch (NumberFormatException e) { return 0f; }
    }

    private void setGlucose(float value) {
        String display = (value == Math.floor(value))
                ? String.valueOf((int) value)
                : String.format(Locale.getDefault(), "%.1f", value);
        etGlucoseLevel.setText(display);
        etGlucoseLevel.setSelection(etGlucoseLevel.getText().length());
    }

    // ── AI Insight ────────────────────────────────────────────────────────────

    private void updateAiInsight() {
        float glucose = parseGlucose();
        if (glucose == 0) {
            tvAiInsight.setText("Enter your glucose level to get a personalized AI insight.");
            tvAiSuggestion.setVisibility(View.GONE);
            return;
        }

        // Fetch 7-day average from repository (Room)
        glucoseRepository.getSevenDayAverage(selectedTiming,
                new GlucoseRepository.Callback<Float>() {
                    @Override public void onResult(Float avg) {
                        String[] insight = buildInsight(glucose, selectedTiming, avg);
                        requireActivity().runOnUiThread(() -> {
                            tvAiInsight.setText(insight[0]);
                            tvAiSuggestion.setText(insight[1]);
                            tvAiSuggestion.setVisibility(View.VISIBLE);
                        });
                    }
                    @Override public void onError(String error) {
                        String[] insight = buildInsight(glucose, selectedTiming, -1f);
                        requireActivity().runOnUiThread(() -> {
                            tvAiInsight.setText(insight[0]);
                            tvAiSuggestion.setVisibility(View.GONE);
                        });
                    }
                });
    }

    private String[] buildInsight(float glucose, String timing, float avg) {
        String main, sub;
        if ("Fasting".equals(timing)) {
            if (glucose < 70)        { main = "⚠️ Fasting level " + fmt(glucose) + " mg/dL is low."; sub = "Have 15g of fast-acting carbs and re-check in 15 min."; }
            else if (glucose <= 99)  { main = "✅ Fasting level " + fmt(glucose) + " mg/dL is normal."; sub = avg > 0 ? "This is " + compare(glucose, avg) + " your 7-day avg of " + fmt(avg) + " mg/dL." : "Keep it up!"; }
            else if (glucose <= 125) { main = "⚡ Fasting level " + fmt(glucose) + " mg/dL is slightly elevated."; sub = "Consider a low-glycemic breakfast and a morning walk."; }
            else                     { main = "🚨 Fasting level " + fmt(glucose) + " mg/dL is high. Consult your provider."; sub = "Track your next reading carefully."; }
        } else if ("Before Meal".equals(timing)) {
            if (glucose < 80)        { main = "⚠️ Pre-meal level " + fmt(glucose) + " mg/dL is below target."; sub = "Consider a small snack before your meal."; }
            else if (glucose <= 130) { main = "✅ Pre-meal level " + fmt(glucose) + " mg/dL looks good."; sub = avg > 0 ? "This is " + compare(glucose, avg) + " your 7-day avg of " + fmt(avg) + " mg/dL." : "Aim for fiber, protein and moderate carbs."; }
            else                     { main = "⚡ Pre-meal level " + fmt(glucose) + " mg/dL is above target."; sub = "Choose low-GI carbohydrates and lean protein."; }
        } else {
            if (glucose <= 140)      { main = "✅ Post-meal level " + fmt(glucose) + " mg/dL is healthy."; sub = avg > 0 ? "This is " + compare(glucose, avg) + " your 7-day avg of " + fmt(avg) + " mg/dL." : "A 10-min walk helps stabilize levels."; }
            else if (glucose <= 180) { main = "⚡ Post-meal level " + fmt(glucose) + " mg/dL is slightly high."; sub = "Light activity after eating helps glucose uptake."; }
            else                     { main = "🚨 Post-meal level " + fmt(glucose) + " mg/dL is high. Review your meal."; sub = "Avoid repeat high-carb meals."; }
        }
        return new String[]{main, sub};
    }

    private String fmt(float v) {
        return (v == Math.floor(v)) ? String.valueOf((int) v)
                : String.format(Locale.getDefault(), "%.1f", v);
    }

    private String compare(float current, float avg) {
        float diff = current - avg;
        if (Math.abs(diff) < 2) return "on par with";
        return diff > 0 ? "slightly higher than" : "slightly lower than";
    }

    // ── Date / Time ───────────────────────────────────────────────────────────

    private void showDatePicker() {
        new DatePickerDialog(requireContext(), (v, y, m, d) -> {
            selectedDateTime.set(Calendar.YEAR, y);
            selectedDateTime.set(Calendar.MONTH, m);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, d);
            showTimePicker();
        }, selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(requireContext(), (v, h, min) -> {
            selectedDateTime.set(Calendar.HOUR_OF_DAY, h);
            selectedDateTime.set(Calendar.MINUTE, min);
            updateDateTimeDisplay();
        }, selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE), false).show();
    }

    private void updateDateTimeDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy, hh:mm a", Locale.getDefault());
        tvDateTime.setText(sdf.format(selectedDateTime.getTime()));
    }

    // ── OCR ───────────────────────────────────────────────────────────────────

    private void showScanOptions() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Scan Glucose Value")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (d, which) -> {
                    if (which == 0) checkCameraPermission(); else checkGalleryPermission();
                }).show();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        try {
            File f = File.createTempFile("GLUCOSE_", ".jpg", requireContext().getExternalFilesDir(null));
            cameraImageUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(i);
        } catch (IOException e) { toast("Could not create image file."); }
    }

    private void checkGalleryPermission() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(requireContext(), perm)
                == PackageManager.PERMISSION_GRANTED) launchGallery();
        else galleryPermissionLauncher.launch(perm);
    }

    private void launchGallery() { galleryPickerLauncher.launch("image/*"); }

    private void runOcr(Uri uri) {
        // Alternative to deprecated ProgressDialog: Custom AlertDialog with ProgressBar
        ProgressBar progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleLarge);
        progressBar.setPadding(0, 40, 0, 40);

        AlertDialog progressDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Reading Glucometer")
                .setMessage("Analysing with Gemini AI…")
                .setView(progressBar)
                .setCancelable(false)
                .create();

        progressDialog.show();

        ocrHelper.extractGlucoseValue(requireContext(), uri, new OcrHelper.OcrCallback() {
            @Override
            public void onSuccess(int glucoseValue) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showOcrConfirmDialog(glucoseValue);
                });
            }

            @Override
            public void onFailure(String msg) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Could Not Read Value")
                            .setMessage(msg)
                            .setPositiveButton("Try Again", (d, w) -> showScanOptions())
                            .setNegativeButton("Enter Manually", (d, w) -> etGlucoseLevel.requestFocus())
                            .show();
                });
            }
        });
    }

    /**
     * Shows a confirmation dialog after OCR detects a value.
     * Lets the user verify and correct the number before it fills the field.
     * This is critical for glucometer screens where OCR can be off by a digit.
     */
    private void showOcrConfirmDialog(int detectedValue) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 24, 60, 8);

        android.widget.TextView label = new android.widget.TextView(requireContext());
        label.setText("Gemini detected this value.\nCorrect it if needed:");
        label.setTextSize(13f);
        label.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_muted));
        layout.addView(label);

        android.widget.EditText etConfirm = new android.widget.EditText(requireContext());
        etConfirm.setText(String.valueOf(detectedValue));
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etConfirm.setTextSize(36f);
        etConfirm.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary));
        etConfirm.setGravity(android.view.Gravity.CENTER);
        layout.addView(etConfirm);

        android.widget.TextView unit = new android.widget.TextView(requireContext());
        unit.setText("mg/dL");
        unit.setTextSize(13f);
        unit.setGravity(android.view.Gravity.CENTER);
        unit.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_muted));
        layout.addView(unit);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Reading ✓")
                .setView(layout)
                .setPositiveButton("Use This Value", (dialog, which) -> {
                    String input = etConfirm.getText().toString().trim();
                    try {
                        int confirmed = Integer.parseInt(input);
                        if (confirmed < 20 || confirmed > 600) {
                            snack("Value must be between 20 and 600 mg/dL.");
                            return;
                        }
                        setGlucose(confirmed);
                        updateAiInsight();
                        snack("Glucose set to " + confirmed + " mg/dL ✓");
                    } catch (NumberFormatException e) {
                        snack("Invalid number — please enter manually.");
                    }
                })
                .setNegativeButton("Scan Again", (d, w) -> showScanOptions())
                .setNeutralButton("Cancel", null)
                .show();

        etConfirm.post(etConfirm::selectAll);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveReading() {
        float glucose = parseGlucose();
        if (glucose == 0)              { snack("Please enter a glucose level."); return; }
        if (glucose < 20 || glucose > 600) { snack("Value must be between 20 and 600 mg/dL."); return; }

        GlucoseReading reading = new GlucoseReading(
                glucose, selectedTiming,
                selectedDateTime.getTimeInMillis(),
                etNotes.getText().toString().trim()
        );

        // ── Repository handles Room + Firestore sync ──────────────────────────
        glucoseRepository.saveReading(reading, new GlucoseRepository.Callback<Void>() {
            @Override public void onResult(Void v) {
                requireActivity().runOnUiThread(() -> { snack("Reading saved! 🎉"); clearForm(); });
            }
            @Override public void onError(String error) {
                // Saved locally even if Firestore failed
                requireActivity().runOnUiThread(() -> { snack("Saved locally (will sync when online)."); clearForm(); });
            }
        });
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Discard Entry")
                .setMessage("Discard this entry?")
                .setPositiveButton("Discard", (d, w) -> clearForm())
                .setNegativeButton("Cancel", null).show();
    }

    private void clearForm() {
        etGlucoseLevel.setText("");
        etNotes.setText("");
        selectedDateTime = Calendar.getInstance();
        updateDateTimeDisplay();
        selectTiming("Before Meal");
        tvAiInsight.setText("Enter your glucose level to get a personalized AI insight.");
        tvAiSuggestion.setVisibility(View.GONE);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void toast(String msg) { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show(); }
    private void snack(String msg) { Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show(); }
}
