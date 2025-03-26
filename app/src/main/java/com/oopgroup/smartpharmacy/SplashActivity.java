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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DELAY_MS = 2000; // 2 seconds
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize handler
        handler = new Handler(Looper.getMainLooper());

        // Initialize SharedPreferences
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

        // Initialize Firebase and check Play Services
        initializeFirebaseAndProceed();
    }

    private void initializeFirebaseAndProceed() {
        // Manually initialize Firebase if not already done
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized manually");
        } else {
            Log.d(TAG, "Firebase already initialized");
        }

        // Check Google Play Services availability
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services unavailable: " + result);
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Play Services resolved, proceeding");
                        initializeAuthAndNavigate();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to resolve Play Services: " + e.getMessage());
                        // Fallback: Proceed anyway, as app is functional
                        initializeAuthAndNavigate();
                    });
        } else {
            Log.d(TAG, "Google Play Services available");
            initializeAuthAndNavigate();
        }
    }

    private void initializeAuthAndNavigate() {
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        // Navigate after delay
        handler.postDelayed(this::navigateToNextScreen, SPLASH_DELAY_MS);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}