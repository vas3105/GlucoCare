package com.example.glucocare;

/**
 * UserProfile — stores the account setup information.
 * Saved to Firestore at: users/{uid}/profile/data
<<<<<<< HEAD
=======
 * Not stored in Room (no need for offline access — just display data).
>>>>>>> origin/master
 */
public class UserProfile {

    public String name;
<<<<<<< HEAD
    public String age; // Changed to String to handle both existing and new data gracefully
=======
    public String age;
>>>>>>> origin/master
    public String gender;
    public String diabetesType;
    public String doctorName;
    public String uid;
<<<<<<< HEAD
    
    // New fields for trends and logging
    public float weight;
    public String height;
    public int breakfastTime;
    public int lunchTime;
    public int dinnerTime;
    public String emergencyPhone;
=======
>>>>>>> origin/master

    public UserProfile() {} // required for Firestore deserialization

    public UserProfile(String uid, String name, String age,
                       String gender, String diabetesType, String doctorName) {
        this.uid          = uid;
        this.name         = name;
        this.age          = age;
        this.gender       = gender;
        this.diabetesType = diabetesType;
        this.doctorName   = doctorName;
<<<<<<< HEAD
        
        // Defaults
        this.weight = 184;
        this.height = "5'11";
        this.breakfastTime = 480;
        this.lunchTime = 780;
        this.dinnerTime = 1140;
        this.emergencyPhone = "5551234567";
=======
>>>>>>> origin/master
    }
}