package com.example.glucocare;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "glucose_readings")
public class GlucoseReading {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public float level;       // mg/dL value

    public String type;       // "Fasting" | "Before Meal" | "After Meal"

    public long timestamp;    // System.currentTimeMillis()

    public String notes;      // optional user context

    // ── Constructor used by LogsFragment when saving ──────────────────────────
    public GlucoseReading(float level, String type, long timestamp, String notes) {
        this.level     = level;
        this.type      = type;
        this.timestamp = timestamp;
        this.notes     = notes;
    }

    // ── Required by Room ──────────────────────────────────────────────────────
    public GlucoseReading() {}
}