package com.example.glucocare;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);

        // Default fragment
        loadFragment(new HomeFragment());

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            if (item.getItemId() == R.id.nav_home)
                selectedFragment = new HomeFragment();
            else if (item.getItemId() == R.id.nav_logs)
                selectedFragment = new LogsFragment();
            else if (item.getItemId() == R.id.nav_meds)
                selectedFragment = new MedsFragment();
            else if (item.getItemId() == R.id.nav_profile)
                selectedFragment = new ProfileFragment();
            else if (item.getItemId()==R.id.nav_insights)
                selectedFragment=new InsightsFragment();

            return loadFragment(selectedFragment);
        });
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}