package com.example.glucocare;

import android.app.TimePickerDialog;
import android.content.Intent;
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

import com.example.glucocare.auth.LoginActivity;
import com.example.glucocare.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView tvName, tvAge, tvWeight, tvHeight, tvEmergencyPhone, tvDiabetesType;
    private Button btnEdit, btnLogout;
    private UserRepository userRepository;

    // Static fields backed by Firebase
    public static String userName = "David Miller";
    public static String userAge = "42";
    public static float userWeight = 184;
    public static String userHeight = "5'11";
    public static String diabetesType = "Type 2";
    public static int breakfastTime = 480; 
    public static int lunchTime = 780;     
    public static int dinnerTime = 1140;   

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        userRepository = new UserRepository();

        tvName = view.findViewById(R.id.profileName);
        tvAge = view.findViewById(R.id.tvProfileAge);
        tvWeight = view.findViewById(R.id.tvProfileWeight);
        tvHeight = view.findViewById(R.id.tvProfileHeight);
        tvEmergencyPhone = view.findViewById(R.id.tvEmergencyPhone);
        tvDiabetesType = view.findViewById(R.id.profileDiabetesType);
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
                    diabetesType = (profile.diabetesType != null && !profile.diabetesType.isEmpty()) ? profile.diabetesType : "Type 2";
                    breakfastTime = profile.breakfastTime;
                    lunchTime = profile.lunchTime;
                    dinnerTime = profile.dinnerTime;
                    HomeFragment.emergencyPhone = profile.emergencyPhone;

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> refreshUI());
                    }
                }
            }
            @Override
            public void onError(String error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> refreshUI());
                }
            }
        });
    }

    private void refreshUI() {
        tvName.setText(userName);
        tvAge.setText(userAge + " yrs");
        tvWeight.setText(userWeight + " lbs");
        tvHeight.setText(userHeight);
        tvEmergencyPhone.setText(HomeFragment.emergencyPhone);
        tvDiabetesType.setText(diabetesType.toUpperCase());
    }

    private void handleLogout() {
        new AlertDialog.Builder(requireContext())
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
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        
        EditText etName = dialogView.findViewById(R.id.etEditName);
        EditText etAge = dialogView.findViewById(R.id.etEditAge);
        EditText etWeight = dialogView.findViewById(R.id.etEditWeight);
        EditText etHeight = dialogView.findViewById(R.id.etEditHeight);
        EditText etPhone = dialogView.findViewById(R.id.etEditEmergencyPhone);
        
        Button btnBreakfast = dialogView.findViewById(R.id.btnBreakfastTime);
        Button btnLunch = dialogView.findViewById(R.id.btnLunchTime);
        Button btnDinner = dialogView.findViewById(R.id.btnDinnerTime);

        etName.setText(userName);
        etAge.setText(userAge);
        etWeight.setText(String.valueOf(userWeight));
        etHeight.setText(userHeight);
        etPhone.setText(HomeFragment.emergencyPhone);

        final int[] tempTimes = {breakfastTime, lunchTime, dinnerTime};

        btnBreakfast.setText("Breakfast: " + formatTime(tempTimes[0]));
        btnLunch.setText("Lunch: " + formatTime(tempTimes[1]));
        btnDinner.setText("Dinner: " + formatTime(tempTimes[2]));

        btnBreakfast.setOnClickListener(v -> showTimePicker(tempTimes[0], newTime -> {
            tempTimes[0] = newTime;
            btnBreakfast.setText("Breakfast: " + formatTime(newTime));
        }));
        btnLunch.setOnClickListener(v -> showTimePicker(tempTimes[1], newTime -> {
            tempTimes[1] = newTime;
            btnLunch.setText("Lunch: " + formatTime(newTime));
        }));
        btnDinner.setOnClickListener(v -> showTimePicker(tempTimes[2], newTime -> {
            tempTimes[2] = newTime;
            btnDinner.setText("Dinner: " + formatTime(newTime));
        }));

        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            final String newName = etName.getText().toString();
            final String newAge = etAge.getText().toString();
            final String newHeight = etHeight.getText().toString();
            float weightVal = userWeight;
            try { weightVal = Float.parseFloat(etWeight.getText().toString()); } catch (Exception e) {}

            final float finalWeight = weightVal;
            final String newPhone = etPhone.getText().toString();

            UserProfile updatedProfile = new UserProfile(
                    FirebaseAuth.getInstance().getUid(),
                    newName, newAge, "Not Specified", diabetesType, ""
            );
            updatedProfile.weight = finalWeight;
            updatedProfile.height = newHeight;
            updatedProfile.emergencyPhone = newPhone;
            updatedProfile.breakfastTime = tempTimes[0];
            updatedProfile.lunchTime = tempTimes[1];
            updatedProfile.dinnerTime = tempTimes[2];

            userRepository.saveUserProfile(updatedProfile, new UserRepository.Callback<Void>() {
                @Override
                public void onResult(Void result) {
                    userName = newName;
                    userAge = newAge;
                    userWeight = finalWeight;
                    userHeight = newHeight;
                    HomeFragment.emergencyPhone = newPhone;
                    breakfastTime = tempTimes[0];
                    lunchTime = tempTimes[1];
                    dinnerTime = tempTimes[2];

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            refreshUI();
                            Toast.makeText(getContext(), "Profile Updated", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Failed: " + error, Toast.LENGTH_SHORT).show());
                    }
                }
            });
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showTimePicker(int currentMinutes, OnTimeSelectedListener listener) {
        int hour = currentMinutes / 60;
        int minute = currentMinutes % 60;
        new TimePickerDialog(requireContext(), (view, hourOfDay, min) -> {
            listener.onTimeSelected(hourOfDay * 60 + min);
        }, hour, minute, true).show();
    }

    private String formatTime(int minutes) {
        return String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60);
    }

    interface OnTimeSelectedListener { void onTimeSelected(int minutesFromMidnight); }
}
