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

    // ── All queries now scoped to a specific userId ───────────────────────────

    @Query("SELECT * FROM medications WHERE userId = :userId ORDER BY time ASC")
    List<Medicine> getAllMedications(String userId);

    @Query("SELECT * FROM medications WHERE userId = :userId AND firestoreId = :firestoreId LIMIT 1")
    Medicine getByFirestoreId(String userId, String firestoreId);

    /** Deletes only the current user's data — used during Firestore sync */
    @Query("DELETE FROM medications WHERE userId = :userId")
    void deleteAllForUser(String userId);

    /** Used by logout — clears all local data when user signs out */
    @Query("DELETE FROM medications")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM medications WHERE userId = :userId AND status = 'TAKEN'")
    int countTaken(String userId);

    @Query("SELECT COUNT(*) FROM medications WHERE userId = :userId")
    int countTotal(String userId);
}