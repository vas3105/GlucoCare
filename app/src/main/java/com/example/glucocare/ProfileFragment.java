package com.example.glucocare;

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
import com.google.android.material.slider.Slider;

public class ProfileFragment extends Fragment {

    private TextView tvName, tvAge, tvWeight, tvHeight, tvEmergencyPhone;
    private TextView tvTimeoutValue;
    private Slider sliderTimeout;
    private Button btnEdit;

    // Static fields to mock "database"
    public static String userName = "David Miller";
    public static int userAge = 42;
    public static float userWeight = 184;
    public static String userHeight = "5'11";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvName = view.findViewById(R.id.profileName);
        tvAge = view.findViewById(R.id.tvProfileAge);
        tvWeight = view.findViewById(R.id.tvProfileWeight);
        tvHeight = view.findViewById(R.id.tvProfileHeight);
        tvEmergencyPhone = view.findViewById(R.id.tvEmergencyPhone);
        tvTimeoutValue = view.findViewById(R.id.tvTimeoutValue);
        sliderTimeout = view.findViewById(R.id.sliderTimeout);
        btnEdit = view.findViewById(R.id.btnEditProfile);

        refreshUI();

        btnEdit.setOnClickListener(v -> showEditDialog());

        sliderTimeout.addOnChangeListener((slider, value, fromUser) -> {
            HomeFragment.inactivityTimeoutMinutes = (int) value;
            tvTimeoutValue.setText((int) value + " Minutes");
        });
    }

    private void refreshUI() {
        tvName.setText(userName);
        tvAge.setText(userAge + " yrs");
        tvWeight.setText(userWeight + " lbs");
        tvHeight.setText(userHeight);
        tvEmergencyPhone.setText(HomeFragment.emergencyPhone);
        tvTimeoutValue.setText(HomeFragment.inactivityTimeoutMinutes + " Minutes");
        sliderTimeout.setValue(HomeFragment.inactivityTimeoutMinutes);
    }

    private void showEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        
        EditText etName = dialogView.findViewById(R.id.etEditName);
        EditText etAge = dialogView.findViewById(R.id.etEditAge);
        EditText etWeight = dialogView.findViewById(R.id.etEditWeight);
        EditText etPhone = dialogView.findViewById(R.id.etEditEmergencyPhone);
        etName.setText(userName);
        etAge.setText(String.valueOf(userAge));
        etWeight.setText(String.valueOf(userWeight));
        etPhone.setText(HomeFragment.emergencyPhone);
        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            userName = etName.getText().toString();
            userAge = Integer.parseInt(etAge.getText().toString());
            userWeight = Float.parseFloat(etWeight.getText().toString());
            HomeFragment.emergencyPhone = etPhone.getText().toString();
            refreshUI();
            Toast.makeText(getContext(), "Profile Updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

}
