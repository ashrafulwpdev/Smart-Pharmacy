package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final int PHONE_STATE_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "SignupActivity";
    private EditText fullNameInput, credInput, passwordInput;
    private LottieAnimationView loadingSpinner;
    private TextView loginText;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin, emailIcon, phoneIcon;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private DatabaseReference emailsReference;
    private DatabaseReference phoneNumbersReference;
    private DatabaseReference usernamesReference;
    private FirebaseFirestore firestore;
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Check Google Play Services availability
        int playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        Log.d(TAG, "Google Play Services status: " + playServicesStatus);
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services unavailable: " + playServicesStatus);
            GoogleApiAvailability.getInstance().getErrorDialog(this, playServicesStatus, 0).show();
            return;
        }

        // Initialize Firebase and Google Sign-In
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        emailsReference = FirebaseDatabase.getInstance().getReference("emails");
        phoneNumbersReference = FirebaseDatabase.getInstance().getReference("phoneNumbers");
        usernamesReference = FirebaseDatabase.getInstance().getReference("usernames");
        firestore = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize Facebook Login
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                loadingSpinner.setVisibility(View.VISIBLE);
                setUiEnabled(false);
                AuthUtils.firebaseAuthWithFacebook(SignupActivity.this, loginResult.getAccessToken().getToken(),
                        databaseReference, emailsReference, phoneNumbersReference, usernamesReference,
                        new AuthUtils.AuthCallback() {
                            @Override
                            public void onSuccess(FirebaseUser user) {
                                handleSocialMediaSuccess(user, "Facebook");
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                handleSocialMediaFailure("Facebook", errorMessage);
                            }
                        });
            }

            @Override
            public void onCancel() {
                showCustomToast("Facebook Sign-In canceled", false);
            }

            @Override
            public void onError(FacebookException exception) {
                Log.e(TAG, "Facebook Sign-In error: " + exception.getMessage(), exception);
                showCustomToast("Facebook Sign-In failed: " + exception.getMessage(), false);
            }
        });

        // UI Initialization
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
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        ccp = findViewById(R.id.ccp);

        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("BD");

        // Setup
        requestPhoneStatePermission();
        AuthUtils.setupDynamicInput(this, credInput, ccp, emailIcon, phoneIcon, null);
        adjustCredInputPadding();
        setupInputValidation();

        // Event Listeners
        signupBtn.setOnClickListener(v -> handleSignup());
        loginText.setOnClickListener(v -> startActivity(new Intent(SignupActivity.this, LoginActivity.class)));
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleSignInClient != null && !isFinishing()) {
            mGoogleSignInClient.signOut().addOnCompleteListener(task -> Log.d(TAG, "Signed out from Google on destroy"));
        }
    }

    private void requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_STATE_PERMISSION_REQUEST_CODE);
        } else {
            AuthUtils.fetchSimNumber(this, credInput);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_STATE_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AuthUtils.fetchSimNumber(this, credInput);
        }
    }

    private void setupInputValidation() {
        fullNameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { resetInputBorders(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        credInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
                adjustCredInputPadding();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
                if (s.length() < 6 && s.length() > 0) passwordInput.setError("Password must be at least 6 characters");
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void adjustCredInputPadding() {
        ccp.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ccp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int paddingStart = ccp.getVisibility() == View.VISIBLE ?
                        ccp.getWidth() + (int) (10 * getResources().getDisplayMetrics().density) :
                        (int) (10 * getResources().getDisplayMetrics().density);
                credInput.setPadding(paddingStart, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
            }
        });
    }

    private void handleSignup() {
        String fullName = fullNameInput.getText().toString().trim();
        String credentials = credInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        Log.d(TAG, "Starting signup with fullName: " + fullName + ", credentials: " + credentials);

        if (!validateInputs(fullName, credentials, password)) return;

        String emailOrPhone = AuthUtils.isValidEmail(credentials) ? credentials : AuthUtils.normalizePhoneNumberForBackend(credentials, ccp, null);
        String signInMethod = AuthUtils.isValidEmail(emailOrPhone) ? "email" : "phone";
        final String syntheticEmail = signInMethod.equals("email") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";

        Log.d(TAG, "Sign-in method: " + signInMethod + ", syntheticEmail: " + syntheticEmail);

        if (!AuthUtils.isValidEmail(emailOrPhone) && !AuthUtils.isValidPhoneNumber(emailOrPhone)) {
            setErrorBorder(credInput);
            showCustomToast("Invalid email or phone number", false);
            return;
        }

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        DatabaseReference ref = signInMethod.equals("email") ? emailsReference : phoneNumbersReference;
        String key = signInMethod.equals("email") ? emailOrPhone.replace(".", "_") : emailOrPhone;

        AuthUtils.checkUniqueness(ref, key, syntheticEmail, isUnique -> {
            if (!isUnique) {
                loadingSpinner.setVisibility(View.GONE);
                setUiEnabled(true);
                setErrorBorder(credInput);
                showCustomToast((signInMethod.equals("email") ? "Email" : "Phone number") + " already in use", false);
                return;
            }

            Log.d(TAG, "Credentials are unique, generating username...");
            AuthUtils.generateUniqueUsername(fullName, usernamesReference, username -> {
                Log.d(TAG, "Generated username: " + username);
                mAuth.createUserWithEmailAndPassword(syntheticEmail, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    saveUserData(user, fullName, emailOrPhone, signInMethod, username);
                                }
                            } else {
                                Log.e(TAG, "Auth creation failed: " + task.getException().getMessage(), task.getException());
                                loadingSpinner.setVisibility(View.GONE);
                                setUiEnabled(true);
                                showCustomToast("Signup failed: " + task.getException().getMessage(), false);
                            }
                        });
            });
        });
    }

    private void signInWithGoogle() {
        Log.d(TAG, "Initiating Google Sign-In");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    private void signInWithFacebook() {
        Log.d(TAG, "Initiating Facebook Sign-In");
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
    }

    private void signInWithGitHub() {
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);
        Log.d(TAG, "Initiating GitHub Sign-In");
        AuthUtils.firebaseAuthWithGitHub(this, "YOUR_GITHUB_CLIENT_ID", databaseReference, emailsReference, phoneNumbersReference, usernamesReference,
                new AuthUtils.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        handleSocialMediaSuccess(user, "GitHub");
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        handleSocialMediaFailure("GitHub", errorMessage);
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google Sign-In successful, email: " + account.getEmail());
                loadingSpinner.setVisibility(View.VISIBLE);
                setUiEnabled(false);
                AuthUtils.firebaseAuthWithGoogle(this, account.getIdToken(), databaseReference, emailsReference, phoneNumbersReference, usernamesReference,
                        new AuthUtils.AuthCallback() {
                            @Override
                            public void onSuccess(FirebaseUser user) {
                                handleSocialMediaSuccess(user, "Google");
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                handleSocialMediaFailure("Google", errorMessage);
                            }
                        });
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed, status code: " + e.getStatusCode(), e);
                handleSocialMediaFailure("Google", "Sign-In failed: " + e.getMessage());
            }
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void togglePasswordVisibility() {
        passwordInput.setTransformationMethod(isPasswordVisible ? PasswordTransformationMethod.getInstance() : SingleLineTransformationMethod.getInstance());
        passwordToggle.setImageResource(isPasswordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye_on);
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

    private void showCustomToast(String message, boolean isSuccess) {
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(toastView);
        toast.show();
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        fullNameInput.setBackgroundResource(R.drawable.edittext_bg);
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void setUserRoleInFirestore(String uid, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("role", role);
        firestore.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore role set for UID: " + uid))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to set Firestore role: " + e.getMessage(), e));
    }

    private boolean validateInputs(String fullName, String credentials, String password) {
        resetInputBorders();
        if (fullName.isEmpty() || credentials.isEmpty() || password.isEmpty()) {
            if (fullName.isEmpty()) setErrorBorder(fullNameInput);
            if (credentials.isEmpty()) setErrorBorder(credInput);
            if (password.isEmpty()) setErrorBorder(passwordInput);
            showCustomToast("All fields are required", false);
            return false;
        }
        if (password.length() < 6) {
            setErrorBorder(passwordInput);
            showCustomToast("Password must be at least 6 characters", false);
            return false;
        }
        return true;
    }

    private void saveUserData(FirebaseUser user, String fullName, String emailOrPhone, String signInMethod, String username) {
        AuthUtils.saveUserData(user, databaseReference, emailsReference, phoneNumbersReference, usernamesReference,
                fullName,
                signInMethod.equals("email") ? emailOrPhone : "",
                signInMethod.equals("phone") ? emailOrPhone : "",
                username,
                signInMethod,
                new AuthUtils.SaveUserDataCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "User data saved to Realtime Database");
                        setUserRoleInFirestore(user.getUid(), "user");
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        showCustomToast("Signup successful!", true);
                        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Failed to save user data: " + errorMessage);
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        showCustomToast("Signup failed: " + errorMessage, false);
                        user.delete()
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Deleted failed user"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete user: " + e.getMessage(), e));
                    }
                });
    }

    private void handleSocialMediaSuccess(FirebaseUser user, String provider) {
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, provider + " signup succeeded but user is null, likely signed out prematurely");
            showCustomToast(provider + " signup failed: Please try again", false);
            loadingSpinner.setVisibility(View.GONE);
            setUiEnabled(true);
            return;
        }
        setUserRoleInFirestore(user.getUid(), "user");
        loadingSpinner.setVisibility(View.GONE);
        setUiEnabled(true);
        showCustomToast(provider + " signup successful!", true);
        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void handleSocialMediaFailure(String provider, String errorMessage) {
        Log.e(TAG, provider + " signup failed: " + errorMessage);
        loadingSpinner.setVisibility(View.GONE);
        setUiEnabled(true);
        showCustomToast(provider + " signup failed: " + errorMessage, false);
    }
}