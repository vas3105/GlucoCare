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

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LogsFragment — "New Glucose Log" screen.
 *
 * Sits inside MainActivity via BottomNavigationView + FragmentManager.
 * Persists data with Room through AppDatabase / GlucoseDao.
 * OCR via OcrHelper (ML Kit).
 */
public class LogsFragment extends Fragment {

    // ── Views ────────────────────────────────────────────────────────────────
    private EditText      etGlucoseLevel, etNotes;
    private ImageButton   btnIncrement, btnDecrement, btnScan;
    private Button        btnLiveAnalysis, btnSaveReading;
    private LinearLayout  rowFasting, rowBeforeMeal, rowAfterMeal;
    private ImageView     ivFastingCheck, ivBeforeMealCheck, ivAfterMealCheck;
    private String        selectedTiming = "Before Meal"; // default
    private TextView      tvAiInsight, tvAiSuggestion, tvDateTime, tvDiscardEntry;

    // ── State ────────────────────────────────────────────────────────────────
    private Calendar selectedDateTime = Calendar.getInstance();
    private Uri      cameraImageUri;

    // ── Helpers ──────────────────────────────────────────────────────────────
    private OcrHelper        ocrHelper;
    private GlucoseDao       glucoseDao;

    /** Background thread for all Room (database) operations. */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // ── Activity-result launchers ────────────────────────────────────────────

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else         toast("Camera permission denied.");
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraImageUri != null)
                    runOcr(cameraImageUri);
            });

    private final ActivityResultLauncher<String> galleryPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchGallery();
                else         toast("Storage permission denied.");
            });

    private final ActivityResultLauncher<String> galleryPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) runOcr(uri);
            });

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Room DAO via singleton AppDatabase
        glucoseDao = AppDatabase.getInstance(requireContext()).glucoseDao();
        ocrHelper  = new OcrHelper();

        bindViews(view);
        setDefaultDateTime();
        setupListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dbExecutor.shutdown();
    }

    // ── Bind ────────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        etGlucoseLevel  = v.findViewById(R.id.etGlucoseLevel);
        btnIncrement    = v.findViewById(R.id.btnIncrement);
        btnDecrement    = v.findViewById(R.id.btnDecrement);
        btnScan         = v.findViewById(R.id.btnScan);
        btnLiveAnalysis = v.findViewById(R.id.btnLiveAnalysis);
        btnSaveReading  = v.findViewById(R.id.btnSaveReading);
        rowFasting       = v.findViewById(R.id.rowFasting);
        rowBeforeMeal    = v.findViewById(R.id.rowBeforeMeal);
        rowAfterMeal     = v.findViewById(R.id.rowAfterMeal);
        ivFastingCheck   = v.findViewById(R.id.ivFastingCheck);
        ivBeforeMealCheck= v.findViewById(R.id.ivBeforeMealCheck);
        ivAfterMealCheck = v.findViewById(R.id.ivAfterMealCheck);
        tvAiInsight     = v.findViewById(R.id.tvAiInsight);
        tvAiSuggestion  = v.findViewById(R.id.tvAiSuggestion);
        tvDateTime      = v.findViewById(R.id.tvDateTime);
        tvDiscardEntry  = v.findViewById(R.id.tvDiscardEntry);
        etNotes         = v.findViewById(R.id.etNotes);
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    private void setupListeners() {

        // Stepper
        btnIncrement.setOnClickListener(v -> {
            float val = parseGlucose();
            setGlucose(Math.min(val + 1, 999));
        });
        btnDecrement.setOnClickListener(v -> {
            float val = parseGlucose();
            setGlucose(Math.max(val - 1, 0));
        });

        // Live insight as user types
        etGlucoseLevel.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateAiInsight(); }
        });

        // Timing rows — clicking the whole row selects the radio
        rowFasting.setOnClickListener(v    -> selectTiming("Fasting"));
        rowBeforeMeal.setOnClickListener(v -> selectTiming("Before Meal"));
        rowAfterMeal.setOnClickListener(v  -> selectTiming("After Meal"));
        // Scan
        btnScan.setOnClickListener(v -> showScanOptions());

        // Live Analysis button
        btnLiveAnalysis.setOnClickListener(v -> {
            if (parseGlucose() == 0) { toast("Enter a glucose level first."); return; }
            updateAiInsight();
            toast("Insight updated!");
        });

        // Date/time
        requireView().findViewById(R.id.rowDateTime)
                .setOnClickListener(v -> showDatePicker());

        // Save
        btnSaveReading.setOnClickListener(v -> saveReading());

        // Discard
        tvDiscardEntry.setOnClickListener(v -> confirmDiscard());
    }
    /** Visually selects one timing row and deselects the others. */
    private void selectTiming(String timing) {
        selectedTiming = timing;

        // Reset all rows to unselected look
        setRowSelected(rowFasting,    ivFastingCheck,    false);
        setRowSelected(rowBeforeMeal, ivBeforeMealCheck, false);
        setRowSelected(rowAfterMeal,  ivAfterMealCheck,  false);

        // Highlight the chosen row
        switch (timing) {
            case "Fasting":
                setRowSelected(rowFasting,    ivFastingCheck,    true); break;
            case "Before Meal":
                setRowSelected(rowBeforeMeal, ivBeforeMealCheck, true); break;
            case "After Meal":
                setRowSelected(rowAfterMeal,  ivAfterMealCheck,  true); break;
        }

        updateAiInsight();
    }

    /** Applies selected/unselected visual styling to a timing row. */
    private void setRowSelected(LinearLayout row, ImageView checkIcon, boolean selected) {
        checkIcon.setVisibility(selected ? View.VISIBLE : View.GONE);
        // Optional: tint the row background when selected
        row.setBackgroundResource(selected
                ? R.drawable.bg_timing_option_selected   // create this (see note below)
                : R.drawable.bg_timing_option);
    }

    // ── Glucose helpers ──────────────────────────────────────────────────────

    private float parseGlucose() {
        try { return Float.parseFloat(etGlucoseLevel.getText().toString().trim()); }
        catch (NumberFormatException e) { return 0f; }
    }

    private void setGlucose(float value) {
        // Show as integer when whole number, otherwise 1 decimal
        String display = (value == Math.floor(value))
                ? String.valueOf((int) value)
                : String.format(Locale.getDefault(), "%.1f", value);
        etGlucoseLevel.setText(display);
        etGlucoseLevel.setSelection(etGlucoseLevel.getText().length());
    }

    // ── AI Insight ───────────────────────────────────────────────────────────

    private void updateAiInsight() {
        float glucose = parseGlucose();
        if (glucose == 0) {
            tvAiInsight.setText("Enter your glucose level to get a personalized AI insight.");
            tvAiSuggestion.setVisibility(View.GONE);
            return;
        }

        String timing = getSelectedTiming();

        // Fetch 7-day average on background thread, then update UI
        dbExecutor.execute(() -> {
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            float avg = glucoseDao.getAverageForType(timing, sevenDaysAgo);

            String main, sub;

            if ("Fasting".equals(timing)) {
                if (glucose < 70) {
                    main = "⚠️ Your fasting level of " + fmt(glucose)
                            + " mg/dL is low (hypoglycemia range). Please have a snack.";
                    sub  = "Have 15 g of fast-acting carbs and re-check in 15 minutes.";
                } else if (glucose <= 99) {
                    main = "✅ Your fasting level of " + fmt(glucose) + " mg/dL is normal.";
                    sub  = avg > 0
                            ? "This is " + compare(glucose, avg) + " your 7-day fasting avg of " + fmt(avg) + " mg/dL."
                            : "Keep up the great work!";
                } else if (glucose <= 125) {
                    main = "⚡ Fasting level " + fmt(glucose) + " mg/dL is slightly elevated (pre-diabetes range).";
                    sub  = "Consider a low-glycemic breakfast and a short morning walk.";
                } else {
                    main = "🚨 Fasting level " + fmt(glucose) + " mg/dL is high. Please consult your provider.";
                    sub  = "Avoid high-carb meals and track your next reading carefully.";
                }
            } else if ("Before Meal".equals(timing)) {
                if (glucose < 80) {
                    main = "⚠️ Pre-meal level " + fmt(glucose) + " mg/dL is below target. Consider a small snack.";
                    sub  = "Adjust insulin timing only as directed by your doctor.";
                } else if (glucose <= 130) {
                    main = "✅ Pre-meal level " + fmt(glucose) + " mg/dL looks good. Proceed with a balanced meal.";
                    sub  = avg > 0
                            ? "This is " + compare(glucose, avg) + " your 7-day pre-meal avg of " + fmt(avg) + " mg/dL."
                            : "Aim for fiber, protein and moderate carbs.";
                } else {
                    main = "⚡ Pre-meal level " + fmt(glucose) + " mg/dL is above target.";
                    sub  = "Choose vegetables, lean protein and low-GI carbohydrates.";
                }
            } else { // After Meal
                if (glucose <= 140) {
                    main = "✅ Post-meal level " + fmt(glucose) + " mg/dL is within the healthy range.";
                    sub  = avg > 0
                            ? "This is " + compare(glucose, avg) + " your 7-day post-meal avg of " + fmt(avg) + " mg/dL."
                            : "A 10-minute walk can help stabilise levels further.";
                } else if (glucose <= 180) {
                    main = "⚡ Post-meal level " + fmt(glucose) + " mg/dL is slightly elevated.";
                    sub  = "Light activity after eating helps glucose uptake.";
                } else {
                    main = "🚨 Post-meal level " + fmt(glucose) + " mg/dL is high. Review your meal and consult your doctor.";
                    sub  = "Avoid repeat high-carb meals. Ask your provider about your 2-hour target.";
                }
            }

            final String finalMain = main;
            final String finalSub  = sub;

            requireActivity().runOnUiThread(() -> {
                tvAiInsight.setText(finalMain);
                tvAiSuggestion.setText(finalSub);
                tvAiSuggestion.setVisibility(View.VISIBLE);
            });
        });
    }

    private String fmt(float v) {
        return (v == Math.floor(v))
                ? String.valueOf((int) v)
                : String.format(Locale.getDefault(), "%.1f", v);
    }

    private String compare(float current, float avg) {
        float diff = current - avg;
        if (Math.abs(diff) < 2) return "on par with";
        return diff > 0 ? "slightly higher than" : "slightly lower than";
    }

    private String getSelectedTiming() {
        return selectedTiming;
    }

    // ── Date / Time ──────────────────────────────────────────────────────────

    private void setDefaultDateTime() { updateDateTimeDisplay(); }

    private void showDatePicker() {
        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    selectedDateTime.set(Calendar.YEAR, year);
                    selectedDateTime.set(Calendar.MONTH, month);
                    selectedDateTime.set(Calendar.DAY_OF_MONTH, day);
                    showTimePicker();
                },
                selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(requireContext(),
                (view, hour, minute) -> {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hour);
                    selectedDateTime.set(Calendar.MINUTE, minute);
                    updateDateTimeDisplay();
                },
                selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void updateDateTimeDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy, hh:mm a", Locale.getDefault());
        tvDateTime.setText(sdf.format(selectedDateTime.getTime()));
    }

    // ── OCR ─────────────────────────────────────────────────────────────────

    private void showScanOptions() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Scan Glucose Value")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (d, which) -> {
                    if (which == 0) checkCameraPermission();
                    else            checkGalleryPermission();
                })
                .show();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        try {
            File photoFile = createTempImageFile();
            cameraImageUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(intent);
        } catch (IOException e) {
            toast("Could not create image file.");
        }
    }

    private File createTempImageFile() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        return File.createTempFile("GLUCOSE_" + stamp, ".jpg",
                requireContext().getExternalFilesDir(null));
    }

    private void checkGalleryPermission() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(requireContext(), perm)
                == PackageManager.PERMISSION_GRANTED) launchGallery();
        else galleryPermissionLauncher.launch(perm);
    }

    private void launchGallery() { galleryPickerLauncher.launch("image/*"); }

    private void runOcr(Uri uri) {
        toast("Processing image…");
        ocrHelper.extractGlucoseValue(requireContext(), uri, new OcrHelper.OcrCallback() {
            @Override public void onSuccess(int glucoseValue) {
                requireActivity().runOnUiThread(() -> {
                    setGlucose(glucoseValue);
                    updateAiInsight();
                    snack("Detected " + glucoseValue + " mg/dL from image.");
                });
            }
            @Override public void onFailure(String msg) {
                requireActivity().runOnUiThread(() ->
                        snack("OCR failed: " + msg + " — please enter manually."));
            }
        });
    }

    // ── Save / Discard ────────────────────────────────────────────────────────

    private void saveReading() {
        float glucose = parseGlucose();

        // Validate
        if (glucose == 0) { snack("Please enter a glucose level."); return; }
        if (glucose < 20 || glucose > 600) { snack("Value must be between 20 and 600 mg/dL."); return; }

        String timing = getSelectedTiming();
        String notes  = etNotes.getText().toString().trim();
        long   ts     = selectedDateTime.getTimeInMillis();

        GlucoseReading reading = new GlucoseReading(glucose, timing, ts, notes);

        // Room insert on background thread
        dbExecutor.execute(() -> {
            glucoseDao.insert(reading);
            requireActivity().runOnUiThread(() -> {
                snack("Reading saved! 🎉");
                clearForm();
            });
        });
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Discard Entry")
                .setMessage("Are you sure you want to discard this entry?")
                .setPositiveButton("Discard", (d, w) -> clearForm())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearForm() {
        etGlucoseLevel.setText("");
       selectTiming("Before Meal");
        etNotes.setText("");
        selectedDateTime = Calendar.getInstance();
        updateDateTimeDisplay();
        tvAiInsight.setText("Enter your glucose level to get a personalized AI insight.");
        tvAiSuggestion.setVisibility(View.GONE);
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void snack(String msg) {
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show();
    }
}