package com.example.glucocare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;

public class HomeFragment extends Fragment {

    private static final int SMS_PERMISSION_CODE = 101;
    private TextView tvWelcome, tvLatestLevel, tvAvgLevel, tvAlertTitle, tvAlertDesc;
    private MaterialCardView cardEmergency;
    private Button btnSOS, btnLog, btnLogMeds;
    
    // Static fields to simulate data
    public static float lastReading = 0;
    public static long lastUpdateTimestamp = System.currentTimeMillis();
    public static String emergencyPhone = "5551234567"; 
    public static int inactivityTimeoutMinutes = 60;

    private Handler monitoringHandler = new Handler();
    private Runnable monitoringTask = new Runnable() {
        @Override
        public void run() {
            checkInactivityAndAlert();

            monitoringHandler.postDelayed(this, 30000); // Check every 30 seconds
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvLatestLevel = view.findViewById(R.id.tvLatestLevel);
        tvAvgLevel = view.findViewById(R.id.tvAvgLevel);
        cardEmergency = view.findViewById(R.id.cardEmergency);
        tvAlertTitle = view.findViewById(R.id.tvAlertTitle);
        tvAlertDesc = view.findViewById(R.id.tvAlertDesc);
        btnSOS = view.findViewById(R.id.btnEmergencySOS);
        btnLog = view.findViewById(R.id.btnLogGlucose);
        btnLogMeds = view.findViewById(R.id.btnLogMeds);

        refreshUI();

        btnLog.setOnClickListener(v -> showLogDialog());
        btnLogMeds.setOnClickListener(v -> showLogMedsDialog());
        btnSOS.setOnClickListener(v -> triggerManualSOS());

        checkSmsPermission();
    }

    private void checkSmsPermission() {
        if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        monitoringHandler.post(monitoringTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        monitoringHandler.removeCallbacks(monitoringTask);
    }

    private void refreshUI() {
        tvWelcome.setText("Welcome, " + ProfileFragment.userName.split(" ")[0]);
        if (lastReading > 0) {
            tvLatestLevel.setText(String.valueOf(lastReading));
            checkGlucoseAlert(lastReading);
        } else {
            tvLatestLevel.setText("--");
            cardEmergency.setVisibility(View.GONE);
        }
    }

    private void checkGlucoseAlert(float level) {
        if (level < 70 || level > 180) {
            cardEmergency.setVisibility(View.VISIBLE);
            tvAlertTitle.setText("CRITICAL LEVEL");
            tvAlertDesc.setText("Abnormal glucose levels detected. Please take immediate action or use SOS.");
        } else {
            cardEmergency.setVisibility(View.GONE);
        }
    }

    private void checkInactivityAndAlert() {
        long diffMinutes = (System.currentTimeMillis() - lastUpdateTimestamp) / (60 * 1000);
        
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        boolean isNearMealTime = (hour >= 7 && hour <= 9) || (hour >= 12 && hour <= 14) || (hour >= 18 && hour <= 20);

        if (isNearMealTime && diffMinutes >= inactivityTimeoutMinutes) {
            cardEmergency.setVisibility(View.VISIBLE);
            tvAlertTitle.setText("INACTIVITY REMINDER");
            tvAlertDesc.setText("No update detected near meal time. Please log your levels for safety.");
            
            if (diffMinutes >= inactivityTimeoutMinutes + 5) {
                tvAlertTitle.setText("LACK OF ACTIVITY ALERT");
                tvAlertDesc.setText("Critical inactivity! Emergency message sent to " + emergencyPhone);
                sendEmergencySms();
            }
        }
    }

    private void sendEmergencySms() {
        try {
            if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SmsManager smsManager = SmsManager.getDefault();
                String message = "GlucoCare AI Alert: Lack of activity detected for patient " + ProfileFragment.userName + ". Please check immediately.";
                smsManager.sendTextMessage(emergencyPhone, null, message, null, null);
                Toast.makeText(getContext(), "Emergency Alert Sent via SMS", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            // Log error
        }
    }

    private void showLogDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Log Glucose Level");
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_log_glucose, null);
        EditText etLevel = dialogView.findViewById(R.id.etGlucoseLevel);
        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String val = etLevel.getText().toString();
            if (!val.isEmpty()) {
                lastReading = Float.parseFloat(val);
                lastUpdateTimestamp = System.currentTimeMillis();
                refreshUI();
                Toast.makeText(getContext(), "Reading logged", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showLogMedsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Log Medication");
        builder.setMessage("Did you take your prescribed medication?");
        builder.setPositiveButton("Yes, Logged", (dialog, which) -> {
            lastUpdateTimestamp = System.currentTimeMillis();
            cardEmergency.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Medication update received", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void triggerManualSOS() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + emergencyPhone));
        startActivity(intent);
    }
}
