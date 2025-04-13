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
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DELAY_MS = 2000; // 2 seconds
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set background_light as fallback
        getWindow().setBackgroundDrawableResource(R.color.background_light);
        setContentView(R.layout.activity_splash);

        // Initialize handler
        handler = new Handler(Looper.getMainLooper());

        // Initialize SharedPreferences
        prefs = getSharedPreferences("SmartPharmacyPrefs", MODE_PRIVATE);

        // Log intent details
        Intent intent = getIntent();
        Log.d(TAG, "Intent received: action=" + intent.getAction() + ", data=" + intent.getData());

        // Handle Dynamic Links
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(intent)
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    Uri deepLink = null;
                    if (pendingDynamicLinkData != null) {
                        deepLink = pendingDynamicLinkData.getLink();
                    }
                    Log.d(TAG, "Dynamic link result: " + (deepLink != null ? deepLink.toString() : "null"));
                    if (deepLink != null) {
                        handleDeepLink(deepLink);
                    } else {
                        Log.d(TAG, "No dynamic link, checking intent data");
                        Uri intentData = intent.getData();
                        Log.d(TAG, "Intent data: " + (intentData != null ? intentData.toString() : "null"));
                        if (intentData != null) {
                            handleDeepLink(intentData);
                        } else {
                            initializeFirebaseAndProceed();
                        }
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Failed to retrieve dynamic link: " + e.getMessage());
                    initializeFirebaseAndProceed();
                });
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
        Log.d(TAG, "Handling deep link: " + deepLink.toString());
        String mode = deepLink.getQueryParameter("mode");
        String oobCode = deepLink.getQueryParameter("oobCode");
        Log.d(TAG, "Deep link details: mode=" + mode + ", oobCode=" + oobCode);

        boolean isResetPasswordLink = deepLink.toString().contains("smartpharmacyuni.page.link/resetpass") ||
                (deepLink.getHost() != null &&
                        deepLink.getHost().equals("smart-pharmacy-city.firebaseapp.com") &&
                        deepLink.getPath() != null &&
                        deepLink.getPath().startsWith("/__/auth/action") &&
                        "resetPassword".equals(mode));

        if (isResetPasswordLink && oobCode != null) {
            Log.d(TAG, "Reset password link detected, navigating to ResetPasswordActivity");
            Intent resetIntent = new Intent(this, ResetPasswordActivity.class);
            resetIntent.setData(deepLink);
            resetIntent.putExtra("isEmailReset", true);
            startActivity(resetIntent);
            finish();
        } else {
            Log.w(TAG, "Invalid or unhandled deep link: " + deepLink.toString());
            initializeFirebaseAndProceed();
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
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}