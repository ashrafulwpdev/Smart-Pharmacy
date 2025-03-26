package com.oopgroup.smartpharmacy.utils;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.oopgroup.smartpharmacy.R;

public class LogoutConfirmationDialog extends Dialog {

    private final OnLogoutListener listener;

    public interface OnLogoutListener {
        void onLogout();
    }

    public LogoutConfirmationDialog(@NonNull Context context, OnLogoutListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_logout_confirmation);

        // Disable hardware acceleration to prevent text blur
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, 0);

        // Set dialog dimensions to 290dp width and 260dp height
        int width = (int) (298 * getContext().getResources().getDisplayMetrics().density);
        int height = (int) (260 * getContext().getResources().getDisplayMetrics().density);
        getWindow().setLayout(width, height);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setDimAmount(0.6f); // Reduced dim amount for better contrast

        // Set up buttons
        ImageView closeButton = findViewById(R.id.dialog_close_button);
        Button cancelButton = findViewById(R.id.dialog_cancel_button);
        TextView logoutButton = findViewById(R.id.dialog_logout_button);

        // Scale animation for close button
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1.0f, 0.8f, 1.0f, 0.8f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnimation.setDuration(100);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        closeButton.setOnClickListener(view -> {
            view.startAnimation(scaleAnimation);
            scaleAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    dismiss();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
        });

        cancelButton.setOnClickListener(view -> dismiss());
        logoutButton.setOnClickListener(view -> {
            listener.onLogout();
            dismiss();
        });
    }
}