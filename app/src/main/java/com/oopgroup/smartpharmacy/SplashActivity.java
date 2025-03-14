package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("SmartPharmacyPrefs", MODE_PRIVATE);

        // Handle deep links first
        Intent intent = getIntent();
        Uri deepLink = intent.getData();
        if (deepLink != null) {
            Log.d(TAG, "Deep link received in SplashActivity: " + deepLink.toString());
            // Check if the deep link is a reset password link
            if (deepLink.toString().contains("smartpharmacyuni.page.link") ||
                    (deepLink.getHost() != null && deepLink.getHost().equals("smart-pharmacy-city.firebaseapp.com") &&
                            deepLink.getPath() != null && deepLink.getPath().startsWith("/__/auth/action") &&
                            "resetPassword".equals(deepLink.getQueryParameter("mode")))) {
                Intent resetIntent = new Intent(this, ResetPasswordActivity.class);
                resetIntent.setData(deepLink);
                startActivity(resetIntent);
                finish();
                return;
            }
        } else {
            Log.d(TAG, "No deep link received in SplashActivity");
        }

        // Delay to show splash screen (e.g., 2 seconds) and then navigate
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNextScreen, 2000);
    }

    private void navigateToNextScreen() {
        // Check if user is logged in
        if (mAuth.getCurrentUser() != null) {
            Log.d(TAG, "User is logged in, proceeding to MainActivity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            // Check if onboarding has been completed
            boolean onboardingCompleted = prefs.getBoolean("onboardingCompleted", false);
            if (onboardingCompleted) {
                Log.d(TAG, "Onboarding completed, proceeding to LoginActivity");
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                Log.d(TAG, "Onboarding not completed, proceeding to OnBoardingActivity");
                startActivity(new Intent(this, OnBoardingActivity.class));
            }
            finish();
        }
    }
}