package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AccelerateDecelerateInterpolator; // Added missing import
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ProfileActivity extends AppCompatActivity {
    private LinearLayout navHome;
    private LinearLayout navCategories;
    private LinearLayout navScanner;
    private LinearLayout navProfile;
    private LinearLayout navLabTest;

    private ImageView icHome;
    private ImageView icCategories;
    private ImageView icScanner;
    private ImageView icProfile;
    private ImageView icLabTest;

    private View homeIndicator;
    private View categoriesIndicator;
    private View scannerIndicator;
    private View labTestIndicator;
    private View profileIndicator;

    // Track the currently selected item
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize navigation items and indicators
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

        // Set the initial state (Profile is active by default in ProfileActivity)
        setSelectedNavItem(navProfile, icProfile, profileIndicator);

        // Set click listeners for navigation
        navHome.setOnClickListener(v -> {
            setSelectedNavItem(navHome, icHome, homeIndicator);
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });

        navCategories.setOnClickListener(v -> {
            setSelectedNavItem(navCategories, icCategories, categoriesIndicator);
            // Navigate to Categories screen (placeholder)
        });

        navScanner.setOnClickListener(v -> {
            setSelectedNavItem(navScanner, icScanner, scannerIndicator);
            // Navigate to Scanner screen (placeholder)
        });

        navProfile.setOnClickListener(v -> {
            setSelectedNavItem(navProfile, icProfile, profileIndicator);
            // Already in Profile screen, no navigation needed
        });

        navLabTest.setOnClickListener(v -> {
            setSelectedNavItem(navLabTest, icLabTest, labTestIndicator);
            // Navigate to Lab Test screen (placeholder)
        });

        // Add click animation to menu items
        LinearLayout menuItems = findViewById(R.id.menuItems);
        for (int i = 0; i < menuItems.getChildCount(); i++) {
            View menuItem = menuItems.getChildAt(i);
            menuItem.setOnClickListener(v -> {
                // Smooth scale animation for premium feel
                ScaleAnimation scaleAnimation = new ScaleAnimation(
                        1.0f, 0.97f, 1.0f, 0.97f, // Slight scale down for subtle effect
                        Animation.RELATIVE_TO_SELF, 0.5f, // Pivot X (center)
                        Animation.RELATIVE_TO_SELF, 0.5f  // Pivot Y (center)
                );
                scaleAnimation.setDuration(150); // Slower for elegance
                scaleAnimation.setRepeatMode(Animation.REVERSE);
                scaleAnimation.setRepeatCount(1);
                scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator()); // Smooth curve
                v.startAnimation(scaleAnimation);
                // Add your navigation logic here later
            });
        }
    }

    private void setSelectedNavItem(LinearLayout newSelectedLayout, ImageView newSelectedIcon, View indicator) {
        // Reset all indicators to transparent (hidden)
        homeIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        categoriesIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        scannerIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        labTestIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        profileIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

        // Reset the previous selected item's styling
        if (selectedNavItem != null && selectedIcon != null) {
            selectedNavItem.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            if (selectedNavItem != navScanner) {  // Skip tint reset for scanner
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

        // Set the new selected item
        selectedNavItem = newSelectedLayout;
        selectedIcon = newSelectedIcon;

        // Apply active state styling
        selectedNavItem.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        if (selectedNavItem != navScanner) {  // Skip tint for scanner
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
}