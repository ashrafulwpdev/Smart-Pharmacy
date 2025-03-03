package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Map;

/**
 * ProfileActivity displays the user's profile information, including name, phone/email, and profile image.
 * It handles navigation to other activities and provides a logout feature.
 */
public class ProfileActivity extends AppCompatActivity {

    // Constants
    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;

    // UI Elements - Navigation
    private LinearLayout navHome, navCategories, navScanner, navProfile, navLabTest;
    private ImageView icHome, icCategories, icScanner, icProfile, icLabTest;
    private View homeIndicator, categoriesIndicator, scannerIndicator, labTestIndicator, profileIndicator;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;

    // UI Elements - Profile Display
    private ImageView profileImage;
    private TextView userName, userPhone;
    private RelativeLayout editProfileLayout;
    private Button logoutBtn;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Firebase Components
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private FirebaseUser currentUser;

    // Cached Profile Data
    private String cachedFullName, cachedPhoneNumber, cachedEmail, cachedImageUrl;
    private String signInMethod;
    private boolean isPhonePrimary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeFirebase();

        if (currentUser == null) {
            logoutAndRedirectToLogin();
            return;
        }

        initializeUI();
        setupNavigationListeners();
        setupMenuListeners();
        setupLogoutListener();
        swipeRefreshLayout.setOnRefreshListener(this::loadProfileDataFromFirebase);

        loadCachedProfileData();
        loadProfileDataFromFirebase();
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
            isPhonePrimary = currentUser.getProviderData().stream()
                    .anyMatch(info -> PhoneAuthProvider.PROVIDER_ID.equals(info.getProviderId()) && info.getPhoneNumber() != null);
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
        editProfileLayout = (RelativeLayout) menuItems.getChildAt(0);
        logoutBtn = findViewById(R.id.logoutBtn);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

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
                    startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class));
                }
            });
        }
    }

    private void setupLogoutListener() {
        logoutBtn.setOnClickListener(v -> logoutAndRedirectToLogin());
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

    private void loadCachedProfileData() {
        userName.setText(cachedFullName != null ? cachedFullName : "User");
        if (isPhonePrimary && cachedPhoneNumber != null && !cachedPhoneNumber.isEmpty()) {
            userPhone.setText(cachedPhoneNumber);
        } else if (cachedEmail != null && !cachedEmail.isEmpty()) {
            userPhone.setText(cachedEmail);
        } else if (currentUser != null && currentUser.getEmail() != null) {
            userPhone.setText(currentUser.getEmail());
        } else {
            userPhone.setText("Not provided");
        }

        profileImage.setImageResource(R.drawable.default_profile);
        if (cachedImageUrl != null && !cachedImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(cachedImageUrl)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .circleCrop()
                            .override(INNER_SIZE, INNER_SIZE)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                    .error(R.drawable.default_profile)
                    .into(profileImage);
        }
    }

    private void loadProfileDataFromFirebase() {
        if (currentUser == null) {
            logoutAndRedirectToLogin();
            return;
        }

        currentUser.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cachedEmail = currentUser.getEmail();
                cachedPhoneNumber = currentUser.getPhoneNumber();
                isPhonePrimary = currentUser.getProviderData().stream()
                        .anyMatch(info -> PhoneAuthProvider.PROVIDER_ID.equals(info.getProviderId()) && info.getPhoneNumber() != null);

                databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            Map<String, Object> userData = (Map<String, Object>) dataSnapshot.getValue();
                            cachedFullName = (String) userData.get("fullName");
                            cachedImageUrl = (String) userData.get("imageUrl");
                            signInMethod = (String) userData.get("signInMethod");

                            userName.setText(cachedFullName != null ? cachedFullName : "User");
                            if (isPhonePrimary && cachedPhoneNumber != null && !cachedPhoneNumber.isEmpty()) {
                                userPhone.setText(cachedPhoneNumber);
                            } else if (cachedEmail != null && !cachedEmail.isEmpty()) {
                                userPhone.setText(cachedEmail);
                            } else {
                                userPhone.setText("Not provided");
                            }

                            profileImage.setImageResource(R.drawable.default_profile);
                            if (cachedImageUrl != null && !cachedImageUrl.isEmpty()) {
                                Glide.with(ProfileActivity.this)
                                        .load(cachedImageUrl)
                                        .apply(new com.bumptech.glide.request.RequestOptions()
                                                .circleCrop()
                                                .override(INNER_SIZE, INNER_SIZE)
                                                .skipMemoryCache(true)
                                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                                        .error(R.drawable.default_profile)
                                        .into(profileImage);
                            }
                        } else {
                            userName.setText("User");
                            if (isPhonePrimary && cachedPhoneNumber != null && !cachedPhoneNumber.isEmpty()) {
                                userPhone.setText(cachedPhoneNumber);
                            } else if (cachedEmail != null && !cachedEmail.isEmpty()) {
                                userPhone.setText(cachedEmail);
                            } else {
                                userPhone.setText("Not provided");
                            }
                            profileImage.setImageResource(R.drawable.default_profile);
                        }
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(ProfileActivity.this, "Failed to load profile: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                        userName.setText("User");
                        if (isPhonePrimary && cachedPhoneNumber != null && !cachedPhoneNumber.isEmpty()) {
                            userPhone.setText(cachedPhoneNumber);
                        } else if (cachedEmail != null && !cachedEmail.isEmpty()) {
                            userPhone.setText(cachedEmail);
                        } else {
                            userPhone.setText("Not provided");
                        }
                        profileImage.setImageResource(R.drawable.default_profile);
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            } else {
                Toast.makeText(ProfileActivity.this, "Failed to refresh user data: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                logoutAndRedirectToLogin();
            }
        });
    }

    private void logoutAndRedirectToLogin() {
        mAuth.signOut();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
}