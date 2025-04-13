package com.oopgroup.smartpharmacy.fragments;

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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;
import com.oopgroup.smartpharmacy.ForgotPassActivity;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.SignupActivity;
import com.oopgroup.smartpharmacy.adminstaff.AdminMainActivity;
import com.oopgroup.smartpharmacy.utils.AuthUtils;
import com.softourtech.slt.SLTLoader;

public class LoginFragment extends Fragment {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final String TAG = "LoginFragment";

    private EditText credInput, passwordInput;
    private TextView signupText, forgotText, validationMessage;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin, emailIcon, phoneIcon;
    private CountryCodePicker ccp;
    private RadioGroup inputTypeGroup;
    private RadioButton emailRadio, phoneRadio;
    private CheckBox rememberMeCheckbox;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore firestore;
    private boolean isPasswordVisible = false;
    private boolean isPhoneInput = false;
    private boolean isTextChanging = false;
    private boolean isModeLocked = false; // Start in dynamic mode
    private SLTLoader sltLoader;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "READ_PHONE_STATE permission granted, fetching SIM number");
                    isPhoneInput = true;
                    phoneRadio.setChecked(true);
                    isModeLocked = true;
                    AuthUtils.fetchSimNumber(requireActivity(), credInput, ccp);
                } else {
                    Log.w(TAG, "Phone state permission denied");
                    isPhoneInput = false;
                    inputTypeGroup.clearCheck();
                    isModeLocked = false;
                    showCustomToast("Permission denied. SIM number detection unavailable.", false);
                }
            });

    public LoginFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize SLTLoader
        View activityRoot = requireActivity().findViewById(android.R.id.content);
        sltLoader = new SLTLoader(requireContext(), activityRoot instanceof ViewGroup ? (ViewGroup) activityRoot : (ViewGroup) view);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

        // Initialize UI elements
        credInput = view.findViewById(R.id.credInput);
        passwordInput = view.findViewById(R.id.passwordInput);
        signupText = view.findViewById(R.id.signupText);
        forgotText = view.findViewById(R.id.forgotText);
        passwordToggle = view.findViewById(R.id.passwordToggle);
        googleLogin = view.findViewById(R.id.googleLogin);
        facebookLogin = view.findViewById(R.id.facebookLogin);
        githubLogin = view.findViewById(R.id.githublogin);
        emailIcon = view.findViewById(R.id.emailIcon);
        phoneIcon = view.findViewById(R.id.phoneIcon);
        ccp = view.findViewById(R.id.ccp);
        validationMessage = view.findViewById(R.id.validationMessage);
        inputTypeGroup = view.findViewById(R.id.inputTypeGroup);
        emailRadio = view.findViewById(R.id.emailRadio);
        phoneRadio = view.findViewById(R.id.phoneRadio);
        rememberMeCheckbox = view.findViewById(R.id.rememberMeCheckbox);
        Button loginBtn = view.findViewById(R.id.loginBtn);

        setupCCP();
        setupDynamicInput();
        setupInputValidation();
        setupInputTypeToggle();

        // Initialize UI state for full dynamic mode
        inputTypeGroup.clearCheck();
        isModeLocked = false;
        isPhoneInput = false;
        ccp.setVisibility(View.GONE);
        emailIcon.setVisibility(View.VISIBLE);
        phoneIcon.setVisibility(View.GONE);
        validationMessage.setText("");

        // Event listeners
        loginBtn.setOnClickListener(v -> handleLogin());
        signupText.setOnClickListener(v -> startActivity(new Intent(requireContext(), SignupActivity.class)));
        forgotText.setOnClickListener(v -> handleForgotPassword());
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());

        // Request permission
        requestPhoneStatePermission();
    }

    private void setupCCP() {
        if (ccp != null && credInput != null) {
            ccp.registerCarrierNumberEditText(credInput);
            ccp.setCustomMasterCountries("BD,MY,SG");
            ccp.setNumberAutoFormattingEnabled(false);
            ccp.setCountryForNameCode("MY");
            Log.d(TAG, "CCP registered with credInput, restricted to BD,MY,SG");
        }
    }

    private void requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_PHONE_STATE permission already granted");
            isPhoneInput = true;
            phoneRadio.setChecked(true);
            isModeLocked = true;
            AuthUtils.fetchSimNumber(requireActivity(), credInput, ccp);
        } else {
            Log.d(TAG, "Requesting READ_PHONE_STATE permission");
            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        }
    }

    private void setupDynamicInput() {
        AuthUtils.setupDynamicInput(requireActivity(), credInput, ccp, emailIcon, phoneIcon, validationMessage, isPhone -> {
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

    private void setupInputTypeToggle() {
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
                isPhoneInput = false;
                credInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                ccp.setVisibility(View.GONE);
                emailIcon.setVisibility(View.VISIBLE);
                phoneIcon.setVisibility(View.GONE);
                validationMessage.setText("");
                adjustCredInputPadding();
                Log.d(TAG, "Mode unlocked, dynamic switching enabled");
            }
        });
    }

    private void setupInputValidation() {
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isTextChanging) return;
                isTextChanging = true;
                resetInputBorders();
                adjustCredInputPadding();
                String input = s.toString().trim();
                if (input.isEmpty()) {
                    validationMessage.setText("");
                    if (!isModeLocked) {
                        isPhoneInput = false;
                        ccp.setVisibility(View.GONE);
                        emailIcon.setVisibility(View.VISIBLE);
                        phoneIcon.setVisibility(View.GONE);
                    }
                } else {
                    if (isModeLocked) {
                        if (isPhoneInput) {
                            String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                            boolean isValid = AuthUtils.isValidPhoneNumber(normalized);
                            validationMessage.setText(isValid ? "Valid phone number" : "Invalid phone number");
                            validationMessage.setTextColor(ContextCompat.getColor(requireContext(),
                                    isValid ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
                        } else {
                            boolean isValid = AuthUtils.isValidEmail(input);
                            validationMessage.setText(isValid ? "Valid email" : "Invalid email format");
                            validationMessage.setTextColor(ContextCompat.getColor(requireContext(),
                                    isValid ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
                        }
                    } else {
                        // Dynamic mode validation
                        if (AuthUtils.looksLikeEmail(input)) {
                            boolean isValid = AuthUtils.isValidEmail(input);
                            validationMessage.setText(isValid ? "Valid email" : "Typing email...");
                            validationMessage.setTextColor(ContextCompat.getColor(requireContext(),
                                    isValid ? android.R.color.holo_green_dark : android.R.color.black));
                        } else {
                            String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                            boolean isValid = AuthUtils.isValidPhoneNumber(normalized);
                            validationMessage.setText(isValid ? "Valid phone number" : "Typing phone number...");
                            validationMessage.setTextColor(ContextCompat.getColor(requireContext(),
                                    isValid ? android.R.color.holo_green_dark : android.R.color.black));
                        }
                    }
                }
                isTextChanging = false;
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
        if (ccp != null) {
            ccp.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (isAdded()) {
                    int paddingStart = ccp.getVisibility() == View.VISIBLE ?
                            ccp.getWidth() + (int) (10 * getResources().getDisplayMetrics().density) :
                            (int) (10 * getResources().getDisplayMetrics().density);
                    credInput.setPadding(paddingStart, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
                }
            });
        }
    }

    private void handleLogin() {
        String credentials = credInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        resetInputBorders();
        if (credentials.isEmpty() || password.isEmpty()) {
            if (credentials.isEmpty()) setErrorBorder(credInput);
            if (password.isEmpty()) setErrorBorder(passwordInput);
            showCustomToast("All fields are required", false);
            return;
        }

        String emailOrPhone = isPhoneInput ?
                AuthUtils.normalizePhoneNumberForBackend(credentials, ccp, null) :
                credentials;
        if (isPhoneInput && !AuthUtils.isValidPhoneNumber(emailOrPhone)) {
            setErrorBorder(credInput);
            showCustomToast("Invalid phone number", false);
            return;
        } else if (!isPhoneInput && !AuthUtils.isValidEmail(emailOrPhone)) {
            setErrorBorder(credInput);
            showCustomToast("Invalid email format", false);
            return;
        }

        String loginEmail = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";
        sltLoader.showCustomLoader(new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000")));
        setUiEnabled(false);

        mAuth.signInWithEmailAndPassword(loginEmail, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    sltLoader.hideLoader();
                    setUiEnabled(true);
                    if (task.isSuccessful() && isAdded()) {
                        checkUserRole(mAuth.getCurrentUser());
                    } else if (isAdded()) {
                        showCustomToast("Login failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void checkUserRole(FirebaseUser user) {
        if (!isAdded()) return;
        SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(R.raw.loading_spinner)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000"));
        sltLoader.showCustomLoader(config);

        firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    sltLoader.hideLoader();
                    String role = documentSnapshot.getString("role");
                    if (role == null) {
                        firestore.collection("users").document(user.getUid())
                                .set(new UserRole("customer"), com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(aVoid -> proceedWithRole(user, "customer"))
                                .addOnFailureListener(e -> proceedWithRole(user, "customer"));
                    } else {
                        proceedWithRole(user, role);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    sltLoader.hideLoader();
                    proceedWithRole(user, "customer");
                });
    }

    private void proceedWithRole(FirebaseUser user, String role) {
        Intent intent;
        switch (role) {
            case "admin":
                intent = new Intent(requireContext(), AdminMainActivity.class);
                break;
            case "pharmacist":
            case "delivery_staff":
            case "customer":
            default:
                intent = new Intent(requireContext(), MainActivity.class);
                break;
        }
        showCustomToast("Login successful as " + role + "!", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void handleForgotPassword() {
        startActivity(new Intent(requireContext(), ForgotPassActivity.class));
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(R.raw.loading_spinner)
                        .setWidthDp(40)
                        .setHeightDp(40)
                        .setUseRoundedBox(true)
                        .setOverlayColor(Color.parseColor("#80000000"));
                sltLoader.showCustomLoader(config);
                setUiEnabled(false);
                AuthUtils.firebaseLoginWithGoogle(requireActivity(), account.getIdToken(), new AuthUtils.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        sltLoader.hideLoader();
                        setUiEnabled(true);
                        if (isAdded()) {
                            checkUserRole(user);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        sltLoader.hideLoader();
                        setUiEnabled(true);
                        if (isAdded()) {
                            showCustomToast("Google login failed: " + errorMessage, false);
                        }
                    }
                });
            } catch (ApiException e) {
                sltLoader.hideLoader();
                setUiEnabled(true);
                if (isAdded()) {
                    showCustomToast("Google Sign-In failed: " + e.getMessage(), false);
                }
            }
        }
    }

    private void signInWithFacebook() {
        showCustomToast("Facebook login not implemented", false);
    }

    private void signInWithGitHub() {
        AuthUtils.firebaseAuthWithGitHub(requireActivity(), "your_github_client_id", firestore, new AuthUtils.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                if (isAdded()) {
                    checkUserRole(user);
                }
            }
            @Override
            public void onFailure(String errorMessage) {
                if (isAdded()) {
                    showCustomToast("GitHub login failed: " + errorMessage, false);
                }
            }
        });
    }

    private void togglePasswordVisibility() {
        passwordInput.setTransformationMethod(isPasswordVisible ? PasswordTransformationMethod.getInstance() : SingleLineTransformationMethod.getInstance());
        passwordToggle.setImageResource(isPasswordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye_on);
        isPasswordVisible = !isPasswordVisible;
        passwordInput.setSelection(passwordInput.getText().toString().length());
    }

    private void setUiEnabled(boolean enabled) {
        credInput.setEnabled(enabled);
        passwordInput.setEnabled(enabled);
        requireView().findViewById(R.id.loginBtn).setEnabled(enabled);
        signupText.setEnabled(enabled);
        forgotText.setEnabled(enabled);
        passwordToggle.setEnabled(enabled);
        googleLogin.setEnabled(enabled);
        facebookLogin.setEnabled(enabled);
        githubLogin.setEnabled(enabled);
        inputTypeGroup.setEnabled(enabled);
        emailRadio.setEnabled(enabled);
        phoneRadio.setEnabled(enabled);
        rememberMeCheckbox.setEnabled(enabled);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        if (!isAdded()) return;
        Toast toast = new Toast(requireContext());
        View toastView = LayoutInflater.from(requireContext()).inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(toastView);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        credInput.setBackgroundResource(R.drawable.edittext_bg);
        passwordInput.setBackgroundResource(R.drawable.edittext_bg);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ccp != null && credInput != null) {
            ccp.deregisterCarrierNumberEditText();
            Log.d(TAG, "CCP deregistered from credInput");
        }
        if (sltLoader != null) {
            sltLoader.hideLoader();
            sltLoader.onDestroy();
            sltLoader = null;
        }
    }

    private static class UserRole {
        public String role;
        public UserRole(String role) {
            this.role = role;
        }
    }
}