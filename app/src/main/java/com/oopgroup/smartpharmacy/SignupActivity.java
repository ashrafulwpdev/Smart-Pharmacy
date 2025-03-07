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
import android.view.Gravity;
import android.view.LayoutInflater;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        emailsReference = FirebaseDatabase.getInstance().getReference("emails");
        phoneNumbersReference = FirebaseDatabase.getInstance().getReference("phoneNumbers");
        usernamesReference = FirebaseDatabase.getInstance().getReference("usernames");

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
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        ccp = findViewById(R.id.ccp);

        // Set default country for CCP
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("BD");

        // Fetch SIM number with permission check
        requestPhoneStatePermission();

        // Setup dynamic input and adjust padding
        AuthInputHandler.setupDynamicInput(this, credInput, ccp, emailIcon, phoneIcon, null);
        adjustCredInputPadding();

        // Add text watchers for real-time validation
        setupInputValidation();

        // Set click listeners
        signupBtn.setOnClickListener(v -> handleSignup());
        loginText.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());
    }

    private void requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                showCustomToast("Phone state permission is needed to auto-fill your phone number", false);
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_STATE_PERMISSION_REQUEST_CODE);
        } else {
            AuthInputHandler.fetchSimNumber(this, credInput);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_STATE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                AuthInputHandler.fetchSimNumber(this, credInput);
            } else {
                Log.d(TAG, "READ_PHONE_STATE permission denied, proceeding with manual entry");
            }
        }
    }

    private void setupInputValidation() {
        fullNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
                adjustCredInputPadding();
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
                if (s.length() < 6 && s.length() > 0) {
                    passwordInput.setError("Password must be at least 6 characters");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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

    private void handleSignup() {
        String fullName = fullNameInput.getText().toString().trim();
        String credentials = credInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        resetInputBorders();

        if (fullName.isEmpty()) {
            setErrorBorder(fullNameInput);
            fullNameInput.setError("Full name cannot be empty");
            showCustomToast("Full name is required", false);
            return;
        }

        if (credentials.isEmpty()) {
            setErrorBorder(credInput);
            credInput.setError("Enter an email or phone number");
            showCustomToast("Phone number or email is required", false);
            return;
        }

        if (password.isEmpty()) {
            setErrorBorder(passwordInput);
            passwordInput.setError("Password cannot be empty");
            showCustomToast("Password is required", false);
            return;
        }

        if (password.length() < 6) {
            setErrorBorder(passwordInput);
            passwordInput.setError("Password too short");
            showCustomToast("Password must be at least 6 characters long", false);
            return;
        }

        String emailOrPhone;
        String signInMethod;
        if (AuthInputHandler.isValidEmail(credentials)) {
            emailOrPhone = credentials;
            signInMethod = "email";
        } else if (AuthInputHandler.isValidPhoneNumber(credentials)) {
            emailOrPhone = AuthInputHandler.normalizePhoneNumber(credentials, ccp);
            if (!emailOrPhone.matches("^\\+\\d{10,14}$")) {
                setErrorBorder(credInput);
                credInput.setError("Invalid phone format (e.g., +60123456789)");
                showCustomToast("Invalid phone number format", false);
                return;
            }
            signInMethod = "phone";
        } else {
            setErrorBorder(credInput);
            credInput.setError("Invalid format");
            showCustomToast("Enter a valid phone number or email", false);
            return;
        }

        final String syntheticEmail = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";
        final String phoneNumber = emailOrPhone.contains("@") ? "" : emailOrPhone;
        Log.d(TAG, "Synthetic email for signup: " + syntheticEmail);

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        checkUniqueness(emailOrPhone, signInMethod, unique -> {
            if (!unique) {
                loadingSpinner.setVisibility(View.GONE);
                setUiEnabled(true);
                setErrorBorder(credInput);
                credInput.setError(signInMethod.equals("email") ? "Email already in use" : "Phone number already in use");
                showCustomToast((signInMethod.equals("email") ? "Email" : "Phone number") + " '" + emailOrPhone + "' is already registered.", false);
                return;
            }

            generateUniqueUsername(fullName, uniqueUsername -> {
                mAuth.fetchSignInMethodsForEmail(syntheticEmail)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                if (task.getResult().getSignInMethods() != null && !task.getResult().getSignInMethods().isEmpty()) {
                                    loadingSpinner.setVisibility(View.GONE);
                                    setUiEnabled(true);
                                    setErrorBorder(credInput);
                                    credInput.setError(signInMethod.equals("email") ? "Email already registered" : "Phone number already registered");
                                    showCustomToast("This " + (signInMethod.equals("email") ? "email" : "phone number") + " is already registered.", false);
                                } else {
                                    createUser(fullName, syntheticEmail, password, emailOrPhone, uniqueUsername, signInMethod);
                                }
                            } else {
                                loadingSpinner.setVisibility(View.GONE);
                                setUiEnabled(true);
                                Log.e(TAG, "Error checking email existence: " + task.getException().getMessage());
                                showCustomToast("Signup failed: Unable to verify " + (signInMethod.equals("email") ? "email" : "phone"), false);
                            }
                        });
            });
        });
    }

    private void createUser(String fullName, String email, String password, String emailOrPhone, String username, String signInMethod) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserData(user, fullName, emailOrPhone.contains("@") ? emailOrPhone : "", emailOrPhone.contains("@") ? "" : emailOrPhone, username, signInMethod);
                        } else {
                            loadingSpinner.setVisibility(View.GONE);
                            setUiEnabled(true);
                            showCustomToast("Signup failed: User not created", false);
                        }
                    } else {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e(TAG, "Signup failed: " + errorMsg);
                        if (errorMsg.toLowerCase().contains("password")) {
                            setErrorBorder(passwordInput);
                            passwordInput.setError("Invalid password");
                        } else {
                            setErrorBorder(credInput);
                            credInput.setError("Signup error");
                        }
                        showCustomToast("Signup failed: " + errorMsg, false);
                    }
                });
    }

    private void saveUserData(FirebaseUser user, String fullName, String email, String phoneNumber, String username, String signInMethod) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName != null ? fullName : "User");
        userData.put("email", email != null ? email : "");
        userData.put("phoneNumber", phoneNumber != null ? phoneNumber : "");
        userData.put("gender", "Not specified");
        userData.put("birthday", "01-01-2000");
        userData.put("username", username != null ? username : "user" + new Random().nextInt(1000));
        userData.put("imageUrl", "");
        userData.put("signInMethod", signInMethod != null ? signInMethod : "unknown");

        databaseReference.child(user.getUid()).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    if (signInMethod.equals("email") && email != null && !email.isEmpty()) {
                        String emailKey = email.replace(".", "_");
                        emailsReference.child(emailKey).setValue(user.getUid());
                    } else if (signInMethod.equals("phone") && phoneNumber != null && !phoneNumber.isEmpty()) {
                        phoneNumbersReference.child(phoneNumber).setValue(user.getUid());
                    }
                    if (username != null && !username.isEmpty()) {
                        usernamesReference.child(username).setValue(user.getUid());
                    }

                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    resetInputBorders();
                    showCustomToast("Sign up successful! Welcome aboard!", true);
                    startActivity(new Intent(SignupActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    Log.e(TAG, "Failed to save user data: " + e.getMessage());
                    showCustomToast("Signup failed: Couldn’t save profile data - " + e.getMessage(), false);
                    mAuth.signOut();
                });
    }

    private void checkUniqueness(String emailOrPhone, String signInMethod, OnUniquenessCheckListener listener) {
        DatabaseReference ref = signInMethod.equals("email") ? emailsReference : phoneNumbersReference;
        String key = signInMethod.equals("email") ? emailOrPhone.replace(".", "_") : emailOrPhone;
        Log.d(TAG, "Checking uniqueness for " + signInMethod + " with key: " + key);

        ref.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                listener.onCheckComplete(!dataSnapshot.exists());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Uniqueness check failed: " + databaseError.getMessage());
                listener.onCheckComplete(false);
            }
        });
    }

    private void generateUniqueUsername(String fullName, OnUsernameGeneratedListener listener) {
        String baseUsername = fullName != null ? fullName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase() : "user";
        String initialUsername = baseUsername.isEmpty() ? "user" : baseUsername;

        checkUsernameAvailability(initialUsername, 1, username -> listener.onUsernameGenerated(username));
    }

    private void checkUsernameAvailability(String baseUsername, int suffix, OnUsernameGeneratedListener listener) {
        String candidateUsername = suffix == 1 ? baseUsername : baseUsername + (suffix - 1);
        usernamesReference.child(candidateUsername).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    listener.onUsernameGenerated(candidateUsername); // Username is available
                } else {
                    checkUsernameAvailability(baseUsername, suffix + 1, listener); // Try next suffix
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Username check failed: " + databaseError.getMessage());
                // Fallback to random suffix on error
                listener.onUsernameGenerated(baseUsername + new Random().nextInt(1000));
            }
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    private void firebaseAuthWithGoogle(String idToken, String displayName, String email) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String userEmail = email != null ? email : (user.getEmail() != null ? user.getEmail() : "");
                            String userDisplayName = displayName != null ? displayName : (user.getDisplayName() != null ? user.getDisplayName() : "User");
                            String userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

                            checkUniqueness(userEmail, "email", unique -> {
                                if (!unique) {
                                    loadingSpinner.setVisibility(View.GONE);
                                    setUiEnabled(true);
                                    showCustomToast("Email '" + userEmail + "' is already in use.", false);
                                    mAuth.signOut();
                                    return;
                                }

                                generateUniqueUsername(userDisplayName, uniqueUsername -> {
                                    saveUserData(user, userDisplayName, userEmail, userPhone, uniqueUsername, "google");
                                });
                            });
                        } else {
                            loadingSpinner.setVisibility(View.GONE);
                            setUiEnabled(true);
                            showCustomToast("Google signup failed: User not found", false);
                        }
                    } else {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        Log.e(TAG, "Google signup failed: " + task.getException().getMessage());
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
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String userEmail = user.getEmail() != null ? user.getEmail() : "";
                            String userDisplayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                            String userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

                            checkUniqueness(userEmail, "email", unique -> {
                                if (!unique) {
                                    loadingSpinner.setVisibility(View.GONE);
                                    setUiEnabled(true);
                                    showCustomToast("Email '" + userEmail + "' is already in use.", false);
                                    mAuth.signOut();
                                    return;
                                }

                                generateUniqueUsername(userDisplayName, uniqueUsername -> {
                                    saveUserData(user, userDisplayName, userEmail, userPhone, uniqueUsername, "facebook");
                                });
                            });
                        } else {
                            loadingSpinner.setVisibility(View.GONE);
                            setUiEnabled(true);
                            showCustomToast("Facebook signup failed: User not found", false);
                        }
                    } else {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        Log.e(TAG, "Facebook signup failed: " + task.getException().getMessage());
                        showCustomToast("Facebook signup failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void signInWithGitHub() {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com");
        provider.addCustomParameter("client_id", "YOUR_GITHUB_CLIENT_ID"); // Replace with your GitHub client ID
        provider.setScopes(Arrays.asList("user:email"));

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        String userEmail = user.getEmail() != null ? user.getEmail() : "";
                        String userDisplayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                        String userPhone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

                        checkUniqueness(userEmail, "email", unique -> {
                            if (!unique) {
                                loadingSpinner.setVisibility(View.GONE);
                                setUiEnabled(true);
                                showCustomToast("Email '" + userEmail + "' is already in use.", false);
                                mAuth.signOut();
                                return;
                            }

                            generateUniqueUsername(userDisplayName, uniqueUsername -> {
                                saveUserData(user, userDisplayName, userEmail, userPhone, uniqueUsername, "github");
                            });
                        });
                    } else {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        showCustomToast("GitHub signup failed: User not found", false);
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    Log.e(TAG, "GitHub signup failed: " + e.getMessage());
                    showCustomToast("GitHub signup failed: " + e.getMessage(), false);
                });
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
                loadingSpinner.setVisibility(View.GONE);
                setUiEnabled(true);
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(SignupActivity.this, LoginActivity.class));
        finish();
    }

    private interface OnUniquenessCheckListener {
        void onCheckComplete(boolean isUnique);
    }

    private interface OnUsernameGeneratedListener {
        void onUsernameGenerated(String username);
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        fullNameInput.setBackgroundResource(R.drawable.edittext_bg);
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
    }
}