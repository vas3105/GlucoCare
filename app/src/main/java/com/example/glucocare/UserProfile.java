package com.example.glucocare;

/**
 * UserProfile — stores the account setup information.
 * Saved to Firestore at: users/{uid}/profile/data
 * Not stored in Room (no need for offline access — just display data).
 */
public class UserProfile {

    public String name;
    public String age;
    public String gender;
    public String diabetesType;
    public String doctorName;
    public String uid;

    public UserProfile() {} // required for Firestore deserialization

    public UserProfile(String uid, String name, String age,
                       String gender, String diabetesType, String doctorName) {
        this.uid          = uid;
        this.name         = name;
        this.age          = age;
        this.gender       = gender;
        this.diabetesType = diabetesType;
        this.doctorName   = doctorName;
    }
}