package com.example.glucocare.repository;

import android.content.Context;
import android.util.Log;

import com.example.glucocare.AppDatabase;
import com.example.glucocare.Medicine;
import com.example.glucocare.MedicineDao;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MedicineRepository — per-user medicine data.
 *
 * Every write stamps the current user's UID onto the Medicine object.
 * Every read filters by the current user's UID.
 * This ensures User A never sees User B's medicines.
 */
public class MedicineRepository {

    private static final String TAG        = "MedicineRepository";
    private static final String COLLECTION = "medications";

    private final MedicineDao      localDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService   executor;

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    public MedicineRepository(Context context) {
        localDao  = AppDatabase.getInstance(context).medicineDao();
        firestore = FirebaseFirestore.getInstance();
        executor  = Executors.newSingleThreadExecutor();
    }

    // ── UID helpers ───────────────────────────────────────────────────────────

    /** Returns current user's UID, or null if not signed in */
    private String getCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private CollectionReference collection() {
        String uid = getCurrentUid();
        if (uid == null) return null;
        return firestore.collection("users").document(uid).collection(COLLECTION);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveMedicine(Medicine medicine, Callback<Void> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onError("Not signed in.");
            return;
        }

        // ── Stamp userId before saving ────────────────────────────────────────
        medicine.userId = uid;

        executor.execute(() -> {
            long localId = localDao.insert(medicine);

            CollectionReference col = collection();
            if (col == null) {
                if (callback != null) callback.onResult(null);
                return;
            }

            col.add(toMap(medicine))
                    .addOnSuccessListener(ref -> {
                        executor.execute(() -> {
                            medicine.id          = (int) localId;
                            medicine.firestoreId = ref.getId();
                            localDao.update(medicine);
                        });
                        Log.d(TAG, "Saved to Firestore: " + ref.getId());
                        if (callback != null) callback.onResult(null);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Firestore save failed: " + e.getMessage());
                        if (callback != null) callback.onError(e.getMessage());
                    });
        });
    }

    // ── Update status ─────────────────────────────────────────────────────────

    public void updateStatus(Medicine medicine, Medicine.Status newStatus,
                             Callback<Void> callback) {
        medicine.setStatusEnum(newStatus);
        executor.execute(() -> {
            localDao.update(medicine);

            if (medicine.firestoreId != null && !medicine.firestoreId.isEmpty()) {
                CollectionReference col = collection();
                if (col != null) {
                    col.document(medicine.firestoreId)
                            .update("status", newStatus.name())
                            .addOnSuccessListener(v -> {
                                if (callback != null) callback.onResult(null);
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onError(e.getMessage());
                            });
                }
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
                CollectionReference col = collection();
                if (col != null) {
                    col.document(medicine.firestoreId).delete()
                            .addOnSuccessListener(v -> {
                                if (callback != null) callback.onResult(null);
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onError(e.getMessage());
                            });
                }
            } else {
                if (callback != null) callback.onResult(null);
            }
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns only the current user's medicines */
    public void getAllMedications(Callback<List<Medicine>> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onError("Not signed in.");
            return;
        }
        executor.execute(() -> {
            // ── Filter by userId ──────────────────────────────────────────────
            List<Medicine> list = localDao.getAllMedications(uid);
            if (callback != null) callback.onResult(list);
        });
    }

    /** Adherence stats for current user only */
    public void getAdherenceStats(Callback<int[]> callback) {
        String uid = getCurrentUid();
        if (uid == null) {
            if (callback != null) callback.onResult(new int[]{0, 0});
            return;
        }
        executor.execute(() -> {
            int taken = localDao.countTaken(uid);
            int total = localDao.countTotal(uid);
            if (callback != null) callback.onResult(new int[]{taken, total});
        });
    }

    // ── Sync Firestore → Room ─────────────────────────────────────────────────

    public void syncFromFirestore(Callback<Void> callback) {
        String uid = getCurrentUid();
        CollectionReference col = collection();

        if (uid == null || col == null) {
            if (callback != null) callback.onError("Not signed in.");
            return;
        }

        col.get()
                .addOnSuccessListener(snapshot -> executor.execute(() -> {
                    // ── Only delete THIS user's local data ────────────────────
                    localDao.deleteAllForUser(uid);

                    for (QueryDocumentSnapshot doc : snapshot) {
                        Medicine m = fromMap(doc);
                        if (m != null) {
                            m.userId = uid; // stamp userId on synced data
                            localDao.insert(m);
                        }
                    }
                    Log.d(TAG, "Sync done: " + snapshot.size() + " meds for " + uid);
                    if (callback != null) callback.onResult(null);
                }))
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ── Clear local data on logout ────────────────────────────────────────────

    /**
     * Call this from MainActivity.signOut() before signing out.
     * Clears ALL local Room data so the next user starts fresh.
     */
    public void clearLocalDataOnLogout() {
        executor.execute(() -> {
            localDao.deleteAll();
            Log.d(TAG, "Local medicine data cleared on logout.");
        });
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private Map<String, Object> toMap(Medicine m) {
        Map<String, Object> map = new HashMap<>();
        map.put("name",     m.name     != null ? m.name     : "");
        map.put("dosage",   m.dosage   != null ? m.dosage   : "");
        map.put("mealTime", m.mealTime != null ? m.mealTime : "");
        map.put("time",     m.time     != null ? m.time     : "");
        map.put("status",   m.status   != null ? m.status   : "UPCOMING");
        return map;
    }

    private Medicine fromMap(QueryDocumentSnapshot doc) {
        try {
            Medicine m    = new Medicine();
            m.name        = doc.getString("name");
            m.dosage      = doc.getString("dosage");
            m.mealTime    = doc.getString("mealTime");
            m.time        = doc.getString("time");
            m.status      = doc.getString("status");
            m.firestoreId = doc.getId();
            return m;
        } catch (Exception e) {
            Log.e(TAG, "fromMap error: " + e.getMessage());
            return null;
        }
    }
}