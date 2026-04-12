package com.example.glucocare;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GlucoseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GlucoseReading reading);

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    List<GlucoseReading> getAllReadings();

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT 1")
    GlucoseReading getLatestReading();

    @Query("SELECT AVG(level) FROM glucose_readings " +
            "WHERE type = :type AND timestamp >= :since")
    float getAverageForType(String type, long since);

    @Query("DELETE FROM glucose_readings")
    void deleteAll();
}