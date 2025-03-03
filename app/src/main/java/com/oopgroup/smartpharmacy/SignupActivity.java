package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SignupActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final String TAG = "SignupActivity";
    private EditText fullNameInput, credInput, passwordInput;
    private LottieAnimationView loadingSpinner;
    private TextView loginText;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

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
                        showCustomToast("Facebook Sign-In canceled", false);
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        showCustomToast("Facebook Sign-In failed: " + exception.getMessage(), false);
                    }
                });

        // Initialize UI elements
        Button signupBtn = findViewById(R.id.signupBtn);
        fullNameInput = findViewById(R.id.fullNameInput);
        credInput = findViewById(R.id.credInput);
        passwordInput = findViewById(R.id.passwordInput);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        loginText = findViewById(R.id.loginText);
        passwordToggle = findViewById(R.id.passwordToggle);
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

        // Set click listeners
        signupBtn.setOnClickListener(v -> {
            String fullName = fullNameInput.getText().toString().trim();
            String credentials = credInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            handleEmailSignup(fullName, credentials, password);
        });

        loginText.setOnClickListener(v -> startActivity(new Intent(SignupActivity.this, LoginActivity.class)));
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());
    }

    private void handleEmailSignup(String fullName, String credentials, String password) {
        if (fullName.isEmpty() || credentials.isEmpty() || password.isEmpty()) {
            showCustomToast(fullName.isEmpty() ? "Please enter your full name" :
                    credentials.isEmpty() ? "Please enter your email or phone number" :
                            "Please enter your password", false);
            applyErrorBorder(fullName.isEmpty() ? fullNameInput : credentials.isEmpty() ? credInput : passwordInput);
            return;
        }

        String emailOrPhone = credentials.contains("@") ? credentials : "+880" + credentials;
        String email = credentials.contains("@") ? credentials : emailOrPhone + "@smartpharmacy.com";
        String username = generateRandomUsername();

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("fullName", fullName);
                            userData.put("email", emailOrPhone.contains("@") ? emailOrPhone : "");
                            userData.put("phoneNumber", emailOrPhone.contains("@") ? "" : emailOrPhone);
                            userData.put("gender", "Not specified");
                            userData.put("birthday", "");
                            userData.put("username", username);
                            userData.put("imageUrl", "");
                            userData.put("signInMethod", "email");

                            Log.d(TAG, "Email Signup - Saving data: " + userData.toString());
                            databaseReference.child(user.getUid()).setValue(userData)
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save email signup data: " + e.getMessage()));
                        }
                        showCustomToast("Sign up successful! Please edit your username later.", true);
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            showCustomToast("This email or phone number is already registered.", false);
                        } else {
                            showCustomToast("Signup failed: " + task.getException().getMessage(), false);
                        }
                        applyErrorBorder(credInput);
                        clearErrorBorders();
                    }
                });
    }

    private String generateRandomUsername() {
        Random random = new Random();
        return "@user" + random.nextInt(1000000);
    }

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
                firebaseAuthWithGoogle(account.getIdToken(), account.getDisplayName(), account.getEmail());
            } catch (ApiException e) {
                showCustomToast("Google Sign-In failed: " + e.getMessage(), false);
            }
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void firebaseAuthWithGoogle(String idToken, String displayName, String email) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        String username = generateRandomUsername();

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String userEmail = user.getEmail() != null ? user.getEmail() : (email != null ? email : "");
                            String userDisplayName = user.getDisplayName() != null ? user.getDisplayName() : (displayName != null ? displayName : "User");
                            String userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

                            Map<String, Object> userData = new HashMap<>();
                            userData.put("fullName", userDisplayName);
                            userData.put("email", userEmail);
                            userData.put("phoneNumber", userPhone);
                            userData.put("gender", "Not specified");
                            userData.put("birthday", "");
                            userData.put("username", username);
                            userData.put("imageUrl", "");
                            userData.put("signInMethod", "google");

                            Log.d(TAG, "Google Signup - Saving data: " + userData.toString());
                            databaseReference.child(user.getUid()).setValue(userData)
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save Google data: " + e.getMessage()));
                        }
                        showCustomToast("Google signup successful! Please edit your username later.", true);
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showCustomToast("Google signup failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void signInWithFacebook() {
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
    }

    private void firebaseAuthWithFacebook(String token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token);
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String userEmail = user.getEmail() != null ? user.getEmail() : "";
                            String userDisplayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                            String userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

                            Map<String, Object> userData = new HashMap<>();
                            userData.put("fullName", userDisplayName);
                            userData.put("email", userEmail);
                            userData.put("phoneNumber", userPhone);
                            userData.put("gender", "Not specified");
                            userData.put("birthday", "");
                            userData.put("username", generateRandomUsername());
                            userData.put("imageUrl", "");
                            userData.put("signInMethod", "facebook");

                            Log.d(TAG, "Facebook Signup - Saving data: " + userData.toString());
                            databaseReference.child(user.getUid()).setValue(userData)
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save Facebook data: " + e.getMessage()));
                            showCustomToast("Facebook signup successful! Please edit your username later.", true);
                            startActivity(new Intent(SignupActivity.this, MainActivity.class));
                            finish();
                        }
                    } else {
                        showCustomToast("Facebook signup failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void signInWithGitHub() {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com");
        provider.addCustomParameter("client_id", "YOUR_GITHUB_CLIENT_ID"); // Replace with your GitHub Client ID
        provider.setScopes(Arrays.asList("user:email")); // Request email scope

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        String userEmail = user.getEmail() != null ? user.getEmail() : "";
                        String userDisplayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                        String userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("fullName", userDisplayName);
                        userData.put("email", userEmail);
                        userData.put("phoneNumber", userPhone);
                        userData.put("gender", "Not specified");
                        userData.put("birthday", "");
                        userData.put("username", generateRandomUsername());
                        userData.put("imageUrl", "");
                        userData.put("signInMethod", "github");

                        Log.d(TAG, "GitHub Signup - Saving data: " + userData.toString());
                        databaseReference.child(user.getUid()).setValue(userData)
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to save GitHub data: " + e.getMessage()));
                        showCustomToast("GitHub signup successful! Please edit your username later.", true);
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    Log.e(TAG, "GitHub signup failed: " + e.getMessage());
                    showCustomToast("GitHub signup failed: " + e.getMessage(), false);
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

    private void setUiEnabled(boolean enabled) {
        fullNameInput.setEnabled(enabled);
        credInput.setEnabled(enabled);
        passwordInput.setEnabled(enabled);
        findViewById(R.id.signupBtn).setEnabled(enabled);
        loginText.setEnabled(enabled);
        passwordToggle.setEnabled(enabled);
        googleLogin.setEnabled(enabled);
        facebookLogin.setEnabled(enabled);
        githubLogin.setEnabled(enabled);
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
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(toastView);
        toast.show();
    }

    // Navigate to LoginActivity on back press
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(SignupActivity.this, LoginActivity.class));
        finish();
    }
}