package com.oopgroup.smartpharmacy;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.hbb20.CountryCodePicker;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.Calendar;
import java.util.Map;
import java.util.Objects;

public class EditProfileActivity extends AppCompatActivity implements ProfileAuthHelper.OnAuthCompleteListener {

    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;
    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "EditProfileActivity";
    private static final String DEFAULT_GENDER = "Not Specified";
    private static final String DEFAULT_BIRTHDAY = "01-01-2000";
    private static final long VERIFICATION_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    private ImageButton backButton;
    private ImageView profileImage, cameraIcon;
    private TextView displayFullNameTextView, verificationStatus, verificationTimer;
    private EditText fullNameEditText, birthdayEditText, phoneNumberInput, emailInput, usernameEditText;
    private Spinner genderSpinner;
    private Button saveButton, cancelVerificationButton;
    private CountryCodePicker ccp;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LottieAnimationView loadingSpinner;
    private CardView verificationControls;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private SharedPreferences sharedPreferences;
    private ProfileAuthHelper authHelper;

    private Uri selectedImageUri;
    private String currentImageUrl, currentEmail, currentPhoneNumber;
    private String previousEmail, previousPhoneNumber;
    private String signInMethod = "email";
    private String pendingEmail = "";
    private String pendingPhoneNumber = "";

    private ActivityResultLauncher<String> pickImage;
    private CountDownTimer countdownTimer;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        pickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        selectedImageUri = result;
                        loadProfileImage(selectedImageUri);
                    }
                }
        );

        if (savedInstanceState != null) {
            selectedImageUri = savedInstanceState.getParcelable("selectedImageUri");
            if (selectedImageUri != null) {
                loadProfileImage(selectedImageUri);
            }
        }

        initializeFirebase();
        initializeUI();
        authHelper = new ProfileAuthHelper(this, this);
        setupListeners();
        setupDynamicInput();
        loadCachedProfileData();
        loadProfileDataFromFirebase();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("selectedImageUri", selectedImageUri);
    }

    private void initializeFirebase() {
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated. Redirecting to login.");
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        storageReference = FirebaseStorage.getInstance().getReference("profile_images").child(currentUser.getUid());
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentEmail = currentUser.getEmail();
        currentPhoneNumber = currentUser.getPhoneNumber();
        previousEmail = currentEmail != null ? currentEmail : "";
        previousPhoneNumber = currentPhoneNumber != null ? currentPhoneNumber : "";

        // Determine sign-in method from Firebase Authentication
        if (!currentUser.getProviderData().isEmpty()) {
            for (com.google.firebase.auth.UserInfo provider : currentUser.getProviderData()) {
                String providerId = provider.getProviderId();
                if ("phone".equals(providerId)) {
                    signInMethod = "phone";
                } else if ("password".equals(providerId)) {
                    signInMethod = "email";
                } else if ("google.com".equals(providerId)) {
                    signInMethod = "google";
                } else if ("facebook.com".equals(providerId)) {
                    signInMethod = "facebook";
                } else if ("github.com".equals(providerId)) {
                    signInMethod = "github";
                }
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("signInMethod", signInMethod);
            editor.apply();
            Log.d(TAG, "Sign-in method determined: " + signInMethod);
        }
    }

    private void initializeUI() {
        backButton = findViewById(R.id.backButton);
        profileImage = findViewById(R.id.profileImage);
        cameraIcon = findViewById(R.id.cameraIcon);
        displayFullNameTextView = findViewById(R.id.displayFullName);
        fullNameEditText = findViewById(R.id.fullName);
        genderSpinner = findViewById(R.id.gender);
        birthdayEditText = findViewById(R.id.birthday);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        emailInput = findViewById(R.id.emailInput);
        usernameEditText = findViewById(R.id.username);
        saveButton = findViewById(R.id.saveButton);
        cancelVerificationButton = findViewById(R.id.cancelVerificationButton);
        ccp = findViewById(R.id.ccp);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        verificationControls = findViewById(R.id.verificationControlsCard);
        verificationStatus = findViewById(R.id.verificationStatus);
        verificationTimer = findViewById(R.id.verificationTimer);

        cameraIcon.setVisibility(View.VISIBLE);

        try {
            loadingSpinner.setAnimation(R.raw.loading_spinner);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Lottie animation: " + e.getMessage(), e);
            loadingSpinner.setVisibility(View.GONE);
        }

        ccp.setCustomMasterCountries("BD,MY,SG");
        ccp.setDefaultCountryUsingNameCode("BD");
        ccp.resetToDefaultCountry();

        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);

        updateVerificationUI();
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> navigateToProfileActivity());
        cameraIcon.setOnClickListener(v -> pickImage.launch("image/*"));
        birthdayEditText.setOnClickListener(v -> showDatePickerDialog());
        saveButton.setOnClickListener(v -> saveProfileData());
        cancelVerificationButton.setOnClickListener(v -> cancelVerification());
        swipeRefreshLayout.setOnRefreshListener(this::loadProfileDataFromFirebase);
    }

    private void setupDynamicInput() {
        phoneNumberInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                clearErrorBorders();

                if (input.isEmpty()) {
                    ccp.setVisibility(View.GONE);
                    adjustPhoneInputPadding(null);
                } else {
                    String cleanedInput = input.replaceAll("[^0-9+]", "");
                    if (!input.contains("@") && cleanedInput.length() >= 8) {
                        AuthUtils.updateCountryFlag(cleanedInput, ccp);
                        String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, null);
                        if (AuthUtils.isValidPhoneNumber(normalized)) {
                            ccp.setVisibility(View.VISIBLE);
                            if (!phoneNumberInput.getText().toString().equals(normalized)) {
                                phoneNumberInput.setText(normalized);
                                phoneNumberInput.setSelection(normalized.length());
                            }
                        } else {
                            ccp.setVisibility(View.GONE);
                        }
                    } else {
                        ccp.setVisibility(View.GONE);
                    }
                    adjustPhoneInputPadding(null);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        emailInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearErrorBorders();
                adjustEmailInputPadding(null);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        adjustPhoneInputPadding(null);
        adjustEmailInputPadding(null);

        restrictFieldsBasedOnSignInMethod();
    }

    private void restrictFieldsBasedOnSignInMethod() {
        if (signInMethod == null) signInMethod = "email";
        if (pendingEmail == null) pendingEmail = "";
        if (pendingPhoneNumber == null) pendingPhoneNumber = "";

        if ("google".equals(signInMethod) || "facebook".equals(signInMethod) || "github".equals(signInMethod)) {
            emailInput.setEnabled(false);
            emailInput.setText(currentEmail != null ? currentEmail : "");
            phoneNumberInput.setEnabled(true);
        } else if ("email".equals(signInMethod)) {
            emailInput.setEnabled(pendingEmail.isEmpty());
            phoneNumberInput.setEnabled(true);
        } else if ("phone".equals(signInMethod)) {
            emailInput.setEnabled(true);
            phoneNumberInput.setEnabled(pendingPhoneNumber.isEmpty());
        }
    }

    private void adjustPhoneInputPadding(View unused) {
        ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ccp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int paddingStart = ccp.getVisibility() == View.VISIBLE ?
                        ccp.getWidth() + (int) (8 * getResources().getDisplayMetrics().density) :
                        (int) (12 * getResources().getDisplayMetrics().density);
                phoneNumberInput.setPadding(paddingStart, phoneNumberInput.getPaddingTop(), phoneNumberInput.getPaddingEnd(), phoneNumberInput.getPaddingBottom());
            }
        };
        ccp.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    private void adjustEmailInputPadding(View unused) {
        int defaultPadding = (int) (12 * getResources().getDisplayMetrics().density);
        emailInput.setPadding(defaultPadding, emailInput.getPaddingTop(), emailInput.getPaddingEnd(), emailInput.getPaddingBottom());
    }

    private void navigateToProfileActivity() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
        finish();
    }

    private void loadCachedProfileData() {
        String cachedFullName = sharedPreferences.getString("fullName", "User");
        String cachedGender = sharedPreferences.getString("gender", DEFAULT_GENDER);
        String cachedBirthday = sharedPreferences.getString("birthday", DEFAULT_BIRTHDAY);
        String cachedPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        String cachedEmail = sharedPreferences.getString("email", "");
        String cachedUsername = sharedPreferences.getString("username", "");
        String cachedImageUrl = sharedPreferences.getString("imageUrl", "");
        signInMethod = sharedPreferences.getString("signInMethod", "email");
        pendingEmail = sharedPreferences.getString("pendingEmail", "");
        pendingPhoneNumber = sharedPreferences.getString("pendingPhoneNumber", "");

        fullNameEditText.setText(cachedFullName);
        genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(cachedGender));
        birthdayEditText.setText(cachedBirthday);
        phoneNumberInput.setText(cachedPhoneNumber);
        emailInput.setText(cachedEmail);
        usernameEditText.setText(cachedUsername);
        displayFullNameTextView.setText(cachedFullName);
        loadProfileImage(cachedImageUrl.isEmpty() ? null : Uri.parse(cachedImageUrl));
    }

    private void loadProfileDataFromFirebase() {
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated. Redirecting to login.");
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUser.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                currentEmail = currentUser.getEmail();
                currentPhoneNumber = currentUser.getPhoneNumber();
            } else {
                Log.e(TAG, "Failed to reload user: " + task.getException().getMessage());
                showCustomToast("Failed to refresh session: " + task.getException().getMessage(), false);
            }

            databaseReference.get().addOnCompleteListener(dbTask -> {
                if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                    Map<String, Object> userData = (Map<String, Object>) dbTask.getResult().getValue();
                    String fullName = userData.get("fullName") != null ? (String) userData.get("fullName") : "User";
                    String gender = userData.get("gender") != null ? (String) userData.get("gender") : DEFAULT_GENDER;
                    String birthday = userData.get("birthday") != null ? (String) userData.get("birthday") : DEFAULT_BIRTHDAY;
                    String phoneNumber = userData.get("phoneNumber") != null ? (String) userData.get("phoneNumber") : "";
                    String email = userData.get("email") != null ? (String) userData.get("email") : "";
                    String username = userData.get("username") != null ? (String) userData.get("username") : "";
                    pendingEmail = userData.get("pendingEmail") != null ? (String) userData.get("pendingEmail") : "";
                    pendingPhoneNumber = userData.get("pendingPhoneNumber") != null ? (String) userData.get("pendingPhoneNumber") : "";
                    currentImageUrl = userData.get("imageUrl") != null ? (String) userData.get("imageUrl") : "";
                    signInMethod = userData.get("signInMethod") != null ? (String) userData.get("signInMethod") : "email";

                    fullNameEditText.setText(fullName);
                    genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(gender));
                    birthdayEditText.setText(birthday);
                    phoneNumberInput.setText(phoneNumber);
                    emailInput.setText(email);
                    usernameEditText.setText(username);
                    displayFullNameTextView.setText(fullName);

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fullName", fullName);
                    editor.putString("gender", gender);
                    editor.putString("birthday", birthday);
                    editor.putString("phoneNumber", phoneNumber);
                    editor.putString("email", email);
                    editor.putString("username", username);
                    editor.putString("imageUrl", currentImageUrl);
                    editor.putString("signInMethod", signInMethod);
                    editor.putString("pendingEmail", pendingEmail);
                    editor.putString("pendingPhoneNumber", pendingPhoneNumber);
                    editor.apply();

                    currentPhoneNumber = phoneNumber;
                    currentEmail = email;
                    previousEmail = currentEmail;
                    previousPhoneNumber = currentPhoneNumber;

                    if (selectedImageUri == null) {
                        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
                    }
                    restrictFieldsBasedOnSignInMethod();
                    updateVerificationUI();
                } else {
                    if (selectedImageUri == null) {
                        loadProfileImage(null);
                    }
                    emailInput.setText(currentEmail);
                    showCustomToast("No profile data found.", false);
                }
                swipeRefreshLayout.setRefreshing(false);
            }).addOnFailureListener(e -> {
                showCustomToast("Failed to fetch profile data: " + e.getMessage(), false);
                swipeRefreshLayout.setRefreshing(false);
            });
        });
    }

    private void loadProfileImage(Uri imageUri) {
        RequestOptions options = new RequestOptions()
                .circleCrop()
                .override(INNER_SIZE, INNER_SIZE)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile);

        if (imageUri != null) {
            Glide.with(this)
                    .load(imageUri)
                    .apply(options)
                    .into(profileImage);
        } else {
            profileImage.setImageResource(R.drawable.default_profile);
        }
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this,
                (view, yearSelected, monthOfYear, dayOfMonth) -> {
                    String date = String.format("%02d-%02d-%d", dayOfMonth, monthOfYear + 1, yearSelected);
                    birthdayEditText.setText(date);
                }, year, month, day).show();
    }

    private void saveProfileData() {
        if (currentUser == null) {
            Log.e(TAG, "Cannot save profile: User is not authenticated.");
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        Log.d(TAG, "Saving profile for user UID: " + currentUser.getUid());

        clearErrorBorders();

        String fullName = fullNameEditText.getText().toString().trim();
        String gender = genderSpinner.getSelectedItem() != null ? genderSpinner.getSelectedItem().toString() : DEFAULT_GENDER;
        String birthday = birthdayEditText.getText().toString().trim().isEmpty() ? DEFAULT_BIRTHDAY : birthdayEditText.getText().toString().trim();
        String phoneNumberInputText = phoneNumberInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();

        boolean hasError = false;
        if (fullName.isEmpty()) {
            setErrorBorder(fullNameEditText);
            hasError = true;
        }
        if (username.isEmpty()) {
            setErrorBorder(usernameEditText);
            hasError = true;
        }

        String phoneNumber = phoneNumberInputText.isEmpty() ? "" : AuthUtils.normalizePhoneNumberForBackend(phoneNumberInputText, ccp, null);
        if (!phoneNumber.isEmpty() && !AuthUtils.isValidPhoneNumber(phoneNumber)) {
            setErrorBorder(phoneNumberInput);
            showCustomToast("Invalid phone number format.", false);
            hasError = true;
        }

        if ("email".equals(signInMethod) && email.isEmpty()) {
            setErrorBorder(emailInput);
            showCustomToast("Email is required for email sign-in accounts.", false);
            hasError = true;
        } else if (!email.isEmpty() && !AuthUtils.isValidEmail(email)) {
            setErrorBorder(emailInput);
            showCustomToast("Please enter a valid email address.", false);
            hasError = true;
        }

        if ("phone".equals(signInMethod) && phoneNumber.isEmpty()) {
            setErrorBorder(phoneNumberInput);
            showCustomToast("Phone number is required for phone sign-in accounts.", false);
            hasError = true;
        }

        if (hasError) {
            showCustomToast("Please correct the errors in the form.", false);
            return;
        }

        String originalEmail = sharedPreferences.getString("email", "");
        String originalPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        String originalUsername = sharedPreferences.getString("username", "");

        Log.d(TAG, "Calling saveProfile with username: " + username);
        authHelper.saveProfile(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri,
                currentImageUrl, originalEmail, originalPhoneNumber, originalUsername);
    }

    private void cancelVerification() {
        if ("email".equals(signInMethod) && !pendingEmail.isEmpty()) {
            databaseReference.child("pendingEmail").removeValue()
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingEmail");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingEmail = "";
                        emailInput.setText(currentEmail);
                        emailInput.setEnabled(true);
                        if (countdownTimer != null) {
                            countdownTimer.cancel();
                        }
                        updateVerificationUI();
                        showCustomToast("Email verification cancelled.", true);
                    })
                    .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
        } else if ("phone".equals(signInMethod) && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentPhoneNumber)) {
            databaseReference.child("pendingPhoneNumber").removeValue()
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingPhoneNumber");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingPhoneNumber = "";
                        phoneNumberInput.setText(currentPhoneNumber);
                        phoneNumberInput.setEnabled(true);
                        if (countdownTimer != null) {
                            countdownTimer.cancel();
                        }
                        updateVerificationUI();
                        showCustomToast("Phone verification cancelled.", true);
                    })
                    .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
        }
    }

    private void updateVerificationUI() {
        boolean emailPending = "email".equals(signInMethod) && !pendingEmail.isEmpty() && !currentUser.isEmailVerified();
        boolean phonePending = "phone".equals(signInMethod) && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentPhoneNumber);

        if (emailPending || phonePending) {
            verificationControls.setVisibility(View.VISIBLE);
            String statusText = emailPending ? "Changing email to " + pendingEmail + " - Verification pending" :
                    "Changing phone to " + pendingPhoneNumber + " - Verification pending";
            verificationStatus.setText(statusText);
            verificationStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
            if (emailPending) emailInput.setEnabled(false);
            if (phonePending) phoneNumberInput.setEnabled(false);
            startVerificationTimer();
        } else {
            verificationControls.setVisibility(View.GONE);
            emailInput.setEnabled(true);
            phoneNumberInput.setEnabled(true);
            verificationTimer.setVisibility(View.GONE);
            if (countdownTimer != null) {
                countdownTimer.cancel();
            }
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void startVerificationTimer() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
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
        if ("email".equals(signInMethod) && !pendingEmail.isEmpty()) {
            databaseReference.child("pendingEmail").removeValue()
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingEmail");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingEmail = "";
                        emailInput.setText(currentEmail);
                        emailInput.setEnabled(true);
                        updateVerificationUI();
                        showCustomToast("Verification timed out and cancelled.", false);
                    })
                    .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
        } else if ("phone".equals(signInMethod) && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentPhoneNumber)) {
            databaseReference.child("pendingPhoneNumber").removeValue()
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingPhoneNumber");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingPhoneNumber = "";
                        phoneNumberInput.setText(currentPhoneNumber);
                        phoneNumberInput.setEnabled(true);
                        updateVerificationUI();
                        showCustomToast("Verification timed out and cancelled.", false);
                    })
                    .addOnFailureListener(e -> showCustomToast("Failed to cancel verification: " + e.getMessage(), false));
        }
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void clearErrorBorders() {
        fullNameEditText.setBackgroundResource(R.drawable.edittext_bg);
        phoneNumberInput.setBackgroundResource(R.drawable.edittext_bg);
        emailInput.setBackgroundResource(R.drawable.edittext_bg);
        usernameEditText.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void showCustomToast(String message, boolean isSuccess) {
        LayoutInflater inflater = getLayoutInflater();
        View toastView = inflater.inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);

        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);

        int offsetDp = (int) (300 * getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offsetDp);

        Log.d(TAG, "Showing toast: " + message + ", Offset: " + offsetDp + "px, Gravity: BOTTOM");
        toast.show();
    }

    @Override
    public void onAuthStart() {
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();
        saveButton.setEnabled(false);
    }

    @Override
    public void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                              String email, String username, String originalEmail,
                              String originalPhoneNumber, String originalUsername) {
        loadingSpinner.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        currentEmail = email;
        currentPhoneNumber = phoneNumber;
        previousEmail = email;
        previousPhoneNumber = phoneNumber;
        displayFullNameTextView.setText(fullName);
        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
        selectedImageUri = null;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("signInMethod", signInMethod); // Ensure signInMethod is preserved
        editor.apply();

        Intent resultIntent = new Intent();
        resultIntent.putExtra("imageUrl", currentImageUrl != null ? currentImageUrl : "");
        resultIntent.putExtra("fullName", fullName);
        resultIntent.putExtra("phoneNumber", phoneNumber);
        resultIntent.putExtra("email", email);
        resultIntent.putExtra("username", username);
        resultIntent.putExtra("pendingEmail", pendingEmail);
        resultIntent.putExtra("pendingPhoneNumber", pendingPhoneNumber);
        setResult(RESULT_OK, resultIntent);

        showCustomToast("Profile saved successfully.", true);
        navigateToProfileActivity();
    }

    @Override
    public void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                        String currentEmail, String username, String pendingEmail,
                                        String originalEmail, String originalPhoneNumber, String originalUsername) {
        loadingSpinner.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        this.pendingEmail = pendingEmail;
        emailInput.setText(currentEmail);
        emailInput.setEnabled(false);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("pendingEmail", pendingEmail);
        editor.putLong("lastVerificationTime", System.currentTimeMillis());
        editor.putString("signInMethod", signInMethod); // Preserve signInMethod
        editor.apply();
        updateVerificationUI();
        showCustomToast("Verification email sent to " + pendingEmail + ". Please verify.", true);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("imageUrl", currentImageUrl != null ? currentImageUrl : "");
        resultIntent.putExtra("fullName", fullName);
        resultIntent.putExtra("phoneNumber", phoneNumber);
        resultIntent.putExtra("email", currentEmail);
        resultIntent.putExtra("username", username);
        resultIntent.putExtra("pendingEmail", pendingEmail);
        resultIntent.putExtra("pendingPhoneNumber", pendingPhoneNumber);
        setResult(RESULT_OK, resultIntent);

        navigateToProfileActivity();
    }

    @Override
    public void onPhoneVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                        String currentEmail, String username, String pendingPhoneNumber,
                                        String originalEmail, String originalPhoneNumber, String originalUsername) {
        loadingSpinner.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        this.pendingPhoneNumber = pendingPhoneNumber;
        phoneNumberInput.setText(currentPhoneNumber);
        phoneNumberInput.setEnabled(false);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("pendingPhoneNumber", pendingPhoneNumber);
        editor.putLong("lastVerificationTime", System.currentTimeMillis());
        editor.putString("signInMethod", signInMethod); // Preserve signInMethod
        editor.apply();
        updateVerificationUI();
        showCustomToast("Verification code sent to " + pendingPhoneNumber + ". Please verify.", true);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("imageUrl", currentImageUrl != null ? currentImageUrl : "");
        resultIntent.putExtra("fullName", fullName);
        resultIntent.putExtra("phoneNumber", currentPhoneNumber);
        resultIntent.putExtra("email", currentEmail);
        resultIntent.putExtra("username", username);
        resultIntent.putExtra("pendingEmail", pendingEmail);
        resultIntent.putExtra("pendingPhoneNumber", pendingPhoneNumber);
        setResult(RESULT_OK, resultIntent);

        navigateToProfileActivity();
    }

    @Override
    public void onAuthFailed() {
        loadingSpinner.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        phoneNumberInput.setText(previousPhoneNumber);
        emailInput.setText(previousEmail);
        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
        selectedImageUri = null;
        showCustomToast("Profile update failed. Please try again.", false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
        handler.removeCallbacksAndMessages(null);
    }
}