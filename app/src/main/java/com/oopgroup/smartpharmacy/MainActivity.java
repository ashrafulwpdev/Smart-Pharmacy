package com.oopgroup.smartpharmacy;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.oopgroup.smartpharmacy.fragments.CategoryFragment;
import com.oopgroup.smartpharmacy.fragments.HomeFragment;
import com.oopgroup.smartpharmacy.fragments.LabTestFragment;
import com.oopgroup.smartpharmacy.fragments.ProfileFragment;
import com.oopgroup.smartpharmacy.fragments.ScannerFragment;

public class MainActivity extends AppCompatActivity {

    private LinearLayout navHome, navCategories, navScanner, navLabTest, navProfile;
    private ImageView icHome, icCategories, icScanner, icLabTest, icProfile;
    private View homeIndicator, categoriesIndicator, scannerIndicator, labTestIndicator, profileIndicator;
    private TextView homeText, categoriesText, labTestText, profileText;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;
    private int backPressCount = 0; // Track back presses on HomeFragment

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        setupNavigationListeners();

        if (savedInstanceState == null) {
            setSelectedNavItem(navHome, icHome, homeIndicator);
            loadFragment(new HomeFragment());
        }
    }

    private void initializeUI() {
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
            loadFragment(new HomeFragment());
            backPressCount = 0; // Reset counter when navigating to Home
        });

        navCategories.setOnClickListener(v -> {
            setSelectedNavItem(navCategories, icCategories, categoriesIndicator);
            loadFragment(new CategoryFragment());
            backPressCount = 0; // Reset counter
        });

        navScanner.setOnClickListener(v -> {
            setSelectedNavItem(navScanner, icScanner, scannerIndicator);
            loadFragment(new ScannerFragment());
            backPressCount = 0; // Reset counter
        });

        navLabTest.setOnClickListener(v -> {
            setSelectedNavItem(navLabTest, icLabTest, labTestIndicator);
            loadFragment(new LabTestFragment());
            backPressCount = 0; // Reset counter
        });

        navProfile.setOnClickListener(v -> {
            setSelectedNavItem(navProfile, icProfile, profileIndicator);
            loadFragment(new ProfileFragment());
            backPressCount = 0; // Reset counter
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void navigateToProfile() {
        setSelectedNavItem(navProfile, icProfile, profileIndicator);
        loadFragment(new ProfileFragment());
        backPressCount = 0; // Reset counter
    }

    public void navigateToHome() {
        setSelectedNavItem(navHome, icHome, homeIndicator);
        loadFragment(new HomeFragment());
        backPressCount = 0; // Reset counter
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
        return null; // Scanner has no TextView
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (!(currentFragment instanceof HomeFragment)) {
            navigateToHome();
        } else {
            backPressCount++;
            if (backPressCount == 1) {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
            } else if (backPressCount >= 2) {
                finish();
            }
        }
        super.onBackPressed(); // Add this to satisfy the warning
    }
}