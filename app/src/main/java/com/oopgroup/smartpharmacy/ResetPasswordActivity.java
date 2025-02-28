package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;

public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";
    private EditText newPasswordInput, confirmPasswordInput;
    private Button resetPasswordBtn, resetAgainBtn;
    private ImageView githubLogin, googleLogin, facebookLogin;
    private ProgressBar progressBar;
    private TextView errorText, errorTitle, loginLink, signupLink;
    private LinearLayout resetFormContainer, errorContainer, bottomSection;
    private FirebaseAuth mAuth;
    private String credentials;
    private boolean isEmailReset;
    private String oobCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        mAuth = FirebaseAuth.getInstance();

        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        resetPasswordBtn = findViewById(R.id.resetPasswordBtn);
        resetAgainBtn = findViewById(R.id.resetAgainBtn);
        githubLogin = findViewById(R.id.githubLogin);
        googleLogin = findViewById(R.id.googleLogin);
        facebookLogin = findViewById(R.id.facebookLogin);
        progressBar = findViewById(R.id.progressBar);
        errorText = findViewById(R.id.errorText);
        errorTitle = findViewById(R.id.errorTitle);
        loginLink = findViewById(R.id.loginLink);
        signupLink = findViewById(R.id.signupLink);
        resetFormContainer = findViewById(R.id.resetFormContainer);
        errorContainer = findViewById(R.id.errorContainer);
        bottomSection = findViewById(R.id.bottomSection);

        credentials = getIntent().getStringExtra("credentials");
        isEmailReset = getIntent().getBooleanExtra("isEmailReset", false);

        Uri data = getIntent().getData();
        if (data != null) {
            Log.d(TAG, "Deep link detected: " + data.toString());
            isEmailReset = true;
            oobCode = data.getQueryParameter("oobCode");
            if (oobCode == null) {
                Log.e(TAG, "No oobCode found in deep link");
                showErrorState("Invalid Link", "Your reset link appears to be incomplete.");
                return;
            }
            validateResetCode(oobCode);
        } else if (credentials == null) {
            Log.e(TAG, "No credentials provided and no deep link detected");
            showErrorState("Reset Issue", "We couldn’t find the details needed to reset your password.");
            return;
        } else {
            Log.d(TAG, "Launched via app flow with credentials: " + credentials);
            bottomSection.setVisibility(View.VISIBLE); // Show bottom section for valid state
        }

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

    private void handlePasswordReset() {
        String newPassword = newPasswordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showCustomToast("Please fill in both password fields", false);
            if (newPassword.isEmpty()) newPasswordInput.setError("Required");
            if (confirmPassword.isEmpty()) confirmPasswordInput.setError("Required");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showCustomToast("Passwords do not match", false);
            confirmPasswordInput.setError("Passwords do not match");
            return;
        }

        if (newPassword.length() < 6) {
            showCustomToast("Password must be at least 6 characters", false);
            newPasswordInput.setError("Too short");
            return;
        }

        setUiEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);

        if (isEmailReset && oobCode != null) {
            resetEmailPassword(oobCode, newPassword);
        } else {
            resetPhonePassword(credentials, newPassword);
        }
    }

    private void validateResetCode(String actionCode) {
        progressBar.setVisibility(View.VISIBLE);
        mAuth.verifyPasswordResetCode(actionCode)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        credentials = task.getResult();
                        Log.d(TAG, "Reset code validated. Email: " + credentials);
                        resetFormContainer.setVisibility(View.VISIBLE);
                        errorContainer.setVisibility(View.GONE);
                        bottomSection.setVisibility(View.VISIBLE);
                    } else {
                        handleResetError(task.getException());
                    }
                });
    }

    private void resetEmailPassword(String actionCode, String newPassword) {
        mAuth.confirmPasswordReset(actionCode, newPassword)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
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
                        progressBar.setVisibility(View.GONE);
                        setUiEnabled(true);
                        if (task.isSuccessful()) {
                            showCustomToast("Password reset successfully", true);
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            handleResetError(task.getException());
                        }
                    });
        } else {
            progressBar.setVisibility(View.GONE);
            setUiEnabled(true);
            showErrorState("Session Expired", "Your session has timed out. Please try again.");
        }
    }

    private void signInAndRedirect(String email, String newPassword) {
        mAuth.signInWithEmailAndPassword(email, newPassword)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        showCustomToast("Password reset successfully", true);
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
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
            message = "An issue occurred while resetting your password.";
        } else {
            title = "Reset Failed";
            message = "An unexpected error occurred.";
        }
        Log.e(TAG, title + ": " + message, exception);
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