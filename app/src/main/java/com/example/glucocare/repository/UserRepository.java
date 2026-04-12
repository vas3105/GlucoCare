package com.example.glucocare.repository;

import android.util.Log;

import com.example.glucocare.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
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

        firestore.collection("users").document(uid)
                .collection("profile").document("data")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserProfile profile = documentSnapshot.toObject(UserProfile.class);
                        callback.onResult(profile);
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
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
