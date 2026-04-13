package com.example.glucocare;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medications")
public class Medicine {

    public enum Status { TAKEN, MISSED, UPCOMING }

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String userId;       // ← NEW: Firebase UID — filters data per user

    public String name;
    public String dosage;
    public String mealTime;
    public String time;
    public String status;
    public String firestoreId;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Medicine(String name, String dosage, String mealTime,
                    String time, Status status) {
        this.name     = name;
        this.dosage   = dosage;
        this.mealTime = mealTime;
        this.time     = time;
        this.status   = status.name();
        // userId is set by MedicineRepository before inserting
    }

    public Medicine() {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Status getStatusEnum() {
        try { return Status.valueOf(status); }
        catch (Exception e) { return Status.UPCOMING; }
    }

    public void setStatusEnum(Status s) { this.status = s.name(); }

    public String getDosageLabel() { return dosage + " • " + mealTime; }

    public int getIconRes() {
        if (name == null) return R.drawable.ic_med_pill;
        String lower = name.toLowerCase();
        if (lower.contains("insulin"))                          return R.drawable.ic_med_insulin;
        if (lower.contains("januvia") || lower.contains("capsule")) return R.drawable.ic_med_capsule;
        return R.drawable.ic_med_pill;
    }
}