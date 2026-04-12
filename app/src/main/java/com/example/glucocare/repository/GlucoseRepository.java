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

    // ── UID helper — fetched fresh every time, never cached ──────────────────
    // This guarantees we always use the real logged-in user's UID,
    // never a stale or empty value.

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

    public void saveReading(GlucoseReading reading, Callback<Void> callback) {
        executor.execute(() -> {
            // 1. Always save locally first
            localDao.insert(reading);

            // 2. Push to Firestore under the correct UID
            CollectionReference col = collection();
            if (col == null) {
                Log.w(TAG, "saveReading: no signed-in user, saved locally only.");
                if (callback != null) callback.onResult(null);
                return;
            }

            Log.d(TAG, "Saving reading to: users/" + getCurrentUid() + "/" + COLLECTION);

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
            long since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            float avg  = localDao.getAverageForType(type, since);
            if (callback != null) callback.onResult(avg);
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
                        GlucoseReading r = fromMap(doc);
                        if (r != null) localDao.insert(r);
                    }
                    Log.d(TAG, "Sync done: " + snapshot.size() + " readings.");
                    if (callback != null) callback.onResult(null);
                }))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Sync failed: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
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