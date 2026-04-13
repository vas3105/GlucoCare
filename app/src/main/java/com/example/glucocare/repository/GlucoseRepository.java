package com.example.glucocare.repository;

import android.content.Context;
import android.util.Log;

import com.example.glucocare.AppDatabase;
import com.example.glucocare.GlucoseDao;
import com.example.glucocare.GlucoseReading;
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

public class GlucoseRepository {

    private static final String TAG        = "GlucoseRepository";
    private static final String COLLECTION = "glucose_readings";

    private final GlucoseDao       localDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService   executor;

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    public GlucoseRepository(Context context) {
        localDao  = AppDatabase.getInstance(context).glucoseDao();
        firestore = FirebaseFirestore.getInstance();
        executor  = Executors.newSingleThreadExecutor();
    }

    // ── UID helpers ───────────────────────────────────────────────────────────

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

    public void saveReading(GlucoseReading reading, Callback<Void> callback) {
        executor.execute(() -> {
            localDao.insert(reading);

            CollectionReference col = collection();
            if (col == null) {
                if (callback != null) callback.onResult(null);
                return;
            }

            col.add(toMap(reading))
                    .addOnSuccessListener(ref -> {
                        Log.d(TAG, "Firestore save ok: " + ref.getId());
                        if (callback != null) callback.onResult(null);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Firestore save failed: " + e.getMessage());
                        if (callback != null) callback.onError(e.getMessage());
                    });
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public void getAllReadings(Callback<List<GlucoseReading>> callback) {
        executor.execute(() -> {
            List<GlucoseReading> list = localDao.getAllReadings();
            if (callback != null) callback.onResult(list);
        });
    }

    public void getSevenDayAverage(String type, Callback<Float> callback) {
        executor.execute(() -> {
            long  since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            float avg   = localDao.getAverageForType(type, since);
            if (callback != null) callback.onResult(avg);
        });
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    public void syncFromFirestore(Callback<Void> callback) {
        CollectionReference col = collection();
        if (col == null) {
            if (callback != null) callback.onError("Not signed in.");
            return;
        }

        col.get()
                .addOnSuccessListener(snapshot -> executor.execute(() -> {
                    localDao.deleteAll();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        GlucoseReading r = fromMap(doc);
                        if (r != null) localDao.insert(r);
                    }
                    Log.d(TAG, "Glucose sync done: " + snapshot.size());
                    if (callback != null) callback.onResult(null);
                }))
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ── Clear on logout ───────────────────────────────────────────────────────

    /**
     * Clears all local glucose readings from Room.
     * Called by MainActivity.signOut() before Firebase signOut().
     */
    public void clearLocalDataOnLogout() {
        executor.execute(() -> {
            localDao.deleteAll();
            Log.d(TAG, "Local glucose data cleared on logout.");
        });
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private Map<String, Object> toMap(GlucoseReading r) {
        Map<String, Object> m = new HashMap<>();
        m.put("level",     r.level);
        m.put("type",      r.type  != null ? r.type  : "");
        m.put("timestamp", r.timestamp);
        m.put("notes",     r.notes != null ? r.notes : "");
        return m;
    }

    private GlucoseReading fromMap(QueryDocumentSnapshot doc) {
        try {
            GlucoseReading r = new GlucoseReading();
            Double levelVal  = doc.getDouble("level");
            r.level          = levelVal  != null ? levelVal.floatValue() : 0f;
            r.type           = doc.getString("type");
            r.notes          = doc.getString("notes");
            Long tsVal       = doc.getLong("timestamp");
            r.timestamp      = tsVal != null ? tsVal : 0L;
            return r;
        } catch (Exception e) {
            Log.e(TAG, "fromMap error: " + e.getMessage());
            return null;
        }
    }
}