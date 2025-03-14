package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerificationActivity";
    private EditText otpBox1, otpBox2, otpBox3, otpBox4, otpBox5, otpBox6;
    private Button verifyBtn, openEmailBtn;
    private TextView resendText, subtitle, title, instructionsText;
    private LottieAnimationView loadingSpinner;
    private LinearLayout otpInputs, verifyContainer, emailContainer;
    private String credentials, verificationId;
    private boolean isReset, isEmailReset;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();

        otpBox1 = findViewById(R.id.otp_box1);
        otpBox2 = findViewById(R.id.otp_box2);
        otpBox3 = findViewById(R.id.otp_box3);
        otpBox4 = findViewById(R.id.otp_box4);
        otpBox5 = findViewById(R.id.otp_box5);
        otpBox6 = findViewById(R.id.otp_box6);
        verifyBtn = findViewById(R.id.verifyBtn);
        openEmailBtn = findViewById(R.id.openEmailBtn);
        resendText = findViewById(R.id.resendText);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        otpInputs = findViewById(R.id.otp_inputs);
        verifyContainer = findViewById(R.id.verify_container);
        emailContainer = findViewById(R.id.email_container);
        subtitle = findViewById(R.id.otp_subtitle);
        title = findViewById(R.id.otp_title);
        instructionsText = findViewById(R.id.instructions_text);

        credentials = getIntent().getStringExtra("credentials");
        verificationId = getIntent().getStringExtra("verificationId");
        isReset = getIntent().getBooleanExtra("isReset", false);
        isEmailReset = getIntent().getBooleanExtra("isEmailReset", false);

        if (credentials == null) {
            showCustomToast("No credentials provided", false);
            finish();
            return;
        }

        if (isEmailReset) {
            // Email reset UI
            otpInputs.setVisibility(View.GONE);
            verifyContainer.setVisibility(View.GONE);
            resendText.setVisibility(View.GONE);
            emailContainer.setVisibility(View.VISIBLE);
            title.setText("Reset Your Password");
            subtitle.setText("A password reset link has been sent to\n" + credentials);
            instructionsText.setText("Please check your email inbox (and spam/junk folder)\nfor a password reset link from us.");
        } else {
            // Phone OTP UI
            if (verificationId == null) {
                showCustomToast("Verification ID missing", false);
                finish();
                return;
            }
            otpInputs.setVisibility(View.VISIBLE);
            verifyContainer.setVisibility(View.VISIBLE);
            resendText.setVisibility(View.VISIBLE);
            emailContainer.setVisibility(View.GONE);
            title.setText("Enter Verification Code");
            subtitle.setText("A 6-digit verification code has been sent to\n" + credentials);
            instructionsText.setText("Enter the 6-digit code we sent to your phone.\nIt may take a few moments to arrive.");
            setupOtpForPhone();
        }

        verifyBtn.setEnabled(true);
        verifyBtn.setAlpha(1.0f);
        verifyContainer.setEnabled(true);
        resendText.setEnabled(true);
        resendText.setClickable(true);

        resendText.setOnClickListener(v -> handleResend());
        verifyBtn.setOnClickListener(v -> verifyOtp(getOtp()));
        openEmailBtn.setOnClickListener(v -> openEmailApp());
    }

    private void setupOtpForPhone() {
        EditText[] otpBoxes = {otpBox1, otpBox2, otpBox3, otpBox4, otpBox5, otpBox6};
        for (EditText box : otpBoxes) {
            box.setFocusable(true);
            box.setFocusableInTouchMode(true);
            box.setCursorVisible(true);
        }
        otpBox1.requestFocus();
        setupOtpNavigation();
    }

    private void setupOtpNavigation() {
        TextWatcher otpWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String otp = getOtp();
                if (otp.length() == 6 && !otp.contains("")) {
                    verifyOtp(otp);
                }
                if (s.length() == 1) {
                    if (otpBox1.hasFocus()) otpBox2.requestFocus();
                    else if (otpBox2.hasFocus()) otpBox3.requestFocus();
                    else if (otpBox3.hasFocus()) otpBox4.requestFocus();
                    else if (otpBox4.hasFocus()) otpBox5.requestFocus();
                    else if (otpBox5.hasFocus()) otpBox6.requestFocus();
                } else if (s.length() == 0) {
                    if (otpBox2.hasFocus()) otpBox1.requestFocus();
                    else if (otpBox3.hasFocus()) otpBox2.requestFocus();
                    else if (otpBox4.hasFocus()) otpBox3.requestFocus();
                    else if (otpBox5.hasFocus()) otpBox4.requestFocus();
                    else if (otpBox6.hasFocus()) otpBox5.requestFocus();
                }
            }
        };

        otpBox1.addTextChangedListener(otpWatcher);
        otpBox2.addTextChangedListener(otpWatcher);
        otpBox3.addTextChangedListener(otpWatcher);
        otpBox4.addTextChangedListener(otpWatcher);
        otpBox5.addTextChangedListener(otpWatcher);
        otpBox6.addTextChangedListener(otpWatcher);
    }

    private String getOtp() {
        return otpBox1.getText().toString() +
                otpBox2.getText().toString() +
                otpBox3.getText().toString() +
                otpBox4.getText().toString() +
                otpBox5.getText().toString() +
                otpBox6.getText().toString();
    }

    private void verifyOtp(String otp) {
        otpInputs.setEnabled(false);
        resendText.setEnabled(false);
        resendText.setClickable(false);
        verifyBtn.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    loadingSpinner.pauseAnimation();
                    otpInputs.setEnabled(true);
                    resendText.setEnabled(true);
                    resendText.setClickable(true);
                    verifyBtn.setVisibility(View.VISIBLE);

                    if (task.isSuccessful()) {
                        showCustomToast("OTP verified successfully!", true);
                        Intent intent = new Intent(OtpVerificationActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("credentials", credentials);
                        startActivity(intent);
                        finish();
                    } else {
                        showCustomToast("Invalid OTP. Please try again.", false);
                        applyErrorBorder(otpBox1);
                        applyErrorBorder(otpBox2);
                        applyErrorBorder(otpBox3);
                        applyErrorBorder(otpBox4);
                        applyErrorBorder(otpBox5);
                        applyErrorBorder(otpBox6);
                    }
                });
    }

    private void handleResend() {
        if (resendText.getTag() != null && (long) resendText.getTag() > System.currentTimeMillis()) {
            showCustomToast("Please wait " + ((long) resendText.getTag() - System.currentTimeMillis()) / 1000 + " seconds to resend", false);
            return;
        }

        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(credentials)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        loadingSpinner.setVisibility(View.GONE);
                        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                showCustomToast("Phone verified automatically", true);
                                Intent intent = new Intent(OtpVerificationActivity.this, ResetPasswordActivity.class);
                                intent.putExtra("credentials", credentials);
                                startActivity(intent);
                                finish();
                            } else {
                                showCustomToast("Auto-verification failed", false);
                            }
                        });
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        loadingSpinner.setVisibility(View.GONE);
                        showCustomToast("Resend failed: " + e.getMessage(), false);
                    }

                    @Override
                    public void onCodeSent(String newVerificationId, PhoneAuthProvider.ForceResendingToken token) {
                        loadingSpinner.setVisibility(View.GONE);
                        verificationId = newVerificationId;
                        showCustomToast("OTP resent to " + credentials, true);
                        resendText.setTag(System.currentTimeMillis() + 30000);
                        updateResendTimer();
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void openEmailApp() {
        Intent emailIntent = new Intent(Intent.ACTION_MAIN);
        emailIntent.addCategory(Intent.CATEGORY_APP_EMAIL);
        try {
            startActivity(Intent.createChooser(emailIntent, "Open email app"));
        } catch (Exception e) {
            showCustomToast("No email app installed", false);
        }
    }

    private void updateResendTimer() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (resendText.getTag() != null && (long) resendText.getTag() > System.currentTimeMillis()) {
                    long remaining = ((long) resendText.getTag() - System.currentTimeMillis()) / 1000;
                    resendText.setText("Didn’t receive the code? Resend (" + remaining + "s)");
                    handler.postDelayed(this, 1000);
                } else {
                    resendText.setText("Didn’t receive the code? Resend");
                    resendText.setTag(null);
                }
            }
        }, 1000);
    }

    private void applyErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        LayoutInflater inflater = getLayoutInflater();
        View toastView = inflater.inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(toastView);
        toast.show();
    }
}