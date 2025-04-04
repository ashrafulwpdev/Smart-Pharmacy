package com.oopgroup.smartpharmacy.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.oopgroup.smartpharmacy.ChangePasswordActivity;
import com.oopgroup.smartpharmacy.EditProfileActivity;
import com.oopgroup.smartpharmacy.LoginActivity;
import com.oopgroup.smartpharmacy.ProfileAuthHelper;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adminstaff.AdminActivity;
import com.oopgroup.smartpharmacy.adminstaff.AdminMainActivity;
import com.oopgroup.smartpharmacy.utils.LogoutConfirmationDialog;
import com.softourtech.slt.SLTLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProfileFragment extends Fragment {

    private static final int IMAGE_SIZE = 120;
    private static final int INNER_SIZE = IMAGE_SIZE - 6;
    private static final String PREFS_NAME = "UserProfile";
    private static final String KEY_DATA_FRESH = "data_fresh";
    private static final String KEY_LAST_FETCH_SUCCESS = "last_fetch_success";
    private static final int EDIT_PROFILE_REQUEST = 1;
    private static final long VERIFICATION_TIMEOUT = 10 * 60 * 1000;

    // UI Elements
    private RelativeLayout backButton;
    private ImageView profileImage;
    private TextView profileTitle, userName, userPhone, verificationStatus, verificationTimer;
    private RelativeLayout editProfileLayout, changePasswordLayout, allOrdersLayout, paymentsLayout, notificationsLayout, termsLayout;
    private Button logoutButton, cancelVerificationButton, adminButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout rootLayout;
    private View rootView;

    // Firebase and data
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private DatabaseReference databaseReference;
    private FirebaseUser currentUser;
    private ProfileAuthHelper authHelper;

    private String cachedFullName, cachedPhoneNumber, cachedEmail, cachedImageUrl, cachedUsername;
    private String signInMethod = "email";
    private String pendingEmail = "";
    private String pendingPhoneNumber = "";

    private SharedPreferences sharedPreferences;
    private CountDownTimer countdownTimer;

    // SLTLoader instance
    private SLTLoader sltLoader;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load cached data
        cachedFullName = sharedPreferences.getString("fullName", "User");
        cachedPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        cachedEmail = sharedPreferences.getString("email", "");
        cachedImageUrl = sharedPreferences.getString("imageUrl", "");
        cachedUsername = sharedPreferences.getString("username", "");
        signInMethod = sharedPreferences.getString("signInMethod", "email");
        pendingEmail = sharedPreferences.getString("pendingEmail", "");
        pendingPhoneNumber = sharedPreferences.getString("pendingPhoneNumber", "");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view your profile.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        // Initialize UI
        initializeUI(view);

        // Initialize SLTLoader with activity's content view
        View activityRoot = requireActivity().findViewById(android.R.id.content);
        if (activityRoot == null || !(activityRoot instanceof ViewGroup)) {
            Log.e("ProfileFragment", "Activity root view (android.R.id.content) not found or not a ViewGroup");
            return;
        }
        sltLoader = new SLTLoader(requireContext(), (ViewGroup) activityRoot);
        sltLoader.setLoaderCallback(new SLTLoader.LoaderCallback() {
            @Override
            public void onLoaderShown() {
                Log.d("ProfileFragment", "Loader shown!");
            }

            @Override
            public void onLoaderHidden() {
                Log.d("ProfileFragment", "Loader hidden!");
            }
        });

        // Set up listeners
        setupMenuListeners();
        setupLogoutListener();
        setupAdminButton();
        swipeRefreshLayout.setOnRefreshListener(this::onRefresh);
        backButton.setOnClickListener(v -> requireActivity().onBackPressed());

        // Display initial data
        displayCachedProfileData();
        showCustomLoader();
        loadProfileDataFromFirebase();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() == null) {
            logoutAndRedirectToLogin();
        } else {
            currentUser = mAuth.getCurrentUser();
            showCustomLoader();
            loadProfileDataFromFirebase();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_PROFILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
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
            showCustomLoader();
            loadProfileDataFromFirebase();
        }
    }

    private void initializeUI(View view) {
        backButton = view.findViewById(R.id.backButton);
        profileTitle = view.findViewById(R.id.profileTitle);
        profileImage = view.findViewById(R.id.profileImage);
        userName = view.findViewById(R.id.userName);
        userPhone = view.findViewById(R.id.userPhone);
        verificationStatus = view.findViewById(R.id.verificationStatus);
        verificationTimer = view.findViewById(R.id.verificationTimer);
        editProfileLayout = view.findViewById(R.id.editProfileLayout);
        changePasswordLayout = view.findViewById(R.id.changePasswordLayout);
        allOrdersLayout = view.findViewById(R.id.allOrdersLayout);
        paymentsLayout = view.findViewById(R.id.paymentsLayout);
        notificationsLayout = view.findViewById(R.id.notificationsLayout);
        termsLayout = view.findViewById(R.id.termsLayout);
        logoutButton = view.findViewById(R.id.logoutButton);
        cancelVerificationButton = view.findViewById(R.id.cancelVerificationButton);
        adminButton = view.findViewById(R.id.adminButton);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
    }

    private void setupMenuListeners() {
        editProfileLayout.setOnClickListener(v -> {
            animateClick(v);
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            intent.putExtra("currentImageUrl", cachedImageUrl);
            intent.putExtra("fullName", cachedFullName);
            intent.putExtra("phoneNumber", cachedPhoneNumber);
            intent.putExtra("email", cachedEmail);
            intent.putExtra("username", cachedUsername);
            intent.putExtra("pendingEmail", pendingEmail);
            intent.putExtra("pendingPhoneNumber", pendingPhoneNumber);
            intent.putExtra("signInMethod", signInMethod);
            startActivityForResult(intent, EDIT_PROFILE_REQUEST);
        });

        changePasswordLayout.setOnClickListener(v -> {
            animateClick(v);
            startActivity(new Intent(requireContext(), ChangePasswordActivity.class));
        });

        allOrdersLayout.setOnClickListener(v -> {
            animateClick(v);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new OrderFragment())
                    .addToBackStack("OrderFragment")
                    .commit();
        });

        paymentsLayout.setOnClickListener(v -> {
            animateClick(v);
            // Add logic for Payments
        });

        notificationsLayout.setOnClickListener(v -> {
            animateClick(v);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new NotificationFragment())
                    .addToBackStack("NotificationFragment")
                    .commit();
        });

        termsLayout.setOnClickListener(v -> {
            animateClick(v);
            // Add logic for Terms & Conditions
        });
    }

    private void animateClick(View v) {
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
    }

    private void setupLogoutListener() {
        logoutButton.setOnClickListener(v -> {
            LogoutConfirmationDialog dialog = new LogoutConfirmationDialog(requireContext(), this::logoutAndRedirectToLogin);
            dialog.show();
        });
    }

    private void setupAdminButton() {
        adminButton.setOnClickListener(v -> {
            if (!isAdded()) return;

            int gmsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext());
            if (gmsStatus != ConnectionResult.SUCCESS) {
                Log.e("ProfileFragment", "Google Play Services unavailable: " + gmsStatus);
                showCustomToast("Google Play Services unavailable.", false);
                return;
            }

            showCustomLoader();
            firestore.collection("users").document(currentUser.getUid())
                    .get(Source.SERVER)
                    .addOnSuccessListener(documentSnapshot -> {
                        Log.d("ProfileFragment", "Firestore success: " + documentSnapshot.getData());
                        if (!isAdded()) return;
                        hideLoader(); // Hide when data is loaded
                        String role = documentSnapshot.getString("role");
                        if ("admin".equals(role)) {
                            startActivity(new Intent(requireContext(), AdminMainActivity.class));
                        } else {
                            showCustomToast("Access denied. Admins only.", false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("ProfileFragment", "Firestore failed: " + e.getMessage(), e);
                        hideLoader(); // Hide on failure
                        if (isAdded()) {
                            showCustomToast("Failed to verify role: " + e.getMessage(), false);
                        }
                    });
        });
    }

    private void displayCachedProfileData() {
        if (rootView == null || !isAdded()) return;

        userName.setText(cachedFullName != null && !cachedFullName.isEmpty() ? cachedFullName : "User");

        String primaryContact = getPrimaryContactInfo();
        String displayContact = primaryContact;
        if ("phone".equals(signInMethod) && displayContact.contains("@")) {
            displayContact = displayContact.split("@")[0];
        } else if (!"phone".equals(signInMethod) && !cachedPhoneNumber.isEmpty() && !cachedPhoneNumber.equals(primaryContact)) {
            displayContact = cachedPhoneNumber.contains("@") ? cachedPhoneNumber.split("@")[0] : cachedPhoneNumber;
        }
        userPhone.setText(displayContact != null ? displayContact : "Not provided");

        LinearLayout verificationStatusContainer = rootView.findViewById(R.id.verificationStatusContainer);
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
                verificationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange));
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
                                    if (countdownTimer != null) countdownTimer.cancel();
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
                                    if (countdownTimer != null) countdownTimer.cancel();
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
                verificationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
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

        if (authHelper == null) {
            authHelper = new ProfileAuthHelper(requireActivity(), new ProfileAuthHelper.OnAuthCompleteListener() {
                @Override
                public void onAuthStart() {
                    showCustomLoader();
                }

                @Override
                public void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                                          String email, String username, String originalEmail,
                                          String originalPhoneNumber, String originalUsername) {
                    showCustomLoader();
                    loadProfileDataFromFirebase();
                }

                @Override
                public void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                                    String currentEmail, String username, String pendingEmail,
                                                    String originalEmail, String originalPhoneNumber, String originalUsername) {
                    hideLoader();
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
                    hideLoader();
                    showCustomToast("Verification code sent to " + pendingPhoneNumber, true);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putLong("lastVerificationTime", System.currentTimeMillis());
                    editor.apply();
                    startVerificationTimer();
                    displayCachedProfileData();
                }

                @Override
                public void onAuthFailed() {
                    hideLoader();
                    showCustomToast("Operation failed. Please try again.", false);
                }
            });
        }
    }

    private void startVerificationTimer() {
        if (countdownTimer != null) countdownTimer.cancel();
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
        showCustomLoader();
        swipeRefreshLayout.setRefreshing(true);
        loadProfileDataFromFirebase(true);
    }

    private void loadProfileDataFromFirebase() {
        loadProfileDataFromFirebase(false);
    }

    private void loadProfileDataFromFirebase(boolean forceRefresh) {
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
                } else {
                    showCustomToast("Error refreshing session: " + e.getMessage(), false);
                }
                hideLoader();
                swipeRefreshLayout.setRefreshing(false);
                return;
            }

            currentUser.getIdToken(true).addOnCompleteListener(tokenTask -> {
                if (!tokenTask.isSuccessful()) {
                    hideLoader();
                    swipeRefreshLayout.setRefreshing(false);
                    showCustomToast("Failed to refresh token: " + tokenTask.getException().getMessage(), false);
                    return;
                }
                fetchProfileData(forceRefresh);
            });
        });
    }

    private void fetchProfileData(boolean forceRefresh) {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> userData = dataSnapshot.getValue(new GenericTypeIndicator<Map<String, Object>>() {});
                    if (userData == null) {
                        userData = new HashMap<>();
                    }

                    String newFullName = userData.get("fullName") != null ? (String) userData.get("fullName") : cachedFullName;
                    String newPhoneNumber = userData.get("phoneNumber") != null ? (String) userData.get("phoneNumber") : cachedPhoneNumber;
                    String newEmail = userData.get("email") != null ? (String) userData.get("email") : cachedEmail;
                    String newUsername = userData.get("username") != null ? (String) userData.get("username") : cachedUsername;
                    String newSignInMethod = userData.get("signInMethod") != null ? (String) userData.get("signInMethod") : signInMethod;
                    pendingEmail = userData.get("pendingEmail") != null ? (String) userData.get("pendingEmail") : pendingEmail;
                    pendingPhoneNumber = userData.get("pendingPhoneNumber") != null ? (String) userData.get("pendingPhoneNumber") : pendingPhoneNumber;

                    if ("email".equals(newSignInMethod) && currentUser.isEmailVerified() && !pendingEmail.isEmpty()) {
                        updateEmailAfterVerification(currentUser.getEmail());
                    } else if ("phone".equals(newSignInMethod)) {
                        newPhoneNumber = currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : newPhoneNumber;
                    }

                    String finalNewPhoneNumber = newPhoneNumber;
                    Source source = forceRefresh ? Source.SERVER : Source.DEFAULT;
                    firestore.collection("users").document(currentUser.getUid())
                            .get(source)
                            .addOnSuccessListener(documentSnapshot -> {
                                String newImageUrl = documentSnapshot.getString("imageUrl") != null ? documentSnapshot.getString("imageUrl") : cachedImageUrl;

                                if (!Objects.equals(newFullName, cachedFullName) ||
                                        !Objects.equals(finalNewPhoneNumber, cachedPhoneNumber) ||
                                        !Objects.equals(newEmail, cachedEmail) ||
                                        !Objects.equals(newImageUrl, cachedImageUrl) ||
                                        !Objects.equals(newUsername, cachedUsername) ||
                                        !Objects.equals(newSignInMethod, signInMethod) ||
                                        !Objects.equals(pendingEmail, sharedPreferences.getString("pendingEmail", "")) ||
                                        !Objects.equals(pendingPhoneNumber, sharedPreferences.getString("pendingPhoneNumber", ""))) {
                                    cachedFullName = newFullName;
                                    cachedPhoneNumber = finalNewPhoneNumber;
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
                                hideLoader(); // Hide when data is fully loaded
                                swipeRefreshLayout.setRefreshing(false);
                            })
                            .addOnFailureListener(e -> {
                                Log.e("ProfileFragment", "Failed to fetch imageUrl from Firestore: " + e.getMessage());
                                displayCachedProfileData();
                                hideLoader(); // Hide on failure
                                swipeRefreshLayout.setRefreshing(false);
                                showCustomToast("Failed to load profile image: " + e.getMessage(), false);
                            });
                } else {
                    Source source = forceRefresh ? Source.SERVER : Source.DEFAULT;
                    firestore.collection("users").document(currentUser.getUid())
                            .get(source)
                            .addOnSuccessListener(documentSnapshot -> {
                                String newImageUrl = documentSnapshot.getString("imageUrl") != null ? documentSnapshot.getString("imageUrl") : cachedImageUrl;
                                if (!Objects.equals(newImageUrl, cachedImageUrl)) {
                                    cachedImageUrl = newImageUrl;
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString("imageUrl", cachedImageUrl);
                                    editor.putBoolean(KEY_DATA_FRESH, true);
                                    editor.putBoolean(KEY_LAST_FETCH_SUCCESS, true);
                                    editor.apply();
                                }
                                displayCachedProfileData();
                                hideLoader(); // Hide when data is loaded
                                swipeRefreshLayout.setRefreshing(false);
                            })
                            .addOnFailureListener(e -> {
                                displayCachedProfileData();
                                hideLoader(); // Hide on failure
                                swipeRefreshLayout.setRefreshing(false);
                                showCustomToast("Failed to load profile: " + e.getMessage(), false);
                            });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                hideLoader(); // Hide on cancellation
                swipeRefreshLayout.setRefreshing(false);
                showCustomToast("Failed to load profile: " + databaseError.getMessage(), false);
                displayCachedProfileData();
            }
        });
    }

    private void updateEmailAfterVerification(String verifiedEmail) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", verifiedEmail);
        updates.put("pendingEmail", "");

        databaseReference.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    cachedEmail = verifiedEmail;
                    pendingEmail = "";
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("email", cachedEmail);
                    editor.putString("pendingEmail", "");
                    editor.apply();
                    if (countdownTimer != null) countdownTimer.cancel();
                    displayCachedProfileData();
                    showCustomToast("Email verified and updated.", true);
                })
                .addOnFailureListener(e -> showCustomToast("Failed to update email: " + e.getMessage(), false));
    }

    private String getPrimaryContactInfo() {
        String contactInfo = null;
        if ("phone".equals(signInMethod) && currentUser.getPhoneNumber() != null) {
            contactInfo = currentUser.getPhoneNumber();
        } else if ("email".equals(signInMethod) && currentUser.getEmail() != null) {
            contactInfo = currentUser.getEmail();
        } else if (signInMethod.contains("google") || signInMethod.contains("facebook") || signInMethod.contains("github")) {
            contactInfo = currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getPhoneNumber();
        }
        if (contactInfo == null) {
            contactInfo = "phone".equals(signInMethod) && !cachedPhoneNumber.isEmpty() ? cachedPhoneNumber : cachedEmail;
        }
        if ("phone".equals(signInMethod) && contactInfo.contains("@")) {
            contactInfo = contactInfo.split("@")[0];
        }
        return contactInfo != null ? contactInfo : "Not provided";
    }

    private void logoutAndRedirectToLogin() {
        mAuth.signOut();
        startActivity(new Intent(requireContext(), LoginActivity.class));
        requireActivity().finish();
    }

    private void showCustomToast(String message, boolean isSuccess) {
        if (!isAdded()) return;
        LayoutInflater inflater = getLayoutInflater();
        View toastView = inflater.inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);

        Toast toast = new Toast(requireContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);
        int offsetDp = (int) (200 * getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offsetDp);
        toast.show();
    }

    @SuppressLint("RestrictedApi")
    private void showCustomLoader() {
        if (!isAdded() || sltLoader == null) {
            Log.w("ProfileFragment", "Cannot show loader: fragment not added or sltLoader null");
            return;
        }

        SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000"))
                .setChangeJsonColor(false);

        try {
            sltLoader.showCustomLoader(config);
            Log.d("ProfileFragment", "Custom loader shown with SLTLoader");
        } catch (Exception e) {
            Log.e("ProfileFragment", "Failed to show loader: " + e.getMessage(), e);
        }
    }

    private void hideLoader() {
        if (isAdded() && sltLoader != null) {
            sltLoader.hideLoader();
            Log.d("ProfileFragment", "Loader hidden immediately");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countdownTimer != null) countdownTimer.cancel();
        if (sltLoader != null) {
            sltLoader.onDestroy();
            Log.d("ProfileFragment", "SLTLoader destroyed");
        }
        rootView = null;
    }
}