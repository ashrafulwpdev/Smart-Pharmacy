package com.oopgroup.smartpharmacy.utils;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.oopgroup.smartpharmacy.R;

import java.io.IOException;

/**
 * BaseActivity provides a reusable Lottie-based loading overlay for Android apps.
 * It blocks UI interaction during background tasks with customizable animations and colors.
 */
public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";
    private static final int DEFAULT_ANIMATION_DURATION = 250;
    public static final int DEFAULT_LOADER_SIZE_DP = 40;
    private static final int LOADER_PADDING_DP = 16;
    private static final int MAX_LOADER_DURATION_MS = 15000; // 15 sec timeout
    public static final int DEFAULT_OVERLAY_COLOR = Color.parseColor("#80000000"); // Default dark gray overlay

    private FrameLayout loaderOverlay;
    private LottieAnimationView loaderAnimation;
    private int currentAnimationRes = -1;
    private boolean isLoaderVisible = false;
    private Runnable hideRunnable;
    private LoaderCallback loaderCallback;

    // Callback interface for loading state changes
    public interface LoaderCallback {
        void onLoaderShown();
        void onLoaderHidden();
    }

    /**
     * Configuration class for the loader settings.
     */
    public static class LoaderConfig {
        int animationResId = R.raw.loading_global;
        int widthDp = DEFAULT_LOADER_SIZE_DP;
        int heightDp = DEFAULT_LOADER_SIZE_DP;
        boolean useRoundedBox = false;
        int overlayColor = DEFAULT_OVERLAY_COLOR;
        boolean changeJsonColor = true;
        int jsonColor = Color.parseColor("#4CAF50");
        float animationSpeed = 1.0f;

        // Default constructor with preset values
        public LoaderConfig() {
            // Defaults are set above
        }

        // Optional: Setter methods for chaining
        public LoaderConfig setAnimationResId(int animationResId) {
            this.animationResId = animationResId;
            return this;
        }

        public LoaderConfig setWidthDp(int widthDp) {
            this.widthDp = widthDp;
            return this;
        }

        public LoaderConfig setHeightDp(int heightDp) {
            this.heightDp = heightDp;
            return this;
        }

        public LoaderConfig setUseRoundedBox(boolean useRoundedBox) {
            this.useRoundedBox = useRoundedBox;
            return this;
        }

        public LoaderConfig setOverlayColor(int overlayColor) {
            this.overlayColor = overlayColor;
            return this;
        }

        public LoaderConfig setChangeJsonColor(boolean changeJsonColor) {
            this.changeJsonColor = changeJsonColor;
            return this;
        }

        public LoaderConfig setJsonColor(int jsonColor) {
            this.jsonColor = jsonColor;
            return this;
        }

        public LoaderConfig setAnimationSpeed(float animationSpeed) {
            this.animationSpeed = animationSpeed;
            return this;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Converts DP to pixels for consistent sizing across devices.
     */
    public int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Validates if a raw resource exists.
     */
    private boolean isValidResource(int resId) {
        try {
            getResources().openRawResource(resId).close();
            return true;
        } catch (Resources.NotFoundException | IOException e) {
            Log.e(TAG, "Resource validation failed for ID: " + resId, e);
        }
        return false;
    }

    /**
     * Checks if the device is in dark mode (kept for future use if you want theme-aware colors).
     */
    public boolean isDarkMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Sets a callback for loader state changes.
     */
    public void setLoaderCallback(LoaderCallback callback) {
        this.loaderCallback = callback;
    }

    /**
     * Returns whether the loader is currently visible.
     */
    public boolean isLoaderVisible() {
        return isLoaderVisible;
    }

    /**
     * Shows a default Lottie loader with original JSON color, 40dp size, no rounded box, and no overlay.
     */
    public void showDefaultLoader() {
        LoaderConfig config = new LoaderConfig()
                .setAnimationResId(R.raw.loading_global)
                .setWidthDp(DEFAULT_LOADER_SIZE_DP)
                .setHeightDp(DEFAULT_LOADER_SIZE_DP)
                .setUseRoundedBox(false)
                .setOverlayColor(Color.TRANSPARENT) // No overlay
                .setChangeJsonColor(false);         // Use original JSON color
        showCustomLoader(config);
    }

    /**
     * Shows a custom Lottie loader using a LoaderConfig object.
     */
    public void showCustomLoader(LoaderConfig config) {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show loader");
            return;
        }

        int widthPx = dpToPx(config.widthDp);
        int heightPx = dpToPx(config.heightDp);

        if (loaderOverlay == null) {
            initializeLoaderOverlay(config.animationResId, widthPx, heightPx, config.useRoundedBox,
                    config.overlayColor, config.changeJsonColor, config.jsonColor, config.animationSpeed);
        } else {
            updateLoaderOverlay(config.animationResId, config.overlayColor, config.changeJsonColor,
                    config.jsonColor, config.animationSpeed);
        }

        loaderOverlay.setAlpha(0f);
        loaderOverlay.animate().alpha(1f).setDuration(DEFAULT_ANIMATION_DURATION).start();
        loaderAnimation.playAnimation();
        isLoaderVisible = true;

        if (loaderCallback != null) {
            loaderCallback.onLoaderShown();
        }
        startLoaderTimeout();
    }

    /**
     * Original method kept for internal use or backward compatibility.
     */
    public void showCustomLoader(int animationResId, int width, int height, boolean useRoundedBox,
                                 int overlayColor, boolean changeJsonColor, int jsonColor, float animationSpeed) {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot show loader");
            return;
        }

        if (loaderOverlay == null) {
            initializeLoaderOverlay(animationResId, width, height, useRoundedBox, overlayColor,
                    changeJsonColor, jsonColor, animationSpeed);
        } else {
            updateLoaderOverlay(animationResId, overlayColor, changeJsonColor, jsonColor, animationSpeed);
        }

        loaderOverlay.setAlpha(0f);
        loaderOverlay.animate().alpha(1f).setDuration(DEFAULT_ANIMATION_DURATION).start();
        loaderAnimation.playAnimation();
        isLoaderVisible = true;

        if (loaderCallback != null) {
            loaderCallback.onLoaderShown();
        }
        startLoaderTimeout();
    }

    /**
     * Starts a timeout to auto-hide the loader after MAX_LOADER_DURATION_MS.
     */
    private void startLoaderTimeout() {
        if (hideRunnable != null) {
            loaderOverlay.removeCallbacks(hideRunnable);
        }
        hideRunnable = this::hideLoader;
        loaderOverlay.postDelayed(hideRunnable, MAX_LOADER_DURATION_MS);
    }

    /**
     * Initializes the loader overlay and animation view.
     */
    private void initializeLoaderOverlay(int animationResId, int width, int height, boolean useRoundedBox,
                                         int overlayColor, boolean changeJsonColor, int jsonColor, float animationSpeed) {
        try {
            loaderOverlay = new FrameLayout(this);
            loaderOverlay.setClickable(true);
            loaderOverlay.setFocusable(true);
            loaderOverlay.setOnTouchListener((v, event) -> true); // Block touch events

            FrameLayout animationContainer = createAnimationContainer(useRoundedBox, width, height);
            loaderAnimation = createLottieAnimationView(animationResId, changeJsonColor, jsonColor, animationSpeed);

            animationContainer.addView(loaderAnimation, createLayoutParams(width, height));
            loaderOverlay.addView(animationContainer, createContainerLayoutParams(width, height, useRoundedBox));
            addOverlayToRootView(loaderOverlay, overlayColor);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing loader: " + e.getMessage(), e);
            cleanupLoader();
        }
    }

    /**
     * Creates a container for the animation.
     */
    private FrameLayout createAnimationContainer(boolean useRoundedBox, int width, int height) {
        FrameLayout animationContainer = new FrameLayout(this);
        if (useRoundedBox) {
            animationContainer.setBackgroundResource(R.drawable.loader_background);
            int padding = dpToPx(LOADER_PADDING_DP);
            animationContainer.setPadding(padding, padding, padding, padding);
        }
        return animationContainer;
    }

    /**
     * Creates the Lottie animation view with speed and accessibility.
     */
    private LottieAnimationView createLottieAnimationView(int animationResId, boolean changeJsonColor,
                                                          int jsonColor, float animationSpeed) {
        LottieAnimationView animationView = new LottieAnimationView(this);
        animationView.setContentDescription("Loading animation"); // Accessibility
        if (isValidResource(animationResId)) {
            animationView.setAnimation(animationResId);
            currentAnimationRes = animationResId;
        } else {
            Log.e(TAG, "Loader file missing. Loading fallback animation.");
            animationView.setAnimation(R.raw.default_loader);
            currentAnimationRes = R.raw.default_loader;
        }
        animationView.setRepeatCount(LottieDrawable.INFINITE);
        animationView.setSpeed(animationSpeed);
        if (changeJsonColor) {
            animationView.addValueCallback(new KeyPath("**"), LottieProperty.COLOR, new LottieValueCallback<>(jsonColor));
        }
        return animationView;
    }

    /**
     * Creates layout params for the animation view.
     */
    private FrameLayout.LayoutParams createLayoutParams(int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        return params;
    }

    /**
     * Creates layout params for the container.
     */
    private FrameLayout.LayoutParams createContainerLayoutParams(int width, int height, boolean useRoundedBox) {
        int backgroundWidth = useRoundedBox ? width + dpToPx(32) : ViewGroup.LayoutParams.WRAP_CONTENT;
        int backgroundHeight = useRoundedBox ? height + dpToPx(32) : ViewGroup.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(backgroundWidth, backgroundHeight);
        params.gravity = Gravity.CENTER;
        return params;
    }

    /**
     * Adds the overlay to the root view.
     */
    private void addOverlayToRootView(FrameLayout overlay, int overlayColor) {
        ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.addView(overlay);
            overlay.setBackgroundColor(overlayColor);
            Log.d(TAG, "Overlay added with color: " + String.format("#%08X", overlayColor));
        } else {
            Log.e(TAG, "Root view not found, cannot show loader");
        }
    }

    /**
     * Updates the existing loader overlay and animation view.
     */
    private void updateLoaderOverlay(int animationResId, int overlayColor, boolean changeJsonColor, int jsonColor,
                                     float animationSpeed) {
        loaderOverlay.setBackgroundColor(overlayColor);
        loaderOverlay.setVisibility(View.VISIBLE);

        if (animationResId != currentAnimationRes && isValidResource(animationResId)) {
            loaderAnimation.setAnimation(animationResId);
            currentAnimationRes = animationResId;
        }

        loaderAnimation.setSpeed(animationSpeed);
        if (changeJsonColor) {
            loaderAnimation.addValueCallback(new KeyPath("**"), LottieProperty.COLOR, new LottieValueCallback<>(jsonColor));
        }

        if (!loaderAnimation.isAnimating()) {
            loaderAnimation.playAnimation();
        }
    }

    /**
     * Hides the Lottie loader with a fade-out animation.
     */
    public void hideLoader() {
        if (loaderOverlay != null && isLoaderVisible) {
            Log.d(TAG, "Hiding loader");
            loaderOverlay.animate()
                    .alpha(0f)
                    .setDuration(DEFAULT_ANIMATION_DURATION)
                    .withEndAction(this::cleanupLoader)
                    .start();
            if (hideRunnable != null) {
                loaderOverlay.removeCallbacks(hideRunnable);
                hideRunnable = null;
            }
            if (loaderCallback != null) {
                loaderCallback.onLoaderHidden();
            }
        }
    }

    /**
     * Cleans up the loader resources and resets the state.
     */
    private void cleanupLoader() {
        if (loaderOverlay != null) {
            loaderOverlay.setVisibility(View.GONE);
            ViewGroup rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                rootView.removeView(loaderOverlay);
            }
            loaderOverlay.removeAllViews();
            loaderOverlay = null;
        }

        if (loaderAnimation != null) {
            loaderAnimation.cancelAnimation();
            loaderAnimation.clearAnimation();
            loaderAnimation.removeAllUpdateListeners();
            loaderAnimation.removeAllAnimatorListeners();
            loaderAnimation = null;
        }

        currentAnimationRes = -1;
        isLoaderVisible = false;
    }

    @Override
    protected void onDestroy() {
        if (loaderOverlay != null && hideRunnable != null) {
            loaderOverlay.removeCallbacks(hideRunnable);
        }
        super.onDestroy();
        cleanupLoader();
    }
}