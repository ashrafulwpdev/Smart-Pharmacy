package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class OnBoardingActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Initialize SharedPreferences
        prefs = getSharedPreferences("SmartPharmacyPrefs", MODE_PRIVATE);

        // Find the Get Started button
        Button getStartedButton = findViewById(R.id.get_started_button);
        getStartedButton.setOnClickListener(v -> {
            // Mark onboarding as completed
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("onboardingCompleted", true);
            editor.apply();

            // Navigate to LoginActivity
            Intent intent = new Intent(OnBoardingActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // Close OnBoardingActivity to prevent back navigation
        });
    }
}