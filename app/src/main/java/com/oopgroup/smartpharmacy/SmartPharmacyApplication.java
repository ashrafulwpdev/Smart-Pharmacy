package com.oopgroup.smartpharmacy;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.BuildConfig;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.oopgroup.smartpharmacy.fragments.NoInternetFragment;
import com.oopgroup.smartpharmacy.utils.NetworkUtils;

public class SmartPharmacyApplication extends Application implements Application.ActivityLifecycleCallbacks, LifecycleObserver {

    private static final String TAG = "SmartPharmacyApplication";
    private FragmentActivity currentActivity;
    private boolean isShowingNoInternet;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        logPlayServicesStatus();
        initializeFirebase();
        setupAppCheck();
        NetworkUtils.initNetworkMonitoring(this);
    }

    private void logPlayServicesStatus() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int result = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (result == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play Services available, version: " + GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE);
        } else {
            Log.e(TAG, "Google Play Services unavailable: " + result);
            if (currentActivity != null) {
                googleApiAvailability.showErrorNotification(currentActivity, result);
            }
        }
    }

    private void initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase", e);
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
                Log.d(TAG, "Debug App Check provider installed");
            } else {
                Log.d(TAG, "App Check setup skipped in release mode (using Play Integrity by default)");
                // firebaseAppCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set up App Check", e);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForeground() {
        observeNetworkStatus();
    }

    private void observeNetworkStatus() {
        NetworkUtils.getNetworkStatus().observeForever(isConnected -> {
            if (isConnected == null || currentActivity == null) return;
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> {
                if (!isConnected && !isShowingNoInternet && !isActivityExcluded()) {
                    showNoInternetFragment();
                } else if (isConnected && isShowingNoInternet) {
                    hideNoInternetFragment();
                }
            }, 500);
        });
    }

    private void showNoInternetFragment() {
        if (currentActivity instanceof FragmentActivity) {
            isShowingNoInternet = true;
            currentActivity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, NoInternetFragment.newInstance(), "NoInternetFragment")
                    .addToBackStack(null)
                    .commitAllowingStateLoss();
        }
    }

    private void hideNoInternetFragment() {
        if (currentActivity instanceof FragmentActivity) {
            isShowingNoInternet = false;
            currentActivity.getSupportFragmentManager().popBackStack("NoInternetFragment", 0);
        }
    }

    private boolean isActivityExcluded() {
        return currentActivity != null && (
                currentActivity.getClass().getSimpleName().equals("LoginActivity") ||
                        currentActivity.getClass().getSimpleName().equals("SplashActivity")
        );
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (activity instanceof FragmentActivity) {
            currentActivity = (FragmentActivity) activity;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity instanceof FragmentActivity) {
            currentActivity = (FragmentActivity) activity;
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (activity == currentActivity) {
            currentActivity = null;
            isShowingNoInternet = false;
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        NetworkUtils.stopNetworkMonitoring(this);
        unregisterActivityLifecycleCallbacks(this);
        handler.removeCallbacksAndMessages(null);
    }
}