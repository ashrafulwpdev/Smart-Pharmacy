package com.oopgroup.smartpharmacy.fragments;

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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
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
import com.oopgroup.smartpharmacy.adminstaff.AdminActivity;
import com.oopgroup.smartpharmacy.utils.AuthUtils;
import com.oopgroup.smartpharmacy.ForgotPassActivity;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.SignupActivity;

public class LoginFragment extends Fragment {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private static final int PHONE_STATE_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "LoginFragment";
    private EditText credInput, passwordInput;
    private LottieAnimationView loadingSpinner;
    private TextView signupText, forgotText;
    private ImageView passwordToggle, googleLogin, emailIcon, phoneIcon;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore firestore;
    private boolean isPasswordVisible = false;

    public LoginFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Initialize Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

        // Initialize UI
        credInput = view.findViewById(R.id.credInput);
        passwordInput = view.findViewById(R.id.passwordInput);
        loadingSpinner = view.findViewById(R.id.loadingSpinner);
        signupText = view.findViewById(R.id.signupText);
        forgotText = view.findViewById(R.id.forgotText);
        passwordToggle = view.findViewById(R.id.passwordToggle);
        googleLogin = view.findViewById(R.id.googleLogin);
        emailIcon = view.findViewById(R.id.emailIcon);
        phoneIcon = view.findViewById(R.id.phoneIcon);
        ccp = view.findViewById(R.id.ccp);
        Button loginBtn = view.findViewById(R.id.loginBtn);

        // Configure CCP
        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setCountryForNameCode("BD");

        // Setup listeners and permissions
        requestPhoneStatePermission();
        setupDynamicInput();
        setupInputValidation();

        loginBtn.setOnClickListener(v -> handleLogin());
        signupText.setOnClickListener(v -> startActivity(new Intent(requireContext(), SignupActivity.class)));
        forgotText.setOnClickListener(v -> handleForgotPassword());
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        googleLogin.setOnClickListener(v -> signInWithGoogle());
    }

    private void requestPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_STATE_PERMISSION_REQUEST_CODE);
        } else {
            AuthUtils.fetchSimNumber(requireActivity(), credInput);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_STATE_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AuthUtils.fetchSimNumber(requireActivity(), credInput);
        }
    }

    private void setupDynamicInput() {
        AuthUtils.setupDynamicInput(requireActivity(), credInput, ccp, emailIcon, phoneIcon, null);
        adjustCredInputPadding();
    }

    private void setupInputValidation() {
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
        ccp.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (ccp.getVisibility() == View.VISIBLE) {
                int ccpWidth = ccp.getWidth();
                int paddingStart = ccpWidth + (int) (10 * getResources().getDisplayMetrics().density);
                credInput.setPadding(paddingStart, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
            } else {
                int defaultPadding = (int) (10 * getResources().getDisplayMetrics().density);
                credInput.setPadding(defaultPadding, credInput.getPaddingTop(), credInput.getPaddingEnd(), credInput.getPaddingBottom());
            }
        });
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

        String emailOrPhone = AuthUtils.isValidEmail(credentials) ? credentials : AuthUtils.normalizePhoneNumberForBackend(credentials, ccp, AuthUtils.getSimCountry(requireActivity()));
        final String loginEmail = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";

        loadingSpinner.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.signInWithEmailAndPassword(loginEmail, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && isAdded()) {
                            Log.d(TAG, "Email/Password login successful, UID: " + user.getUid());
                            checkUserRole(user);
                        }
                    } else if (isAdded()) {
                        Log.e(TAG, "Email/Password login failed: " + task.getException().getMessage(), task.getException());
                        showCustomToast("Login failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void checkUserRole(FirebaseUser user) {
        if (!isAdded() || getActivity() == null) return;
        firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    String role = documentSnapshot.getString("role");
                    if (role == null) {
                        firestore.collection("users").document(user.getUid())
                                .set(new UserRole("user"), com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Default role 'user' set for UID: " + user.getUid());
                                    proceedWithRole(user, "user");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error setting default role: " + e.getMessage(), e);
                                    showCustomToast("Error setting role, defaulting to user", false);
                                    proceedWithRole(user, "user");
                                });
                    } else {
                        Log.d(TAG, "Role fetched: " + role + " for UID: " + user.getUid());
                        proceedWithRole(user, role);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Firestore fetch failed, falling back to 'user' role: " + e.getMessage(), e);
                    showCustomToast("Database error, defaulting to user role", false);
                    proceedWithRole(user, "user");
                });
    }

    private void proceedWithRole(FirebaseUser user, String role) {
        Intent intent = "admin".equals(role) ?
                new Intent(requireContext(), AdminActivity.class) :
                new Intent(requireContext(), MainActivity.class);
        showCustomToast("Login successful as " + role + "!", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void handleForgotPassword() {
        startActivity(new Intent(requireContext(), ForgotPassActivity.class));
    }

    private void signInWithGoogle() {
        Log.d(TAG, "Initiating Google Sign-In");
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
                Log.d(TAG, "Google Sign-In successful, email: " + account.getEmail() + ", ID token: " + account.getIdToken());
                loadingSpinner.setVisibility(View.VISIBLE);
                setUiEnabled(false);
                AuthUtils.firebaseLoginWithGoogle(requireActivity(), account.getIdToken(), new AuthUtils.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        if (isAdded()) {
                            Log.d(TAG, "Google login callback success, UID: " + user.getUid());
                            checkUserRole(user);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        loadingSpinner.setVisibility(View.GONE);
                        setUiEnabled(true);
                        if (isAdded()) {
                            Log.e(TAG, "Google login callback failed: " + errorMessage);
                            showCustomToast("Google login failed: " + errorMessage, false);
                        }
                    }
                });
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed, status code: " + e.getStatusCode() + ", message: " + e.getMessage(), e);
                loadingSpinner.setVisibility(View.GONE);
                setUiEnabled(true);
                if (isAdded()) {
                    showCustomToast("Google Sign-In failed: " + e.getMessage(), false);
                }
            }
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
    }

    private void showCustomToast(String message, boolean isSuccess) {
        if (!isAdded() || getActivity() == null) return;
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

    // Helper class for Firestore role setting
    private static class UserRole {
        public String role;

        public UserRole(String role) {
            this.role = role;
        }
    }
}