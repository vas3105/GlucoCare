package com.example.glucocare;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Medicine — Room entity + Firestore model.
 *
 * Stored locally in Room table "medications".
 * Synced to Firestore at: users/{uid}/medications/{firestoreId}
 *
 * status values: "TAKEN" | "MISSED" | "UPCOMING"
 */
@Entity(tableName = "medications")
public class Medicine {

    public enum Status { TAKEN, MISSED, UPCOMING }

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String dosage;       // e.g. "500mg"
    public String mealTime;     // e.g. "Breakfast"
    public String time;         // e.g. "8:00 AM"
    public String status;       // stored as String for Room ("TAKEN"/"MISSED"/"UPCOMING")
    public String firestoreId;  // Firestore document ID — used to sync updates/deletes

    // ── Constructors ──────────────────────────────────────────────────────────

    public Medicine(String name, String dosage, String mealTime,
                    String time, Status status) {
        this.name     = name;
        this.dosage   = dosage;
        this.mealTime = mealTime;
        this.time     = time;
        this.status   = status.name();
    }

    public Medicine() {} // required by Room

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Status getStatusEnum() {
        try { return Status.valueOf(status); }
        catch (Exception e) { return Status.UPCOMING; }
    }

    public void setStatusEnum(Status s) { this.status = s.name(); }

    /** "500mg • Breakfast" */
    public String getDosageLabel() { return dosage + " • " + mealTime; }

    /** Icon resource based on medicine name keywords */
    public int getIconRes() {
        if (name == null) return R.drawable.ic_med_pill;
        String lower = name.toLowerCase();
        if (lower.contains("insulin")) return R.drawable.ic_med_insulin;
        if (lower.contains("januvia") || lower.contains("capsule"))
            return R.drawable.ic_med_capsule;
        return R.drawable.ic_med_pill;
    }
}