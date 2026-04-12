package com.example.glucocare;

<<<<<<< HEAD
import android.app.TimePickerDialog;
import android.content.Intent;
=======
>>>>>>> origin/master
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

<<<<<<< HEAD
import com.example.glucocare.auth.LoginActivity;
import com.example.glucocare.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;
=======
import com.google.android.material.slider.Slider;
>>>>>>> origin/master

public class ProfileFragment extends Fragment {

    private TextView tvName, tvAge, tvWeight, tvHeight, tvEmergencyPhone;
<<<<<<< HEAD
    private Button btnEdit, btnLogout;
    private UserRepository userRepository;

    // Static fields used across fragments, now backed by Firebase
    public static String userName = "David Miller";
    public static String userAge = "42";
    public static float userWeight = 184;
    public static String userHeight = "5'11";
    public static int breakfastTime = 480; 
    public static int lunchTime = 780;     
    public static int dinnerTime = 1140;   
=======
    private TextView tvTimeoutValue;
    private Slider sliderTimeout;
    private Button btnEdit;

    // Static fields to mock "database"
    public static String userName = "David Miller";
    public static int userAge = 42;
    public static float userWeight = 184;
    public static String userHeight = "5'11";
>>>>>>> origin/master

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
<<<<<<< HEAD
        userRepository = new UserRepository();
=======
>>>>>>> origin/master

        tvName = view.findViewById(R.id.profileName);
        tvAge = view.findViewById(R.id.tvProfileAge);
        tvWeight = view.findViewById(R.id.tvProfileWeight);
        tvHeight = view.findViewById(R.id.tvProfileHeight);
        tvEmergencyPhone = view.findViewById(R.id.tvEmergencyPhone);
<<<<<<< HEAD
        btnEdit = view.findViewById(R.id.btnEditProfile);
        btnLogout = view.findViewById(R.id.btnLogout);

        loadProfileFromFirebase();

        btnEdit.setOnClickListener(v -> showEditDialog());
        btnLogout.setOnClickListener(v -> handleLogout());
    }

    private void loadProfileFromFirebase() {
        userRepository.getUserProfile(new UserRepository.Callback<UserProfile>() {
            @Override
            public void onResult(UserProfile profile) {
                if (profile != null) {
                    userName = profile.name;
                    userAge = profile.age;
                    userWeight = profile.weight;
                    userHeight = profile.height;
                    breakfastTime = profile.breakfastTime;
                    lunchTime = profile.lunchTime;
                    dinnerTime = profile.dinnerTime;
                    HomeFragment.emergencyPhone = profile.emergencyPhone;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> refreshUI());
                    }
                }
            }
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        refreshUI();
                        Toast.makeText(getContext(), "Cloud sync failed: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
=======
        tvTimeoutValue = view.findViewById(R.id.tvTimeoutValue);
        sliderTimeout = view.findViewById(R.id.sliderTimeout);
        btnEdit = view.findViewById(R.id.btnEditProfile);

        refreshUI();

        btnEdit.setOnClickListener(v -> showEditDialog());

        sliderTimeout.addOnChangeListener((slider, value, fromUser) -> {
            HomeFragment.inactivityTimeoutMinutes = (int) value;
            tvTimeoutValue.setText((int) value + " Minutes");
>>>>>>> origin/master
        });
    }

    private void refreshUI() {
        tvName.setText(userName);
        tvAge.setText(userAge + " yrs");
        tvWeight.setText(userWeight + " lbs");
        tvHeight.setText(userHeight);
        tvEmergencyPhone.setText(HomeFragment.emergencyPhone);
<<<<<<< HEAD
    }

    private void handleLogout() {
        new AlertDialog.Builder(getContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
=======
        tvTimeoutValue.setText(HomeFragment.inactivityTimeoutMinutes + " Minutes");
        sliderTimeout.setValue(HomeFragment.inactivityTimeoutMinutes);
>>>>>>> origin/master
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        
        EditText etName = dialogView.findViewById(R.id.etEditName);
        EditText etAge = dialogView.findViewById(R.id.etEditAge);
        EditText etWeight = dialogView.findViewById(R.id.etEditWeight);
        EditText etPhone = dialogView.findViewById(R.id.etEditEmergencyPhone);
<<<<<<< HEAD
        Button btnBreakfast = dialogView.findViewById(R.id.btnBreakfastTime);
        Button btnLunch = dialogView.findViewById(R.id.btnLunchTime);
        Button btnDinner = dialogView.findViewById(R.id.btnDinnerTime);
=======
>>>>>>> origin/master

        etName.setText(userName);
        etAge.setText(String.valueOf(userAge));
        etWeight.setText(String.valueOf(userWeight));
        etPhone.setText(HomeFragment.emergencyPhone);

<<<<<<< HEAD
        final int[] selectedTimes = {breakfastTime, lunchTime, dinnerTime};

        btnBreakfast.setText("Breakfast: " + formatTime(selectedTimes[0]));
        btnLunch.setText("Lunch: " + formatTime(selectedTimes[1]));
        btnDinner.setText("Dinner: " + formatTime(selectedTimes[2]));

        btnBreakfast.setOnClickListener(v -> showTimePicker(selectedTimes[0], time -> {
            selectedTimes[0] = time;
            btnBreakfast.setText("Breakfast: " + formatTime(time));
        }));
        btnLunch.setOnClickListener(v -> showTimePicker(selectedTimes[1], time -> {
            selectedTimes[1] = time;
            btnLunch.setText("Lunch: " + formatTime(time));
        }));
        btnDinner.setOnClickListener(v -> showTimePicker(selectedTimes[2], time -> {
            selectedTimes[2] = time;
            btnDinner.setText("Dinner: " + formatTime(time));
        }));

        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            final String newName = etName.getText().toString();
            final String newAge = etAge.getText().toString();
            float weightVal = userWeight;
            try {
                weightVal = Float.parseFloat(etWeight.getText().toString());
            } catch (Exception e) {}

            final float finalWeight = weightVal;
            final String newPhone = etPhone.getText().toString();

            UserProfile updatedProfile = new UserProfile(
                    FirebaseAuth.getInstance().getUid(),
                    newName, newAge, "Not Specified", "Type 2", ""
            );
            updatedProfile.weight = finalWeight;
            updatedProfile.height = userHeight;
            updatedProfile.breakfastTime = selectedTimes[0];
            updatedProfile.lunchTime = selectedTimes[1];
            updatedProfile.dinnerTime = selectedTimes[2];
            updatedProfile.emergencyPhone = newPhone;

            userRepository.saveUserProfile(updatedProfile, new UserRepository.Callback<Void>() {
                @Override
                public void onResult(Void result) {
                    userName = newName;
                    userAge = newAge;
                    userWeight = finalWeight;
                    HomeFragment.emergencyPhone = newPhone;
                    breakfastTime = selectedTimes[0];
                    lunchTime = selectedTimes[1];
                    dinnerTime = selectedTimes[2];

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            refreshUI();
                            Toast.makeText(getContext(), "Profile Saved to Cloud", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Failed to save to cloud: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
=======
        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            userName = etName.getText().toString();
            userAge = Integer.parseInt(etAge.getText().toString());
            userWeight = Float.parseFloat(etWeight.getText().toString());
            HomeFragment.emergencyPhone = etPhone.getText().toString();
            refreshUI();
            Toast.makeText(getContext(), "Profile Updated", Toast.LENGTH_SHORT).show();
>>>>>>> origin/master
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
<<<<<<< HEAD

    private void showTimePicker(int initialMinutes, OnTimeSelectedListener listener) {
        int hour = initialMinutes / 60;
        int minute = initialMinutes % 60;
        new TimePickerDialog(getContext(), (view, hourOfDay, min) -> {
            listener.onTimeSelected(hourOfDay * 60 + min);
        }, hour, minute, true).show();
    }

    private String formatTime(int minutes) {
        return String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60);
    }

    interface OnTimeSelectedListener {
        void onTimeSelected(int minutesFromMidnight);
    }
=======
>>>>>>> origin/master
}
