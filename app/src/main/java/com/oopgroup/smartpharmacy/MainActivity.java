package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.firebase.auth.FirebaseAuth;
import com.oopgroup.smartpharmacy.fragments.CartFragment;
import com.oopgroup.smartpharmacy.fragments.HomeFragment;
import com.oopgroup.smartpharmacy.fragments.LabTestDetailsFragment;
import com.oopgroup.smartpharmacy.fragments.LabTestFragment;
import com.oopgroup.smartpharmacy.fragments.NavCategoriesFragment;
import com.oopgroup.smartpharmacy.fragments.NoInternetFragment;
import com.oopgroup.smartpharmacy.fragments.ProductDetailsFragment;
import com.oopgroup.smartpharmacy.fragments.ProfileFragment;
import com.oopgroup.smartpharmacy.fragments.ScannerFragment;
import com.oopgroup.smartpharmacy.utils.BaseActivity;
import com.oopgroup.smartpharmacy.utils.NetworkUtils;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private static final long TOAST_COOLDOWN = 2000; // 2 seconds

    private RelativeLayout bottomNav;
    private LinearLayout navHome, navCategories, navScanner, navLabTest, navProfile;
    private ImageView icHome, icCategories, icScanner, icLabTest, icProfile;
    private View homeIndicator, categoriesIndicator, scannerIndicator, labTestIndicator, profileIndicator;
    private TextView homeText, categoriesText, labTestText, profileText;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;
    private int backPressCount = 0;
    private long lastToastTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        setupNavigationListeners();
        setupBackStackListener();
        setupBackPressedCallback();

        // Check network status
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("No internet. Please connect and try again.");
            loadFragment(new NoInternetFragment(), false);
            return;
        }

        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("SHOW_PROFILE_FRAGMENT", false)) {
            Log.d(TAG, "Received intent to show ProfileFragment");
            navigateToProfile();
        } else if (savedInstanceState == null) {
            setSelectedNavItem(navHome, icHome, homeIndicator);
            loadFragment(new HomeFragment(), false);
        }
    }

    private void initializeUI() {
        bottomNav = findViewById(R.id.bottomNav);
        navHome = findViewById(R.id.navHome);
        navCategories = findViewById(R.id.navCategories);
        navScanner = findViewById(R.id.navScanner);
        navLabTest = findViewById(R.id.navLabTest);
        navProfile = findViewById(R.id.navProfile);

        icHome = findViewById(R.id.ic_home);
        icCategories = findViewById(R.id.ic_categories);
        icScanner = findViewById(R.id.ic_scanner);
        icLabTest = findViewById(R.id.ic_lab_test);
        icProfile = findViewById(R.id.ic_profile);

        homeIndicator = findViewById(R.id.homeIndicator);
        categoriesIndicator = findViewById(R.id.categoriesIndicator);
        scannerIndicator = findViewById(R.id.scannerIndicator);
        labTestIndicator = findViewById(R.id.labTestIndicator);
        profileIndicator = findViewById(R.id.profileIndicator);

        homeText = findViewById(R.id.homeText);
        categoriesText = findViewById(R.id.categoriesText);
        labTestText = findViewById(R.id.labTestText);
        profileText = findViewById(R.id.profileText);
    }

    private void setupNavigationListeners() {
        navHome.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                showToast("No internet. Please try again later.");
                return;
            }
            setSelectedNavItem(navHome, icHome, homeIndicator);
            loadFragment(new HomeFragment(), false);
            backPressCount = 0;
            showBottomNav();
        });

        navCategories.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                showToast("No internet. Please try again later.");
                return;
            }
            setSelectedNavItem(navCategories, icCategories, categoriesIndicator);
            loadFragment(new NavCategoriesFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navScanner.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                showToast("No internet. Please try again later.");
                return;
            }
            setSelectedNavItem(navScanner, icScanner, scannerIndicator);
            loadFragment(new ScannerFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navLabTest.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                showToast("No internet. Please try again later.");
                return;
            }
            setSelectedNavItem(navLabTest, icLabTest, labTestIndicator);
            loadFragment(new LabTestFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navProfile.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                showToast("No internet. Please try again later.");
                return;
            }
            setSelectedNavItem(navProfile, icProfile, profileIndicator);
            loadFragment(new ProfileFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        if (isFinishing()) {
            Log.w(TAG, "Activity is finishing, skipping fragment load");
            return;
        }
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment);
        if (addToBackStack) {
            transaction.addToBackStack(fragment.getClass().getSimpleName());
        }
        try {
            transaction.commit();
            updateBottomNavForFragment(fragment);
            Log.d(TAG, "Loaded fragment: " + fragment.getClass().getSimpleName());
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to commit fragment transaction: " + e.getMessage());
        }
    }

    public void navigateToProfile() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("No internet. Please try again later.");
            return;
        }
        Log.d(TAG, "Navigating to ProfileFragment");
        setSelectedNavItem(navProfile, icProfile, profileIndicator);
        loadFragment(new ProfileFragment(), true);
        backPressCount = 0;
        showBottomNav();
    }

    public void navigateToHome() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast("No internet. Please try again later.");
            return;
        }
        Log.d(TAG, "Navigating to HomeFragment");
        setSelectedNavItem(navHome, icHome, homeIndicator);
        loadFragment(new HomeFragment(), false);
        backPressCount = 0;
        showBottomNav();
    }

    public void handleLogout() {
        FirebaseAuth.getInstance().signOut();
        // CHANGE: Clear back stack before finishing to prevent FragmentManager issues
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (!isFinishing() && fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Log.d(TAG, "User logged out, navigating to LoginActivity");
    }

    private void setSelectedNavItem(LinearLayout newSelectedLayout, ImageView newSelectedIcon, View indicator) {
        if (isFinishing()) return;

        // Reset all indicators
        homeIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        categoriesIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        scannerIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        labTestIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        profileIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

        // Reset previous selection
        if (selectedNavItem != null && selectedIcon != null) {
            if (selectedNavItem != navScanner) {
                selectedIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive));
                TextView textView = getTextView(selectedNavItem);
                if (textView != null) {
                    textView.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive));
                }
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selectedIcon.getLayoutParams();
            params.width = dpToPx(24);
            params.height = dpToPx(24);
            selectedIcon.setLayoutParams(params);

            if (selectedNavItem == navScanner) {
                ((LinearLayout) selectedNavItem.getChildAt(1)).setBackgroundResource(android.R.color.transparent);
            }
        }

        // Set new selection
        selectedNavItem = newSelectedLayout;
        selectedIcon = newSelectedIcon;

        if (selectedNavItem != navScanner) {
            selectedIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_active));
            TextView textView = getTextView(selectedNavItem);
            if (textView != null) {
                textView.setTextColor(ContextCompat.getColor(this, R.color.nav_active));
            }
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selectedIcon.getLayoutParams();
        params.width = dpToPx(28);
        params.height = dpToPx(28);
        selectedIcon.setLayoutParams(params);
        indicator.setBackgroundColor(ContextCompat.getColor(this, R.color.nav_active));

        if (selectedNavItem == navScanner) {
            ((LinearLayout) selectedNavItem.getChildAt(1)).setBackgroundResource(R.drawable.scan_bg);
        }
    }

    private TextView getTextView(LinearLayout layout) {
        if (layout == navHome) return homeText;
        if (layout == navCategories) return categoriesText;
        if (layout == navLabTest) return labTestText;
        if (layout == navProfile) return profileText;
        return null;
    }

    @Override
    public int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void setupBackStackListener() {
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (isFinishing()) return;
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            Log.d(TAG, "Back stack changed, currentFragment=" + (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null"));
            updateBottomNavForFragment(currentFragment);
        });
    }

    private void setupBackPressedCallback() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFinishing()) return;

                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                Log.d(TAG, "handleOnBackPressed: currentFragment=" + (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null") +
                        ", backPressCount=" + backPressCount);

                // Handle NoInternetFragment
                if (currentFragment instanceof NoInternetFragment) {
                    if (!NetworkUtils.isNetworkAvailable(MainActivity.this)) {
                        showToast("Please connect to the internet");
                        return;
                    }
                    navigateToHome();
                    return;
                }

                int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
                Log.d(TAG, "Back stack count: " + backStackCount);

                if (backStackCount > 0) {
                    Log.d(TAG, "Popping back stack");
                    getSupportFragmentManager().popBackStack();
                } else if (currentFragment instanceof HomeFragment) {
                    backPressCount++;
                    Log.d(TAG, "On HomeFragment, backPressCount=" + backPressCount);
                    if (backPressCount == 1) {
                        showToast("Press back again to exit");
                    } else if (backPressCount >= 2) {
                        Log.d(TAG, "Finishing activity");
                        finish();
                    }
                } else {
                    Log.d(TAG, "Navigating to HomeFragment");
                    navigateToHome();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void updateBottomNavForFragment(Fragment currentFragment) {
        if (isFinishing()) return;
        if (currentFragment != null) {
            Log.d(TAG, "Updating bottom nav for fragment: " + currentFragment.getClass().getSimpleName());
            if (currentFragment instanceof HomeFragment) {
                setSelectedNavItem(navHome, icHome, homeIndicator);
                showBottomNav();
            } else if (currentFragment instanceof NavCategoriesFragment) {
                setSelectedNavItem(navCategories, icCategories, categoriesIndicator);
                showBottomNav();
            } else if (currentFragment instanceof ScannerFragment) {
                setSelectedNavItem(navScanner, icScanner, scannerIndicator);
                showBottomNav();
            } else if (currentFragment instanceof LabTestFragment || currentFragment instanceof LabTestDetailsFragment) {
                setSelectedNavItem(navLabTest, icLabTest, labTestIndicator);
                showBottomNav();
            } else if (currentFragment instanceof ProfileFragment) {
                setSelectedNavItem(navProfile, icProfile, profileIndicator);
                showBottomNav();
            } else if (currentFragment instanceof ProductDetailsFragment || currentFragment instanceof CartFragment ||
                    currentFragment instanceof NoInternetFragment) {
                hideBottomNav();
                Log.d(TAG, "Bottom nav hidden for " + currentFragment.getClass().getSimpleName());
            } else {
                showBottomNav();
                Log.d(TAG, "Bottom nav shown for unhandled fragment: " + currentFragment.getClass().getSimpleName());
            }
        } else {
            hideBottomNav();
            Log.d(TAG, "No current fragment, hiding bottom nav");
        }
    }

    public void showBottomNav() {
        if (isFinishing() || bottomNav == null) {
            Log.e(TAG, "Cannot show bottomNav: activity finishing or bottomNav null");
            return;
        }
        bottomNav.setVisibility(View.VISIBLE);
        Log.d(TAG, "Bottom nav shown");
    }

    public void hideBottomNav() {
        if (isFinishing() || bottomNav == null) {
            Log.e(TAG, "Cannot hide bottomNav: activity finishing or bottomNav null");
            return;
        }
        bottomNav.setVisibility(View.GONE);
        Log.d(TAG, "Bottom nav hidden in MainActivity");
    }

    private void showToast(String message) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToastTime >= TOAST_COOLDOWN) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            lastToastTime = currentTime;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // CHANGE: Removed back stack clearing to prevent IllegalStateException
        Log.d(TAG, "onDestroy: Activity destroyed");
    }
}