package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.oopgroup.smartpharmacy.utils.LoadingSpinnerUtil;
import com.oopgroup.smartpharmacy.utils.LogoutConfirmationDialog;

/**
 * AdminActivity: Core activity class managing lifecycle, navigation, and high-level control.
 */
public class AdminActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "AdminActivity";

    // UI Components
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Helpers
    private AdminDataManager dataManager;
    private AdminUIHelper uiHelper;
    private LoadingSpinnerUtil loadingSpinnerUtil;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.d(TAG, "No authenticated user found, redirecting to login");
            redirectToLogin();
            return;
        }

        Log.d(TAG, "Current User UID: " + currentUser.getUid());

        // Setup UI components
        setupToolbarAndDrawer();

        // Initialize Loading Spinner
        LottieAnimationView loadingSpinner = findViewById(R.id.loadingSpinner);
        if (loadingSpinner == null) {
            Log.e(TAG, "Loading spinner view not found in layout");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadingSpinnerUtil = new LoadingSpinnerUtil(this, loadingSpinner);

        // Initialize SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout == null) {
            Log.e(TAG, "SwipeRefreshLayout not found in layout");
        } else {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (isAdmin && uiHelper != null) {
                    uiHelper.refreshCurrentSection();
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }

        // Initialize Helpers
        dataManager = new AdminDataManager(this, currentUser, loadingSpinnerUtil);
        uiHelper = new AdminUIHelper(this, dataManager, swipeRefreshLayout);

        // Check User Role and Proceed
        checkUserRole();
    }

    /** Sets up the toolbar and navigation drawer. */
    private void setupToolbarAndDrawer() {
        toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) {
            Log.e(TAG, "Toolbar not found in layout");
            finish();
            return;
        }
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawerLayout);
        if (drawerLayout == null) {
            Log.e(TAG, "DrawerLayout not found in layout");
            finish();
            return;
        }

        navView = findViewById(R.id.navView);
        if (navView == null) {
            Log.e(TAG, "NavigationView not found in layout");
            finish();
            return;
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navView.setNavigationItemSelectedListener(this);
    }

    /** Verifies if the user is an admin and initializes UI if true. */
    private void checkUserRole() {
        loadingSpinnerUtil.toggleLoadingSpinner(true); // Show spinner while checking role
        dataManager.checkUserRole(
                role -> {
                    loadingSpinnerUtil.toggleLoadingSpinner(false);
                    isAdmin = "admin".equalsIgnoreCase(role); // Case-insensitive check
                    if (isAdmin) {
                        Log.d(TAG, "User confirmed as admin, initializing UI");
                        uiHelper.initializeUI();
                    } else {
                        Log.w(TAG, "User is not an admin, role: " + role);
                        Toast.makeText(this, "Access denied. Admins only.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                },
                error -> {
                    loadingSpinnerUtil.toggleLoadingSpinner(false);
                    Log.e(TAG, "Failed to check role: " + error);
                    Toast.makeText(this, "Error verifying role: " + error, Toast.LENGTH_LONG).show();
                    finish();
                }
        );
    }

    /** Redirects to LoginActivity if user is not authenticated. */
    private void redirectToLogin() {
        Toast.makeText(this, "Please log in to access admin panel.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (!isAdmin || uiHelper == null) {
            Log.w(TAG, "Navigation item selected but user is not admin or UI not initialized");
            drawerLayout.closeDrawer(GravityCompat.START);
            return false;
        }

        int itemId = item.getItemId();
        if (itemId == R.id.nav_banner) {
            uiHelper.showSection("banner");
        } else if (itemId == R.id.nav_categories) {
            uiHelper.showSection("categories");
        } else if (itemId == R.id.nav_lab_tests) {
            uiHelper.showSection("labTests");
        } else if (itemId == R.id.nav_products) {
            uiHelper.showSection("products");
        } else if (itemId == R.id.nav_users) {
            uiHelper.showSection("users");
        } else if (itemId == R.id.nav_orders) {
            uiHelper.showSection("orders");
        } else if (itemId == R.id.nav_notifications) {
            uiHelper.showSection("notifications");
        } else if (itemId == R.id.nav_scanner) {
            uiHelper.showSection("scanner");
        } else if (itemId == R.id.nav_logout) {
            showLogoutConfirmationDialog();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /** Displays logout confirmation dialog. */
    private void showLogoutConfirmationDialog() {
        LogoutConfirmationDialog dialog = new LogoutConfirmationDialog(this, this::logoutAndRedirectToLogin);
        dialog.show();
    }

    /** Logs out the user and redirects to LoginActivity. */
    private void logoutAndRedirectToLogin() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            Log.d(TAG, "Drawer open, closing it");
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            Log.d(TAG, "Drawer closed, proceeding with default back press");
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadingSpinnerUtil != null) {
            loadingSpinnerUtil.cleanup(); // Ensure spinner is cleaned up
        }
    }
}