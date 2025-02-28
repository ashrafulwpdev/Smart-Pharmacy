package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class SignupActivity extends AppCompatActivity {

    private EditText fullNameInput, credInput, passwordInput;
    private ProgressBar progressBar;
    private TextView loginText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        Button signupBtn = findViewById(R.id.signupBtn);
        fullNameInput = findViewById(R.id.fullNameInput);
        credInput = findViewById(R.id.credInput);
        passwordInput = findViewById(R.id.passwordInput);
        progressBar = findViewById(R.id.progressBar);
        loginText = findViewById(R.id.loginText);

        signupBtn.setOnClickListener(v -> {
            String fullName = fullNameInput.getText().toString().trim();
            String credentials = credInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            handleSignup(fullName, credentials, password);
        });

        loginText.setOnClickListener(v -> {
            showCustomToast("Login clicked", false);
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
        });
    }

    private void handleSignup(String fullName, String credentials, String password) {
        boolean fullNameEmpty = fullName.isEmpty();
        boolean credEmpty = credentials.isEmpty();
        boolean passEmpty = password.isEmpty();

        if (fullNameEmpty || credEmpty || passEmpty) {
            if (fullNameEmpty && credEmpty && passEmpty) {
                showCustomToast("Please fill all fields", false);
                applyErrorBorder(fullNameInput);
                applyErrorBorder(credInput);
                applyErrorBorder(passwordInput);
            } else {
                if (fullNameEmpty) {
                    showCustomToast("Please enter your full name", false);
                    applyErrorBorder(fullNameInput);
                }
                if (credEmpty) {
                    showCustomToast("Please enter your email or phone number", false);
                    applyErrorBorder(credInput);
                }
                if (passEmpty) {
                    showCustomToast("Please enter your password", false);
                    applyErrorBorder(passwordInput);
                }
            }
            return;
        }

        // Validate phone number (Bangladesh: +880) or email
        String email;
        if (credentials.contains("@") && credentials.contains(".")) {
            email = credentials; // It's an email
        } else {
            if (!credentials.startsWith("+880")) {
                credentials = "+880" + credentials; // Add Bangladesh country code
            }
            email = credentials + "@smartpharmacy.com"; // Convert phone to email format
        }

        progressBar.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        showCustomToast("Sign up successful!", true);
                        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            showCustomToast("This email or phone number is already registered.", false);
                            applyErrorBorder(credInput);
                        } else {
                            showCustomToast("Signup failed: " + task.getException().getMessage(), false);
                            applyErrorBorder(credInput);
                        }
                        clearErrorBorders();
                    }
                });
    }

    private void setUiEnabled(boolean enabled) {
        fullNameInput.setEnabled(enabled);
        credInput.setEnabled(enabled);
        passwordInput.setEnabled(enabled);
        findViewById(R.id.signupBtn).setEnabled(enabled);
        loginText.setEnabled(enabled);
    }

    private void applyErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void clearErrorBorders() {
        fullNameInput.setBackgroundResource(R.drawable.edittext_bg);
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(toastView);
        toast.show();
    }
}