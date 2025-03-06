package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.hbb20.CountryCodePicker;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class ForgotPassActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassActivity";
    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private EditText credInput;
    private Button resetBtn;
    private LottieAnimationView loadingSpinner;
    private TextView backToLoginText, validationMessage;
    private ImageView emailIcon, phoneIcon, googleLogin, facebookLogin, githubLogin;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager callbackManager;
    private boolean isPhoneInput = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgotpass);

        mAuth = FirebaseAuth.getInstance();

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
        credInput = findViewById(R.id.credInput);
        resetBtn = findViewById(R.id.resetBtn);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        backToLoginText = findViewById(R.id.backToLoginText);
        validationMessage = findViewById(R.id.validationMessage);
        emailIcon = findViewById(R.id.emailIcon);
        phoneIcon = findViewById(R.id.phoneIcon);
        googleLogin = findViewById(R.id.googleLogin);
        facebookLogin = findViewById(R.id.facebookLogin);
        githubLogin = findViewById(R.id.githubLogin);
        ccp = findViewById(R.id.ccp);

        // Set default country based on device locale, restricted to BD, MY, SG
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("MY"); // Default to Malaysia for testing

        // Attempt to fetch the SIM number
        AuthInputHandler.fetchSimNumber(this, credInput);

        // Setup dynamic input and adjust padding
        AuthInputHandler.setupDynamicInput(this, credInput, ccp, emailIcon, phoneIcon, validationMessage);
        adjustCredInputPadding();

        // TextWatcher for border reset and phone detection
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetInputBorders();
                adjustCredInputPadding();
                String input = s.toString().trim();
                String cleanedInput = input.replaceAll("[^0-9+]", "");
                isPhoneInput = !input.contains("@") && AuthInputHandler.isValidPhoneNumber(cleanedInput);
                Log.d(TAG, "Input: " + input + ", isPhoneInput: " + isPhoneInput);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Reset button logic
        resetBtn.setOnClickListener(v -> {
            String input = credInput.getText().toString().trim();
            resetInputBorders();

            if (input.isEmpty()) {
                showCustomToast("Please enter an email or phone number.", false);
                setErrorBorder(credInput);
                validationMessage.setText("Input cannot be empty");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            }

            String emailOrPhone;
            if (AuthInputHandler.isValidEmail(input)) {
                emailOrPhone = input;
            } else if (isPhoneInput) {
                String cleanedInput = input.replaceAll("[^0-9+]", "");
                emailOrPhone = AuthInputHandler.normalizePhoneNumber(cleanedInput, ccp);
                Log.d(TAG, "Input: " + input + ", Cleaned: " + cleanedInput + ", Normalized: " + emailOrPhone + ", CCP: " + ccp.getSelectedCountryCodeWithPlus());
                if (!emailOrPhone.matches("\\+[0-9]{10,13}")) {
                    showCustomToast("Invalid phone number format. Please use a valid number (e.g., +60123456789).", false);
                    setErrorBorder(credInput);
                    validationMessage.setText("Invalid phone number format");
                    validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    return;
                }
            } else {
                showCustomToast("Invalid email or phone number format", false);
                setErrorBorder(credInput);
                validationMessage.setText("Invalid email or phone number format");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            }

            String emailToCheck = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";
            Log.d(TAG, "Checking user existence with email: " + emailToCheck);
            checkUserExists(emailToCheck, emailOrPhone, isPhoneInput);
        });

        backToLoginText.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        if (googleLogin != null) {
            googleLogin.setOnClickListener(v -> signInWithGoogle());
        }
        if (facebookLogin != null) {
            facebookLogin.setOnClickListener(v -> signInWithFacebook());
        }
        if (githubLogin != null) {
            githubLogin.setOnClickListener(v -> signInWithGitHub());
        }
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

    private void checkUserExists(String emailToCheck, String originalInput, boolean isPhone) {
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.fetchSignInMethodsForEmail(emailToCheck)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        if (task.getResult().getSignInMethods() != null && !task.getResult().getSignInMethods().isEmpty()) {
                            if (isPhone) {
                                sendOtpToPhone(originalInput);
                            } else {
                                sendEmailResetLink(originalInput);
                            }
                        } else {
                            showCustomToast(isPhone ? "This phone number is not registered." : "This email is not registered.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText(isPhone ? "Phone number not found" : "Email not found");
                            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Network error");
                        } else if (errorMessage.contains("badly formatted")) {
                            showCustomToast("Invalid format. Please use a valid email or phone number.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Invalid format");
                        } else {
                            showCustomToast("Error checking user: " + errorMessage, false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Error checking user");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendEmailResetLink(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        resetInputBorders();
                        showCustomToast("Reset link sent to " + email, true);
                        Intent intent = new Intent(this, OtpVerificationActivity.class);
                        intent.putExtra("credentials", email);
                        intent.putExtra("isReset", true);
                        intent.putExtra("isEmailReset", true);
                        startActivity(intent);
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Network error");
                        } else {
                            showCustomToast("Failed to send reset link: " + errorMessage, false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Failed to send reset link");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendOtpToPhone(String phoneNumber) {
        if (!phoneNumber.matches("\\+[0-9]{10,13}")) {
            Log.e(TAG, "Invalid phone format: " + phoneNumber);
            showCustomToast("Invalid phone number format. Must be in E.164 (e.g., +60123456789).", false);
            setErrorBorder(credInput);
            validationMessage.setText("Invalid phone format");
            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
            return;
        }

        Log.d(TAG, "Sending OTP to: " + phoneNumber);
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        resetInputBorders();
                        showCustomToast("Phone verified automatically", true);
                        Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("credentials", phoneNumber);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                        Log.e(TAG, "OTP sending failed: " + errorMessage);
                        if (e instanceof FirebaseAuthException) {
                            String errorCode = ((FirebaseAuthException) e).getErrorCode();
                            if (errorCode.equals("ERROR_INVALID_PHONE_NUMBER")) {
                                showCustomToast("Invalid phone number format. Please check the number.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("Invalid phone number format");
                            } else if (errorCode.equals("ERROR_INVALID_CREDENTIALS") || errorCode.equals("ERROR_USER_NOT_FOUND")) {
                                showCustomToast("This phone number is not registered. Please sign up first.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("Phone number not found");
                            } else if (errorCode.equals("ERROR_TOO_MANY_REQUESTS")) {
                                showCustomToast("Too many requests. Please try again later.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("Request limit reached");
                            } else if (errorMessage.contains("not authorized")) {
                                showCustomToast("App not authorized for Firebase Authentication. Contact support.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("App authorization error");
                            } else if (errorMessage.contains("quota")) {
                                showCustomToast("SMS quota exceeded. Please try again later.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("SMS quota exceeded");
                            } else if (errorMessage.contains("blocked")) {
                                showCustomToast("SMS sending is blocked for this country. Contact support.", false);
                                setErrorBorder(credInput);
                                validationMessage.setText("SMS blocked for country");
                            } else {
                                showCustomToast("Failed to send OTP: " + errorMessage, false);
                                setErrorBorder(credInput);
                                validationMessage.setText("OTP sending failed");
                            }
                        } else if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            setErrorBorder(credInput);
                            validationMessage.setText("Network error");
                        } else {
                            showCustomToast("Failed to send OTP: " + errorMessage, false);
                            setErrorBorder(credInput);
                            validationMessage.setText("OTP sending failed");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        resetInputBorders();
                        showCustomToast("OTP sent to " + phoneNumber, true);
                        Intent intent = new Intent(ForgotPassActivity.this, OtpVerificationActivity.class);
                        intent.putExtra("credentials", phoneNumber);
                        intent.putExtra("verificationId", verificationId);
                        intent.putExtra("isReset", true);
                        startActivity(intent);
                        finish();
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void setUiEnabled(boolean enabled) {
        credInput.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
        backToLoginText.setEnabled(enabled);
        if (googleLogin != null) googleLogin.setEnabled(enabled);
        if (facebookLogin != null) facebookLogin.setEnabled(enabled);
        if (githubLogin != null) githubLogin.setEnabled(enabled);
    }

    private void showCustomToast(String message, boolean isSuccess) {
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

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            showCustomToast("Signed in with Google. You can now reset your password.", true);
                            Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                            intent.putExtra("credentials", user.getEmail());
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                        } else {
                            showCustomToast("Google Sign-In failed: " + errorMessage, false);
                        }
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
                            showCustomToast("Signed in with Facebook. You can now reset your password.", true);
                            Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                            intent.putExtra("credentials", user.getEmail());
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                        } else {
                            showCustomToast("Facebook Sign-In failed: " + errorMessage, false);
                        }
                    }
                });
    }

    private void signInWithGitHub() {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com");
        provider.addCustomParameter("client_id", "YOUR_GITHUB_CLIENT_ID");
        provider.setScopes(Arrays.asList("user:email"));

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener(authResult -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        showCustomToast("Signed in with GitHub. You can now reset your password.", true);
                        Intent intent = new Intent(ForgotPassActivity.this, ResetPasswordActivity.class);
                        intent.putExtra("credentials", user.getEmail());
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    Log.e(TAG, "GitHub Sign-In failed: " + errorMessage);
                    if (errorMessage.contains("network")) {
                        showCustomToast("Network error. Please check your internet connection.", false);
                    } else {
                        showCustomToast("GitHub Sign-In failed: " + errorMessage, false);
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
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                showCustomToast("Google Sign-In failed: " + e.getMessage(), false);
            }
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        credInput.setBackgroundResource(R.drawable.edittext_bg);
    }
}