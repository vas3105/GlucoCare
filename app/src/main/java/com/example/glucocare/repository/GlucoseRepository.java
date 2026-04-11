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

/**
 * GlucoseRepository — offline-first sync layer.
 *
 * WRITE → Room immediately → Firestore async.
 * READ  → always Room (instant, works offline).
 * SYNC  → Firestore → Room on login.
 */
public class GlucoseRepository {

    private static final String TAG        = "GlucoseRepository";
    private static final String COLLECTION = "glucose_readings";

    private final GlucoseDao       localDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService   executor;

    // ── Callback ─────────────────────────────────────────────────────────────

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public GlucoseRepository(Context context) {
        localDao  = AppDatabase.getInstance(context).glucoseDao();
        firestore = FirebaseFirestore.getInstance();
        executor  = Executors.newSingleThreadExecutor();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the Firestore collection for the current user,
     * or null if no user is signed in.
     */
    private CollectionReference collection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        return firestore
                .collection("users")
                .document(user.getUid())
                .collection(COLLECTION);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveReading(GlucoseReading reading, Callback<Void> callback) {
        executor.execute(() -> {
            // 1. Save to Room immediately
            localDao.insert(reading);

            // 2. Push to Firestore if signed in
            CollectionReference col = collection();
            if (col == null) {
                Log.w(TAG, "No signed-in user — saved locally only.");
                if (callback != null) callback.onResult(null);
                return;
            }

            col.add(toMap(reading))
                    .addOnSuccessListener(ref -> {
                        Log.d(TAG, "Firestore save ok: " + ref.getId());
                        if (callback != null) callback.onResult(null);
                    })
                    .addOnFailureListener(e -> {
                        // Local copy is safe — cloud will sync when online
                        Log.w(TAG, "Firestore save failed (local ok): " + e.getMessage());
                        if (callback != null) callback.onError(e.getMessage());
                    });
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Always reads from Room — works offline, instant. */
    public void getAllReadings(Callback<List<GlucoseReading>> callback) {
        executor.execute(() -> {
            List<GlucoseReading> list = localDao.getAllReadings();
            if (callback != null) callback.onResult(list);
        });
    }

    /** 7-day average for a timing type, queried from Room. */
    public void getSevenDayAverage(String type, Callback<Float> callback) {
        executor.execute(() -> {
            long since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            float avg  = localDao.getAverageForType(type, since);
            if (callback != null) callback.onResult(avg);
        });
    }

    // ── Sync (Firestore → Room) ───────────────────────────────────────────────

    /**
     * Pull all readings from Firestore and refresh Room.
     * Called once after login to bring cloud data onto the device.
     */
    public void syncFromFirestore(Callback<Void> callback) {
        CollectionReference col = collection();
        if (col == null) {
            Log.w(TAG, "syncFromFirestore: no signed-in user.");
            if (callback != null) callback.onError("Not signed in.");
            return;
        }

        col.get()
                .addOnSuccessListener(snapshot -> {
                    executor.execute(() -> {
                        localDao.deleteAll();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            GlucoseReading r = fromMap(doc);
                            if (r != null) localDao.insert(r);
                        }
                        Log.d(TAG, "Sync complete: " + snapshot.size() + " readings.");
                        if (callback != null) callback.onResult(null);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Sync failed: " + e.getMessage());
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private Map<String, Object> toMap(GlucoseReading r) {
        Map<String, Object> m = new HashMap<>();
        m.put("level",     r.level);
        m.put("type",      r.type     != null ? r.type  : "");
        m.put("timestamp", r.timestamp);
        m.put("notes",     r.notes    != null ? r.notes : "");
        return m;
    }

    private GlucoseReading fromMap(QueryDocumentSnapshot doc) {
        try {
            GlucoseReading r = new GlucoseReading();

            // level — null-safe Double → float
            Double levelVal = doc.getDouble("level");
            r.level = (levelVal != null) ? levelVal.floatValue() : 0f;

            // type / notes — null-safe strings
            r.type  = doc.getString("type");
            r.notes = doc.getString("notes");

            // timestamp — null-safe Long
            Long tsVal = doc.getLong("timestamp");
            r.timestamp = (tsVal != null) ? tsVal : 0L;

            return r;
        } catch (Exception e) {
            Log.e(TAG, "fromMap error: " + e.getMessage());
            return null;
        }
    }
}