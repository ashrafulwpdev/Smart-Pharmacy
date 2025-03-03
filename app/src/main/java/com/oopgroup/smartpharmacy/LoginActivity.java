package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import java.util.Arrays;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final String TAG = "LoginActivity";
    private EditText credInput, passwordInput;
    private LottieAnimationView loadingSpinner;
    private TextView forgotText, signupText;
    private Button loginBtn;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin;
    private CheckBox rememberMeCheckbox;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("SmartPharmacyPrefs", MODE_PRIVATE);

        // Google Sign-In Setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Facebook Sign-In Setup
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        firebaseAuthWithFacebook(loginResult.getAccessToken().getToken());
                    }

                    @Override
                    public void onCancel() {
                        showCustomToast("Facebook login canceled", false);
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        showCustomToast("Facebook login failed: " + exception.getMessage(), false);
                    }
                });

        // Initialize UI elements
        loginBtn = findViewById(R.id.loginBtn);
        credInput = findViewById(R.id.credInput);
        passwordInput = findViewById(R.id.passwordInput);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        forgotText = findViewById(R.id.forgotText);
        signupText = findViewById(R.id.signupText);
        passwordToggle = findViewById(R.id.passwordToggle);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);
        googleLogin = findViewById(R.id.googleLogin);
        facebookLogin = findViewById(R.id.facebookLogin);
        githubLogin = findViewById(R.id.githublogin);

        // Set Lottie animation programmatically
        try {
            loadingSpinner.setAnimation(R.raw.loading_global); // Use your new file
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Lottie animation: " + e.getMessage(), e);
            loadingSpinner.setVisibility(View.GONE); // Hide if file is missing
        }

        // Check if user is remembered
        if (prefs.getBoolean("rememberMe", false) && mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Set click listeners
        loginBtn.setOnClickListener(v -> {
            String credentials = credInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            handleEmailLogin(credentials, password);
        });

        forgotText.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPassActivity.class)));
        signupText.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SignupActivity.class)));
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());
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

        String email = credentials.contains("@") ? credentials : "+880" + credentials + "@smartpharmacy.com";

        setUiEnabled(false);
        loadingSpinner.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
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

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        setUiEnabled(false);
        loadingSpinner.setVisibility(View.VISIBLE);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
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

    // Facebook Sign-In
    private void signInWithFacebook() {
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
    }

    private void firebaseAuthWithFacebook(String token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token);
        setUiEnabled(false);
        loadingSpinner.setVisibility(View.VISIBLE);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        if (rememberMeCheckbox.isChecked()) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean("rememberMe", true);
                            editor.apply();
                        }
                        showCustomToast("Facebook login successful!", true);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showCustomToast("Facebook login failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    // GitHub Sign-In
    private void signInWithGitHub() {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com");
        provider.addCustomParameter("client_id", "YOUR_GITHUB_CLIENT_ID"); // Replace with your GitHub Client ID
        provider.setScopes(Arrays.asList("user:email"));

        setUiEnabled(false);
        loadingSpinner.setVisibility(View.VISIBLE);

        mAuth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener(authResult -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (rememberMeCheckbox.isChecked()) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("rememberMe", true);
                        editor.apply();
                    }
                    showCustomToast("GitHub login successful!", true);
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    Log.e(TAG, "GitHub login failed: " + e.getMessage());
                    showCustomToast("GitHub login failed: " + e.getMessage(), false);
                });
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
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
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
        facebookLogin.setEnabled(enabled);
        githubLogin.setEnabled(enabled);
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