package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class ForgotPassActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassActivity";
    private EditText credInput;
    private Button resetBtn;
    private ProgressBar progressBar;
    private TextView backToLoginText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgotpass);

        mAuth = FirebaseAuth.getInstance();

        credInput = findViewById(R.id.credInput);
        resetBtn = findViewById(R.id.resetBtn);
        progressBar = findViewById(R.id.progressBar);
        backToLoginText = findViewById(R.id.backToLoginText);

        resetBtn.setOnClickListener(v -> {
            String input = credInput.getText().toString().trim();
            handleResetRequest(input);
        });

        backToLoginText.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void handleResetRequest(String input) {
        if (input.isEmpty()) {
            showCustomToast("Please enter your email or phone number", false);
            credInput.setError("This field cannot be empty");
            credInput.requestFocus();
            return;
        }

        setUiEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        if (input.contains("@") && input.contains(".")) {
            if (input.endsWith("@smartpharmacy.com")) {
                String phoneNumber = input.replace("@smartpharmacy.com", "");
                if (!phoneNumber.startsWith("+880")) {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    showCustomToast("Invalid phone number format in email", false);
                    credInput.setError("Invalid format");
                    credInput.requestFocus();
                    return;
                }
                sendOtpToPhone(phoneNumber);
            } else {
                sendEmailResetLink(input);
            }
        } else {
            String phoneNumber = input.startsWith("+880") ? input : "+880" + input;
            if (!isValidPhone(phoneNumber)) {
                progressBar.setVisibility(View.GONE);
                setUiEnabled(true);
                showCustomToast("Please enter a valid phone number (10 digits after +880)", false);
                credInput.setError("Invalid phone number");
                credInput.requestFocus();
                return;
            }
            sendOtpToPhone(phoneNumber);
        }
    }

    private void sendEmailResetLink(String email) {
        Log.d(TAG, "Sending reset link to: " + email);
        ActionCodeSettings actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://smartpharmacyuni.page.link/resetpass")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("com.oopgroup.smartpharmacy", true, null)
                .build();

        mAuth.sendPasswordResetEmail(email, actionCodeSettings)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Reset link sent successfully to " + email);
                        showCustomToast("Reset link sent to " + email + ". Check your inbox.", true);
                        Intent intent = new Intent(ForgotPassActivity.this, OtpVerificationActivity.class);
                        intent.putExtra("credentials", email);
                        intent.putExtra("isReset", true);
                        intent.putExtra("isEmailReset", true);
                        startActivity(intent);
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e(TAG, "Failed to send reset link: " + errorMessage);
                        showCustomToast("Failed to send reset link: " + errorMessage, false);
                        credInput.setError("Check email and try again");
                        credInput.requestFocus();
                    }
                });
    }

    private void sendOtpToPhone(String phoneNumber) {
        Log.d(TAG, "Sending OTP to: " + phoneNumber);
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        progressBar.setVisibility(View.GONE);
                        setUiEnabled(true);
                        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Phone auto-verified for " + phoneNumber);
                                showCustomToast("Phone verified automatically", true);
                                Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                                intent.putExtra("credentials", phoneNumber);
                                startActivity(intent);
                                finish();
                            } else {
                                Log.e(TAG, "Auto-verification failed: " + task.getException().getMessage());
                                showCustomToast("Auto-verification failed: " + task.getException().getMessage(), false);
                            }
                        });
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        progressBar.setVisibility(View.GONE);
                        setUiEnabled(true);
                        Log.e(TAG, "Phone verification failed: " + e.getMessage());
                        showCustomToast("Verification failed: " + e.getMessage(), false);
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        progressBar.setVisibility(View.GONE);
                        setUiEnabled(true);
                        Log.d(TAG, "OTP sent to " + phoneNumber + " with verificationId: " + verificationId);
                        showCustomToast("OTP sent to " + phoneNumber, true);
                        Intent intent = new Intent(ForgotPassActivity.this, OtpVerificationActivity.class);
                        intent.putExtra("credentials", phoneNumber);
                        intent.putExtra("verificationId", verificationId);
                        intent.putExtra("isReset", true);
                        startActivity(intent);
                        finish();
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private boolean isValidPhone(String phone) {
        return phone.matches("\\+880\\d{10}");
    }

    private void setUiEnabled(boolean enabled) {
        credInput.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
        backToLoginText.setEnabled(enabled);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        try {
            LayoutInflater inflater = getLayoutInflater();
            View toastView = inflater.inflate(R.layout.custom_toast, null);
            TextView toastText = toastView.findViewById(R.id.toast_text);
            toastText.setText(message);
            toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
            Toast toast = new Toast(this);
            toast.setDuration(Toast.LENGTH_LONG);
            toast.setView(toastView);
            toast.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing toast: " + e.getMessage());
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}