package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hbb20.CountryCodePicker;

import java.util.Arrays;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final String TAG = "LoginActivity";
    private EditText credInput, passwordInput;
    private LottieAnimationView loadingSpinner;
    private TextView forgotText, signupText;
    private Button loginBtn;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin, emailIcon, phoneIcon;
    private CheckBox rememberMeCheckbox;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private SharedPreferences prefs;
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
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
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        ccp = findViewById(R.id.ccp);

        // Set default country for CCP
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("BD");

        // Fetch SIM number
        AuthInputHandler.fetchSimNumber(this, credInput);

        // Setup dynamic input and adjust padding
        AuthInputHandler.setupDynamicInput(this, credInput, ccp, emailIcon, phoneIcon, null);
        adjustCredInputPadding();

        // Check if user is remembered
        if (prefs.getBoolean("rememberMe", false) && mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Add text watcher to adjust padding when input changes and synchronize CCP
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
                adjustCredInputPadding();
                String input = s.toString().trim();
                if (!AuthInputHandler.isValidEmail(input) && AuthInputHandler.isValidPhoneNumber(input)) {
                    String cleanedInput = input.replaceAll("[^0-9+]", "");
                    AuthInputHandler.updateCountryFlag(cleanedInput, ccp);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

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

    private void handleEmailLogin(String credentials, String password) {
        resetInputBorders();

        if (credentials.isEmpty() && password.isEmpty()) {
            showCustomToast("Please fill all fields", false);
            setErrorBorder(credInput);
            setErrorBorder(passwordInput);
            return;
        }

        if (credentials.isEmpty()) {
            showCustomToast("Please enter your email or phone number", false);
            setErrorBorder(credInput);
            return;
        }

        if (password.isEmpty()) {
            showCustomToast("Please enter your password", false);
            setErrorBorder(passwordInput);
            return;
        }

        String emailOrPhone;
        if (AuthInputHandler.isValidEmail(credentials)) {
            emailOrPhone = credentials;
            Log.d(TAG, "Logging in with email: " + emailOrPhone);
        } else if (AuthInputHandler.isValidPhoneNumber(credentials)) {
            emailOrPhone = AuthInputHandler.normalizePhoneNumber(credentials, ccp);
            Log.d(TAG, "Normalized phone number: " + credentials + " -> " + emailOrPhone);
        } else {
            showCustomToast("Invalid email or phone number format", false);
            setErrorBorder(credInput);
            return;
        }

        String email = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";
        Log.d(TAG, "Final email used for login: " + email);

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
                        resetInputBorders();
                        showCustomToast("Login successful!", true);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showCustomToast("Invalid email/phone number or password", false);
                        setErrorBorder(credInput);
                        setErrorBorder(passwordInput);
                        Log.e(TAG, "Login failed: " + task.getException().getMessage());
                    }
                });
    }

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

    private void signInWithGitHub() {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com");
        provider.addCustomParameter("client_id", "YOUR_GITHUB_CLIENT_ID");
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

    // Helper methods for border handling
    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
    }
}