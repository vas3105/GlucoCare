package com.example.glucocare;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * AppDatabase — version 3
 *
 * v1 → v2: added notes column to glucose_readings
 * v2 → v3: added medications table
 */
@Database(
        entities  = { GlucoseReading.class, Medicine.class },
        version   = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract GlucoseDao   glucoseDao();
    public abstract MedicineDao  medicineDao();

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "glucocare_db"
                            )
                            .fallbackToDestructiveMigration() // safe for dev
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}