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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

/**
 * SignupActivity — creates a new Firebase Auth account.
 * After signup → goes straight to MainActivity (no manual login needed).
 */
public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword;
    private TextView          tvError, tvGoToLogin;
    private MaterialButton    btnCreateAccount;
    private ProgressBar       progressBar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        bindViews();
        setupListeners();
    }

    // ── Bind ─────────────────────────────────────────────────────────────────

    private void bindViews() {
        etName             = findViewById(R.id.etSignupName);
        etEmail            = findViewById(R.id.etSignupEmail);
        etPassword         = findViewById(R.id.etSignupPassword);
        etConfirmPassword  = findViewById(R.id.etSignupConfirmPassword);
        tvError            = findViewById(R.id.tvSignupError);
        tvGoToLogin        = findViewById(R.id.tvGoToLogin);
        btnCreateAccount   = findViewById(R.id.btnCreateAccount);
        progressBar        = findViewById(R.id.progressSignup);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnCreateAccount.setOnClickListener(v -> attemptSignup());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    // ── Signup ────────────────────────────────────────────────────────────────

    private void attemptSignup() {
        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirm  = etConfirmPassword.getText().toString().trim();

        // Validate
        if (TextUtils.isEmpty(name)) {
            showError("Please enter your name.");  return;
        }
        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email."); return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters."); return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match."); return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // Optionally update display name
                    if (result.getUser() != null) {
                        com.google.firebase.auth.UserProfileChangeRequest profile =
                                new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build();
                        result.getUser().updateProfile(profile);
                    }
                    setLoading(false);
                    goToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(friendlyError(e.getMessage()));
                });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnCreateAccount.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private String friendlyError(String raw) {
        if (raw == null) return "Signup failed. Please try again.";
        if (raw.contains("email already"))  return "An account with this email already exists.";
        if (raw.contains("badly formatted")) return "Please enter a valid email address.";
        if (raw.contains("network"))         return "No internet connection.";
        return "Signup failed: " + raw;
    }
}