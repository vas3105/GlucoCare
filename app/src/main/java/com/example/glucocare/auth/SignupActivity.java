package com.example.glucocare.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.glucocare.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SignupActivity — creates a new Firebase Auth account.
 *
 * Key fix: after signup, uid is read immediately from the AuthResult
 * (not from getCurrentUser() later) and passed via Intent to
 * AccountSetupActivity so there is no timing issue.
 */
public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword, etConfirmPassword;
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

    private void bindViews() {
        etEmail           = findViewById(R.id.etSignupEmail);
        etPassword        = findViewById(R.id.etSignupPassword);
        etConfirmPassword = findViewById(R.id.etSignupConfirmPassword);
        tvError           = findViewById(R.id.tvSignupError);
        tvGoToLogin       = findViewById(R.id.tvGoToLogin);
        btnCreateAccount  = findViewById(R.id.btnCreateAccount);
        progressBar       = findViewById(R.id.progressSignup);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnCreateAccount.setOnClickListener(v -> attemptSignup());
        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptSignup() {
        String email   = etEmail.getText().toString().trim();
        String password= etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

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
                    setLoading(false);

                    // ── Read uid directly from AuthResult — guaranteed non-null ──
                    FirebaseUser user = result.getUser();
                    String uid = (user != null) ? user.getUid() : "";

                    goToAccountSetup(uid);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(friendlyError(e.getMessage()));
                });
    }

    /**
     * Passes uid as an Intent extra so AccountSetupActivity
     * never needs to call getCurrentUser() itself.
     */
    private void goToAccountSetup(String uid) {
        Intent intent = new Intent(this, AccountSetupActivity.class);
        intent.putExtra("uid", uid);         // ← key fix
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

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
        if (raw.contains("email already"))    return "An account with this email already exists.";
        if (raw.contains("badly formatted"))  return "Please enter a valid email address.";
        if (raw.contains("network"))          return "No internet connection.";
        return "Signup failed: " + raw;
    }
}