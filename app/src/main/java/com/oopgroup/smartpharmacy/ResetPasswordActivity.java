package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.oopgroup.smartpharmacy.utils.BaseActivity;
import com.softourtech.slt.SLTLoader;

public class ResetPasswordActivity extends BaseActivity {
    private static final String TAG = "ResetPasswordActivity";
    private EditText newPasswordInput, confirmPasswordInput;
    private ImageView newPasswordToggle, confirmPasswordToggle;
    private Button resetPasswordBtn, resetAgainBtn;
    private ImageView githubLogin, googleLogin, facebookLogin;
    private TextView errorText, errorTitle, loginLink, signupLink;
    private LinearLayout resetFormContainer, errorContainer, bottomSection;
    private FirebaseAuth mAuth;
    private String credentials;
    private String verificationId;
    private boolean isEmailReset;
    private String oobCode;
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private SLTLoader sltLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Initialize SLTLoader with the activity's root view
        View activityRoot = findViewById(android.R.id.content);
        if (activityRoot == null || !(activityRoot instanceof ViewGroup)) {
            Log.e(TAG, "Activity root view not found or not a ViewGroup");
            return;
        }
        sltLoader = new SLTLoader(this, (ViewGroup) activityRoot);

        mAuth = FirebaseAuth.getInstance();

        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        newPasswordToggle = findViewById(R.id.newPasswordToggle);
        confirmPasswordToggle = findViewById(R.id.confirmPasswordToggle);
        resetPasswordBtn = findViewById(R.id.resetPasswordBtn);
        resetAgainBtn = findViewById(R.id.resetAgainBtn);
        githubLogin = findViewById(R.id.githubLogin);
        googleLogin = findViewById(R.id.googleLogin);
        facebookLogin = findViewById(R.id.facebookLogin);
        errorText = findViewById(R.id.errorText);
        errorTitle = findViewById(R.id.errorTitle);
        loginLink = findViewById(R.id.loginLink);
        signupLink = findViewById(R.id.signupLink);
        resetFormContainer = findViewById(R.id.resetFormContainer);
        errorContainer = findViewById(R.id.errorContainer);
        bottomSection = findViewById(R.id.bottomSection);

        Intent intent = getIntent();
        credentials = intent.getStringExtra("credentials");
        verificationId = intent.getStringExtra("verificationId");
        isEmailReset = intent.getBooleanExtra("isEmailReset", false);
        Uri data = intent.getData();

        Log.d(TAG, "Intent received: " + intent.toString());
        if (data != null) {
            Log.d(TAG, "Deep link detected: " + data.toString());
            isEmailReset = true;
            oobCode = data.getQueryParameter("oobCode");
            if (oobCode == null || oobCode.isEmpty()) {
                Log.e(TAG, "No oobCode found in deep link: " + data.toString());
                showErrorState("Invalid Link", "Your reset link appears to be incomplete.");
                return;
            }
            Log.d(TAG, "oobCode extracted: " + oobCode);
            validateResetCode(oobCode);
        } else if (credentials == null) {
            Log.e(TAG, "No credentials provided and no deep link detected");
            showErrorState("Reset Issue", "We couldn’t find the details needed to reset your password.");
            return;
        } else {
            Log.d(TAG, "Launched via app flow with credentials: " + credentials);
            resetFormContainer.setVisibility(View.VISIBLE);
            bottomSection.setVisibility(View.VISIBLE);
        }

        newPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { resetInputBorders(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        confirmPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { resetInputBorders(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        newPasswordToggle.setOnClickListener(v -> toggleNewPasswordVisibility());
        confirmPasswordToggle.setOnClickListener(v -> toggleConfirmPasswordVisibility());
        resetPasswordBtn.setOnClickListener(v -> handlePasswordReset());
        resetAgainBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPassActivity.class));
            finish();
        });
        loginLink.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        signupLink.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        githubLogin.setOnClickListener(v -> showCustomToast("GitHub login not implemented yet", false));
        googleLogin.setOnClickListener(v -> showCustomToast("Google login not implemented yet", false));
        facebookLogin.setOnClickListener(v -> showCustomToast("Facebook login not implemented yet", false));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sltLoader != null) {
            sltLoader.onDestroy();
        }
    }

    private void toggleNewPasswordVisibility() {
        isNewPasswordVisible = !isNewPasswordVisible;
        newPasswordInput.setTransformationMethod(isNewPasswordVisible ?
                SingleLineTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
        newPasswordToggle.setImageResource(isNewPasswordVisible ? R.drawable.ic_eye_on : R.drawable.ic_eye_off);
        newPasswordInput.setSelection(newPasswordInput.getText().length());
    }

    private void toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;
        confirmPasswordInput.setTransformationMethod(isConfirmPasswordVisible ?
                SingleLineTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
        confirmPasswordToggle.setImageResource(isConfirmPasswordVisible ? R.drawable.ic_eye_on : R.drawable.ic_eye_off);
        confirmPasswordInput.setSelection(confirmPasswordInput.getText().length());
    }

    private void handlePasswordReset() {
        String newPassword = newPasswordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        resetInputBorders();
        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            if (newPassword.isEmpty()) setErrorBorder(newPasswordInput);
            if (confirmPassword.isEmpty()) setErrorBorder(confirmPasswordInput);
            showCustomToast("Please fill in both password fields", false);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            setErrorBorder(confirmPasswordInput);
            showCustomToast("Passwords do not match", false);
            return;
        }

        if (newPassword.length() < 6) {
            setErrorBorder(newPasswordInput);
            showCustomToast("Password must be at least 6 characters", false);
            return;
        }

        setUiEnabled(false);
        showLoader();
        errorContainer.setVisibility(View.GONE);

        if (isEmailReset && oobCode != null) {
            resetEmailPassword(oobCode, newPassword);
        } else {
            resetPhonePassword(credentials, newPassword);
        }
    }

    private void validateResetCode(String actionCode) {
        showLoader();
        mAuth.verifyPasswordResetCode(actionCode)
                .addOnCompleteListener(task -> {
                    hideLoader();
                    if (task.isSuccessful()) {
                        credentials = task.getResult();
                        Log.d(TAG, "Reset code validated. Email: " + credentials);
                        resetFormContainer.setVisibility(View.VISIBLE);
                        errorContainer.setVisibility(View.GONE);
                        bottomSection.setVisibility(View.VISIBLE);
                    } else {
                        Log.e(TAG, "Reset code validation failed", task.getException());
                        handleResetError(task.getException());
                    }
                });
    }

    private void resetEmailPassword(String actionCode, String newPassword) {
        mAuth.confirmPasswordReset(actionCode, newPassword)
                .addOnCompleteListener(task -> {
                    hideLoader();
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        signInAndRedirect(credentials, newPassword);
                    } else {
                        handleResetError(task.getException());
                    }
                });
    }

    private void resetPhonePassword(String phoneNumber, String newPassword) {
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().updatePassword(newPassword)
                    .addOnCompleteListener(task -> {
                        hideLoader();
                        setUiEnabled(true);
                        if (task.isSuccessful()) {
                            resetInputBorders();
                            showCustomToast("Password reset successfully", true);
                            mAuth.signOut();
                            startActivity(new Intent(this, LoginActivity.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                            finish();
                        } else {
                            handleResetError(task.getException());
                        }
                    });
        } else {
            hideLoader();
            setUiEnabled(true);
            showErrorState("Session Expired", "Your session has timed out. Please try again.");
        }
    }

    private void signInAndRedirect(String email, String newPassword) {
        mAuth.signInWithEmailAndPassword(email, newPassword)
                .addOnCompleteListener(task -> {
                    hideLoader();
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        resetInputBorders();
                        showCustomToast("Password reset successfully", true);
                        mAuth.signOut();
                        startActivity(new Intent(this, LoginActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        finish();
                    } else {
                        handleResetError(task.getException());
                    }
                });
    }

    private void handleResetError(Exception exception) {
        String title, message;
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            title = "Reset Failed";
            message = "Your reset link is no longer valid.";
        } else if (exception != null) {
            title = "Reset Failed";
            message = "An issue occurred: " + exception.getMessage();
            Log.e(TAG, "Reset error details: " + exception.getMessage(), exception);
        } else {
            title = "Reset Failed";
            message = "An unexpected error occurred.";
        }
        Log.e(TAG, title + ": " + message, exception);
        setErrorBorder(newPasswordInput);
        setErrorBorder(confirmPasswordInput);
        showErrorState(title, message);
    }

    private void showErrorState(String title, String message) {
        resetFormContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        bottomSection.setVisibility(View.VISIBLE);
        errorTitle.setText(title);
        errorText.setText(message);
    }

    private void setUiEnabled(boolean enabled) {
        newPasswordInput.setEnabled(enabled);
        confirmPasswordInput.setEnabled(enabled);
        resetPasswordBtn.setEnabled(enabled);
        newPasswordToggle.setEnabled(enabled);
        confirmPasswordToggle.setEnabled(enabled);
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

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        newPasswordInput.setBackgroundResource(R.drawable.edittext_bg);
        confirmPasswordInput.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void showLoader() {
        SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000"))
                .setChangeJsonColor(false);
        sltLoader.showCustomLoader(config);
    }

    public void hideLoader() {
        if (sltLoader != null) {
            sltLoader.hideLoader();
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, OtpVerificationActivity.class);
        intent.putExtra("credentials", credentials);
        intent.putExtra("verificationId", verificationId);
        intent.putExtra("isReset", true);
        intent.putExtra("isEmailReset", isEmailReset);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (resetFormContainer.getVisibility() == View.VISIBLE) {
            mAuth.signOut();
            Log.d(TAG, "User signed out on pause due to incomplete password reset");
        }
    }
}