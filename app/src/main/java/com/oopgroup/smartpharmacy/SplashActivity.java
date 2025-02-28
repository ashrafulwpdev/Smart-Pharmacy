package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Intent intent = getIntent();
        Uri deepLink = intent.getData();
        if (deepLink != null) {
            Log.d(TAG, "Deep link received in SplashActivity: " + deepLink.toString());
            // Check if the deep link is a reset password link
            if (deepLink.toString().contains("smartpharmacyuni.page.link") ||
                    (deepLink.getHost().equals("smart-pharmacy-city.firebaseapp.com") &&
                            deepLink.getPath().startsWith("/__/auth/action") &&
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

        // Normal flow if no deep link or not a reset link
        Log.d(TAG, "Proceeding to OnBoardingActivity");
        Intent nextIntent = new Intent(this, OnBoardingActivity.class);
        startActivity(nextIntent);
        finish();
    }
}