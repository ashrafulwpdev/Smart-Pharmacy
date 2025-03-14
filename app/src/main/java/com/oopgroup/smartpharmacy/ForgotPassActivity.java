package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.hbb20.CountryCodePicker;

import java.util.concurrent.TimeUnit;

public class ForgotPassActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassActivity";
    private EditText credInput;
    private Button resetBtn;
    private LottieAnimationView loadingSpinner;
    private TextView backToLoginText, validationMessage;
    private ImageView emailIcon, phoneIcon; // Removed googleLogin, facebookLogin, githubLogin
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private boolean isPhoneInput = false;
    private boolean isTextChanging = false; // Guard against recursive TextWatcher calls

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgotpass);

        mAuth = FirebaseAuth.getInstance();

        // Initialize UI elements
        credInput = findViewById(R.id.credInput);
        resetBtn = findViewById(R.id.resetBtn);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        backToLoginText = findViewById(R.id.backToLoginText);
        validationMessage = findViewById(R.id.validationMessage);
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        ccp = findViewById(R.id.ccp);

        // Removed Google, Facebook, GitHub initialization code

        // Set default country based on device locale, restricted to BD, MY, SG
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("MY"); // Default to Malaysia for testing

        // Attempt to fetch the SIM number
        AuthUtils.fetchSimNumber(this, credInput);

        // Setup dynamic input and adjust padding
        AuthUtils.setupDynamicInput(this, credInput, ccp, emailIcon, phoneIcon, validationMessage);
        adjustCredInputPadding();

        // TextWatcher for border reset and phone detection
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                Log.d(TAG, "Before text changed: " + s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isTextChanging) {
                    Log.d(TAG, "Skipping TextWatcher due to self-induced change");
                    return;
                }
                isTextChanging = true;
                resetInputBorders();
                adjustCredInputPadding();
                String input = s.toString().trim();
                if (input.isEmpty()) {
                    isPhoneInput = false;
                } else if (AuthUtils.looksLikeEmail(input)) {
                    isPhoneInput = false;
                } else {
                    String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                    isPhoneInput = AuthUtils.isValidPhoneNumber(normalized);
                }
                Log.d(TAG, "Input: " + input + ", isPhoneInput: " + isPhoneInput);
                isTextChanging = false;
            }

            @Override
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "After text changed: " + s);
            }
        });

        // Disable CCP's auto-formatting to prevent recursive calls
        ccp.setNumberAutoFormattingEnabled(false);

        // Reset button logic
        resetBtn.setOnClickListener(v -> {
            String input = credInput.getText().toString().trim();
            resetInputBorders();

            if (input.isEmpty()) {
                showCustomToast("Please enter an email or phone number.", false);
                setErrorBorder(credInput);
                validationMessage.setText("Input cannot be empty");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            }

            String emailOrPhone;
            if (AuthUtils.isValidEmail(input)) {
                emailOrPhone = input;
            } else {
                emailOrPhone = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                Log.d(TAG, "Input: " + input + ", Normalized: " + emailOrPhone + ", CCP: " + ccp.getSelectedCountryCodeWithPlus());
                if (!AuthUtils.isValidPhoneNumber(emailOrPhone)) {
                    showCustomToast("Invalid phone number format. Examples: +8801712345678, +60123456789, +6591234567", false);
                    setErrorBorder(credInput);
                    validationMessage.setText("Invalid phone number format");
                    validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    return;
                }
                isPhoneInput = true;
            }

            String emailToCheck = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";
            Log.d(TAG, "Checking user existence with email: " + emailToCheck);
            checkUserExists(emailToCheck, emailOrPhone, isPhoneInput);
        });

        backToLoginText.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Removed Google, Facebook, GitHub login button listeners
    }

    private void adjustCredInputPadding() {
        ccp.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ccp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (ccp.getVisibility() == View.VISIBLE) {
                    int ccpWidth = ccp.getWidth();
                    int paddingStart = ccpWidth + (int) (10 * getResources().getDisplayMetrics().density);
                    credInput.setPadding(paddingStart, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
                } else {
                    int defaultPadding = (int) (10 * getResources().getDisplayMetrics().density);
                    credInput.setPadding(defaultPadding, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
                }
            }
        });
    }

    private void checkUserExists(String emailToCheck, String originalInput, boolean isPhone) {
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.fetchSignInMethodsForEmail(emailToCheck)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        if (task.getResult().getSignInMethods() != null && !task.getResult().getSignInMethods().isEmpty()) {
                            if (isPhone) {
                                sendOtpToPhone(originalInput);
                            } else {
                                sendEmailResetLink(originalInput);
                            }
                        } else {
                            showCustomToast(isPhone ? "This phone number is not registered." : "This email is not registered.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText(isPhone ? "Phone number not found" : "Email not found");
                            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Network error");
                        } else if (errorMessage.contains("badly formatted")) {
                            showCustomToast("Invalid format. Please use a valid email or phone number.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Invalid format");
                        } else {
                            showCustomToast("Error checking user: " + errorMessage, false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Error checking user");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendEmailResetLink(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        resetInputBorders();
                        showCustomToast("Reset link sent to " + email, true);
                        Intent intent = new Intent(this, OtpVerificationActivity.class);
                        intent.putExtra("credentials", email);
                        intent.putExtra("isReset", true);
                        intent.putExtra("isEmailReset", true);
                        startActivity(intent);
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Network error");
                        } else {
                            showCustomToast("Failed to send reset link: " + errorMessage, false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Failed to send reset link");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendOtpToPhone(String phoneNumber) {
        if (!AuthUtils.isValidPhoneNumber(phoneNumber)) {
            Log.e(TAG, "Invalid phone format: " + phoneNumber);
            showCustomToast("Invalid phone number format. Examples: +8801712345678, +60123456789, +6591234567", false);
            setErrorBorder(credInput);
            validationMessage.setText("Invalid phone format");
            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
            return;
        }

        Log.d(TAG, "Sending OTP to: " + phoneNumber);
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        resetInputBorders();
                        showCustomToast("Phone verified automatically", true);
                        Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("credentials", phoneNumber);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                        Log.e(TAG, "OTP sending failed: " + errorMessage);
                        if (e instanceof FirebaseAuthException) {
                            String errorCode = ((FirebaseAuthException) e).getErrorCode();
                            if (errorCode.equals("ERROR_INVALID_PHONE_NUMBER")) {
                                showCustomToast("Invalid phone number format. Please check the number.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("Invalid phone number format");
                            } else if (errorCode.equals("ERROR_INVALID_CREDENTIALS") || errorCode.equals("ERROR_USER_NOT_FOUND")) {
                                showCustomToast("This phone number is not registered. Please sign up first.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("Phone number not found");
                            } else if (errorCode.equals("ERROR_TOO_MANY_REQUESTS")) {
                                showCustomToast("Too many requests. Please try again later.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("Request limit reached");
                            } else if (errorMessage.contains("not authorized")) {
                                showCustomToast("App not authorized for Firebase Authentication. Contact support.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("App authorization error");
                            } else if (errorMessage.contains("quota")) {
                                showCustomToast("SMS quota exceeded. Please try again later.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("SMS quota exceeded");
                            } else if (errorMessage.contains("blocked")) {
                                showCustomToast("SMS sending is blocked for this country. Contact support.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("SMS blocked for country");
                            } else {
                                showCustomToast("Failed to send OTP: " + errorMessage, false);
                                setErrorBorder(credInput);
                                validationMessage.setText("OTP sending failed");
                            }
                        } else if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Network error");
                        } else {
                            showCustomToast("Failed to send OTP: " + errorMessage, false);
                            setErrorBorder(credInput);
                            validationMessage.setText("OTP sending failed");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        resetInputBorders();
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

    private void setUiEnabled(boolean enabled) {
        credInput.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
        backToLoginText.setEnabled(enabled);
        // Removed Google, Facebook, GitHub UI enable/disable code
    }

    private void showCustomToast(String message, boolean isSuccess) {
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
    }

    // Removed Google, Facebook, GitHub sign-in methods

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Removed Google, Facebook activity result handling
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        credInput.setBackgroundResource(R.drawable.edittext_bg);
    }
}