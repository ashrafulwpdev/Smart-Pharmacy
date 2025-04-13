package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;
import com.oopgroup.smartpharmacy.utils.AuthUtils;
import com.softourtech.slt.SLTLoader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final String TAG = "SignupActivity";
    private EditText fullNameInput, credInput, passwordInput;
    private TextView loginText, validationMessage;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin, emailIcon, phoneIcon;
    private CountryCodePicker ccp;
    private RadioGroup inputTypeGroup;
    private RadioButton emailRadio, phoneRadio;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private boolean isPasswordVisible = false;
    private boolean isPhoneInput = false;
    private boolean isModeLocked = false;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;
    private SLTLoader sltLoader;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "READ_PHONE_STATE permission granted, fetching SIM number");
                    isPhoneInput = true;
                    phoneRadio.setChecked(true);
                    isModeLocked = true;
                    AuthUtils.fetchSimNumber(this, credInput, ccp);
                } else {
                    Log.w(TAG, "Phone state permission denied");
                    isPhoneInput = false;
                    inputTypeGroup.clearCheck();
                    isModeLocked = false;
                    credInput.setText("");
                    showCustomToast("Permission denied. Please enter phone number manually.", false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        Log.d(TAG, "SignupActivity created");

        // Check Google Play Services
        int playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services unavailable: " + playServicesStatus);
            GoogleApiAvailability.getInstance().getErrorDialog(this, playServicesStatus, 0).show();
            return;
        }

        // Initialize SLTLoader
        View activityRoot = findViewById(android.R.id.content);
        if (activityRoot == null || !(activityRoot instanceof ViewGroup)) {
            Log.e(TAG, "Activity root view not found or not a ViewGroup");
            finish();
            return;
        }
        sltLoader = new SLTLoader(this, (ViewGroup) activityRoot);
        Log.d(TAG, "SLTLoader initialized");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        Log.d(TAG, "Firebase initialized");

        // Google Sign-In setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        Log.d(TAG, "Google Sign-In client initialized");

        // Facebook Login setup
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "Facebook login success: " + loginResult.getAccessToken().getUserId());
                showLoader();
                setUiEnabled(false);
                AuthUtils.firebaseAuthWithFacebook(SignupActivity.this, loginResult.getAccessToken().getToken(),
                        firestore, new AuthUtils.AuthCallback() {
                            @Override
                            public void onSuccess(FirebaseUser user) {
                                Log.d(TAG, "Facebook auth success for UID: " + user.getUid());
                                handleSocialMediaSuccess(user, "Facebook");
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                Log.e(TAG, "Facebook auth failed: " + errorMessage);
                                handleSocialMediaFailure("Facebook", errorMessage);
                            }
                        });
            }

            @Override
            public void onCancel() {
                Log.w(TAG, "Facebook login canceled");
                showCustomToast("Facebook Sign-In canceled", false);
            }

            @Override
            public void onError(FacebookException exception) {
                Log.e(TAG, "Facebook login error: " + exception.getMessage(), exception);
                showCustomToast("Facebook Sign-In failed: " + exception.getMessage(), false);
            }
        });
        Log.d(TAG, "Facebook login callback registered");

        // UI Initialization
        Button signupBtn = findViewById(R.id.signupBtn);
        fullNameInput = findViewById(R.id.fullNameInput);
        credInput = findViewById(R.id.credInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginText = findViewById(R.id.loginText);
        validationMessage = findViewById(R.id.validationMessage);
        passwordToggle = findViewById(R.id.passwordToggle);
        googleLogin = findViewById(R.id.googleLogin);
        facebookLogin = findViewById(R.id.facebookLogin);
        githubLogin = findViewById(R.id.githublogin);
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        ccp = findViewById(R.id.ccp);
        inputTypeGroup = findViewById(R.id.inputTypeGroup);
        emailRadio = findViewById(R.id.emailRadio);
        phoneRadio = findViewById(R.id.phoneRadio);
        Log.d(TAG, "UI components initialized");

        // CCP Setup
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("MY");
        ccp.registerCarrierNumberEditText(credInput);
        ccp.setNumberAutoFormattingEnabled(false);
        Log.d(TAG, "CountryCodePicker configured");

        // Initialize UI state
        inputTypeGroup.clearCheck();
        isModeLocked = false;
        isPhoneInput = false;
        ccp.setVisibility(View.GONE);
        emailIcon.setVisibility(View.VISIBLE);
        phoneIcon.setVisibility(View.GONE);
        validationMessage.setText("");
        Log.d(TAG, "Initial UI state set");

        // Setup
        setupInputTypeToggle();
        setupInputValidation();
        adjustCredInputPadding();

        // Event Listeners
        signupBtn.setOnClickListener(v -> handleSignup());
        loginText.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to LoginActivity");
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());
        Log.d(TAG, "Event listeners attached");

        // Request permission
        requestPhoneStatePermission();
    }

    private void requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_PHONE_STATE permission already granted");
            isPhoneInput = true;
            phoneRadio.setChecked(true);
            isModeLocked = true;
            AuthUtils.fetchSimNumber(this, credInput, ccp);
        } else {
            Log.d(TAG, "Requesting READ_PHONE_STATE permission");
            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        }
    }

    private void setupInputTypeToggle() {
        inputTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.emailRadio) {
                Log.d(TAG, "Selected email input mode");
                isPhoneInput = false;
                isModeLocked = true;
                credInput.setText("");
                credInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                ccp.setVisibility(View.GONE);
                emailIcon.setVisibility(View.VISIBLE);
                phoneIcon.setVisibility(View.GONE);
                validationMessage.setText("");
                adjustCredInputPadding();
            } else if (checkedId == R.id.phoneRadio) {
                Log.d(TAG, "Selected phone input mode");
                isPhoneInput = true;
                isModeLocked = true;
                credInput.setText("");
                credInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                ccp.setVisibility(View.VISIBLE);
                emailIcon.setVisibility(View.GONE);
                phoneIcon.setVisibility(View.VISIBLE);
                validationMessage.setText("");
                adjustCredInputPadding();
            } else {
                Log.d(TAG, "No input mode selected");
                isModeLocked = false;
                isPhoneInput = false;
                credInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                ccp.setVisibility(View.GONE);
                emailIcon.setVisibility(View.VISIBLE);
                phoneIcon.setVisibility(View.GONE);
                validationMessage.setText("");
                adjustCredInputPadding();
            }
        });
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
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "Full name input changed: " + s.toString());
            }
        });

        final boolean[] isTextChanging = {false};

        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isTextChanging[0]) return;
                isTextChanging[0] = true;
                resetInputBorders();
                adjustCredInputPadding();
                String input = s.toString().trim();
                Log.d(TAG, "Credentials input changed: " + input);
                if (input.isEmpty()) {
                    validationMessage.setText("");
                } else if (isModeLocked) {
                    if (isPhoneInput) {
                        String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                        boolean isValid = AuthUtils.isValidPhoneNumber(normalized);
                        validationMessage.setText(isValid ? "Valid phone number" : "Invalid phone number");
                        validationMessage.setTextColor(ContextCompat.getColor(SignupActivity.this,
                                isValid ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
                        Log.d(TAG, "Phone validation: " + normalized + ", valid: " + isValid);
                    } else {
                        boolean isValid = AuthUtils.isValidEmail(input);
                        validationMessage.setText(isValid ? "Valid email" : "Invalid email format");
                        validationMessage.setTextColor(ContextCompat.getColor(SignupActivity.this,
                                isValid ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
                        Log.d(TAG, "Email validation: " + input + ", valid: " + isValid);
                    }
                }
                isTextChanging[0] = false;
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
                Log.d(TAG, "Password input length: " + s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void adjustCredInputPadding() {
        ccp.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!isFinishing()) {
                int paddingStart = ccp.getVisibility() == View.VISIBLE ?
                        ccp.getWidth() + (int) (10 * getResources().getDisplayMetrics().density) :
                        (int) (10 * getResources().getDisplayMetrics().density);
                credInput.setPadding(paddingStart, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
                Log.d(TAG, "Adjusted credInput paddingStart: " + paddingStart);
            }
        });
    }

    private void handleSignup() {
        String fullName = fullNameInput.getText().toString().trim();
        String credentials = credInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        Log.d(TAG, "handleSignup called with fullName: " + fullName + ", credentials: " + credentials + ", password length: " + password.length());

        if (!validateInputs(fullName, credentials, password)) {
            Log.w(TAG, "Input validation failed");
            return;
        }

        if (!isModeLocked) {
            Log.w(TAG, "No input mode selected");
            showCustomToast("Please select Email or Phone", false);
            return;
        }

        String emailOrPhone = isPhoneInput ?
                AuthUtils.normalizePhoneNumberForBackend(credentials, ccp, null) :
                credentials;
        String signInMethod = isPhoneInput ? "phone" : "email";
        Log.d(TAG, "Sign-in method: " + signInMethod + ", value: " + emailOrPhone);

        if (signInMethod.equals("phone") && !AuthUtils.isValidPhoneNumber(emailOrPhone)) {
            Log.w(TAG, "Invalid phone number: " + emailOrPhone);
            setErrorBorder(credInput);
            showCustomToast("Invalid phone number", false);
            return;
        } else if (signInMethod.equals("email") && !AuthUtils.isValidEmail(emailOrPhone)) {
            Log.w(TAG, "Invalid email: " + emailOrPhone);
            setErrorBorder(credInput);
            showCustomToast("Invalid email format", false);
            return;
        }

        String syntheticEmail = signInMethod.equals("email") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";
        Log.d(TAG, "Synthetic email for auth: " + syntheticEmail);

        showLoader();
        setUiEnabled(false);

        // Check if email or phone already exists
        String docId = signInMethod.equals("email") ? emailOrPhone.replace(".", "_") : emailOrPhone;
        Log.d(TAG, "Checking existence of " + signInMethod + " with docId: " + docId);
        firestore.collection(signInMethod.equals("email") ? "emails" : "phoneNumbers")
                .document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.w(TAG, signInMethod + " already exists: " + docId);
                        hideLoader();
                        setUiEnabled(true);
                        showCustomToast(signInMethod.equals("email") ? "Email already in use" : "Phone number already in use", false);
                    } else {
                        Log.d(TAG, signInMethod + " is available: " + docId);
                        AuthUtils.generateUniqueUsername(fullName, firestore.collection("usernames"), username -> {
                            Log.d(TAG, "Generated username: " + username);
                            mAuth.createUserWithEmailAndPassword(syntheticEmail, password)
                                    .addOnCompleteListener(SignupActivity.this, task -> {
                                        if (task.isSuccessful()) {
                                            FirebaseUser user = mAuth.getCurrentUser();
                                            if (user != null) {
                                                Log.d(TAG, "Auth successful, UID: " + user.getUid());
                                                saveUserData(user, fullName, emailOrPhone, signInMethod, username);
                                            } else {
                                                Log.e(TAG, "FirebaseUser is null after auth");
                                                hideLoader();
                                                setUiEnabled(true);
                                                showCustomToast("Signup failed: User is null", false);
                                            }
                                        } else {
                                            Log.e(TAG, "Auth failed: " + task.getException().getMessage(), task.getException());
                                            hideLoader();
                                            setUiEnabled(true);
                                            showCustomToast("Signup failed: " + task.getException().getMessage(), false);
                                        }
                                    });
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking credentials: " + e.getMessage(), e);
                    hideLoader();
                    setUiEnabled(true);
                    showCustomToast("Error checking credentials: " + e.getMessage(), false);
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
        Log.d(TAG, "Initiating GitHub Sign-In");
        showLoader();
        setUiEnabled(false);
        AuthUtils.firebaseAuthWithGitHub(this, "YOUR_GITHUB_CLIENT_ID", firestore, new AuthUtils.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                Log.d(TAG, "GitHub auth success for UID: " + user.getUid());
                handleSocialMediaSuccess(user, "GitHub");
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "GitHub auth failed: " + errorMessage);
                handleSocialMediaFailure("GitHub", errorMessage);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Log.d(TAG, "Google Sign-In result received");
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google Sign-In account: " + account.getEmail());
                showLoader();
                setUiEnabled(false);
                AuthUtils.firebaseAuthWithGoogle(this, account.getIdToken(), firestore, new AuthUtils.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        Log.d(TAG, "Google auth success for UID: " + user.getUid());
                        handleSocialMediaSuccess(user, "Google");
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Google auth failed: " + errorMessage);
                        handleSocialMediaFailure("Google", errorMessage);
                    }
                });
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed: " + e.getMessage(), e);
                handleSocialMediaFailure("Google", "Sign-In failed: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "Passing activity result to Facebook callback");
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void togglePasswordVisibility() {
        Log.d(TAG, "Toggling password visibility, current state: " + isPasswordVisible);
        passwordInput.setTransformationMethod(isPasswordVisible ? PasswordTransformationMethod.getInstance() : SingleLineTransformationMethod.getInstance());
        passwordToggle.setImageResource(isPasswordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye_on);
        isPasswordVisible = !isPasswordVisible;
        passwordInput.setSelection(passwordInput.getText().length());
    }

    private void setUiEnabled(boolean enabled) {
        fullNameInput.setEnabled(enabled);
        credInput.setEnabled(enabled);
        passwordInput.setEnabled(enabled);
        inputTypeGroup.setEnabled(enabled);
        emailRadio.setEnabled(enabled);
        phoneRadio.setEnabled(enabled);
        findViewById(R.id.signupBtn).setEnabled(enabled);
        loginText.setEnabled(enabled);
        passwordToggle.setEnabled(enabled);
        googleLogin.setEnabled(enabled);
        facebookLogin.setEnabled(enabled);
        githubLogin.setEnabled(enabled);
        Log.d(TAG, "UI enabled: " + enabled);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        Log.d(TAG, "Showing toast: " + message + ", success: " + isSuccess);
        Toast toast = new Toast(this);
        View toastView = LayoutInflater.from(this).inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(toastView);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
        Log.d(TAG, "Set error border on: " + editText.getId());
    }

    private void resetInputBorders() {
        fullNameInput.setBackgroundResource(R.drawable.edittext_bg);
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
        Log.d(TAG, "Reset input borders");
    }

    private boolean validateInputs(String fullName, String credentials, String password) {
        resetInputBorders();
        if (fullName.isEmpty() || credentials.isEmpty() || password.isEmpty()) {
            if (fullName.isEmpty()) setErrorBorder(fullNameInput);
            if (credentials.isEmpty()) setErrorBorder(credInput);
            if (password.isEmpty()) setErrorBorder(passwordInput);
            Log.w(TAG, "Validation failed: Empty fields");
            showCustomToast("All fields are required", false);
            return false;
        }
        if (password.length() < 6) {
            setErrorBorder(passwordInput);
            Log.w(TAG, "Validation failed: Password too short");
            showCustomToast("Password must be at least 6 characters", false);
            return false;
        }
        Log.d(TAG, "Inputs validated successfully");
        return true;
    }

    private void saveUserData(FirebaseUser user, String fullName, String emailOrPhone, String signInMethod, String username) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("email", signInMethod.equals("email") ? emailOrPhone : "");
        userData.put("phoneNumber", signInMethod.equals("phone") ? emailOrPhone : "");
        userData.put("username", username);
        userData.put("signInMethod", signInMethod);
        userData.put("role", "customer");
        userData.put("imageUrl", "");
        userData.put("gender", "Not specified");
        userData.put("birthday", "01-01-2000");

        Map<String, Object> uniqueData = new HashMap<>();
        uniqueData.put("uid", user.getUid());

        Log.d(TAG, "Saving user data for UID: " + user.getUid() + ", method: " + signInMethod + ", username: " + username);
        Log.d(TAG, "User data: " + userData.toString());

        firestore.runTransaction(transaction -> {
            Log.d(TAG, "Starting transaction for UID: " + user.getUid());
            DocumentReference userRef = firestore.collection("users").document(user.getUid());
            transaction.set(userRef, userData);
            Log.d(TAG, "Set /users/" + user.getUid());

            if (signInMethod.equals("email") && !emailOrPhone.isEmpty()) {
                DocumentReference emailRef = firestore.collection("emails").document(emailOrPhone.replace(".", "_"));
                transaction.set(emailRef, uniqueData);
                Log.d(TAG, "Set /emails/" + emailOrPhone.replace(".", "_"));
            } else if (signInMethod.equals("phone") && !emailOrPhone.isEmpty()) {
                DocumentReference phoneRef = firestore.collection("phoneNumbers").document(emailOrPhone);
                transaction.set(phoneRef, uniqueData);
                Log.d(TAG, "Set /phoneNumbers/" + emailOrPhone);
            } else {
                Log.w(TAG, "No email or phone provided for indexing");
            }

            DocumentReference usernameRef = firestore.collection("usernames").document(username);
            transaction.set(usernameRef, uniqueData);
            Log.d(TAG, "Set /usernames/" + username);

            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Transaction succeeded");
            hideLoader();
            setUiEnabled(true);
            showCustomToast("Signup successful!", true);
            Intent intent = new Intent(SignupActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Transaction failed: " + e.getMessage(), e);
            hideLoader();
            setUiEnabled(true);
            String errorMsg = e.getMessage() != null && e.getMessage().contains("PERMISSION_DENIED") ?
                    (signInMethod.equals("email") ? "Email already in use" : "Phone number already in use") :
                    "Signup failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
            showCustomToast(errorMsg, false);
            user.delete().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Deleted failed user from Firebase Auth");
                } else {
                    Log.e(TAG, "Failed to delete user: " + task.getException().getMessage());
                }
            });
        });
    }

    private void handleSocialMediaSuccess(FirebaseUser user, String provider) {
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, provider + " signup failed: No current user");
            showCustomToast(provider + " signup failed: Please try again", false);
            hideLoader();
            setUiEnabled(true);
            return;
        }
        String fullName = user.getDisplayName() != null ? user.getDisplayName() : "Unknown";
        String email = user.getEmail() != null ? user.getEmail() : "";
        String phone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";
        String username = email.split("@")[0];
        Log.d(TAG, provider + " signup success, UID: " + user.getUid() + ", email: " + email);
        AuthUtils.generateUniqueUsername(fullName, firestore.collection("usernames"), uniqueUsername -> {
            Log.d(TAG, "Social media username generated: " + uniqueUsername);
            saveUserData(user, fullName, email, "email", uniqueUsername);
        });
    }

    private void handleSocialMediaFailure(String provider, String errorMessage) {
        Log.e(TAG, provider + " signup failed: " + errorMessage);
        hideLoader();
        setUiEnabled(true);
        showCustomToast(provider + " signup failed: " + errorMessage, false);
    }

    private void showLoader() {
        if (!isFinishing()) {
            SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                    .setWidthDp(40)
                    .setHeightDp(40)
                    .setUseRoundedBox(true)
                    .setOverlayColor(Color.parseColor("#80000000"))
                    .setChangeJsonColor(false);
            sltLoader.showCustomLoader(config);
            Log.d(TAG, "Loader shown");
        }
    }

    private void hideLoader() {
        if (sltLoader != null && !isFinishing()) {
            sltLoader.hideLoader();
            Log.d(TAG, "Loader hidden");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleSignInClient != null && !isFinishing()) {
            mGoogleSignInClient.signOut();
            Log.d(TAG, "Google Sign-In client signed out");
        }
        if (sltLoader != null) {
            sltLoader.onDestroy();
            sltLoader = null;
            Log.d(TAG, "SLTLoader destroyed");
        }
        if (ccp != null && credInput != null) {
            ccp.deregisterCarrierNumberEditText();
            Log.d(TAG, "CCP deregistered from credInput");
        }
    }
}