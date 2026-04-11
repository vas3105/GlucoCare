package com.example.glucocare.repository;

import android.content.Context;
import android.util.Log;

import com.example.glucocare.AppDatabase;
import com.example.glucocare.Medicine;
import com.example.glucocare.MedicineDao;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MedicineRepository — single source of truth for medications.
 *
 * Same offline-first pattern as GlucoseRepository:
 *   WRITE → Room first, then Firestore async.
 *   READ  → always Room.
 *   SYNC  → Firestore → Room on login.
 */
public class MedicineRepository {

    private static final String TAG        = "MedicineRepository";
    private static final String COLLECTION = "medications";

    private final MedicineDao      localDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService  executor;

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    public MedicineRepository(Context context) {
        localDao  = AppDatabase.getInstance(context).medicineDao();
        firestore = FirebaseFirestore.getInstance();
        executor  = Executors.newSingleThreadExecutor();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String uid() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private com.google.firebase.firestore.CollectionReference collection() {
        return firestore.collection("users").document(uid())
                .collection(COLLECTION);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveMedicine(Medicine medicine, Callback<Void> callback) {
        executor.execute(() -> {
            // 1. Save locally
            long localId = localDao.insert(medicine);

            // 2. Push to Firestore
            collection().add(toMap(medicine))
                    .addOnSuccessListener(ref -> {
                        // Store Firestore ID back in Room so we can update/delete later
                        executor.execute(() -> {
                            Medicine saved = localDao.getByFirestoreId(ref.getId());
                            if (saved == null) {
                                // fetch by local id workaround
                                medicine.id          = (int) localId;
                                medicine.firestoreId = ref.getId();
                                localDao.update(medicine);
                            }
                        });
                        Log.d(TAG, "Firestore save ok: " + ref.getId());
                        if (callback != null) callback.onResult(null);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Firestore save failed (local ok): " + e.getMessage());
                        if (callback != null) callback.onError(e.getMessage());
                    });
        });
    }

    // ── Update status ─────────────────────────────────────────────────────────

    public void updateStatus(Medicine medicine, Medicine.Status newStatus,
                             Callback<Void> callback) {
        medicine.setStatusEnum(newStatus);

        executor.execute(() -> {
            // 1. Update Room
            localDao.update(medicine);

            // 2. Update Firestore (if we have a firestoreId)
            if (medicine.firestoreId != null && !medicine.firestoreId.isEmpty()) {
                collection().document(medicine.firestoreId)
                        .update("status", newStatus.name())
                        .addOnSuccessListener(v -> {
                            if (callback != null) callback.onResult(null);
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Status update failed in Firestore: " + e.getMessage());
                            if (callback != null) callback.onError(e.getMessage());
                        });
            } else {
                if (callback != null) callback.onResult(null);
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteMedicine(Medicine medicine, Callback<Void> callback) {
        executor.execute(() -> {
            localDao.delete(medicine);

            if (medicine.firestoreId != null && !medicine.firestoreId.isEmpty()) {
                collection().document(medicine.firestoreId).delete()
                        .addOnSuccessListener(v -> {
                            if (callback != null) callback.onResult(null);
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Delete failed in Firestore: " + e.getMessage());
                            if (callback != null) callback.onError(e.getMessage());
                        });
            } else {
                if (callback != null) callback.onResult(null);
            }
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public void getAllMedications(Callback<List<Medicine>> callback) {
        executor.execute(() -> {
            List<Medicine> list = localDao.getAllMedications();
            callback.onResult(list);
        });
    }

    public void getAdherenceStats(Callback<int[]> callback) {
        // returns int[] { taken, total }
        executor.execute(() -> {
            int taken = localDao.countTaken();
            int total = localDao.countTotal();
            callback.onResult(new int[]{taken, total});
        });
    }

    // ── Sync (Firestore → Room) ───────────────────────────────────────────────

    public void syncFromFirestore(Callback<Void> callback) {
        collection().get()
                .addOnSuccessListener(snapshot -> {
                    executor.execute(() -> {
                        localDao.deleteAll();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            Medicine m = fromMap(doc);
                            if (m != null) localDao.insert(m);
                        }
                        Log.d(TAG, "Meds sync complete: " + snapshot.size());
                        if (callback != null) callback.onResult(null);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Meds sync failed: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private Map<String, Object> toMap(Medicine m) {
        Map<String, Object> map = new HashMap<>();
        map.put("name",     m.name);
        map.put("dosage",   m.dosage);
        map.put("mealTime", m.mealTime);
        map.put("time",     m.time);
        map.put("status",   m.status);
        return map;
    }

    private Medicine fromMap(QueryDocumentSnapshot doc) {
        try {
            Medicine m       = new Medicine();
            m.name           = doc.getString("name");
            m.dosage         = doc.getString("dosage");
            m.mealTime       = doc.getString("mealTime");
            m.time           = doc.getString("time");
            m.status         = doc.getString("status");
            m.firestoreId    = doc.getId();
            return m;
        } catch (Exception e) {
            Log.e(TAG, "fromMap error: " + e.getMessage());
            return null;
        }
    }
}