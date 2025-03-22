package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DELAY_MS = 2000; // 2 seconds
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize Firebase Auth and SharedPreferences
        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("SmartPharmacyPrefs", MODE_PRIVATE);

        // Handle deep links
        Intent intent = getIntent();
        Uri deepLink = intent.getData();

        if (deepLink != null) {
            Log.d(TAG, "Deep link received: " + deepLink.toString());
            handleDeepLink(deepLink);
            return;
        }

        Log.d(TAG, "No deep link received");
        // Show splash screen for specified duration then navigate
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNextScreen, SPLASH_DELAY_MS);
    }

    private void handleDeepLink(Uri deepLink) {
        boolean isResetPasswordLink = deepLink.toString().contains("smartpharmacyuni.page.link") ||
                (deepLink.getHost() != null &&
                        deepLink.getHost().equals("smart-pharmacy-city.firebaseapp.com") &&
                        deepLink.getPath() != null &&
                        deepLink.getPath().startsWith("/__/auth/action") &&
                        "resetPassword".equals(deepLink.getQueryParameter("mode")));

        if (isResetPasswordLink) {
            Intent resetIntent = new Intent(this, ResetPasswordActivity.class);
            resetIntent.setData(deepLink);
            startActivity(resetIntent);
            finish();
        }
    }

    private void navigateToNextScreen() {
        Intent nextIntent;

        if (mAuth.getCurrentUser() != null) {
            Log.d(TAG, "User authenticated, navigating to MainActivity");
            nextIntent = new Intent(this, MainActivity.class);
        } else {
            boolean onboardingCompleted = prefs.getBoolean("onboardingCompleted", false);

            if (onboardingCompleted) {
                Log.d(TAG, "Onboarding completed, navigating to LoginActivity");
                nextIntent = new Intent(this, LoginActivity.class);
            } else {
                Log.d(TAG, "First launch, navigating to OnBoardingActivity");
                nextIntent = new Intent(this, OnBoardingActivity.class);
            }
        }

        startActivity(nextIntent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        // Prevent back press during splash screen
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Maintain immersive mode
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}