package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProfileActivity extends AppCompatActivity {

    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;
    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "ProfileActivity";
    private static final String KEY_DATA_FRESH = "data_fresh";
    private static final String KEY_LAST_FETCH_SUCCESS = "last_fetch_success";
    private static final int EDIT_PROFILE_REQUEST = 1;
    private static final long VERIFICATION_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    private LinearLayout navHome, navCategories, navScanner, navProfile, navLabTest;
    private ImageView icHome, icCategories, icScanner, icProfile, icLabTest;
    private View homeIndicator, categoriesIndicator, scannerIndicator, labTestIndicator, profileIndicator;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;

    private ImageView profileImage;
    private TextView userName, userPhone, verificationStatus, verificationTimer;
    private RelativeLayout editProfileLayout, changePasswordLayout;
    private Button logoutBtn, cancelVerificationButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar loadingProgress;

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private FirebaseUser currentUser;
    private ProfileAuthHelper authHelper;

    private String cachedFullName, cachedPhoneNumber, cachedEmail, cachedImageUrl, cachedUsername;
    private String signInMethod = "email";
    private String pendingEmail = "";
    private String pendingPhoneNumber = "";

    private SharedPreferences sharedPreferences;
    private CountDownTimer countdownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        cachedFullName = sharedPreferences.getString("fullName", "User");
        cachedPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        cachedEmail = sharedPreferences.getString("email", "");
        cachedImageUrl = sharedPreferences.getString("imageUrl", "");
        cachedUsername = sharedPreferences.getString("username", "");
        signInMethod = sharedPreferences.getString("signInMethod", "email");
        pendingEmail = sharedPreferences.getString("pendingEmail", "");
        pendingPhoneNumber = sharedPreferences.getString("pendingPhoneNumber", "");

        initializeFirebase();

        if (currentUser == null) {
            logoutAndRedirectToLogin();
            return;
        }

        initializeUI();
        setupNavigationListeners();
        setupMenuListeners();
        setupLogoutListener();
        swipeRefreshLayout.setOnRefreshListener(this::onRefresh);

        displayCachedProfileData();
        loadingProgress.setVisibility(View.VISIBLE);
        loadProfileDataFromFirebase();

        mAuth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && "email".equals(signInMethod) && user.isEmailVerified() && !pendingEmail.isEmpty()) {
                updateEmailAfterVerification(user.getEmail());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() == null) {
            logoutAndRedirectToLogin();
        } else {
            currentUser = mAuth.getCurrentUser();
            loadProfileDataFromFirebase();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_PROFILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Log.d(TAG, "Received save result from EditProfileActivity");
            String newImageUrl = data.getStringExtra("imageUrl");
            String newFullName = data.getStringExtra("fullName");
            String newPhoneNumber = data.getStringExtra("phoneNumber");
            String newEmail = data.getStringExtra("email");
            String newUsername = data.getStringExtra("username");
            String newPendingEmail = data.getStringExtra("pendingEmail");
            String newPendingPhoneNumber = data.getStringExtra("pendingPhoneNumber");

            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (newImageUrl != null) cachedImageUrl = newImageUrl;
            if (newFullName != null) cachedFullName = newFullName;
            if (newPhoneNumber != null) cachedPhoneNumber = newPhoneNumber;
            if (newEmail != null) cachedEmail = newEmail;
            if (newUsername != null) cachedUsername = newUsername;
            if (newPendingEmail != null) pendingEmail = newPendingEmail;
            if (newPendingPhoneNumber != null) pendingPhoneNumber = newPendingPhoneNumber;
            editor.putString("fullName", cachedFullName);
            editor.putString("phoneNumber", cachedPhoneNumber);
            editor.putString("email", cachedEmail);
            editor.putString("username", cachedUsername);
            editor.putString("imageUrl", cachedImageUrl);
            editor.putString("pendingEmail", pendingEmail);
            editor.putString("pendingPhoneNumber", pendingPhoneNumber);
            editor.putBoolean(KEY_DATA_FRESH, true);
            editor.putBoolean(KEY_LAST_FETCH_SUCCESS, true);
            editor.apply();

            displayCachedProfileData();
            loadingProgress.setVisibility(View.VISIBLE);
            loadProfileDataFromFirebase();
        }
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
            // Determine sign-in method from Firebase Authentication
            if (!currentUser.getProviderData().isEmpty()) {
                for (com.google.firebase.auth.UserInfo provider : currentUser.getProviderData()) {
                    String providerId = provider.getProviderId();
                    if ("phone".equals(providerId)) {
                        signInMethod = "phone";
                    } else if ("password".equals(providerId)) {
                        signInMethod = "email";
                    } else if ("google.com".equals(providerId)) {
                        signInMethod = "google";
                    } else if ("facebook.com".equals(providerId)) {
                        signInMethod = "facebook";
                    } else if ("github.com".equals(providerId)) {
                        signInMethod = "github";
                    }
                }
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("signInMethod", signInMethod);
                editor.apply();
                Log.d(TAG, "Sign-in method determined: " + signInMethod);
            }
        } else {
            logoutAndRedirectToLogin();
        }
    }

    private void initializeUI() {
        navHome = findViewById(R.id.navHome);
        navCategories = findViewById(R.id.navCategories);
        navScanner = findViewById(R.id.navScanner);
        navProfile = findViewById(R.id.navProfile);
        navLabTest = findViewById(R.id.navLabTest);

        icHome = findViewById(R.id.ic_home);
        icCategories = findViewById(R.id.ic_categories);
        icScanner = findViewById(R.id.ic_scanner);
        icProfile = findViewById(R.id.ic_profile);
        icLabTest = findViewById(R.id.ic_lab_test);

        homeIndicator = findViewById(R.id.homeIndicator);
        categoriesIndicator = findViewById(R.id.categoriesIndicator);
        scannerIndicator = findViewById(R.id.scannerIndicator);
        labTestIndicator = findViewById(R.id.labTestIndicator);
        profileIndicator = findViewById(R.id.profileIndicator);

        profileImage = findViewById(R.id.profileImage);
        userName = findViewById(R.id.userName);
        userPhone = findViewById(R.id.userPhone);
        verificationStatus = findViewById(R.id.verificationStatus);
        verificationTimer = findViewById(R.id.verificationTimer);
        LinearLayout menuItems = findViewById(R.id.menuItems);
        editProfileLayout = (RelativeLayout) menuItems.getChildAt(0);
        changePasswordLayout = (RelativeLayout) menuItems.getChildAt(1);
        logoutBtn = findViewById(R.id.logoutBtn);
        cancelVerificationButton = findViewById(R.id.cancelVerificationButton);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        loadingProgress = findViewById(R.id.loadingProgress);

        setSelectedNavItem(navProfile, icProfile, profileIndicator);
    }

    private void setupNavigationListeners() {
        navHome.setOnClickListener(v -> {
            setSelectedNavItem(navHome, icHome, homeIndicator);
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });
        navCategories.setOnClickListener(v -> setSelectedNavItem(navCategories, icCategories, categoriesIndicator));
        navScanner.setOnClickListener(v -> setSelectedNavItem(navScanner, icScanner, scannerIndicator));
        navProfile.setOnClickListener(v -> setSelectedNavItem(navProfile, icProfile, profileIndicator));
        navLabTest.setOnClickListener(v -> setSelectedNavItem(navLabTest, icLabTest, labTestIndicator));
    }

    private void setupMenuListeners() {
        LinearLayout menuItems = findViewById(R.id.menuItems);
        for (int i = 0; i < menuItems.getChildCount(); i++) {
            View menuItem = menuItems.getChildAt(i);
            menuItem.setOnClickListener(v -> {
                ScaleAnimation scaleAnimation = new ScaleAnimation(
                        1.0f, 0.97f, 1.0f, 0.97f,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f
                );
                scaleAnimation.setDuration(150);
                scaleAnimation.setRepeatMode(Animation.REVERSE);
                scaleAnimation.setRepeatCount(1);
                scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                v.startAnimation(scaleAnimation);

                if (v == editProfileLayout) {
                    Intent intent = new Intent(ProfileActivity.this, EditProfileActivity.class);
                    intent.putExtra("currentImageUrl", cachedImageUrl);
                    intent.putExtra("fullName", cachedFullName);
                    intent.putExtra("phoneNumber", cachedPhoneNumber);
                    intent.putExtra("email", cachedEmail);
                    intent.putExtra("username", cachedUsername);
                    intent.putExtra("pendingEmail", pendingEmail);
                    intent.putExtra("pendingPhoneNumber", pendingPhoneNumber);
                    intent.putExtra("signInMethod", signInMethod);
                    startActivityForResult(intent, EDIT_PROFILE_REQUEST);
                } else if (v == changePasswordLayout) {
                    Intent intent = new Intent(ProfileActivity.this, ChangePasswordActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void setupLogoutListener() {
        logoutBtn.setOnClickListener(v -> {
            LogoutConfirmationDialog dialog = new LogoutConfirmationDialog(this, () -> logoutAndRedirectToLogin());
            dialog.show();
        });
    }

    private void setSelectedNavItem(LinearLayout newSelectedLayout, ImageView newSelectedIcon, View indicator) {
        homeIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        categoriesIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        scannerIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        labTestIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        profileIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

        if (selectedNavItem != null && selectedIcon != null) {
            selectedNavItem.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            if (selectedNavItem != navScanner) {
                selectedIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive));
                if (selectedNavItem.getChildCount() > 2) {
                    TextView textView = (TextView) selectedNavItem.getChildAt(2);
                    textView.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive));
                }
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selectedIcon.getLayoutParams();
            params.width = (int) (24 * getResources().getDisplayMetrics().density);
            params.height = (int) (24 * getResources().getDisplayMetrics().density);
            selectedIcon.setLayoutParams(params);
            if (selectedNavItem == navScanner) {
                ((LinearLayout) selectedNavItem.getChildAt(1)).setBackgroundResource(R.drawable.scan_bg);
            }
        }

        selectedNavItem = newSelectedLayout;
        selectedIcon = newSelectedIcon;

        selectedNavItem.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        if (selectedNavItem != navScanner) {
            selectedIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_active));
            if (selectedNavItem.getChildCount() > 2) {
                TextView textView = (TextView) selectedNavItem.getChildAt(2);
                textView.setTextColor(ContextCompat.getColor(this, R.color.nav_active));
            }
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selectedIcon.getLayoutParams();
        params.width = (int) (28 * getResources().getDisplayMetrics().density);
        params.height = (int) (28 * getResources().getDisplayMetrics().density);
        selectedIcon.setLayoutParams(params);
        indicator.setBackgroundColor(ContextCompat.getColor(this, R.color.nav_active));
        if (selectedNavItem == navScanner) {
            ((LinearLayout) selectedNavItem.getChildAt(1)).setBackgroundResource(R.drawable.scan_bg);
        }
    }

    private void displayCachedProfileData() {
        Log.d(TAG, "Displaying cached data - signInMethod: " + signInMethod + ", pendingEmail: " + pendingEmail + ", emailVerified: " + (currentUser != null ? currentUser.isEmailVerified() : "null"));
        userName.setText(cachedFullName != null && !cachedFullName.isEmpty() ? cachedFullName : "User");

        String primaryContact = getPrimaryContactInfo();
        String displayContact = primaryContact;
        if ("phone".equals(signInMethod) && displayContact.contains("@")) {
            displayContact = displayContact.split("@")[0]; // Clean phone number
        } else if (!"phone".equals(signInMethod) && !cachedPhoneNumber.isEmpty() && !cachedPhoneNumber.equals(primaryContact)) {
            // Show optional phone number if not primary
            displayContact = cachedPhoneNumber.contains("@") ? cachedPhoneNumber.split("@")[0] : cachedPhoneNumber;
        }
        userPhone.setText(displayContact != null ? displayContact : "Not provided");

        LinearLayout verificationStatusContainer = findViewById(R.id.verificationStatusContainer);

        verificationStatusContainer.setVisibility(View.GONE);
        cancelVerificationButton.setVisibility(View.GONE);
        verificationTimer.setVisibility(View.GONE);

        if (currentUser != null) {
            boolean emailPending = "email".equals(signInMethod) && pendingEmail != null && !pendingEmail.isEmpty() && !currentUser.isEmailVerified();
            boolean phonePending = "phone".equals(signInMethod) && pendingPhoneNumber != null && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentUser.getPhoneNumber());

            if (emailPending || phonePending) {
                verificationStatusContainer.setVisibility(View.VISIBLE);
                String statusText = emailPending ? "Verification pending for: " + pendingEmail : "Phone verification pending: " + pendingPhoneNumber;
                verificationStatus.setText(statusText);
                verificationStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
                cancelVerificationButton.setVisibility(View.VISIBLE);

                cancelVerificationButton.setOnClickListener(v -> {
                    if (emailPending) {
                        databaseReference.child("pendingEmail").removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.remove("pendingEmail");
                                    editor.remove("lastVerificationTime");
                                    editor.apply();
                                    pendingEmail = "";
                                    if (countdownTimer != null) {
                                        countdownTimer.cancel();
                                    }
                                    displayCachedProfileData();
                                    showCustomToast("Email verification cancelled.", true);
                                })
                                .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
                    } else if (phonePending) {
                        databaseReference.child("pendingPhoneNumber").removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.remove("pendingPhoneNumber");
                                    editor.remove("lastVerificationTime");
                                    editor.apply();
                                    pendingPhoneNumber = "";
                                    if (countdownTimer != null) {
                                        countdownTimer.cancel();
                                    }
                                    displayCachedProfileData();
                                    showCustomToast("Phone verification cancelled.", true);
                                })
                                .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
                    }
                });

                startVerificationTimer();
            } else if ("email".equals(signInMethod) && currentUser.isEmailVerified()) {
                verificationStatusContainer.setVisibility(View.VISIBLE);
                verificationStatus.setText("Email verified");
                verificationStatus.setTextColor(ContextCompat.getColor(this, R.color.green));
            }
        }

        profileImage.setImageDrawable(null);
        RequestOptions options = new RequestOptions()
                .circleCrop()
                .override(INNER_SIZE, INNER_SIZE)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .diskCacheStrategy(DiskCacheStrategy.ALL);

        if (cachedImageUrl != null && !cachedImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(cachedImageUrl)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(profileImage);
        } else {
            profileImage.setImageResource(R.drawable.default_profile);
        }
        Log.d(TAG, "Displayed image URL: " + cachedImageUrl);

        if (authHelper == null) {
            authHelper = new ProfileAuthHelper(this, new ProfileAuthHelper.OnAuthCompleteListener() {
                @Override
                public void onAuthStart() {
                    loadingProgress.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                                          String email, String username, String originalEmail,
                                          String originalPhoneNumber, String originalUsername) {
                    loadProfileDataFromFirebase();
                }

                @Override
                public void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                                    String currentEmail, String username, String pendingEmail,
                                                    String originalEmail, String originalPhoneNumber, String originalUsername) {
                    showCustomToast("Verification email sent to " + pendingEmail, true);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putLong("lastVerificationTime", System.currentTimeMillis());
                    editor.apply();
                    startVerificationTimer();
                    displayCachedProfileData();
                }

                @Override
                public void onPhoneVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                                    String currentEmail, String username, String pendingPhoneNumber,
                                                    String originalEmail, String originalPhoneNumber, String originalUsername) {
                    showCustomToast("Verification code sent to " + pendingPhoneNumber, true);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putLong("lastVerificationTime", System.currentTimeMillis());
                    editor.apply();
                    startVerificationTimer();
                    displayCachedProfileData();
                }

                @Override
                public void onAuthFailed() {
                    loadingProgress.setVisibility(View.GONE);
                    showCustomToast("Operation failed. Please try again.", false);
                }
            });
        }
    }

    private void startVerificationTimer() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
        long lastVerificationTime = sharedPreferences.getLong("lastVerificationTime", 0);
        long elapsedTime = System.currentTimeMillis() - lastVerificationTime;
        long remainingTime = VERIFICATION_TIMEOUT - elapsedTime;

        if (remainingTime <= 0) {
            autoCancelVerification();
            return;
        }

        countdownTimer = new CountDownTimer(remainingTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = millisUntilFinished / 60000;
                long seconds = (millisUntilFinished % 60000) / 1000;
                verificationTimer.setText(String.format("%dm %ds", minutes, seconds));
                verificationTimer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinish() {
                verificationTimer.setVisibility(View.GONE);
                autoCancelVerification();
            }
        };
        countdownTimer.start();
    }

    private void autoCancelVerification() {
        boolean emailPending = "email".equals(signInMethod) && pendingEmail != null && !pendingEmail.isEmpty() && !currentUser.isEmailVerified();
        boolean phonePending = "phone".equals(signInMethod) && pendingPhoneNumber != null && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentUser.getPhoneNumber());

        if (emailPending) {
            databaseReference.child("pendingEmail").removeValue()
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingEmail");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingEmail = "";
                        displayCachedProfileData();
                        showCustomToast("Verification timed out and cancelled.", false);
                    })
                    .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
        } else if (phonePending) {
            databaseReference.child("pendingPhoneNumber").removeValue()
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingPhoneNumber");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingPhoneNumber = "";
                        displayCachedProfileData();
                        showCustomToast("Verification timed out and cancelled.", false);
                    })
                    .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
        }
    }

    private void onRefresh() {
        Log.d(TAG, "Swipe refresh triggered");
        loadingProgress.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(true);
        loadProfileDataFromFirebase();
    }

    private void loadProfileDataFromFirebase() {
        if (currentUser == null) {
            logoutAndRedirectToLogin();
            return;
        }

        currentUser.reload().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Exception e = task.getException();
                if (e instanceof FirebaseAuthInvalidUserException) {
                    showCustomToast("Session expired. Please log in again.", false);
                    logoutAndRedirectToLogin();
                    return;
                } else {
                    showCustomToast("Error refreshing session: " + e.getMessage(), false);
                }
            }

            currentUser.getIdToken(true).addOnCompleteListener(tokenTask -> {
                if (tokenTask.isSuccessful()) {
                    Log.d(TAG, "Token refreshed, emailVerified: " + currentUser.isEmailVerified());
                } else {
                    Log.e(TAG, "Token refresh failed: " + tokenTask.getException().getMessage());
                    showCustomToast("Failed to refresh token: " + tokenTask.getException().getMessage(), false);
                }
                fetchProfileData();
            });
        });
    }

    private void fetchProfileData() {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> userData = (Map<String, Object>) dataSnapshot.getValue();
                    String newFullName = userData.get("fullName") != null ? (String) userData.get("fullName") : cachedFullName != null ? cachedFullName : "User";
                    String newPhoneNumber = userData.get("phoneNumber") != null ? (String) userData.get("phoneNumber") : cachedPhoneNumber != null ? cachedPhoneNumber : "";
                    String newEmail = userData.get("email") != null ? (String) userData.get("email") : cachedEmail != null ? cachedEmail : "";
                    String newUsername = userData.get("username") != null ? (String) userData.get("username") : cachedUsername != null ? cachedUsername : "";
                    String newImageUrl = userData.get("imageUrl") != null ? (String) userData.get("imageUrl") : cachedImageUrl != null ? cachedImageUrl : "";
                    String newSignInMethod = userData.get("signInMethod") != null ? (String) userData.get("signInMethod") : signInMethod != null ? signInMethod : "email";
                    pendingEmail = userData.get("pendingEmail") != null ? (String) userData.get("pendingEmail") : pendingEmail != null ? pendingEmail : "";
                    pendingPhoneNumber = userData.get("pendingPhoneNumber") != null ? (String) userData.get("pendingPhoneNumber") : pendingPhoneNumber != null ? pendingPhoneNumber : "";

                    if ("email".equals(newSignInMethod)) {
                        if (currentUser.isEmailVerified() && !pendingEmail.isEmpty()) {
                            updateEmailAfterVerification(currentUser.getEmail());
                        } else {
                            newEmail = currentUser.isEmailVerified() ? currentUser.getEmail() : newEmail;
                        }
                    } else if ("phone".equals(newSignInMethod)) {
                        newPhoneNumber = currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : newPhoneNumber;
                    }

                    if (!Objects.equals(newFullName, cachedFullName) ||
                            !Objects.equals(newPhoneNumber, cachedPhoneNumber) ||
                            !Objects.equals(newEmail, cachedEmail) ||
                            !Objects.equals(newImageUrl, cachedImageUrl) ||
                            !Objects.equals(newUsername, cachedUsername) ||
                            !Objects.equals(newSignInMethod, signInMethod) ||
                            !Objects.equals(pendingEmail, sharedPreferences.getString("pendingEmail", "")) ||
                            !Objects.equals(pendingPhoneNumber, sharedPreferences.getString("pendingPhoneNumber", ""))) {
                        cachedFullName = newFullName;
                        cachedPhoneNumber = newPhoneNumber;
                        cachedEmail = newEmail;
                        cachedUsername = newUsername;
                        cachedImageUrl = newImageUrl;
                        signInMethod = newSignInMethod;

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("fullName", cachedFullName);
                        editor.putString("phoneNumber", cachedPhoneNumber);
                        editor.putString("email", cachedEmail);
                        editor.putString("username", cachedUsername);
                        editor.putString("imageUrl", cachedImageUrl);
                        editor.putString("signInMethod", signInMethod);
                        editor.putString("pendingEmail", pendingEmail);
                        editor.putString("pendingPhoneNumber", pendingPhoneNumber);
                        editor.putBoolean(KEY_DATA_FRESH, true);
                        editor.putBoolean(KEY_LAST_FETCH_SUCCESS, true);
                        editor.apply();
                    }

                    displayCachedProfileData();
                } else {
                    showCustomToast("No profile data found.", false);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(KEY_LAST_FETCH_SUCCESS, false);
                    editor.apply();
                    displayCachedProfileData();
                }
                loadingProgress.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                Log.d(TAG, "Fetch completed, pendingEmail: " + pendingEmail + ", pendingPhoneNumber: " + pendingPhoneNumber);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                handleDatabaseError(databaseError);
            }
        });
    }

    private void updateEmailAfterVerification(String verifiedEmail) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", verifiedEmail);
        updates.put("pendingEmail", "");

        databaseReference.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String emailKey = verifiedEmail.replace(".", "_");
                    FirebaseDatabase.getInstance().getReference("emails").child(emailKey).setValue(currentUser.getUid());
                    cachedEmail = verifiedEmail;
                    pendingEmail = "";
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("email", cachedEmail);
                    editor.putString("pendingEmail", "");
                    editor.apply();
                    if (countdownTimer != null) {
                        countdownTimer.cancel();
                    }
                    displayCachedProfileData();
                    showCustomToast("Email verified and updated.", true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update verified email: " + e.getMessage());
                    showCustomToast("Failed to update email after verification: " + e.getMessage(), false);
                });
    }

    private void handleDatabaseError(DatabaseError databaseError) {
        loadingProgress.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LAST_FETCH_SUCCESS, false);
        editor.apply();

        String errorMessage;
        switch (databaseError.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                errorMessage = "Permission denied. Contact support.";
                break;
            case DatabaseError.NETWORK_ERROR:
                errorMessage = "Network error. Retrying in 2 seconds...";
                new android.os.Handler().postDelayed(() -> {
                    if (!isFinishing()) {
                        loadProfileDataFromFirebase();
                    }
                }, 2000);
                break;
            case DatabaseError.DISCONNECTED:
                errorMessage = "Disconnected from server. Please try again later.";
                break;
            default:
                errorMessage = "Failed to load profile: " + databaseError.getMessage();
                break;
        }
        showCustomToast(errorMessage, false);
        Log.e(TAG, "Database error: " + databaseError.getDetails());
        displayCachedProfileData();
    }

    private String getPrimaryContactInfo() {
        Log.d(TAG, "Determining primary contact info - signInMethod: " + signInMethod);
        String contactInfo = null;
        if ("phone".equals(signInMethod) && currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().isEmpty()) {
            contactInfo = currentUser.getPhoneNumber();
        } else if ("email".equals(signInMethod) && currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
            contactInfo = currentUser.getEmail();
        } else if (signInMethod.contains("google") || signInMethod.contains("facebook") || signInMethod.contains("github")) {
            if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                contactInfo = currentUser.getEmail();
            } else if (currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().isEmpty()) {
                contactInfo = currentUser.getPhoneNumber();
            }
        }
        if (contactInfo == null) {
            if ("phone".equals(signInMethod) && !cachedPhoneNumber.isEmpty()) {
                contactInfo = cachedPhoneNumber;
            } else {
                contactInfo = !cachedEmail.isEmpty() ? cachedEmail : "Not provided";
            }
        }
        // Clean up phone number if it has an email-like suffix
        if ("phone".equals(signInMethod) && contactInfo.contains("@")) {
            contactInfo = contactInfo.split("@")[0];
        }
        return contactInfo;
    }

    private void logoutAndRedirectToLogin() {
        mAuth.signOut();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

        int offsetDp = (int) (200 * getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offsetDp);

        Log.d(TAG, "Showing toast: " + message + ", Offset: " + offsetDp + "px, Gravity: BOTTOM");
        toast.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
    }
}