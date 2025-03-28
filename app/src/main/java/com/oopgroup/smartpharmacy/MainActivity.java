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

import com.oopgroup.smartpharmacy.fragments.NavCategoriesFragment;
import com.oopgroup.smartpharmacy.fragments.HomeFragment;
import com.oopgroup.smartpharmacy.fragments.LabTestFragment;
import com.oopgroup.smartpharmacy.fragments.ProfileFragment;
import com.oopgroup.smartpharmacy.fragments.ScannerFragment;
import com.oopgroup.smartpharmacy.utils.BaseActivity;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";

    private RelativeLayout bottomNav;
    private LinearLayout navHome, navCategories, navScanner, navLabTest, navProfile;
    private ImageView icHome, icCategories, icScanner, icLabTest, icProfile;
    private View homeIndicator, categoriesIndicator, scannerIndicator, labTestIndicator, profileIndicator;
    private TextView homeText, categoriesText, labTestText, profileText;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;
    private int backPressCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        setupNavigationListeners();
        setupBackStackListener(); // Add back stack listener

        // Set up the modern back press handling
        setupBackPressedCallback();

        // Handle intent extras for navigation
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("SHOW_PROFILE_FRAGMENT", false)) {
            Log.d(TAG, "Received intent to show ProfileFragment");
            navigateToProfile();
        } else if (savedInstanceState == null) {
            setSelectedNavItem(navHome, icHome, homeIndicator);
            loadFragment(new HomeFragment(), false); // Don't add to back stack initially
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
            setSelectedNavItem(navHome, icHome, homeIndicator);
            loadFragment(new HomeFragment(), false); // Don't add to back stack
            backPressCount = 0;
            showBottomNav();
        });

        navCategories.setOnClickListener(v -> {
            setSelectedNavItem(navCategories, icCategories, categoriesIndicator);
            loadFragment(new NavCategoriesFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navScanner.setOnClickListener(v -> {
            setSelectedNavItem(navScanner, icScanner, scannerIndicator);
            loadFragment(new ScannerFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navLabTest.setOnClickListener(v -> {
            setSelectedNavItem(navLabTest, icLabTest, labTestIndicator);
            loadFragment(new LabTestFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navProfile.setOnClickListener(v -> {
            setSelectedNavItem(navProfile, icProfile, profileIndicator);
            loadFragment(new ProfileFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    public void navigateToProfile() {
        Log.d(TAG, "Navigating to ProfileFragment");
        setSelectedNavItem(navProfile, icProfile, profileIndicator);
        loadFragment(new ProfileFragment(), true);
        backPressCount = 0;
        showBottomNav();
    }

    public void navigateToHome() {
        Log.d(TAG, "Navigating to HomeFragment");
        setSelectedNavItem(navHome, icHome, homeIndicator);
        loadFragment(new HomeFragment(), false); // Don't add to back stack
        backPressCount = 0;
        showBottomNav();
    }

    private void setSelectedNavItem(LinearLayout newSelectedLayout, ImageView newSelectedIcon, View indicator) {
        homeIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        categoriesIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        scannerIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        labTestIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        profileIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

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
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            Log.d(TAG, "Back stack changed, currentFragment=" + (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null"));
            updateBottomNavOnBack();
        });
    }

    private void setupBackPressedCallback() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                Log.d(TAG, "handleOnBackPressed: currentFragment=" + (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null") +
                        ", backPressCount=" + backPressCount);

                int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
                Log.d(TAG, "Back stack count: " + backStackCount);

                if (backStackCount > 0) {
                    // Pop the back stack to go to the previous fragment
                    Log.d(TAG, "Popping back stack");
                    getSupportFragmentManager().popBackStack();
                    // Note: updateBottomNavOnBack is now handled by the back stack listener
                } else if (currentFragment instanceof HomeFragment) {
                    // If on HomeFragment, handle exit logic
                    backPressCount++;
                    Log.d(TAG, "On HomeFragment, backPressCount=" + backPressCount);
                    if (backPressCount == 1) {
                        Toast.makeText(MainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    } else if (backPressCount >= 2) {
                        Log.d(TAG, "Finishing activity");
                        finish();
                    }
                } else {
                    // If no back stack and not on HomeFragment, go to HomeFragment
                    Log.d(TAG, "Navigating to HomeFragment");
                    navigateToHome();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void updateBottomNavOnBack() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            Log.d(TAG, "Updating bottom nav for fragment: " + currentFragment.getClass().getSimpleName());
            if (currentFragment instanceof HomeFragment) {
                setSelectedNavItem(navHome, icHome, homeIndicator);
            } else if (currentFragment instanceof NavCategoriesFragment) {
                setSelectedNavItem(navCategories, icCategories, categoriesIndicator);
            } else if (currentFragment instanceof ScannerFragment) {
                setSelectedNavItem(navScanner, icScanner, scannerIndicator);
            } else if (currentFragment instanceof LabTestFragment) {
                setSelectedNavItem(navLabTest, icLabTest, labTestIndicator);
            } else if (currentFragment instanceof ProfileFragment) {
                setSelectedNavItem(navProfile, icProfile, profileIndicator);
            }
            // Note: Fragments like ProductDetailsFragment or ProductsFragment won't update the bottom nav
            // as they are not directly tied to a bottom nav item
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed(); // Call super to let dispatcher handle it
    }

    public void showBottomNav() {
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
    }

    public void hideBottomNav() {
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
    }
}