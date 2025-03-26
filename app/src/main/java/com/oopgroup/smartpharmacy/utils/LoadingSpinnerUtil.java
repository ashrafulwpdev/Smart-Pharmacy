package com.oopgroup.smartpharmacy.utils;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieListener;
import com.oopgroup.smartpharmacy.R;

public class LoadingSpinnerUtil {
    private static final String TAG = "LoadingSpinnerUtil";
    private LottieAnimationView loadingSpinner;

    public LoadingSpinnerUtil(Context context, LottieAnimationView loadingSpinner) {
        this.loadingSpinner = loadingSpinner;
        setupLoadingSpinner(context);
    }

    public static LoadingSpinnerUtil initialize(Context context, View view, int lottieViewId) {
        LottieAnimationView spinner = view.findViewById(lottieViewId);
        if (spinner == null) {
            Log.e(TAG, "Loading spinner view not found with ID: " + lottieViewId);
            return null;
        }
        Log.d(TAG, "Loading spinner view found: " + spinner);
        return new LoadingSpinnerUtil(context, spinner);
    }

    private void setupLoadingSpinner(Context context) {
        if (loadingSpinner == null) {
            Log.e(TAG, "Loading spinner is null");
            return;
        }

        Log.d(TAG, "Setting up loading spinner");
        LottieCompositionFactory.fromRawRes(context, R.raw.loading_global)
                .addListener(new LottieListener<LottieComposition>() {
                    @Override
                    public void onResult(LottieComposition composition) {
                        Log.d(TAG, "Lottie animation loaded successfully");
                        loadingSpinner.setComposition(composition);
                        loadingSpinner.setRepeatCount(LottieDrawable.INFINITE);
                        loadingSpinner.playAnimation();
                        loadingSpinner.setVisibility(View.GONE); // Initially hidden
                    }
                })
                .addFailureListener(throwable -> {
                    Log.e(TAG, "Failed to load Lottie animation: " + throwable.getMessage());
                    loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(context, "Failed to load loading animation", Toast.LENGTH_SHORT).show();
                });
    }

    public void toggleLoadingSpinner(boolean show) {
        if (loadingSpinner != null) {
            Log.d(TAG, "Toggling spinner visibility to: " + show);
            loadingSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                loadingSpinner.playAnimation();
            } else {
                loadingSpinner.cancelAnimation();
            }
        } else {
            Log.e(TAG, "Cannot toggle spinner - loadingSpinner is null");
        }
    }

    /**
     * Cleans up resources used by the loading spinner.
     */
    public void cleanup() {
        if (loadingSpinner != null) {
            Log.d(TAG, "Cleaning up loading spinner");
            loadingSpinner.cancelAnimation();
            loadingSpinner.setVisibility(View.GONE);
            // Removed clearComposition() as it’s private; cancelAnimation and visibility change should suffice
        } else {
            Log.e(TAG, "Cannot cleanup - loadingSpinner is null");
        }
    }
}