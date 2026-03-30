package com.example.glucocare;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.ArrayList;
import java.util.List;
@Dao
public interface GlucoseDao {
    @Insert
    void insert(GlucoseReading reading);

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    List<GlucoseReading> getAllReadings();

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT 1")
    GlucoseReading getLatestReading();

    // ── Extra queries used by LogsFragment AI Insight ─────────────────────────

    /** 7-day average for a specific timing type (used to build insight text). */
    @Query("SELECT AVG(level) FROM glucose_readings " +
            "WHERE type = :type AND timestamp >= :since")
    float getAverageForType(String type, long since);

    /** All readings logged since a given timestamp — used by InsightsFragment
     *  to fetch only today's readings (pass midnight millis as :since). */
    @Query("SELECT * FROM glucose_readings WHERE timestamp >= :since ORDER BY timestamp ASC")
    List<GlucoseReading> getReadingsSince(long since);
}
