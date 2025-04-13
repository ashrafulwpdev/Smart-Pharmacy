package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;
import com.oopgroup.smartpharmacy.utils.AuthUtils;
import com.softourtech.slt.SLTLoader;

import java.util.concurrent.TimeUnit;

public class ForgotPassActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassActivity";
    private EditText credInput;
    private Button resetBtn;
    private TextView backToLoginText, validationMessage;
    private ImageView emailIcon, phoneIcon;
    private CountryCodePicker ccp;
    private RadioGroup inputTypeGroup;
    private RadioButton emailRadio, phoneRadio;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private boolean isPhoneInput = false;
    private boolean isTextChanging = false;
    private boolean isModeLocked = false;
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
                    showCustomToast("Permission denied. SIM number detection unavailable.", false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgotpass);

        // Initialize App Check
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
        Log.d(TAG, "App Check initialized with Play Integrity");

        // Initialize SLTLoader
        View activityRoot = findViewById(android.R.id.content);
        if (activityRoot == null || !(activityRoot instanceof ViewGroup)) {
            Log.e(TAG, "Activity root view not found or not a ViewGroup");
            return;
        }
        sltLoader = new SLTLoader(this, (ViewGroup) activityRoot);
        Log.d(TAG, "SLTLoader initialized");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        Log.d(TAG, "Firebase initialized");

        // Initialize UI elements
        credInput = findViewById(R.id.credInput);
        resetBtn = findViewById(R.id.resetBtn);
        backToLoginText = findViewById(R.id.backToLoginText);
        validationMessage = findViewById(R.id.validationMessage);
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        ccp = findViewById(R.id.ccp);
        inputTypeGroup = findViewById(R.id.inputTypeGroup);
        emailRadio = findViewById(R.id.emailRadio);
        phoneRadio = findViewById(R.id.phoneRadio);
        Log.d(TAG, "UI components initialized");

        // Configure CCP
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("MY");
        ccp.setNumberAutoFormattingEnabled(false);
        Log.d(TAG, "CCP configured: default=MY");

        // Start SMS Retriever
        startSmsRetriever();

        // Setup dynamic input
        setupDynamicInput();
        adjustCredInputPadding();

        // Radio button listener
        inputTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.emailRadio) {
                isPhoneInput = false;
                isModeLocked = true;
                credInput.setText("");
                credInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                ccp.setVisibility(View.GONE);
                emailIcon.setVisibility(View.VISIBLE);
                phoneIcon.setVisibility(View.GONE);
                validationMessage.setText("");
                adjustCredInputPadding();
                Log.d(TAG, "Mode locked to email");
            } else if (checkedId == R.id.phoneRadio) {
                isPhoneInput = true;
                isModeLocked = true;
                credInput.setText("");
                credInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                ccp.setVisibility(View.VISIBLE);
                emailIcon.setVisibility(View.GONE);
                phoneIcon.setVisibility(View.VISIBLE);
                validationMessage.setText("");
                adjustCredInputPadding();
                Log.d(TAG, "Mode locked to phone");
            } else {
                isModeLocked = false;
                setupDynamicInput();
                Log.d(TAG, "Mode unlocked, dynamic switching enabled");
            }
        });

        // TextWatcher for input validation
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isTextChanging) return;
                isTextChanging = true;
                resetInputBorders();
                adjustCredInputPadding();
                validationMessage.setText("");
                String input = s.toString().trim();
                if (!input.isEmpty()) {
                    if (isPhoneInput) {
                        String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                        if (!AuthUtils.isValidPhoneNumber(normalized)) {
                            validationMessage.setText("Invalid phone format");
                            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                        }
                    } else if (!AuthUtils.isValidEmail(input)) {
                        validationMessage.setText("Invalid email format");
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }
                }
                isTextChanging = false;
            }

            @Override
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "Input changed: " + s.toString());
            }
        });

        // Reset button logic
        resetBtn.setOnClickListener(v -> {
            String input = credInput.getText().toString().trim();
            resetInputBorders();
            validationMessage.setText("");
            Log.d(TAG, "Reset button clicked, raw input: " + input);

            if (input.isEmpty()) {
                showCustomToast("Please enter an email or phone number.", false);
                setErrorBorder(credInput);
                validationMessage.setText("Input cannot be empty");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            }

            String emailOrPhone = isPhoneInput ?
                    AuthUtils.normalizePhoneNumberForBackend(input, ccp, null) : input;
            Log.d(TAG, "Processed input: emailOrPhone=" + emailOrPhone + ", isPhoneInput=" + isPhoneInput);

            if (isPhoneInput && !AuthUtils.isValidPhoneNumber(emailOrPhone)) {
                showCustomToast("Invalid phone number format (e.g., +60123456789)", false);
                setErrorBorder(credInput);
                validationMessage.setText("Invalid phone number format");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            } else if (!isPhoneInput && !AuthUtils.isValidEmail(emailOrPhone)) {
                showCustomToast("Invalid email format", false);
                setErrorBorder(credInput);
                validationMessage.setText("Invalid email format");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            }

            checkUserExists(emailOrPhone, isPhoneInput);
        });

        // Back to login
        backToLoginText.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to LoginActivity");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Request phone state permission
        requestPhoneStatePermission();
    }

    private void startSmsRetriever() {
        SmsRetrieverClient client = SmsRetriever.getClient(this);
        Task<Void> task = client.startSmsRetriever();
        task.addOnSuccessListener(aVoid -> Log.d(TAG, "SMS Retriever started successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to start SMS Retriever: " + e.getMessage(), e));
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

    private void setupDynamicInput() {
        AuthUtils.setupDynamicInput(this, credInput, ccp, emailIcon, phoneIcon, validationMessage, isPhone -> {
            if (!isModeLocked) {
                isPhoneInput = isPhone;
                credInput.setInputType(isPhone ?
                        android.text.InputType.TYPE_CLASS_NUMBER :
                        (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
                ccp.setVisibility(isPhone ? View.VISIBLE : View.GONE);
                emailIcon.setVisibility(isPhone ? View.GONE : View.VISIBLE);
                phoneIcon.setVisibility(isPhone ? View.VISIBLE : View.GONE);
                adjustCredInputPadding();
                Log.d(TAG, "Dynamic input type changed: isPhone=" + isPhone);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sltLoader != null) {
            sltLoader.onDestroy();
            Log.d(TAG, "SLTLoader destroyed");
        }
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
                Log.d(TAG, "Adjusted credInput paddingStart: " + paddingStart);
            }
        });
    }

    private void checkUserExists(String emailOrPhone, boolean isPhone) {
        showLoader();
        setUiEnabled(false);
        String docId = isPhone ? emailOrPhone : emailOrPhone.replace(".", "_");
        String collection = isPhone ? "phoneNumbers" : "emails";
        Log.d(TAG, "Checking credential in Firestore: collection=" + collection + ", docId=" + docId + ", emailOrPhone=" + emailOrPhone);

        firestore.collection(collection)
                .document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "Credential found in Firestore: " + emailOrPhone);
                        String authEmail = isPhone ? emailOrPhone + "@smartpharmacy.com" : emailOrPhone;
                        verifyFirebaseAuth(authEmail, emailOrPhone, isPhone);
                    } else {
                        Log.w(TAG, "Credential not found in Firestore: " + emailOrPhone);
                        hideLoader();
                        setUiEnabled(true);
                        showCustomToast(isPhone ? "This phone number is not registered." : "This email is not registered.", false);
                        setErrorBorder(credInput);
                        validationMessage.setText(isPhone ? "Phone number not found" : "Email not found");
                        validationMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoader();
                    setUiEnabled(true);
                    Log.e(TAG, "Error checking Firestore credential: " + emailOrPhone + ", error: " + e.getMessage(), e);
                    showCustomToast("Error verifying credential: " + e.getMessage(), false);
                    validationMessage.setText("Verification failed");
                    validationMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                });
    }

    private void verifyFirebaseAuth(String authEmail, String originalInput, boolean isPhone) {
        showLoader();
        Log.d(TAG, "Verifying Firebase Auth for: authEmail=" + authEmail + ", originalInput=" + originalInput);
        mAuth.fetchSignInMethodsForEmail(authEmail)
                .addOnCompleteListener(task -> {
                    hideLoader();
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        if (task.getResult().getSignInMethods() != null && !task.getResult().getSignInMethods().isEmpty()) {
                            Log.d(TAG, "Firebase Auth user exists: " + authEmail);
                            if (isPhone) {
                                sendOtpToPhone(originalInput);
                            } else {
                                sendEmailResetLink(originalInput);
                            }
                        } else {
                            Log.w(TAG, "No Firebase Auth user found: " + authEmail);
                            showCustomToast(isPhone ? "This phone number is not registered." : "This email is not registered.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText(isPhone ? "Phone number not found" : "Email not found");
                            validationMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                        }
                    } else {
                        Log.e(TAG, "Firebase Auth check failed for: " + authEmail + ", error: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), task.getException());
                        showCustomToast("Error verifying credential: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), false);
                        validationMessage.setText("Verification failed");
                        validationMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendEmailResetLink(String email) {
        showLoader();
        Log.d(TAG, "Sending password reset email to: " + email);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    hideLoader();
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Reset email sent successfully to: " + email);
                        resetInputBorders();
                        showCustomToast("Reset link sent to " + email, true);
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    } else {
                        Log.e(TAG, "Failed to send reset email to: " + email + ", error: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), task.getException());
                        setErrorBorder(credInput);
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        showCustomToast("Failed to send reset link: " + errorMessage, false);
                        validationMessage.setText("Failed to send reset link");
                        validationMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendOtpToPhone(String phoneNumber) {
        showLoader();
        Log.d(TAG, "Initiating OTP request for phone number: " + phoneNumber);
        if (!phoneNumber.matches("\\+\\d{10,15}")) {
            hideLoader();
            setUiEnabled(true);
            setErrorBorder(credInput);
            showCustomToast("Invalid phone number format. Must be E.164 (e.g., +60123456789)", false);
            validationMessage.setText("Invalid phone number format");
            validationMessage.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            Log.w(TAG, "Invalid phone number format rejected: " + phoneNumber);
            return;
        }

        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        hideLoader();
                        setUiEnabled(true);
                        resetInputBorders();
                        Log.d(TAG, "Phone verification completed automatically for: " + phoneNumber);
                        showCustomToast("Phone verified automatically", true);
                        Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("credentials", phoneNumber);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        hideLoader();
                        setUiEnabled(true);
                        setErrorBorder(credInput);
                        String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                        Log.e(TAG, "OTP verification failed for: " + phoneNumber + ", error: " + errorMessage, e);
                        String toastMessage;
                        String validationText;
                        if (e instanceof FirebaseAuthException) {
                            String errorCode = ((FirebaseAuthException) e).getErrorCode();
                            switch (errorCode) {
                                case "ERROR_INVALID_PHONE_NUMBER":
                                    toastMessage = "Invalid phone number format.";
                                    validationText = "Invalid phone number format";
                                    break;
                                case "ERROR_TOO_MANY_REQUESTS":
                                    toastMessage = "Too many requests. Try again later.";
                                    validationText = "Request limit reached";
                                    break;
                                case "ERROR_QUOTA_EXCEEDED":
                                    toastMessage = "SMS quota exceeded. Try again later.";
                                    validationText = "SMS quota exceeded";
                                    break;
                                case "ERROR_INTERNAL_ERROR":
                                    toastMessage = "Internal error, possibly reCAPTCHA failure. Please try again.";
                                    validationText = "Internal error";
                                    break;
                                case "ERROR_RECAPTCHA_NOT_ENABLED":
                                    toastMessage = "reCAPTCHA verification required. Please try again.";
                                    validationText = "reCAPTCHA required";
                                    break;
                                default:
                                    toastMessage = "Failed to send OTP: " + errorMessage;
                                    validationText = "OTP sending failed";
                                    break;
                            }
                        } else if (errorMessage.contains("network")) {
                            toastMessage = "Network error. Check your connection.";
                            validationText = "Network error";
                        } else {
                            toastMessage = "Failed to send OTP: " + errorMessage;
                            validationText = "OTP sending failed";
                        }
                        showCustomToast(toastMessage, false);
                        validationMessage.setText(validationText);
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        hideLoader();
                        setUiEnabled(true);
                        resetInputBorders();
                        Log.d(TAG, "OTP sent successfully to: " + phoneNumber + ", verificationId: " + verificationId);
                        showCustomToast("OTP sent to " + phoneNumber, true);
                        Intent intent = new Intent(ForgotPassActivity.this, OtpVerificationActivity.class);
                        intent.putExtra("credentials", phoneNumber);
                        intent.putExtra("verificationId", verificationId);
                        intent.putExtra("isReset", true);
                        startActivity(intent);
                        finish();
                    }
                });

        // Add reCAPTCHA fallback
        builder.setForceResendingToken(null);
        Log.d(TAG, "Sending OTP with App Check for: " + phoneNumber);
        PhoneAuthProvider.verifyPhoneNumber(builder.build());
    }

    private void setUiEnabled(boolean enabled) {
        credInput.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
        backToLoginText.setEnabled(enabled);
        inputTypeGroup.setEnabled(enabled);
        emailRadio.setEnabled(enabled);
        phoneRadio.setEnabled(enabled);
        Log.d(TAG, "UI enabled: " + enabled);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        Log.d(TAG, "Toast: " + message + ", success: " + isSuccess);
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
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
        Log.d(TAG, "Error border set on credInput");
    }

    private void resetInputBorders() {
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        Log.d(TAG, "Input borders reset");
    }

    private void showLoader() {
        Log.d(TAG, "Showing loader");
        SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000"))
                .setChangeJsonColor(false);
        sltLoader.showCustomLoader(config);
    }

    private void hideLoader() {
        if (sltLoader != null) {
            sltLoader.hideLoader();
            Log.d(TAG, "Loader hidden");
        }
    }
}