package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001; // Request code for Google Sign-In
    private EditText credInput, passwordInput;
    private ProgressBar progressBar;
    private TextView forgotText, signupText;
    private Button loginBtn;
    private ImageView passwordToggle, googleLogin;
    private CheckBox rememberMeCheckbox;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("SmartPharmacyPrefs", MODE_PRIVATE);

        // Initialize Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize UI elements
        loginBtn = findViewById(R.id.loginBtn);
        credInput = findViewById(R.id.credInput);
        passwordInput = findViewById(R.id.passwordInput);
        progressBar = findViewById(R.id.progressBar);
        forgotText = findViewById(R.id.forgotText);
        signupText = findViewById(R.id.signupText);
        passwordToggle = findViewById(R.id.passwordToggle);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);
        googleLogin = findViewById(R.id.googleLogin);

        // Check if user is remembered
        if (prefs.getBoolean("rememberMe", false) && mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        loginBtn.setOnClickListener(v -> {
            String credentials = credInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            handleEmailLogin(credentials, password);
        });

        forgotText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgotPassActivity.class));
        });

        signupText.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });

        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());

        googleLogin.setOnClickListener(v -> signInWithGoogle());
    }

    // Email/Password Login
    private void handleEmailLogin(String credentials, String password) {
        boolean credEmpty = credentials.isEmpty();
        boolean passEmpty = password.isEmpty();

        if (credEmpty || passEmpty) {
            clearErrorBorders();
            if (credEmpty && passEmpty) {
                showCustomToast("Please fill all fields", false);
                applyErrorBorder(credInput);
                applyErrorBorder(passwordInput);
            } else if (credEmpty) {
                showCustomToast("Please enter your email or phone number", false);
                applyErrorBorder(credInput);
            } else {
                showCustomToast("Please enter your password", false);
                applyErrorBorder(passwordInput);
            }
            return;
        }

        String email;
        if (credentials.contains("@") && credentials.contains(".")) {
            email = credentials; // It's an email
        } else {
            if (!credentials.startsWith("+880")) {
                credentials = "+880" + credentials; // Add Bangladesh country code
            }
            email = credentials + "@smartpharmacy.com"; // Convert phone to email format
        }

        setUiEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        if (rememberMeCheckbox.isChecked()) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean("rememberMe", true);
                            editor.apply();
                        }
                        showCustomToast("Login successful!", true);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showCustomToast("Invalid email/phone number or password", false);
                        applyErrorBorder(credInput);
                        applyErrorBorder(passwordInput);
                        clearErrorBorders();
                    }
                });
    }

    // Google Sign-In
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                showCustomToast("Google Sign-In failed: " + e.getMessage(), false);
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        setUiEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        if (rememberMeCheckbox.isChecked()) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean("rememberMe", true);
                            editor.apply();
                        }
                        showCustomToast("Google login successful!", true);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showCustomToast("Google login failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_eye_off);
        } else {
            passwordInput.setTransformationMethod(SingleLineTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_eye_on);
        }
        isPasswordVisible = !isPasswordVisible;
        passwordInput.setSelection(passwordInput.getText().length());
    }

    private void applyErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void clearErrorBorders() {
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void setUiEnabled(boolean enabled) {
        credInput.setEnabled(enabled);
        passwordInput.setEnabled(enabled);
        loginBtn.setEnabled(enabled);
        forgotText.setEnabled(enabled);
        signupText.setEnabled(enabled);
        passwordToggle.setEnabled(enabled);
        rememberMeCheckbox.setEnabled(enabled);
        googleLogin.setEnabled(enabled);
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