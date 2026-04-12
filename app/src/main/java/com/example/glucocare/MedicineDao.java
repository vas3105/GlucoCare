package com.example.glucocare;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MedicineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Medicine medicine);

    @Update
    void update(Medicine medicine);

    @Delete
    void delete(Medicine medicine);

    @Query("SELECT * FROM medications ORDER BY time ASC")
    List<Medicine> getAllMedications();

    @Query("SELECT * FROM medications WHERE firestoreId = :firestoreId LIMIT 1")
    Medicine getByFirestoreId(String firestoreId);

    @Query("DELETE FROM medications")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM medications WHERE status = 'TAKEN'")
    int countTaken();

    @Query("SELECT COUNT(*) FROM medications")
    int countTotal();
}