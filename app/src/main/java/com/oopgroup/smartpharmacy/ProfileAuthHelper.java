package com.oopgroup.smartpharmacy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class ProfileAuthHelper {

    private static final String TAG = "ProfileAuthHelper";
    private static final String PREFS_NAME = "AuthPrefs";
    private static final String LAST_VERIFICATION_TIME = "lastVerificationTime";
    private static final long RESEND_TIMEOUT_HOURS = 30 * 1000;

    private final Context context;
    private final FirebaseAuth auth;
    private final FirebaseUser currentUser;
    private final OnAuthCompleteListener authCompleteListener;
    private final SharedPreferences authPrefs;

    public ProfileAuthHelper(Context context, OnAuthCompleteListener listener) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.currentUser = auth.getCurrentUser();
        this.authCompleteListener = listener;
        this.authPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void showPasswordDialog(String fullName, String gender, String birthday, String phoneNumber,
                                   String email, String username, boolean isEmailReauth,
                                   String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (!(context instanceof Activity)) return;

        View dialogView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setGravity(Gravity.CENTER);
        layout.setAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in));

        TextView title = new TextView(context);
        title.setText("Authenticate to Proceed");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        EditText passwordInput = new EditText(context);
        passwordInput.setHint("Enter your password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(20, 15, 20, 15);
        passwordInput.setBackgroundResource(android.R.drawable.edit_text);
        passwordInput.setTextColor(Color.BLACK);
        passwordInput.setHintTextColor(Color.GRAY);
        layout.addView(passwordInput);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Verify", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(Color.parseColor("#083EC9"));
            positiveButton.setBackgroundResource(android.R.drawable.btn_default_small);
            positiveButton.setPadding(20, 10, 20, 10);
            positiveButton.setOnClickListener(v -> {
                String currentPassword = passwordInput.getText().toString().trim();
                if (currentPassword.isEmpty()) {
                    showToast("Please enter your password.", false);
                } else {
                    AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);
                    reauthenticateAndVerifyEmail(fullName, gender, birthday, phoneNumber, email, username,
                            credential, originalEmail, originalPhoneNumber, originalUsername);
                    dialog.dismiss();
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(Color.GRAY);
            negativeButton.setBackgroundResource(android.R.drawable.btn_default_small);
            negativeButton.setPadding(20, 10, 20, 10);
        });

        dialog.show();
    }

    public void verifyPhoneNumber(String fullName, String gender, String birthday, String phoneNumber,
                                  String email, String username, String originalEmail,
                                  String originalPhoneNumber, String originalUsername) {
        long lastVerificationTime = authPrefs.getLong(LAST_VERIFICATION_TIME, 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastVerificationTime < RESEND_TIMEOUT_HOURS) {
            long timeLeft = RESEND_TIMEOUT_HOURS - (currentTime - lastVerificationTime);
            long hoursLeft = TimeUnit.MILLISECONDS.toHours(timeLeft);
            long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(timeLeft % (60 * 60 * 1000));
            showToast("Please wait " + hoursLeft + "h " + minutesLeft + "m before resending.", false);
            showOTPDialog(fullName, gender, birthday, phoneNumber, email, username, null,
                    originalEmail, originalPhoneNumber, originalUsername);
            return;
        }

        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                TimeUnit.SECONDS,
                (Activity) context,
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        authPrefs.edit().putLong(LAST_VERIFICATION_TIME, System.currentTimeMillis()).apply();
                        showOTPDialog(fullName, gender, birthday, phoneNumber, email, username,
                                verificationId, originalEmail, originalPhoneNumber, originalUsername);
                    }

                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        updatePhoneNumber(credential, fullName, gender, birthday, phoneNumber, email, username,
                                originalEmail, originalPhoneNumber, originalUsername);
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        showToast("Phone verification failed: " + e.getMessage(), false);
                        authCompleteListener.onAuthFailed();
                    }
                }
        );
    }

    private void showOTPDialog(String fullName, String gender, String birthday, String phoneNumber,
                               String email, String username, String verificationId,
                               String originalEmail, String originalPhoneNumber, String originalUsername) {
        View dialogView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setGravity(Gravity.CENTER);
        layout.setAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in));

        TextView title = new TextView(context);
        title.setText("Verify Your New Phone");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        EditText otpInput = new EditText(context);
        otpInput.setHint("Enter OTP");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpInput.setPadding(20, 15, 20, 15);
        otpInput.setBackgroundResource(android.R.drawable.edit_text);
        otpInput.setTextColor(Color.BLACK);
        otpInput.setHintTextColor(Color.GRAY);
        layout.addView(otpInput);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", (d, which) -> {
                    authCompleteListener.onAuthFailed();
                    d.dismiss();
                })
                .setNeutralButton("Resend", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(Color.parseColor("#083EC9"));
            positiveButton.setBackgroundResource(android.R.drawable.btn_default_small);
            positiveButton.setPadding(20, 10, 20, 10);
            positiveButton.setOnClickListener(v -> {
                String otp = otpInput.getText().toString().trim();
                if (otp.isEmpty()) {
                    showToast("Please enter the OTP.", false);
                } else if (verificationId != null) {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                    updatePhoneNumber(credential, fullName, gender, birthday, phoneNumber, email, username,
                            originalEmail, originalPhoneNumber, originalUsername);
                    dialog.dismiss();
                } else {
                    showToast("No verification in progress.", false);
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(Color.GRAY);
            negativeButton.setBackgroundResource(android.R.drawable.btn_default_small);
            negativeButton.setPadding(20, 10, 20, 10);

            Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            neutralButton.setTextColor(Color.parseColor("#083EC9"));
            neutralButton.setBackgroundResource(android.R.drawable.btn_default_small);
            neutralButton.setPadding(20, 10, 20, 10);
            neutralButton.setOnClickListener(v -> {
                dialog.dismiss();
                verifyPhoneNumber(fullName, gender, birthday, phoneNumber, email, username,
                        originalEmail, originalPhoneNumber, originalUsername);
            });
        });

        dialog.show();
    }

    private void reauthenticateAndVerifyEmail(String fullName, String gender, String birthday,
                                              String phoneNumber, String email, String username,
                                              AuthCredential credential, String originalEmail,
                                              String originalPhoneNumber, String originalUsername) {
        if (currentUser == null) {
            showToast("User session expired. Please log in again.", false);
            context.startActivity(new Intent(context, LoginActivity.class));
            ((Activity) context).finish();
            return;
        }

        authCompleteListener.onAuthStart();
        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!email.equalsIgnoreCase(currentUser.getEmail())) {
                            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener(fetchTask -> {
                                if (fetchTask.isSuccessful() && !fetchTask.getResult().getSignInMethods().isEmpty()) {
                                    showToast("This email is already in use by another account.", false);
                                    authCompleteListener.onAuthFailed();
                                } else {
                                    currentUser.verifyBeforeUpdateEmail(email)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Verification email sent to: " + email);
                                                showToast("Verification email sent to " + email + ". Please verify before logging in.", true);
                                                authCompleteListener.onEmailVerificationSent(fullName, gender, birthday,
                                                        phoneNumber, currentUser.getEmail(), username, email,
                                                        originalEmail, originalPhoneNumber, originalUsername);
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to send verification email: " + e.getMessage());
                                                showToast("Failed to send verification email: " + e.getMessage(), false);
                                                authCompleteListener.onAuthFailed();
                                            });
                                }
                            });
                        } else {
                            authCompleteListener.onAuthSuccess(fullName, gender, birthday, phoneNumber, email, username,
                                    originalEmail, originalPhoneNumber, originalUsername);
                        }
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMsg.contains("no user record")) {
                            showToast("Account not found. Please log in again.", false);
                            auth.signOut();
                            context.startActivity(new Intent(context, LoginActivity.class));
                            ((Activity) context).finish();
                        } else {
                            showToast("Re-authentication failed: " + errorMsg, false);
                            authCompleteListener.onAuthFailed();
                        }
                    }
                });
    }

    private void updatePhoneNumber(PhoneAuthCredential credential, String fullName, String gender,
                                   String birthday, String phoneNumber, String email, String username,
                                   String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (currentUser == null) {
            showToast("User session expired. Please log in again.", false);
            context.startActivity(new Intent(context, LoginActivity.class));
            ((Activity) context).finish();
            return;
        }

        authCompleteListener.onAuthStart();
        currentUser.updatePhoneNumber(credential)
                .addOnSuccessListener(aVoid -> {
                    showToast("Phone number verified and updated.", true);
                    authCompleteListener.onAuthSuccess(fullName, gender, birthday, phoneNumber, email, username,
                            originalEmail, originalPhoneNumber, originalUsername);
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update phone number: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
    }

    private void showToast(String message, boolean isSuccess) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        View view = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
        TextView text = view.findViewById(R.id.toast_text);
        text.setText(message);
        text.setTextColor(Color.WHITE);
        view.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(view);
        toast.show();
    }

    public interface OnAuthCompleteListener {
        void onAuthStart();
        void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                           String email, String username, String originalEmail,
                           String originalPhoneNumber, String originalUsername);
        void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                     String currentEmail, String username, String pendingEmail,
                                     String originalEmail, String originalPhoneNumber, String originalUsername);
        void onAuthFailed();
    }
}