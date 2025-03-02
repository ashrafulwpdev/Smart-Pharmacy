package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Map;
import java.util.HashMap;

public class SignupActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;
    private EditText fullNameInput, credInput, passwordInput;
    private ProgressBar progressBar;
    private TextView loginText;
    private ImageView passwordToggle, googleLogin, facebookLogin, githubLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private boolean isPasswordVisible = false;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        Button signupBtn = findViewById(R.id.signupBtn);
        fullNameInput = findViewById(R.id.fullNameInput);
        credInput = findViewById(R.id.credInput);
        passwordInput = findViewById(R.id.passwordInput);
        progressBar = findViewById(R.id.progressBar);
        loginText = findViewById(R.id.loginText);
        passwordToggle = findViewById(R.id.passwordToggle);
        googleLogin = findViewById(R.id.googleLogin);
        facebookLogin = findViewById(R.id.facebookLogin);
        githubLogin = findViewById(R.id.githublogin);

        signupBtn.setOnClickListener(v -> {
            String fullName = fullNameInput.getText().toString().trim();
            String credentials = credInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            handleEmailSignup(fullName, credentials, password);
        });

        loginText.setOnClickListener(v -> {
            showCustomToast("Login clicked", false);
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
        });

        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());

        googleLogin.setOnClickListener(v -> signInWithGoogle());
        facebookLogin.setOnClickListener(v -> signInWithFacebook());
        githubLogin.setOnClickListener(v -> signInWithGitHub());
    }

    private void handleEmailSignup(String fullName, String credentials, String password) {
        boolean fullNameEmpty = fullName.isEmpty();
        boolean credEmpty = credentials.isEmpty();
        boolean passEmpty = password.isEmpty();

        if (fullNameEmpty || credEmpty || passEmpty) {
            if (fullNameEmpty && credEmpty && passEmpty) {
                showCustomToast("Please fill all fields", false);
                applyErrorBorder(fullNameInput);
                applyErrorBorder(credInput);
                applyErrorBorder(passwordInput);
            } else {
                if (fullNameEmpty) {
                    showCustomToast("Please enter your full name", false);
                    applyErrorBorder(fullNameInput);
                }
                if (credEmpty) {
                    showCustomToast("Please enter your email or phone number", false);
                    applyErrorBorder(credInput);
                }
                if (passEmpty) {
                    showCustomToast("Please enter your password", false);
                    applyErrorBorder(passwordInput);
                }
            }
            return;
        }

        String emailOrPhone;
        if (credentials.contains("@") && credentials.contains(".")) {
            emailOrPhone = credentials; // Email
        } else {
            if (!credentials.startsWith("+880")) {
                credentials = "+880" + credentials;
            }
            emailOrPhone = credentials; // Phone
        }

        String email = emailOrPhone.contains("@") ? emailOrPhone : emailOrPhone + "@smartpharmacy.com";

        progressBar.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
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
                            userData.put("username", "@" + fullName.replaceAll("\\s+", "").toLowerCase());
                            userData.put("imageUrl", "");

                            databaseReference.child(user.getUid()).setValue(userData);
                        }
                        showCustomToast("Sign up successful!", true);
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    } else {
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            showCustomToast("This email or phone number is already registered.", false);
                            applyErrorBorder(credInput);
                        } else {
                            showCustomToast("Signup failed: " + task.getException().getMessage(), false);
                            applyErrorBorder(credInput);
                        }
                        clearErrorBorders();
                    }
                });
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
        }
    }

    private void firebaseAuthWithGoogle(String idToken, String displayName, String email) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        progressBar.setVisibility(View.VISIBLE);
        setUiEnabled(false);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    setUiEnabled(true);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("fullName", displayName != null ? displayName : "User");
                            userData.put("email", email != null ? email : "");
                            userData.put("phoneNumber", "");
                            userData.put("gender", "Not specified");
                            userData.put("birthday", "");
                            userData.put("username", "@" + displayName.replaceAll("\\s+", "").toLowerCase());
                            userData.put("imageUrl", "");

                            databaseReference.child(user.getUid()).setValue(userData);
                        }
                        showCustomToast("Google signup successful!", true);
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    } else {
                        showCustomToast("Google signup failed: " + task.getException().getMessage(), false);
                    }
                });
    }

    private void signInWithFacebook() {
        showCustomToast("Facebook signup not implemented yet", false);
    }

    private void signInWithGitHub() {
        showCustomToast("GitHub signup not implemented yet", false);
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
}