package com.example.glucocare;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {GlucoseReading.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract GlucoseDao glucoseDao();

    // ── Thread-safe singleton ─────────────────────────────────────────────────
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Builder<AppDatabase> glucocareDb = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "glucocare_db"
                    );
                    glucocareDb.fallbackToDestructiveMigration();// Wipes and rebuilds on schema change (safe for dev).
// Replace with a proper Migration for production.
                    INSTANCE = glucocareDb
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}