package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.softourtech.slt.SLTLoader;

public class ChangePasswordActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "ChangePwordActivity";

    private FirebaseAuth mAuth;
    private DatabaseReference usersReference;
    private EditText currentPassword, newPassword, confirmPassword;
    private ImageView currentPasswordToggle, newPasswordToggle, confirmPasswordToggle;
    private Button updatePasswordButton;
    private ImageButton backButton;
    private TextView socialWarningText;
    private ScrollView scrollView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedPreferences sharedPreferences;
    private SLTLoader sltLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        // Initialize SLTLoader with the activity's root view
        View activityRoot = findViewById(android.R.id.content);
        if (activityRoot == null || !(activityRoot instanceof ViewGroup)) {
            Log.e(TAG, "Activity root view not found or not a ViewGroup");
            return;
        }
        sltLoader = new SLTLoader(this, (ViewGroup) activityRoot);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showCustomToast("Please log in to change your password", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        usersReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        // Initialize UI elements
        currentPassword = findViewById(R.id.currentPassword);
        newPassword = findViewById(R.id.newPassword);
        confirmPassword = findViewById(R.id.confirmPassword);
        currentPasswordToggle = findViewById(R.id.currentPasswordToggle);
        newPasswordToggle = findViewById(R.id.newPasswordToggle);
        confirmPasswordToggle = findViewById(R.id.confirmPasswordToggle);
        updatePasswordButton = findViewById(R.id.updatePasswordButton);
        backButton = findViewById(R.id.backButton);
        socialWarningText = findViewById(R.id.changePassSocialWarningText);
        scrollView = findViewById(R.id.scrollView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Verify back button initialization
        if (backButton == null) {
            Log.e(TAG, "Back button not found! Check XML ID.");
        } else {
            Log.d(TAG, "Back button initialized successfully.");
        }

        // Check sign-in method
        checkSignInMethod(currentUser);

        // Setup click listeners
        setupBackButton();
        setupPasswordToggles();

        updatePasswordButton.setOnClickListener(v -> {
            String currentPass = currentPassword.getText().toString().trim();
            String newPass = newPassword.getText().toString().trim();
            String confirmPass = confirmPassword.getText().toString().trim();

            if (validateInputs(currentPass, newPass, confirmPass)) {
                changePassword(currentPass, newPass);
            }
        });

        // Ensure button is visible when keyboard appears
        setupKeyboardScroll();

        // Setup swipe refresh listener
        setupSwipeRefresh();
    }

    private void setupBackButton() {
        backButton.setOnClickListener(v -> {
            Log.d(TAG, "Back button clicked");
            navigateToProfileFragment();
        });
    }

    private void navigateToProfileFragment() {
        Log.d(TAG, "Navigating to ProfileFragment");
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("SHOW_PROFILE_FRAGMENT", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            currentPassword.setText("");
            newPassword.setText("");
            confirmPassword.setText("");
            resetInputBorders();
            checkSignInMethod(mAuth.getCurrentUser());

            new android.os.Handler().postDelayed(() -> {
                swipeRefreshLayout.setRefreshing(false);
                showCustomToast("Fields refreshed", true);
            }, 1000);
        });
    }

    private void setupKeyboardScroll() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus) {
                scrollToBottom();
            }
        };
        currentPassword.setOnFocusChangeListener(focusListener);
        newPassword.setOnFocusChangeListener(focusListener);
        confirmPassword.setOnFocusChangeListener(focusListener);

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int rootHeight = getWindow().getDecorView().getHeight();
            int visibleHeight = getWindow().getDecorView().getRootView().getHeight();
            if (rootHeight - visibleHeight > 200) {
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.smoothScrollTo(0, updatePasswordButton.getBottom()));
    }

    private void checkSignInMethod(FirebaseUser user) {
        if (user == null) return;

        String cachedSignInMethod = sharedPreferences.getString("signInMethod", "email");
        if (cachedSignInMethod.equals("google") || cachedSignInMethod.equals("facebook") || cachedSignInMethod.equals("github")) {
            disablePasswordChange(cachedSignInMethod);
        } else {
            usersReference.child("signInMethod").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String signInMethod = snapshot.getValue(String.class);
                    if (signInMethod != null && (signInMethod.equals("google") || signInMethod.equals("facebook") || signInMethod.equals("github"))) {
                        disablePasswordChange(signInMethod);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("signInMethod", signInMethod);
                        editor.apply();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error retrieving sign-in method: " + error.getMessage());
                    showCustomToast("Error checking sign-in method: " + error.getMessage(), false);
                }
            });
        }
    }

    private void disablePasswordChange(String signInMethod) {
        currentPassword.setEnabled(false);
        newPassword.setEnabled(false);
        confirmPassword.setEnabled(false);
        updatePasswordButton.setEnabled(false);
        currentPasswordToggle.setVisibility(View.GONE);
        newPasswordToggle.setVisibility(View.GONE);
        confirmPasswordToggle.setVisibility(View.GONE);
        socialWarningText.setVisibility(View.VISIBLE);
        String provider = signInMethod.substring(0, 1).toUpperCase() + signInMethod.substring(1);
        socialWarningText.setText("Password change is not available for " + provider + " logins. Please use your " + provider + " account to log in.");
    }

    private void setupPasswordToggles() {
        currentPasswordToggle.setOnClickListener(v -> togglePasswordVisibility(currentPassword, currentPasswordToggle));
        newPasswordToggle.setOnClickListener(v -> togglePasswordVisibility(newPassword, newPasswordToggle));
        confirmPasswordToggle.setOnClickListener(v -> togglePasswordVisibility(confirmPassword, confirmPasswordToggle));
    }

    private void togglePasswordVisibility(EditText editText, ImageView toggleIcon) {
        if (editText.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.black));
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
        editText.setSelection(editText.getText().length());
    }

    private boolean validateInputs(String currentPass, String newPass, String confirmPass) {
        resetInputBorders(); // Reset to default borders before validation

        if (currentPass.isEmpty()) {
            setErrorBorder(currentPassword);
            showCustomToast("Current password is required", false);
            currentPassword.requestFocus();
            return false;
        }
        if (newPass.isEmpty()) {
            setErrorBorder(newPassword);
            showCustomToast("New password is required", false);
            newPassword.requestFocus();
            return false;
        }
        if (confirmPass.isEmpty()) {
            setErrorBorder(confirmPassword);
            showCustomToast("Please confirm your new password", false);
            confirmPassword.requestFocus();
            return false;
        }
        if (!newPass.equals(confirmPass)) {
            setErrorBorder(confirmPassword);
            showCustomToast("Passwords do not match", false);
            confirmPassword.requestFocus();
            return false;
        }
        if (newPass.length() < 6) {
            setErrorBorder(newPassword);
            showCustomToast("New password must be at least 6 characters", false);
            newPassword.requestFocus();
            return false;
        }
        return true;
    }

    private void changePassword(String currentPass, String newPass) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            showLoader(); // Show SLTLoader
            setUiEnabled(false);

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);
            user.reauthenticate(credential)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            user.updatePassword(newPass)
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            hideLoader(); // Hide SLTLoader
                                            setUiEnabled(true);
                                            resetInputBorders();
                                            showCustomToast("Password updated successfully", true);
                                            navigateToProfileFragment();
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            hideLoader(); // Hide SLTLoader
                                            setUiEnabled(true);
                                            setErrorBorder(newPassword);
                                            showCustomToast("Failed to update password: " + e.getMessage(), false);
                                            Log.e(TAG, "Password update failed: " + e.getMessage());
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            hideLoader(); // Hide SLTLoader
                            setUiEnabled(true);
                            setErrorBorder(currentPassword);
                            showCustomToast("Incorrect current password", false);
                            currentPassword.requestFocus();
                            Log.e(TAG, "Re-authentication failed: " + e.getMessage());
                        }
                    });
        } else {
            hideLoader(); // Hide SLTLoader
            setUiEnabled(true);
            showCustomToast("User not logged in or email not found", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void setUiEnabled(boolean enabled) {
        currentPassword.setEnabled(enabled);
        newPassword.setEnabled(enabled);
        confirmPassword.setEnabled(enabled);
        updatePasswordButton.setEnabled(enabled);
        backButton.setEnabled(enabled);
        currentPasswordToggle.setEnabled(enabled);
        newPasswordToggle.setEnabled(enabled);
        confirmPasswordToggle.setEnabled(enabled);
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

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        currentPassword.setBackgroundResource(R.drawable.edittext_bg);
        newPassword.setBackgroundResource(R.drawable.edittext_bg);
        confirmPassword.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void showLoader() {
        SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000"))
                .setChangeJsonColor(false);
        sltLoader.showCustomLoader(config);
        Log.d(TAG, "SLTLoader shown");
    }

    private void hideLoader() {
        if (sltLoader != null) {
            sltLoader.hideLoader();
            Log.d(TAG, "SLTLoader hidden");
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "Hardware back pressed");
        navigateToProfileFragment();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sltLoader != null) {
            sltLoader.onDestroy();
            Log.d(TAG, "SLTLoader destroyed");
        }
    }
}