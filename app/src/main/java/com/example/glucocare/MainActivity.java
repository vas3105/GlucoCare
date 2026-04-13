package com.example.glucocare;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.glucocare.auth.LoginActivity;
import com.example.glucocare.repository.GlucoseRepository;
import com.example.glucocare.repository.MedicineRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

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

    /**
     * Signs out the user and clears all local Room data.
     * Call this from ProfileFragment's sign-out button.
     *
     * Clears local data BEFORE signing out so getCurrentUser()
     * is still available when repositories clear their data.
     */
    public void signOut() {
        // Clear local Room data BEFORE signing out
        new MedicineRepository(this).clearLocalDataOnLogout();
        new GlucoseRepository(this).clearLocalDataOnLogout();

        // Sign out from Firebase
        auth.signOut();

        // Navigate to login, clear back stack
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}