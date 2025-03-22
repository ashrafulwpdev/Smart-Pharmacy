package com.oopgroup.smartpharmacy;

import android.util.Log;

import com.google.firebase.FirebaseApp;

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseApp.initializeApp(this);
            Log.d("MyApplication", "Firebase initialized successfully");
        } catch (Exception e) {
            Log.e("MyApplication", "Failed to initialize Firebase: " + e.getMessage());
        }
    }
}