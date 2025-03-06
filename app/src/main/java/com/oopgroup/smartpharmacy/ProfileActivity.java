package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;
    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "ProfileActivity";
    private static final String KEY_DATA_FRESH = "data_fresh";
    private static final String KEY_LAST_FETCH_SUCCESS = "last_fetch_success";
    private static final int EDIT_PROFILE_REQUEST = 1;

    private LinearLayout navHome, navCategories, navScanner, navProfile, navLabTest;
    private ImageView icHome, icCategories, icScanner, icProfile, icLabTest;
    private View homeIndicator, categoriesIndicator, scannerIndicator, labTestIndicator, profileIndicator;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;

    private ImageView profileImage;
    private TextView userName, userPhone;
    private RelativeLayout editProfileLayout, changePasswordLayout;
    private Button logoutBtn;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar loadingProgress;

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private FirebaseUser currentUser;

    private String cachedFullName, cachedPhoneNumber, cachedEmail, cachedImageUrl;
    private String signInMethod;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Preload cached data
        cachedFullName = sharedPreferences.getString("fullName", "User");
        cachedPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        cachedEmail = sharedPreferences.getString("email", "");
        cachedImageUrl = sharedPreferences.getString("imageUrl", "");
        signInMethod = sharedPreferences.getString("signInMethod", "email");

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

        // Display cached data immediately
        Log.d(TAG, "Displaying cached data with image URL: " + cachedImageUrl);
        displayCachedProfileData();

        // Fetch fresh data in the background if needed
        if (!sharedPreferences.getBoolean(KEY_DATA_FRESH, false) || !sharedPreferences.getBoolean(KEY_LAST_FETCH_SUCCESS, false)) {
            loadingProgress.setVisibility(View.VISIBLE);
            loadProfileDataFromFirebase();
        } else {
            loadingProgress.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() == null) {
            logoutAndRedirectToLogin();
        } else {
            currentUser = mAuth.getCurrentUser();
            displayCachedProfileData();
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

            SharedPreferences.Editor editor = sharedPreferences.edit();
            if (newImageUrl != null) {
                cachedImageUrl = newImageUrl;
                editor.putString("imageUrl", cachedImageUrl);
            }
            if (newFullName != null) {
                cachedFullName = newFullName;
                editor.putString("fullName", cachedFullName);
            }
            if (newPhoneNumber != null) {
                cachedPhoneNumber = newPhoneNumber;
                editor.putString("phoneNumber", cachedPhoneNumber);
            }
            if (newEmail != null) {
                cachedEmail = newEmail;
                editor.putString("email", cachedEmail);
            }
            if (newUsername != null) {
                editor.putString("username", newUsername);
            }
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
        LinearLayout menuItems = findViewById(R.id.menuItems);
        editProfileLayout = (RelativeLayout) menuItems.getChildAt(0); // Edit Profile
        changePasswordLayout = (RelativeLayout) menuItems.getChildAt(1); // Change Password
        logoutBtn = findViewById(R.id.logoutBtn);
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
                TextView textView = (TextView) selectedNavItem.getChildAt(2); // Fixed typo
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
        userName.setText(cachedFullName != null ? cachedFullName : "User");
        String contactInfo = getPrimaryContactInfo();
        userPhone.setText(contactInfo != null ? contactInfo : "Not provided");

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
                    Toast.makeText(ProfileActivity.this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                    logoutAndRedirectToLogin();
                    return;
                } else {
                    Toast.makeText(ProfileActivity.this, "Error refreshing session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    boolean dataUpdated = false;
                    if (dataSnapshot.exists()) {
                        Map<String, Object> userData = (Map<String, Object>) dataSnapshot.getValue();
                        String newFullName = (String) userData.get("fullName");
                        String newPhoneNumber = (String) userData.get("phoneNumber");
                        String newEmail = (String) userData.get("email");
                        String newImageUrl = (String) userData.get("imageUrl");
                        String newSignInMethod = (String) userData.get("signInMethod");

                        if (!newFullName.equals(cachedFullName) || !newPhoneNumber.equals(cachedPhoneNumber) ||
                                !newEmail.equals(cachedEmail) || !newImageUrl.equals(cachedImageUrl) ||
                                !newSignInMethod.equals(signInMethod)) {
                            cachedFullName = newFullName;
                            cachedPhoneNumber = newPhoneNumber;
                            cachedEmail = newEmail;
                            cachedImageUrl = newImageUrl;
                            signInMethod = newSignInMethod;
                            dataUpdated = true;

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("fullName", cachedFullName);
                            editor.putString("phoneNumber", cachedPhoneNumber);
                            editor.putString("email", cachedEmail);
                            editor.putString("imageUrl", cachedImageUrl);
                            editor.putString("signInMethod", signInMethod);
                            editor.putBoolean(KEY_DATA_FRESH, true);
                            editor.putBoolean(KEY_LAST_FETCH_SUCCESS, true);
                            editor.apply();

                            displayCachedProfileData();
                        }
                    } else {
                        Toast.makeText(ProfileActivity.this, "No profile data found.", Toast.LENGTH_SHORT).show();
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(KEY_LAST_FETCH_SUCCESS, false);
                        editor.apply();
                    }
                    loadingProgress.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    Log.d(TAG, "Fetch completed, dataUpdated: " + dataUpdated);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    handleDatabaseError(databaseError);
                }
            });
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
                new Handler().postDelayed(() -> {
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
        Toast.makeText(ProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Database error: " + databaseError.getDetails());
    }

    private String getPrimaryContactInfo() {
        if ("phone".equals(signInMethod) && cachedPhoneNumber != null && !cachedPhoneNumber.isEmpty()) {
            return cachedPhoneNumber;
        } else if (cachedEmail != null && !cachedEmail.isEmpty()) {
            return cachedEmail;
        } else if (currentUser != null) {
            if (currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().isEmpty()) {
                return currentUser.getPhoneNumber();
            } else if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                return currentUser.getEmail();
            }
        }
        return null;
    }

    private void logoutAndRedirectToLogin() {
        mAuth.signOut();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}