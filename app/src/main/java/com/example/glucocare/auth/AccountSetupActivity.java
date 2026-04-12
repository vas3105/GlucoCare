package com.example.glucocare.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.glucocare.MainActivity;
import com.example.glucocare.R;
import com.example.glucocare.UserProfile;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

public class AccountSetupActivity extends AppCompatActivity {

    private static final String TAG = "AccountSetup";

    private TextInputEditText etName, etAge, etDoctorName, etEmergencyPhone;
    private TextView          tvSetupError, tvSkipSetup;
    private MaterialButton    btnCompleteSetup;
    private ProgressBar       progressSetup;

    private LinearLayout cardMale, cardFemale, cardOther;
    private TextView     tvMale, tvFemale, tvOther;
    private String       selectedGender = "Male";

    private LinearLayout cardType1, cardType2, cardGestational;
    private TextView     tvType1, tvType2, tvGestational;
    private String       selectedDiabetesType = "";

    private String            uid;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accountsetup);

        uid = getIntent().getStringExtra("uid");

        if (TextUtils.isEmpty(uid)) {
            goToMain();
            return;
        }

        firestore = FirebaseFirestore.getInstance();

        bindViews();
        setupGenderSelector();
        setupDiabetesTypeSelector();
        setupListeners();
    }

    private void bindViews() {
        etName           = findViewById(R.id.etProfileName);
        etAge            = findViewById(R.id.etProfileAge);
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone);
        etDoctorName     = findViewById(R.id.etDoctorName);
        tvSetupError     = findViewById(R.id.tvSetupError);
        tvSkipSetup      = findViewById(R.id.tvSkipSetup);
        btnCompleteSetup = findViewById(R.id.btnCompleteSetup);
        progressSetup    = findViewById(R.id.progressSetup);

        cardMale         = findViewById(R.id.cardMale);
        cardFemale       = findViewById(R.id.cardFemale);
        cardOther        = findViewById(R.id.cardOther);
        tvMale           = findViewById(R.id.tvMale);
        tvFemale         = findViewById(R.id.tvFemale);
        tvOther          = findViewById(R.id.tvOther);

        cardType1        = findViewById(R.id.cardType1);
        cardType2        = findViewById(R.id.cardType2);
        cardGestational  = findViewById(R.id.cardGestational);
        tvType1          = findViewById(R.id.tvType1);
        tvType2          = findViewById(R.id.tvType2);
        tvGestational    = findViewById(R.id.tvGestational);
    }

    private void setupGenderSelector() {
        selectGender("Male");
        cardMale.setOnClickListener(v   -> selectGender("Male"));
        cardFemale.setOnClickListener(v -> selectGender("Female"));
        cardOther.setOnClickListener(v  -> selectGender("Prefer not to say"));
    }

    private void selectGender(String gender) {
        selectedGender = gender;
        setCardState(cardMale,   tvMale,   "Male".equals(gender));
        setCardState(cardFemale, tvFemale, "Female".equals(gender));
        setCardState(cardOther,  tvOther,  "Prefer not to say".equals(gender));
    }

    private void setupDiabetesTypeSelector() {
        cardType1.setOnClickListener(v       -> selectDiabetesType("Type 1"));
        cardType2.setOnClickListener(v       -> selectDiabetesType("Type 2"));
        cardGestational.setOnClickListener(v -> selectDiabetesType("Gestational"));
    }

    private void selectDiabetesType(String type) {
        selectedDiabetesType = type;
        setCardState(cardType1,       tvType1,       "Type 1".equals(type));
        setCardState(cardType2,       tvType2,       "Type 2".equals(type));
        setCardState(cardGestational, tvGestational, "Gestational".equals(type));
    }

    private void setCardState(LinearLayout card, TextView label, boolean selected) {
        card.setBackgroundResource(selected
                ? R.drawable.bg_gender_option_selected
                : R.drawable.bg_gender_option);
        label.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.secondary : R.color.text_muted));
    }

    private void setupListeners() {
        btnCompleteSetup.setOnClickListener(v -> attemptSave());
        tvSkipSetup.setOnClickListener(v -> goToMain());
    }

    private void attemptSave() {
        String name       = etName.getText().toString().trim();
        String ageStr     = etAge.getText().toString().trim();
        String phone      = etEmergencyPhone.getText().toString().trim();
        String doctorName = etDoctorName.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            showError("Please enter your name."); return;
        }
        if (TextUtils.isEmpty(ageStr)) {
            showError("Please enter your age."); return;
        }
        if (TextUtils.isEmpty(phone)) {
            showError("Please enter emergency phone number."); return;
        }
        if (TextUtils.isEmpty(selectedDiabetesType)) {
            showError("Please select your diabetes type."); return;
        }

        try {
            Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            showError("Please enter a valid age.");
            return;
        }

        setLoading(true);

        UserProfile profile = new UserProfile(
                uid, name, ageStr,
                selectedGender,
                selectedDiabetesType,
                TextUtils.isEmpty(doctorName) ? "" : doctorName
        );
        profile.emergencyPhone = phone;

        firestore.collection("users")
                .document(uid)
                .collection("profile")
                .document("data")
                .set(profile)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Save failed: " + e.getMessage());
                });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setLoading(boolean loading) {
        btnCompleteSetup.setEnabled(!loading);
        progressSetup.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvSetupError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        tvSetupError.setText(msg);
        tvSetupError.setVisibility(View.VISIBLE);
    }
}
