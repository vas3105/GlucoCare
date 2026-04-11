package com.example.glucocare;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.glucocare.repository.MedicineRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * MedsFragment — medications screen.
 *
 * Data flow (every write → reload from Room → display):
 *  1. onViewCreated       → loadMedications() from Room
 *  2. First launch (empty) → seedMockData() saves 4 defaults → loadMedications()
 *  3. User adds a med     → saveMedicine() → loadMedications()
 *  4. User taps UPCOMING  → updateStatus() → loadMedications()
 *
 * Always reloading from Room after every write means the list
 * always reflects what is actually persisted — no stale state.
 */
public class MedsFragment extends Fragment {

    // ── Views ────────────────────────────────────────────────────────────────
    private RecyclerView   rvMedications;
    private TextView       tvAdherencePercent, tvAdherenceMessage;
    private ProgressBar    progressAdherence;
    private MaterialButton btnAddNew;

    // ── Data ─────────────────────────────────────────────────────────────────
    private MedicineAdapter      adapter;
    private final List<Medicine> medicineList = new ArrayList<>();
    private MedicineRepository   medicineRepo;

    /** Prevents seeding mock data more than once per app install */
    private boolean mockDataSeeded = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meds, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        medicineRepo = new MedicineRepository(requireContext());

        bindViews(view);
        setupRecyclerView();
        setupListeners();
        loadMedications();   // always reload from Room on entry
    }

    // ── Bind ─────────────────────────────────────────────────────────────────

    private void bindViews(View v) {
        rvMedications      = v.findViewById(R.id.rvMedications);
        tvAdherencePercent = v.findViewById(R.id.tvAdherencePercent);
        tvAdherenceMessage = v.findViewById(R.id.tvAdherenceMessage);
        progressAdherence  = v.findViewById(R.id.progressAdherence);
        btnAddNew          = v.findViewById(R.id.btnAddNew);
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new MedicineAdapter(requireContext(), medicineList);
        rvMedications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMedications.setAdapter(adapter);
        rvMedications.setHasFixedSize(false);

        // Tap UPCOMING → mark TAKEN → persist → reload list
        adapter.setOnItemClickListener((medicine, position) -> {
            if (medicine.getStatusEnum() == Medicine.Status.UPCOMING) {
                medicineRepo.updateStatus(medicine, Medicine.Status.TAKEN,
                        new MedicineRepository.Callback<Void>() {
                            @Override
                            public void onResult(Void v) {
                                requireActivity().runOnUiThread(() -> {
                                    loadMedications(); // reload from Room
                                    snack("Marked as taken ✅");
                                });
                            }
                            @Override
                            public void onError(String error) {
                                requireActivity().runOnUiThread(() ->
                                        snack("Updated locally (syncing…)"));
                            }
                        });
            }
        });
    }

    // ── Load from Room ────────────────────────────────────────────────────────

    /**
     * The single source of truth for the displayed list.
     * Every add / update calls this after finishing to refresh the UI.
     */
    private void loadMedications() {
        medicineRepo.getAllMedications(new MedicineRepository.Callback<List<Medicine>>() {
            @Override
            public void onResult(List<Medicine> list) {
                requireActivity().runOnUiThread(() -> {

                    if (list.isEmpty() && !mockDataSeeded) {
                        // Very first launch — populate with defaults
                        mockDataSeeded = true;
                        seedMockData();
                        return;
                    }

                    medicineList.clear();
                    medicineList.addAll(list);
                    adapter.notifyDataSetChanged();
                    updateAdherenceDisplay();
                });
            }
            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() ->
                        snack("Could not load medications."));
            }
        });
    }

    // ── Seed mock data (first launch only) ───────────────────────────────────

    /**
     * Saves 4 default medicines to Room + Firestore.
     * Waits for all 4 callbacks before calling loadMedications()
     * so the list reload happens after all inserts complete.
     */
    private void seedMockData() {
        List<Medicine> defaults = new ArrayList<>();
        defaults.add(new Medicine("Metformin",     "500mg",    "Breakfast", "8:00 AM",  Medicine.Status.TAKEN));
        defaults.add(new Medicine("Januvia",        "100mg",    "Morning",   "10:30 AM", Medicine.Status.MISSED));
        defaults.add(new Medicine("Lantus Insulin", "15 Units", "Lunch",     "1:00 PM",  Medicine.Status.UPCOMING));
        defaults.add(new Medicine("Metformin",      "500mg",    "Dinner",    "7:00 PM",  Medicine.Status.UPCOMING));

        final int[] doneCount = {0};
        final int   total     = defaults.size();

        for (Medicine m : defaults) {
            medicineRepo.saveMedicine(m, new MedicineRepository.Callback<Void>() {
                @Override public void onResult(Void v)       { checkDone(); }
                @Override public void onError(String error)  { checkDone(); }

                private void checkDone() {
                    doneCount[0]++;
                    if (doneCount[0] == total) {
                        // All defaults saved — reload so Room IDs are populated
                        requireActivity().runOnUiThread(() -> loadMedications());
                    }
                }
            });
        }
    }

    // ── Adherence display ─────────────────────────────────────────────────────

    private void updateAdherenceDisplay() {
        int total = medicineList.size();
        if (total == 0) return;

        int taken  = 0;
        int missed = 0;
        for (Medicine m : medicineList) {
            if (m.getStatusEnum() == Medicine.Status.TAKEN)  taken++;
            if (m.getStatusEnum() == Medicine.Status.MISSED) missed++;
        }

        int percent = (int) ((taken / (float) total) * 100);
        tvAdherencePercent.setText(percent + "%");

        ObjectAnimator.ofInt(progressAdherence, "progress",
                        progressAdherence.getProgress(), percent)
                .setDuration(900)
                .start();

        tvAdherenceMessage.setText(buildMessage(percent, taken, total, missed));
    }

    private String buildMessage(int percent, int taken, int total, int missed) {
        if (percent == 100)
            return "Perfect! You've taken all your medications today. Keep it up!";
        if (percent >= 75)
            return "You've missed " + missed + " dose" + (missed > 1 ? "s" : "")
                    + " today. Your consistency is helping stabilize your glucose levels.";
        if (percent >= 50)
            return "You're halfway there — " + taken + " of " + total
                    + " doses taken. Try setting reminders for the rest.";
        return "Your adherence needs attention. Only "
                + taken + " of " + total + " doses taken today.";
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        btnAddNew.setOnClickListener(v -> showAddMedicineDialog());
    }

    // ── Add Medicine Dialog ───────────────────────────────────────────────────

    private void showAddMedicineDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_add_medicine);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText       etName     = dialog.findViewById(R.id.etMedName);
        EditText       etDosage   = dialog.findViewById(R.id.etDosage);
        EditText       etMealTime = dialog.findViewById(R.id.etMealTime);
        EditText       etTime     = dialog.findViewById(R.id.etTime);
        Button         btnCancel  = dialog.findViewById(R.id.btnCancelAdd);
        MaterialButton btnConfirm = dialog.findViewById(R.id.btnConfirmAdd);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String name     = etName.getText().toString().trim();
            String dosage   = etDosage.getText().toString().trim();
            String mealTime = etMealTime.getText().toString().trim();
            String time     = etTime.getText().toString().trim();

            if (name.isEmpty() || dosage.isEmpty()
                    || mealTime.isEmpty() || time.isEmpty()) {
                snack("Please fill in all fields.");
                return;
            }

            Medicine newMed = new Medicine(
                    name, dosage, mealTime, time, Medicine.Status.UPCOMING);

            // Save → reload from Room → display
            // This guarantees the new entry appears with its real Room ID
            medicineRepo.saveMedicine(newMed, new MedicineRepository.Callback<Void>() {
                @Override
                public void onResult(Void result) {
                    requireActivity().runOnUiThread(() -> {
                        loadMedications();                       // ← key line
                        snack(name + " added to your schedule.");
                    });
                }
                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        loadMedications();
                        snack(name + " saved locally (will sync when online).");
                    });
                }
            });

            dialog.dismiss();
        });

        dialog.show();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void snack(String msg) {
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show();
    }
}