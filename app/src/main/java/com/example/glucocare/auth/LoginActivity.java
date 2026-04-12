package com.example.glucocare.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.glucocare.MainActivity;
import com.example.glucocare.R;
import com.example.glucocare.repository.GlucoseRepository;
import com.example.glucocare.repository.MedicineRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * LoginActivity — entry point of the app.
 *
 * On launch:
 *  • If user is already signed in → go straight to MainActivity.
 *  • Otherwise → show login form.
 *
 * After successful login → sync Firestore data to Room → open MainActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TextView          tvError, tvGoToSignup;
    private MaterialButton    btnSignIn;
    private ProgressBar       progressBar;

    private FirebaseAuth      auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // Already logged in — skip to main
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        bindViews();
        setupListeners();
    }

    // ── Bind ─────────────────────────────────────────────────────────────────

    private void bindViews() {
        etEmail      = findViewById(R.id.etLoginEmail);
        etPassword   = findViewById(R.id.etLoginPassword);
        tvError      = findViewById(R.id.tvLoginError);
        tvGoToSignup = findViewById(R.id.tvGoToSignup);
        btnSignIn    = findViewById(R.id.btnSignIn);
        progressBar  = findViewById(R.id.progressLogin);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        btnSignIn.setOnClickListener(v -> attemptLogin());

        tvGoToSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validate
        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password.");
            return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // Sync cloud data to local Room, then open app
                    syncAndProceed();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(friendlyError(e.getMessage()));
                });
    }

    // ── Sync then navigate ────────────────────────────────────────────────────

    private void syncAndProceed() {
        GlucoseRepository  glucoseRepo  = new GlucoseRepository(this);
        MedicineRepository medicineRepo = new MedicineRepository(this);

        // Sync glucose readings
        glucoseRepo.syncFromFirestore(new GlucoseRepository.Callback<Void>() {
            @Override public void onResult(Void v) {
                // Sync medications next
                medicineRepo.syncFromFirestore(new MedicineRepository.Callback<Void>() {
                    @Override public void onResult(Void v2) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            goToMain();
                        });
                    }
                    @Override public void onError(String error) {
                        // Sync failed but local data is usable — continue anyway
                        runOnUiThread(() -> { setLoading(false); goToMain(); });
                    }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> { setLoading(false); goToMain(); });
            }
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish(); // remove LoginActivity from back stack
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnSignIn.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private String friendlyError(String raw) {
        if (raw == null) return "Login failed. Please try again.";
        if (raw.contains("password")) return "Incorrect password. Please try again.";
        if (raw.contains("no user"))  return "No account found with that email.";
        if (raw.contains("network"))  return "No internet connection.";
        return "Login failed: " + raw;
    }
}