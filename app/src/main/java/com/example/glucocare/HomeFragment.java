package com.example.glucocare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.glucocare.repository.GlucoseRepository;
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
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int SMS_PERMISSION_CODE = 101;
    private TextView tvWelcome, tvLatestLevel, tvAvgLevel, tvAlertTitle, tvAlertDesc;
    private MaterialCardView cardEmergency;
    private Button btnSOS;
    private LineChart glucoseChart;

    private GlucoseRepository glucoseRepository;
    private UserRepository userRepository;
    
    public static float lastReading = 0;
    public static long lastUpdateTimestamp = 0;
    public static String emergencyPhone = "5551234567"; 

    // Static to persist across fragment switches
    private static String lastAlertedMeal = "";

    private Handler monitoringHandler = new Handler();
    private Runnable monitoringTask = new Runnable() {
        @Override
        public void run() {
            if (glucoseRepository != null) {
                glucoseRepository.getAllReadings(new GlucoseRepository.Callback<List<GlucoseReading>>() {
                    @Override
                    public void onResult(List<GlucoseReading> readings) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> checkInactivityAndAlert(readings));
                        }
                    }
                    @Override
                    public void onError(String error) {}
                });
            }
            monitoringHandler.postDelayed(this, 30000); 
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        glucoseRepository = new GlucoseRepository(requireContext());
        userRepository = new UserRepository();

        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvLatestLevel = view.findViewById(R.id.tvLatestLevel);
        tvAvgLevel = view.findViewById(R.id.tvAvgLevel);
        cardEmergency = view.findViewById(R.id.cardEmergency);
        tvAlertTitle = view.findViewById(R.id.tvAlertTitle);
        tvAlertDesc = view.findViewById(R.id.tvAlertDesc);
        btnSOS = view.findViewById(R.id.btnEmergencySOS);
        glucoseChart = view.findViewById(R.id.glucoseChart);

        setupChart();
        btnSOS.setOnClickListener(v -> triggerManualSOS());
        checkSmsPermission();
        syncData();
    }

    private void setupChart() {
        if (glucoseChart == null) return;
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
    }

    private void syncData() {
        userRepository.getUserProfile(new UserRepository.Callback<UserProfile>() {
            @Override
            public void onResult(UserProfile profile) {
                if (profile != null) {
                    ProfileFragment.userName = profile.name;
                    emergencyPhone = profile.emergencyPhone;
                    ProfileFragment.breakfastTime = profile.breakfastTime;
                    ProfileFragment.lunchTime = profile.lunchTime;
                    ProfileFragment.dinnerTime = profile.dinnerTime;
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> refreshUI());
                    }
                }
            }
            @Override
            public void onError(String error) { Log.e(TAG, "Profile Sync Error: " + error); }
        });

        glucoseRepository.syncFromFirestore(new GlucoseRepository.Callback<Void>() {
            @Override
            public void onResult(Void result) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        refreshUI();
                        loadChartData();
                    });
                }
            }
            @Override
            public void onError(String error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        refreshUI();
                        loadChartData();
                    });
                }
            }
        });
    }

    private void loadChartData() {
        glucoseRepository.getAllReadings(new GlucoseRepository.Callback<List<GlucoseReading>>() {
            @Override
            public void onResult(List<GlucoseReading> readings) {
                if (readings == null || readings.isEmpty()) return;
                List<Entry> entries = new ArrayList<>();
                Collections.sort(readings, (a, b) -> Long.compare(a.timestamp, b.timestamp));
                for (int i = 0; i < readings.size(); i++) {
                    entries.add(new Entry(i, readings.get(i).level));
                }
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        LineDataSet dataSet = new LineDataSet(entries, "Glucose Levels");
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
            }
            @Override
            public void onError(String error) { Log.e(TAG, "Chart Data Error: " + error); }
        });
    }

    private void checkSmsPermission() {
        if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        monitoringHandler.post(monitoringTask);
        syncData();
    }

    @Override
    public void onPause() {
        super.onPause();
        monitoringHandler.removeCallbacks(monitoringTask);
    }

    private void refreshUI() {
        if (ProfileFragment.userName != null && !ProfileFragment.userName.isEmpty()) {
            tvWelcome.setText("Welcome, " + ProfileFragment.userName.split(" ")[0]);
        }

        glucoseRepository.getAllReadings(new GlucoseRepository.Callback<List<GlucoseReading>>() {
            @Override
            public void onResult(List<GlucoseReading> readings) {
                if (readings != null && !readings.isEmpty()) {
                    GlucoseReading latest = readings.get(0); 
                    lastReading = latest.level;
                    lastUpdateTimestamp = latest.timestamp;
                    float sum = 0;
                    for (GlucoseReading r : readings) sum += r.level;
                    float avg = sum / readings.size();
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            tvLatestLevel.setText(String.valueOf(lastReading));
                            tvAvgLevel.setText(String.format(Locale.getDefault(), "%.1f", avg));
                            checkInactivityAndAlert(readings);
                        });
                    }
                } else {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            tvLatestLevel.setText("--");
                            tvAvgLevel.setText("--");
                            checkInactivityAndAlert(new ArrayList<>());
                        });
                    }
                }
            }
            @Override
            public void onError(String error) { Log.e(TAG, "Refresh UI Error: " + error); }
        });
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

    private void checkInactivityAndAlert(List<GlucoseReading> allReadings) {
        // SAFETY GUARD: If the user hasn't setup their profile OR has never logged a single reading, 
        // skip all inactivity alerts. This prevents new users from getting "Missed Meal" alerts immediately.
        if (emergencyPhone.equals("5551234567") || ProfileFragment.userName.equals("David Miller") || allReadings.isEmpty()) {
            cardEmergency.setVisibility(View.GONE);
            // However, we still check for the glucose level alert if they just added their first reading
            if (!allReadings.isEmpty()) {
                checkGlucoseAlert(lastReading);
            }
            return;
        }

        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int[] schedule = {ProfileFragment.breakfastTime, ProfileFragment.lunchTime, ProfileFragment.dinnerTime};
        String[] labels = {"Breakfast", "Lunch", "Dinner"};

        boolean alertShown = false;

        for (int i = 2; i >= 0; i--) {
            int mealTime = schedule[i];
            
            if (currentMinutes >= mealTime) {
                boolean foundLogForThisMeal = false;
                for (GlucoseReading r : allReadings) {
                    Calendar logCal = Calendar.getInstance();
                    logCal.setTimeInMillis(r.timestamp);
                    
                    boolean sameDay = logCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                                     logCal.get(Calendar.YEAR) == now.get(Calendar.YEAR);
                    int logMin = logCal.get(Calendar.HOUR_OF_DAY) * 60 + logCal.get(Calendar.MINUTE);

                    // A log is valid if it was recorded AFTER mealTime started or within the window.
                    if (sameDay && logMin >= (mealTime - 60)) {
                        foundLogForThisMeal = true;
                        break;
                    }
                }

                if (!foundLogForThisMeal) {
                    cardEmergency.setVisibility(View.VISIBLE);
                    if (currentMinutes >= mealTime + 60) {
                        tvAlertTitle.setText("LACK OF ACTIVITY ALERT");
                        tvAlertDesc.setText("Missed " + labels[i] + " reading! Emergency SMS sent to " + emergencyPhone);
                        sendEmergencySms(labels[i]);
                    } else {
                        tvAlertTitle.setText("INACTIVITY REMINDER");
                        tvAlertDesc.setText("It's time for your " + labels[i] + " glucose reading.");
                    }
                    alertShown = true;
                    break; 
                } else {
                    // Safe for the most recent meal!
                    break;
                }
            }
        }

        if (!alertShown) {
            checkGlucoseAlert(lastReading);
        }
    }

    private void sendEmergencySms(String meal) {
        if (lastAlertedMeal.equals(meal)) return;
        
        // Don't send SMS to the default dummy number
        if (emergencyPhone.equals("5551234567")) return;

        try {
            if (getContext() != null && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                SmsManager smsManager = SmsManager.getDefault();
                String message = "GlucoCare AI Alert: " + ProfileFragment.userName + " missed their " + meal + " reading by over 1 hour. Please check on them.";
                smsManager.sendTextMessage(emergencyPhone, null, message, null, null);
                lastAlertedMeal = meal;
                Toast.makeText(getContext(), "Emergency SMS Sent", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) { Log.e(TAG, "SMS Send Error: " + e.getMessage()); }
    }

    private void triggerManualSOS() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + emergencyPhone));
        startActivity(intent);
    }
}
