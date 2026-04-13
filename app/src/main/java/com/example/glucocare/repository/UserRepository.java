package com.example.glucocare.repository;

import android.util.Log;

import com.example.glucocare.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {

    private static final String TAG = "UserRepository";
    private final FirebaseFirestore firestore;

    public interface Callback<T> {
        void onResult(T result);
        void onError(String error);
    }

    public UserRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    private String getUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return null;
    }

    public void getUserProfile(Callback<UserProfile> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not logged in");
            return;
        }

        Log.d(TAG, "Fetching profile for UID: " + uid);

        firestore.collection("users").document(uid)
                .collection("profile").document("data")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "Profile data found: " + documentSnapshot.getData());
                        UserProfile profile = mapSnapshotToProfile(documentSnapshot);
                        callback.onResult(profile);
                    } else {
                        Log.d(TAG, "No profile document found at users/" + uid + "/profile/data");
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore fetch failed: " + e.getMessage());
                    callback.onError(e.getMessage());
                });
    }

    private UserProfile mapSnapshotToProfile(DocumentSnapshot doc) {
        UserProfile p = new UserProfile();
        p.uid = doc.getString("uid");
        p.name = doc.getString("name");
        p.age = doc.getString("age");
        p.gender = doc.getString("gender");
        p.diabetesType = doc.getString("diabetesType");
        p.doctorName = doc.getString("doctorName");
        p.emergencyPhone = doc.getString("emergencyPhone");
        
        Double weightVal = doc.getDouble("weight");
        p.weight = weightVal != null ? weightVal.floatValue() : 184f;
        
        p.height = doc.getString("height");
        if (p.height == null) p.height = "5'11";
        
        Long b = doc.getLong("breakfastTime");
        p.breakfastTime = b != null ? b.intValue() : 480;
        
        Long l = doc.getLong("lunchTime");
        p.lunchTime = l != null ? l.intValue() : 780;
        
        Long d = doc.getLong("dinnerTime");
        p.dinnerTime = d != null ? d.intValue() : 1140;
        
        return p;
    }

    public void saveUserProfile(UserProfile profile, Callback<Void> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not logged in");
            return;
        }

        profile.uid = uid;
        firestore.collection("users").document(uid)
                .collection("profile").document("data")
                .set(profile)
                .addOnSuccessListener(aVoid -> callback.onResult(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
