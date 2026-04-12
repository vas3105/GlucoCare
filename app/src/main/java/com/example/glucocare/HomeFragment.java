package com.example.glucocare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
<<<<<<< HEAD
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
=======
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
>>>>>>> origin/master
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
<<<<<<< HEAD
=======
import android.widget.EditText;
>>>>>>> origin/master
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
<<<<<<< HEAD
=======
import androidx.appcompat.app.AlertDialog;
>>>>>>> origin/master
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

<<<<<<< HEAD
import com.example.glucocare.repository.UserRepository;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int SMS_PERMISSION_CODE = 101;
    private TextView tvWelcome, tvLatestLevel, tvAvgLevel, tvAlertTitle, tvAlertDesc;
    private MaterialCardView cardEmergency;
    private Button btnSOS;
    private LineChart glucoseChart;
    
    // Static fields to simulate data, now synced from Firebase
    public static float lastReading = 0;
    public static String emergencyPhone = ""; 

    private Handler monitoringHandler = new Handler();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private UserRepository userRepository;

=======
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
>>>>>>> origin/master
    private Runnable monitoringTask = new Runnable() {
        @Override
        public void run() {
            checkInactivityAndAlert();
<<<<<<< HEAD
            monitoringHandler.postDelayed(this, 10000); 
=======
            monitoringHandler.postDelayed(this, 30000); // Check every 30 seconds
>>>>>>> origin/master
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
<<<<<<< HEAD
        userRepository = new UserRepository();
=======
>>>>>>> origin/master

        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvLatestLevel = view.findViewById(R.id.tvLatestLevel);
        tvAvgLevel = view.findViewById(R.id.tvAvgLevel);
        cardEmergency = view.findViewById(R.id.cardEmergency);
        tvAlertTitle = view.findViewById(R.id.tvAlertTitle);
        tvAlertDesc = view.findViewById(R.id.tvAlertDesc);
        btnSOS = view.findViewById(R.id.btnEmergencySOS);
<<<<<<< HEAD
        glucoseChart = view.findViewById(R.id.glucoseChart);

        setupChart();
        
        btnSOS.setOnClickListener(v -> triggerManualSOS());

        checkSmsPermission();
        loadProfileData();
    }

    private void loadProfileData() {
        userRepository.getUserProfile(new UserRepository.Callback<UserProfile>() {
            @Override
            public void onResult(UserProfile profile) {
                if (profile != null) {
                    ProfileFragment.userName = profile.name;
                    ProfileFragment.userAge = profile.age;
                    ProfileFragment.userWeight = profile.weight;
                    ProfileFragment.userHeight = profile.height;
                    ProfileFragment.breakfastTime = profile.breakfastTime;
                    ProfileFragment.lunchTime = profile.lunchTime;
                    ProfileFragment.dinnerTime = profile.dinnerTime;
                    emergencyPhone = profile.emergencyPhone;
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String nameDisplay = (ProfileFragment.userName != null && !ProfileFragment.userName.isEmpty()) 
                                ? ProfileFragment.userName.split(" ")[0] 
                                : "User";
                            tvWelcome.setText("Welcome, " + nameDisplay);
                            checkInactivityAndAlert(); 
                        });
                    }
                }
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "loadProfileData error: " + error);
            }
        });
    }

    private void setupChart() {
        glucoseChart.getDescription().setEnabled(false);
        glucoseChart.setTouchEnabled(true);
        glucoseChart.setDragEnabled(true);
        glucoseChart.setScaleEnabled(true);
        glucoseChart.setPinchZoom(true);
        glucoseChart.setDrawGridBackground(false);
        glucoseChart.getLegend().setEnabled(false);

        XAxis xAxis = glucoseChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#707E94"));

        glucoseChart.getAxisLeft().setTextColor(Color.parseColor("#707E94"));
        glucoseChart.getAxisLeft().setDrawGridLines(true);
        glucoseChart.getAxisRight().setEnabled(false);
=======
        btnLog = view.findViewById(R.id.btnLogGlucose);
        btnLogMeds = view.findViewById(R.id.btnLogMeds);

        refreshUI();

        btnLog.setOnClickListener(v -> showLogDialog());
        btnLogMeds.setOnClickListener(v -> showLogMedsDialog());
        btnSOS.setOnClickListener(v -> triggerManualSOS());

        checkSmsPermission();
>>>>>>> origin/master
    }

    private void checkSmsPermission() {
        if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
<<<<<<< HEAD
        refreshUI();
        monitoringHandler.post(monitoringTask);
        loadChartData();
=======
        monitoringHandler.post(monitoringTask);
>>>>>>> origin/master
    }

    @Override
    public void onPause() {
        super.onPause();
        monitoringHandler.removeCallbacks(monitoringTask);
    }

    private void refreshUI() {
<<<<<<< HEAD
        if (ProfileFragment.userName != null) {
            String nameDisplay = ProfileFragment.userName.isEmpty() ? "User" : ProfileFragment.userName.split(" ")[0];
            tvWelcome.setText("Welcome, " + nameDisplay);
        }
        
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getContext());
            GlucoseReading latest = db.glucoseDao().getLatestReading();
            
            float avg = 0;
            List<GlucoseReading> all = db.glucoseDao().getAllReadings();
            if (!all.isEmpty()) {
                float sum = 0;
                for (GlucoseReading r : all) sum += r.level;
                avg = sum / all.size();
            }
            
            final float finalAvg = avg;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (latest != null) {
                        tvLatestLevel.setText(String.valueOf(latest.level));
                        lastReading = latest.level;
                    } else {
                        tvLatestLevel.setText("--");
                        lastReading = 0;
                    }
                    tvAvgLevel.setText(String.format("%.1f", finalAvg));
                    
                    checkInactivityAndAlert();
                });
            }
        });
    }

    private void loadChartData() {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getContext());
            List<GlucoseReading> readings = db.glucoseDao().getAllReadings();
            Collections.sort(readings, (a, b) -> Long.compare(a.timestamp, b.timestamp));

            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < readings.size(); i++) {
                entries.add(new Entry(i, readings.get(i).level));
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (entries.isEmpty()) {
                        glucoseChart.setNoDataText("No data available for trends");
                        glucoseChart.invalidate();
                        return;
                    }

                    LineDataSet dataSet = new LineDataSet(entries, "Glucose Level");
                    dataSet.setColor(Color.parseColor("#3D79F2"));
                    dataSet.setCircleColor(Color.parseColor("#3D79F2"));
                    dataSet.setLineWidth(2f);
                    dataSet.setCircleRadius(4f);
                    dataSet.setDrawCircleHole(true);
                    dataSet.setValueTextSize(9f);
                    dataSet.setDrawFilled(true);
                    dataSet.setFillColor(Color.parseColor("#3D79F2"));
                    dataSet.setFillAlpha(30);
                    dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                    LineData lineData = new LineData(dataSet);
                    glucoseChart.setData(lineData);
                    glucoseChart.invalidate();
                });
            }
        });
    }

    private void checkInactivityAndAlert() {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getContext());
            List<GlucoseReading> allToday = db.glucoseDao().getAllReadings();

            long nowMs = System.currentTimeMillis();
            Calendar now = Calendar.getInstance();
            now.setTimeInMillis(nowMs);
            int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            int[] schedule = {ProfileFragment.breakfastTime, ProfileFragment.lunchTime, ProfileFragment.dinnerTime};
            String[] labels = {"Breakfast", "Lunch", "Dinner"};

            String missingMeal = null;
            boolean isOverdue = false;

            // Iterate BACKWARDS to find the meal closest to current time
            for (int i = schedule.length - 1; i >= 0; i--) {
                int sched = schedule[i];
                
                // If we are currently past the scheduled time OR within 1 hour before it (reminder window)
                if (currentMinutes >= (sched - 60)) {
                    boolean found = false;
                    for (GlucoseReading r : allToday) {
                        Calendar logCal = Calendar.getInstance();
                        logCal.setTimeInMillis(r.timestamp);
                        
                        if (logCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            logCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
                            
                            int logMin = logCal.get(Calendar.HOUR_OF_DAY) * 60 + logCal.get(Calendar.MINUTE);
                            // Log counts for this meal if it was within +/- 1 hour of scheduled time
                            if (Math.abs(logMin - sched) <= 60) {
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        missingMeal = labels[i];
                        if (currentMinutes >= sched + 60) isOverdue = true;
                        // Since we are iterating backwards, the first one we find is the "current" or "most recent" meal
                        break; 
                    } else {
                        // If we found a log for the most recent meal, we don't need to check older meals (like breakfast)
                        break;
                    }
                }
            }

            final String finalMissingMeal = missingMeal;
            final boolean finalIsOverdue = isOverdue;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    StringBuilder title = new StringBuilder();
                    StringBuilder desc = new StringBuilder();
                    boolean showAlert = false;

                    if (lastReading > 0 && (lastReading < 70 || lastReading > 180)) {
                        showAlert = true;
                        title.append("CRITICAL LEVEL");
                        desc.append("Abnormal glucose level (").append(lastReading).append(") detected.");
                    }

                    if (finalMissingMeal != null) {
                        if (showAlert) desc.append("\n\nALSO: ");
                        showAlert = true;
                        
                        String reminderTitle = finalMissingMeal.toUpperCase() + (finalIsOverdue ? " LOG OVERDUE" : " REMINDER");
                        if (title.length() == 0) title.append(reminderTitle);
                        else title.append(" & REMINDER");

                        if (finalIsOverdue) {
                            desc.append("It is over 1 hour past your scheduled ").append(finalMissingMeal).append(" time. Please log your level immediately.");
                            sendEmergencySmsOnce(finalMissingMeal);
                        } else {
                            desc.append("It is time for your scheduled ").append(finalMissingMeal).append(" log. Please enter your sugar level.");
                        }
                    }

                    if (showAlert) {
                        cardEmergency.setVisibility(View.VISIBLE);
                        tvAlertTitle.setText(title.toString());
                        tvAlertDesc.setText(desc.toString());
                    } else {
                        cardEmergency.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private String lastAlertedMeal = "";
    private void sendEmergencySmsOnce(String meal) {
        if (lastAlertedMeal.equals(meal)) return;
        
        // Final cleaning of the phone number
        String phone = (emergencyPhone != null) ? emergencyPhone.replaceAll("[^0-9+]", "") : "";
        
        if (phone.isEmpty() || phone.length() < 10) {
            Log.w(TAG, "Invalid cleaned phone number: " + phone);
            return;
        }

        try {
            if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SmsManager smsManager;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    smsManager = getContext().getSystemService(SmsManager.class);
                } else {
                    smsManager = SmsManager.getDefault();
                }
                
                String patientName = ProfileFragment.userName != null ? ProfileFragment.userName : "Patient";
                String message = "GlucoCare AI Alert: " + patientName + " missed their " + meal + " reading by over 1 hour. Please check on them.";
                
                smsManager.sendTextMessage(phone, null, message, null, null);
                lastAlertedMeal = meal;
                
                Toast.makeText(getContext(), "EMERGENCY SMS SENT TO: " + phone, Toast.LENGTH_LONG).show();
                Log.d(TAG, "SMS Successfully Sent to " + phone);
            } else {
                Log.w(TAG, "SEND_SMS permission missing.");
                Toast.makeText(getContext(), "SMS Permission Denied", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS failed: " + e.getMessage());
            Toast.makeText(getContext(), "Error sending SMS: Check balance or network", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerManualSOS() {
        String phone = (emergencyPhone != null) ? emergencyPhone.replaceAll("[^0-9+]", "") : "";
        if (phone.isEmpty()) {
            Toast.makeText(getContext(), "No emergency contact saved", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phone));
=======
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
>>>>>>> origin/master
        startActivity(intent);
    }
}
