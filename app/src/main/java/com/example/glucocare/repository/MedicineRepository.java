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

    // ── UID helper — fetched fresh every time, never cached ──────────────────

    private String getCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        return user.getUid();
    }

    private CollectionReference collection() {
        String uid = getCurrentUid();
        if (uid == null) return null;
        return firestore
                .collection("users")
                .document(uid)
                .collection(COLLECTION);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveMedicine(Medicine medicine, Callback<Void> callback) {
        executor.execute(() -> {
            // 1. Save locally first
            long localId = localDao.insert(medicine);

            // 2. Push to Firestore under real UID
            CollectionReference col = collection();
            if (col == null) {
                Log.w(TAG, "saveMedicine: no signed-in user, saved locally only.");
                if (callback != null) callback.onResult(null);
                return;
            }

            Log.d(TAG, "Saving medicine to: users/" + getCurrentUid() + "/" + COLLECTION);

            col.add(toMap(medicine))
                    .addOnSuccessListener(ref -> {
                        // Store Firestore doc ID back into Room for future updates/deletes
                        executor.execute(() -> {
                            medicine.id          = (int) localId;
                            medicine.firestoreId = ref.getId();
                            localDao.update(medicine);
                        });
                        Log.d(TAG, "Medicine saved to Firestore: " + ref.getId());
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
                                Log.w(TAG, "Status update failed: " + e.getMessage());
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
                                Log.w(TAG, "Delete failed: " + e.getMessage());
                                if (callback != null) callback.onError(e.getMessage());
                            });
                }
            } else {
                if (callback != null) callback.onResult(null);
            }
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public void getAllMedications(Callback<List<Medicine>> callback) {
        executor.execute(() -> {
            List<Medicine> list = localDao.getAllMedications();
            if (callback != null) callback.onResult(list);
        });
    }

    public void getAdherenceStats(Callback<int[]> callback) {
        executor.execute(() -> {
            int taken = localDao.countTaken();
            int total = localDao.countTotal();
            if (callback != null) callback.onResult(new int[]{taken, total});
        });
    }

    // ── Sync Firestore → Room ─────────────────────────────────────────────────

    public void syncFromFirestore(Callback<Void> callback) {
        CollectionReference col = collection();
        if (col == null) {
            Log.w(TAG, "syncFromFirestore: no signed-in user.");
            if (callback != null) callback.onError("Not signed in.");
            return;
        }

        Log.d(TAG, "Syncing from: users/" + getCurrentUid() + "/" + COLLECTION);

        col.get()
                .addOnSuccessListener(snapshot -> executor.execute(() -> {
                    localDao.deleteAll();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Medicine m = fromMap(doc);
                        if (m != null) localDao.insert(m);
                    }
                    Log.d(TAG, "Meds sync done: " + snapshot.size() + " items.");
                    if (callback != null) callback.onResult(null);
                }))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Meds sync failed: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private Map<String, Object> toMap(Medicine m) {
        Map<String, Object> map = new HashMap<>();
        map.put("name",        m.name     != null ? m.name     : "");
        map.put("dosage",      m.dosage   != null ? m.dosage   : "");
        map.put("mealTime",    m.mealTime != null ? m.mealTime : "");
        map.put("time",        m.time     != null ? m.time     : "");
        map.put("status",      m.status   != null ? m.status   : "UPCOMING");
        map.put("firestoreId", ""); // placeholder; updated after doc is created
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