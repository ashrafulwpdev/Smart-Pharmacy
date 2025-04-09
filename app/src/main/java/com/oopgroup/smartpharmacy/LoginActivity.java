package com.oopgroup.smartpharmacy;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.oopgroup.smartpharmacy.adminstaff.AdminActivity;
import com.oopgroup.smartpharmacy.fragments.LoginFragment;
import com.softourtech.slt.SLTLoader;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth mAuth;
    private SLTLoader sltLoader; // Add SLTLoader instance

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize SLTLoader with the fragment_container as root view
        FrameLayout rootView = findViewById(R.id.fragment_container);
        sltLoader = new SLTLoader(this, rootView);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            checkUserRoleAndRedirect(currentUser);
        } else {
            loadLoginFragment();
        }
    }

    private void loadLoginFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new LoginFragment());
        transaction.commit();
    }

    private void checkUserRoleAndRedirect(FirebaseUser user) {
        sltLoader.showDefaultLoader(R.raw.loading_spinner); // Show loader
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    sltLoader.hideLoader(); // Hide loader
                    String role = documentSnapshot.getString("role");
                    if (role == null) role = "customer"; // Default to customer if not set
                    Intent intent;
                    switch (role) {
                        case "admin":
                            intent = new Intent(this, AdminActivity.class);
                            break;
                        case "pharmacist":
                            intent = new Intent(this, MainActivity.class);
                            Log.w(TAG, "Pharmacist role detected, redirecting to MainActivity (placeholder)");
                            break;
                        case "delivery_staff":
                            intent = new Intent(this, MainActivity.class);
                            Log.w(TAG, "Delivery Staff role detected, redirecting to MainActivity (placeholder)");
                            break;
                        case "customer":
                        default:
                            intent = new Intent(this, MainActivity.class);
                            break;
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    sltLoader.hideLoader(); // Hide loader on failure
                    Log.e(TAG, "Failed to fetch user role: " + e.getMessage(), e);
                    Intent intent = new Intent(this, MainActivity.class); // Default to customer
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() != null && getSupportFragmentManager().findFragmentById(R.id.fragment_container) instanceof LoginFragment) {
            checkUserRoleAndRedirect(mAuth.getCurrentUser());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sltLoader.onDestroy(); // Clean up SLTLoader
    }
}