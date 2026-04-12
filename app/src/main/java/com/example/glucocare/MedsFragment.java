package com.example.glucocare;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.glucocare.repository.MedicineRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

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
    private boolean              mockDataSeeded = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable @Override
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
        loadMedications();
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

        // Card tap → mark UPCOMING as TAKEN → reload
        adapter.setOnItemClickListener((medicine, position) -> {
            if (medicine.getStatusEnum() == Medicine.Status.UPCOMING) {
                medicineRepo.updateStatus(medicine, Medicine.Status.TAKEN,
                        new MedicineRepository.Callback<Void>() {
                            @Override public void onResult(Void v) {
                                requireActivity().runOnUiThread(() -> {
                                    loadMedications();
                                    snack("Marked as taken ✅");
                                });
                            }
                            @Override public void onError(String e) {
                                requireActivity().runOnUiThread(() ->
                                        snack("Updated locally (syncing…)"));
                            }
                        });
            }
        });

        // Trash icon tap → confirm dialog → delete
        adapter.setOnDeleteClickListener((medicine, position) ->
                confirmDelete(medicine, position));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Shows a confirmation dialog before deleting.
     * Prevents accidental deletions.
     */
    private void confirmDelete(Medicine medicine, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Medication")
                .setMessage("Remove \"" + medicine.name + "\" from your schedule?")
                .setPositiveButton("Remove", (dialog, which) ->
                        performDelete(medicine, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDelete(Medicine medicine, int position) {
        medicineRepo.deleteMedicine(medicine, new MedicineRepository.Callback<Void>() {
            @Override public void onResult(Void v) {
                requireActivity().runOnUiThread(() -> {
                    // Instant UI feedback — remove from list directly
                    if (position >= 0 && position < medicineList.size()) {
                        medicineList.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, medicineList.size());
                    }
                    updateAdherenceDisplay();
                    snack(medicine.name + " removed from your schedule.");
                });
            }
            @Override public void onError(String error) {
                requireActivity().runOnUiThread(() ->
                        snack("Could not delete. Please try again."));
            }
        });
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadMedications() {
        medicineRepo.getAllMedications(new MedicineRepository.Callback<List<Medicine>>() {
            @Override public void onResult(List<Medicine> list) {
                requireActivity().runOnUiThread(() -> {
                    if (list.isEmpty() && !mockDataSeeded) {
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
            @Override public void onError(String error) {
                requireActivity().runOnUiThread(() ->
                        snack("Could not load medications."));
            }
        });
    }

    // ── Seed (first launch) ───────────────────────────────────────────────────

    private void seedMockData() {
        List<Medicine> defaults = new ArrayList<>();
        defaults.add(new Medicine("Metformin",     "500mg",    "Breakfast", "8:00 AM",  Medicine.Status.TAKEN));
        defaults.add(new Medicine("Januvia",        "100mg",    "Morning",   "10:30 AM", Medicine.Status.MISSED));
        defaults.add(new Medicine("Lantus Insulin", "15 Units", "Lunch",     "1:00 PM",  Medicine.Status.UPCOMING));
        defaults.add(new Medicine("Metformin",      "500mg",    "Dinner",    "7:00 PM",  Medicine.Status.UPCOMING));

        final int[] doneCount = {0};
        for (Medicine m : defaults) {
            medicineRepo.saveMedicine(m, new MedicineRepository.Callback<Void>() {
                @Override public void onResult(Void v)      { checkDone(); }
                @Override public void onError(String error) { checkDone(); }
                private void checkDone() {
                    doneCount[0]++;
                    if (doneCount[0] == defaults.size())
                        requireActivity().runOnUiThread(() -> loadMedications());
                }
            });
        }
    }

    // ── Adherence ────────────────────────────────────────────────────────────

    private void updateAdherenceDisplay() {
        int total = medicineList.size();
        if (total == 0) {
            tvAdherencePercent.setText("0%");
            progressAdherence.setProgress(0);
            tvAdherenceMessage.setText("No medications scheduled today.");
            return;
        }

        int taken = 0, missed = 0;
        for (Medicine m : medicineList) {
            if (m.getStatusEnum() == Medicine.Status.TAKEN)  taken++;
            if (m.getStatusEnum() == Medicine.Status.MISSED) missed++;
        }

        int percent = (int) ((taken / (float) total) * 100);
        tvAdherencePercent.setText(percent + "%");

        ObjectAnimator.ofInt(progressAdherence, "progress",
                        progressAdherence.getProgress(), percent)
                .setDuration(900).start();

        tvAdherenceMessage.setText(buildMessage(percent, taken, total, missed));
    }

    private String buildMessage(int percent, int taken, int total, int missed) {
        if (percent == 100)
            return "Perfect! You've taken all your medications today. Keep it up!";
        if (percent >= 75)
            return "You've missed " + missed + " dose" + (missed > 1 ? "s" : "")
                    + " today. Your consistency is helping stabilize your glucose levels.";
        if (percent >= 50)
            return "Halfway there — " + taken + " of " + total
                    + " doses taken. Set reminders for the rest.";
        return "Adherence needs attention. Only "
                + taken + " of " + total + " doses taken today.";
    }

    // ── Add Medicine Dialog ───────────────────────────────────────────────────

    private void setupListeners() {
        btnAddNew.setOnClickListener(v -> showAddMedicineDialog());
    }

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

            if (name.isEmpty() || dosage.isEmpty() || mealTime.isEmpty() || time.isEmpty()) {
                snack("Please fill in all fields.");
                return;
            }

            Medicine newMed = new Medicine(
                    name, dosage, mealTime, time, Medicine.Status.UPCOMING);

            medicineRepo.saveMedicine(newMed, new MedicineRepository.Callback<Void>() {
                @Override public void onResult(Void result) {
                    requireActivity().runOnUiThread(() -> {
                        loadMedications();
                        snack(name + " added to your schedule.");
                    });
                }
                @Override public void onError(String error) {
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