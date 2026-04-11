package com.example.glucocare;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.glucocare.auth.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * MainActivity — hosts all fragments via BottomNavigationView.
 *
 * Auth check: if somehow reached without a signed-in user,
 * redirect immediately back to LoginActivity.
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // Guard: must be logged in to reach here
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selected = null;
            int id = item.getItemId();

            if      (id == R.id.nav_home)     selected = new HomeFragment();
            else if (id == R.id.nav_logs)     selected = new LogsFragment();
            else if (id == R.id.nav_meds)     selected = new MedsFragment();
            else if (id == R.id.nav_insights) selected = new InsightsFragment();
            else if (id == R.id.nav_profile)  selected = new ProfileFragment();

            if (selected != null) { loadFragment(selected); return true; }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    // ── Sign out — call this from ProfileFragment ─────────────────────────────
    public void signOut() {
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}