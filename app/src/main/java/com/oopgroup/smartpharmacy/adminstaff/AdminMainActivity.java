package com.oopgroup.smartpharmacy.adminstaff;

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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.oopgroup.smartpharmacy.LoginActivity;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.fragments.HomeFragment;

public class AdminMainActivity extends AppCompatActivity {
    private static final String TAG = "AdminMainActivity";

    private FirebaseAuth mAuth;
    public FirebaseUser currentUser;
    private RelativeLayout bottomNav;
    private LinearLayout navDashboard, navOrders, navHome, navDelivery, navManagement;
    private ImageView icDashboard, icOrders, icHome, icDelivery, icManagement;
    private View dashboardIndicator, ordersIndicator, homeIndicator, deliveryIndicator, managementIndicator;
    private TextView dashboardText, ordersText, homeText, deliveryText, managementText;
    private LinearLayout selectedNavItem;
    private ImageView selectedIcon;
    private int backPressCount = 0;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private Toolbar toolbar; // Add Toolbar reference

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.d(TAG, "No authenticated user found, redirecting to login");
            redirectToLogin();
            return;
        }

        toolbar = findViewById(R.id.toolbar); // Initialize Toolbar
        setSupportActionBar(toolbar);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                        if (currentFragment instanceof ManagementFragment) {
                            ((ManagementFragment) currentFragment).handleImageResult(uri);
                        }
                    }
                });

        initializeUI();
        setupNavigationListeners();
        setupBackStackListener();
        setupBackPressedCallback();

        if (savedInstanceState == null) {
            setSelectedNavItem(navDashboard, icDashboard, dashboardIndicator);
            loadFragment(new DashboardFragment(), false); // Load initial fragment
        }
    }

    private void initializeUI() {
        bottomNav = findViewById(R.id.bottomNav);
        navDashboard = findViewById(R.id.navDashboard);
        navOrders = findViewById(R.id.navOrders);
        navHome = findViewById(R.id.navHome);
        navDelivery = findViewById(R.id.navDelivery);
        navManagement = findViewById(R.id.navManagement);

        icDashboard = findViewById(R.id.ic_dashboard);
        icOrders = findViewById(R.id.ic_orders);
        icHome = findViewById(R.id.ic_home);
        icDelivery = findViewById(R.id.ic_delivery);
        icManagement = findViewById(R.id.ic_management);

        dashboardIndicator = findViewById(R.id.dashboardIndicator);
        ordersIndicator = findViewById(R.id.ordersIndicator);
        homeIndicator = findViewById(R.id.homeIndicator);
        deliveryIndicator = findViewById(R.id.deliveryIndicator);
        managementIndicator = findViewById(R.id.managementIndicator);

        dashboardText = findViewById(R.id.dashboardText);
        ordersText = findViewById(R.id.ordersText);
        homeText = findViewById(R.id.homeText);
        deliveryText = findViewById(R.id.deliveryText);
        managementText = findViewById(R.id.managementText);
    }

    private void setupNavigationListeners() {
        navDashboard.setOnClickListener(v -> {
            setSelectedNavItem(navDashboard, icDashboard, dashboardIndicator);
            loadFragment(new DashboardFragment(), false);
            backPressCount = 0;
            showBottomNav();
        });

        navOrders.setOnClickListener(v -> {
            setSelectedNavItem(navOrders, icOrders, ordersIndicator);
            loadFragment(new OrdersManFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navHome.setOnClickListener(v -> {
            Log.d(TAG, "Home clicked, exiting admin panel and redirecting to MainActivity");
            Intent intent = new Intent(AdminMainActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        navDelivery.setOnClickListener(v -> {
            setSelectedNavItem(navDelivery, icDelivery, deliveryIndicator);
            loadFragment(new DeliveryFragment(), true);
            backPressCount = 0;
            showBottomNav();
        });

        navManagement.setOnClickListener(v -> {
            setSelectedNavItem(navManagement, icManagement, managementIndicator);
            loadFragment(new ManagementFragment(), true);
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

        // Update Toolbar title based on fragment (except for Home)
        updateToolbarTitle(fragment);
        updateBottomNavForFragment(fragment);
    }

    private void updateToolbarTitle(Fragment fragment) {
        if (fragment instanceof DashboardFragment) {
            toolbar.setTitle("Dashboard");
        } else if (fragment instanceof OrdersManFragment) {
            toolbar.setTitle("Orders Management");
        } else if (fragment instanceof DeliveryFragment) {
            toolbar.setTitle("Delivery");
        } else if (fragment instanceof ManagementFragment) {
            toolbar.setTitle("Management");
        } else if (fragment instanceof HomeFragment) {
            // Do not change title for Home fragment
            toolbar.setTitle(""); // Or keep the default title from XML
        }
    }

    private void setSelectedNavItem(LinearLayout newSelectedLayout, ImageView newSelectedIcon, View indicator) {
        dashboardIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        ordersIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        homeIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        deliveryIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        managementIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));

        if (selectedNavItem != null && selectedIcon != null) {
            selectedIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive));
            TextView textView = getTextView(selectedNavItem);
            if (textView != null) {
                textView.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive));
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selectedIcon.getLayoutParams();
            params.width = dpToPx(24);
            params.height = dpToPx(24);
            selectedIcon.setLayoutParams(params);
        }

        selectedNavItem = newSelectedLayout;
        selectedIcon = newSelectedIcon;

        selectedIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_active));
        TextView textView = getTextView(selectedNavItem);
        if (textView != null) {
            textView.setTextColor(ContextCompat.getColor(this, R.color.nav_active));
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selectedIcon.getLayoutParams();
        params.width = dpToPx(28);
        params.height = dpToPx(28);
        selectedIcon.setLayoutParams(params);
        indicator.setBackgroundColor(ContextCompat.getColor(this, R.color.nav_active));
    }

    private TextView getTextView(LinearLayout layout) {
        if (layout == navDashboard) return dashboardText;
        if (layout == navOrders) return ordersText;
        if (layout == navHome) return homeText;
        if (layout == navDelivery) return deliveryText;
        if (layout == navManagement) return managementText;
        return null;
    }

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
                    getSupportFragmentManager().popBackStack();
                } else if (currentFragment instanceof DashboardFragment) {
                    backPressCount++;
                    if (backPressCount == 1) {
                        Toast.makeText(AdminMainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    } else if (backPressCount >= 2) {
                        finish();
                    }
                } else {
                    setSelectedNavItem(navDashboard, icDashboard, dashboardIndicator);
                    loadFragment(new DashboardFragment(), false);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void updateBottomNavOnBack() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        updateBottomNavForFragment(currentFragment);
        updateToolbarTitle(currentFragment); // Update title on back navigation
    }

    private void updateBottomNavForFragment(Fragment currentFragment) {
        if (currentFragment != null) {
            Log.d(TAG, "Updating bottom nav for fragment: " + currentFragment.getClass().getSimpleName());
            if (currentFragment instanceof DashboardFragment) {
                setSelectedNavItem(navDashboard, icDashboard, dashboardIndicator);
                showBottomNav();
            } else if (currentFragment instanceof OrdersManFragment) {
                setSelectedNavItem(navOrders, icOrders, ordersIndicator);
                showBottomNav();
            } else if (currentFragment instanceof HomeFragment) {
                setSelectedNavItem(navHome, icHome, homeIndicator);
                showBottomNav();
            } else if (currentFragment instanceof DeliveryFragment) {
                setSelectedNavItem(navDelivery, icDelivery, deliveryIndicator);
                showBottomNav();
            } else if (currentFragment instanceof ManagementFragment) {
                setSelectedNavItem(navManagement, icManagement, managementIndicator);
                showBottomNav();
            } else {
                hideBottomNav();
            }
        } else {
            hideBottomNav();
        }
    }

    public void showBottomNav() {
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
            Log.d(TAG, "Bottom nav shown");
        }
    }

    public void hideBottomNav() {
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
            Log.d(TAG, "Bottom nav hidden");
        }
    }

    public ActivityResultLauncher<String> getImagePickerLauncher() {
        return imagePickerLauncher;
    }

    private void redirectToLogin() {
        Toast.makeText(this, "Please log in to access admin panel.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}