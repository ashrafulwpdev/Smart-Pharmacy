package com.oopgroup.smartpharmacy;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.BuildConfig;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public class Application extends android.app.Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        logPlayServicesStatus();
        initializeFirebase();
        setupAppCheck();
    }

    private void logPlayServicesStatus() {
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (result == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play Services available, version: " + GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE);
        } else {
            Log.e(TAG, "Google Play Services unavailable: " + result);
        }
    }

    private void initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase: " + e.getMessage());
        }
    }

    private void setupAppCheck() {
        try {
            FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Setting up Debug App Check provider");
                firebaseAppCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                );
            } else {
                Log.d(TAG, "App Check setup skipped in release mode (using Play Integrity by default)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set up App Check: " + e.getMessage());
        }
    }
}