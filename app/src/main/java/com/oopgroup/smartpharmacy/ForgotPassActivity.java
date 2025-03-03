package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.regex.Pattern;

public class ForgotPassActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassActivity";
    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final int REQUEST_PHONE_STATE_PERMISSION = 1001;
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

    // Updated regex for BD, MY, SG with strict length requirements
    // BD: +8801738861411, 8801738861411, 01738861411, 1738861411 (10 digits after +880 or 01)
    // MY: +60123456789, 60123456789, 0123456789 (9-10 digits after +60 or 01)
    // SG: +6581234567, 6581234567, 81234567 (8 digits after +65)
    private static final String PHONE_REGEX = "^((\\+?8801[3-9]\\d{8})|01[3-9]\\d{8}|1[3-9]\\d{8}|(\\+?601[0-9]\\d{7,8})|01\\d{7,8}|(\\+?65[89]\\d{6})|[89]\\d{6})$";
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    private static boolean isValidPhoneNumber(String phone) {
        // Clean the input by removing spaces and non-numeric characters (except +)
        String cleanedPhone = phone.replaceAll("[^0-9+]", "");
        return Pattern.matches(PHONE_REGEX, cleanedPhone);
    }

    private static boolean isValidEmail(String email) {
        return Pattern.matches(EMAIL_REGEX, email) && email.contains(".");
    }

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
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String countryCode = tm.getSimCountryIso().toUpperCase();
        if (countryCode.equals("BD") || countryCode.equals("MY") || countryCode.equals("SG")) {
            ccp.setCountryForNameCode(countryCode);
        } else {
            ccp.setCountryForNameCode("BD");
        }
        ccp.setCustomMasterCountries("BD,MY,SG");

        // Attempt to fetch the SIM number
        fetchSimNumber();

        // TextWatcher for seamless detection
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                credInput.setError(null);

                if (isValidEmail(input)) {
                    isPhoneInput = false;
                    emailIcon.setVisibility(View.VISIBLE);
                    phoneIcon.setVisibility(View.GONE);
                    ccp.setVisibility(View.GONE);
                    validationMessage.setText("Valid email");
                    validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_green_dark));
                } else {
                    // Clean the input for phone number validation
                    String cleanedInput = input.replaceAll("[^0-9+]", "");
                    if (!input.contains("@") && cleanedInput.length() >= 8 && isValidPhoneNumber(cleanedInput)) {
                        isPhoneInput = true;
                        emailIcon.setVisibility(View.GONE);
                        phoneIcon.setVisibility(View.VISIBLE);
                        ccp.setVisibility(View.VISIBLE);
                        updateCountryFlag(cleanedInput);
                        validationMessage.setText("Valid phone number");
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_green_dark));
                    } else {
                        isPhoneInput = false;
                        emailIcon.setVisibility(View.GONE);
                        phoneIcon.setVisibility(View.GONE);
                        ccp.setVisibility(View.GONE);
                        if (!input.isEmpty()) {
                            validationMessage.setText("Invalid format (e.g., +60123456789, 0123456789, +6581234567, or email@example.com)");
                            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                        } else {
                            validationMessage.setText("");
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Reset button logic
        resetBtn.setOnClickListener(v -> {
            String input = credInput.getText().toString().trim();
            if (input.isEmpty()) {
                showCustomToast("Please enter an email or phone number.", false);
                validationMessage.setText("Input cannot be empty");
                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                return;
            }

            // Clean the input for phone number validation
            String cleanedInput = input.replaceAll("[^0-9+]", "");
            if (isPhoneInput && isValidPhoneNumber(cleanedInput)) {
                String normalizedPhone = normalizePhoneNumber(cleanedInput);
                checkUserExists(normalizedPhone, true);
            } else {
                if (cleanedInput.length() >= 8 && !input.contains("@")) {
                    showCustomToast("Invalid phone number format. Please use a valid number (e.g., +60123456789).", false);
                    validationMessage.setText("Invalid phone number format");
                    validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                } else {
                    checkUserExists(input, false);
                }
            }
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

    private void fetchSimNumber() {
        // Check for READ_PHONE_STATE permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    REQUEST_PHONE_STATE_PERMISSION);
        } else {
            retrieveSimNumber();
        }
    }

    private void retrieveSimNumber() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String simNumber = tm.getLine1Number(); // Fetch the phone number from the SIM
            if (simNumber != null && !simNumber.isEmpty()) {
                // Clean the number (remove spaces, non-numeric chars except +)
                simNumber = simNumber.replaceAll("[^0-9+]", "");
                if (isValidPhoneNumber(simNumber)) {
                    credInput.setText(simNumber);
                } else {
                    Log.d(TAG, "SIM number found but invalid: " + simNumber);
                }
            } else {
                Log.d(TAG, "SIM number not available.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied to read SIM number: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error fetching SIM number: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PHONE_STATE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                retrieveSimNumber();
            }
            // Silently ignore if permission is denied
        }
    }

    private String normalizePhoneNumber(String input) {
        String normalized = input.replaceAll("[^0-9+]", ""); // Remove non-numeric chars except +
        String countryCode = ccp.getSelectedCountryCodeWithPlus();

        if (normalized.startsWith("+")) {
            return normalized; // Already in E.164 format, e.g., +8801775267893, +60123456789, +6581234567
        } else if (normalized.startsWith("880")) {
            return "+" + normalized; // BD: 8801775267893 → +8801775267893
        } else if (normalized.startsWith("60")) {
            return "+" + normalized; // MY: 60123456789 → +60123456789
        } else if (normalized.startsWith("65")) {
            return "+" + normalized; // SG: 6581234567 → +6581234567
        } else if (normalized.startsWith("0")) {
            // Local formats: 01775267893 (BD), 0123456789 (MY)
            if (normalized.matches("^01[3-9]\\d{8}$")) {
                return "+880" + normalized.substring(1); // BD: 01775267893 → +8801775267893
            } else if (normalized.matches("^01[0-9]\\d{7,8}$")) {
                return "+60" + normalized.substring(1); // MY: 0123456789 → +60123456789
            }
            return countryCode + normalized.substring(1); // Fallback to CCP
        } else if (normalized.matches("^[1][3-9]\\d{8}$")) {
            return "+880" + normalized; // BD: 1775267893 → +8801775267893
        } else if (normalized.matches("^[89]\\d{6}$")) {
            return "+65" + normalized; // SG: 81234567 → +6581234567
        } else {
            // Fallback: prepend country code from CCP (e.g., 123456789 → +60123456789 if MY selected)
            return countryCode + normalized;
        }
    }

    private void updateCountryFlag(String phoneNumber) {
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        if (normalized.startsWith("+880") || normalized.startsWith("880") || normalized.matches("^01[3-9]\\d{8}$") || normalized.matches("^1[3-9]\\d{8}$")) {
            ccp.setCountryForNameCode("BD");
        } else if (normalized.startsWith("+60") || normalized.startsWith("60") || normalized.matches("^01[0-9]\\d{7,8}$")) {
            ccp.setCountryForNameCode("MY");
        } else if (normalized.startsWith("+65") || normalized.startsWith("65") || normalized.matches("^[89]\\d{6}$")) {
            ccp.setCountryForNameCode("SG");
        }
    }

    private void checkUserExists(String input, boolean isPhone) {
        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        if (isPhone) {
            String normalizedPhone = normalizePhoneNumber(input);
            sendOtpToPhone(normalizedPhone);
        } else {
            mAuth.fetchSignInMethodsForEmail(input)
                    .addOnCompleteListener(task -> {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        if (task.isSuccessful()) {
                            if (task.getResult().getSignInMethods() != null && !task.getResult().getSignInMethods().isEmpty()) {
                                sendEmailResetLink(input);
                            } else {
                                showCustomToast("This email is not registered.", false);
                                validationMessage.setText("Email not found");
                                validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                            }
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            if (errorMessage.contains("network")) {
                                showCustomToast("Network error. Please check your internet connection.", false);
                                validationMessage.setText("Network error");
                            } else if (errorMessage.contains("badly formatted")) {
                                showCustomToast("Invalid email format. Please use a valid email (e.g., user@example.com).", false);
                                validationMessage.setText("Invalid email format");
                            } else {
                                showCustomToast("Error checking email: " + errorMessage, false);
                                validationMessage.setText("Error checking email");
                            }
                            validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                        }
                    });
        }
    }

    private void sendEmailResetLink(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
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
                            validationMessage.setText("Network error");
                        } else {
                            showCustomToast("Failed to send reset link: " + errorMessage, false);
                            validationMessage.setText("Failed to send reset link");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }
                });
    }

    private void sendOtpToPhone(String phoneNumber) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
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
                        if (e instanceof FirebaseAuthException) {
                            String errorCode = ((FirebaseAuthException) e).getErrorCode();
                            if (errorCode.equals("ERROR_INVALID_PHONE_NUMBER")) {
                                showCustomToast("Invalid phone number format. Please check the number.", false);
                                validationMessage.setText("Invalid phone number format");
                            } else if (errorCode.equals("ERROR_INVALID_CREDENTIALS") || errorCode.equals("ERROR_USER_NOT_FOUND")) {
                                showCustomToast("This phone number is not registered. Please sign up first.", false);
                                validationMessage.setText("Phone number not found");
                            } else if (errorCode.equals("ERROR_TOO_MANY_REQUESTS")) {
                                showCustomToast("Too many requests. Please try again later.", false);
                                validationMessage.setText("Request limit reached");
                            } else if (errorMessage.contains("not authorized")) {
                                showCustomToast("App not authorized for Firebase Authentication. Contact support.", false);
                                validationMessage.setText("App authorization error");
                            } else if (errorMessage.contains("quota")) {
                                showCustomToast("SMS quota exceeded. Please try again later.", false);
                                validationMessage.setText("SMS quota exceeded");
                            } else if (errorMessage.contains("blocked")) {
                                showCustomToast("SMS sending is blocked for this country. Contact support.", false);
                                validationMessage.setText("SMS blocked for country");
                            } else {
                                showCustomToast("Failed to send OTP: " + errorMessage, false);
                                validationMessage.setText("OTP sending failed");
                            }
                        } else if (errorMessage.contains("network")) {
                            showCustomToast("Network error. Please check your internet connection.", false);
                            validationMessage.setText("Network error");
                        } else {
                            showCustomToast("Failed to send OTP: " + errorMessage, false);
                            validationMessage.setText("OTP sending failed");
                        }
                        validationMessage.setTextColor(ContextCompat.getColor(ForgotPassActivity.this, android.R.color.holo_red_dark));
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
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
}