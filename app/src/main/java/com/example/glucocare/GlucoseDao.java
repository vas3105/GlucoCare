package com.example.glucocare;

import java.util.ArrayList;
import java.util.List;

public interface GlucoseDao {
    void insert(GlucoseReading reading);
    List<GlucoseReading> getAllReadings();
    GlucoseReading getLatestReading();
}
