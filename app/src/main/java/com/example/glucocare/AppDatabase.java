package com.example.glucocare;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * AppDatabase — version 4
 *
 * v1 → v2: added notes to glucose_readings
 * v2 → v3: added medications table
 * v3 → v4: added userId column to medications (per-user data separation)
 */
@Database(
        entities  = { GlucoseReading.class, Medicine.class },
        version   = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract GlucoseDao  glucoseDao();
    public abstract MedicineDao medicineDao();

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
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}