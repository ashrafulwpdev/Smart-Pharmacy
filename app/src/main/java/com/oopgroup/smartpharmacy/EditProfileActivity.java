package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.hbb20.CountryCodePicker;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.oopgroup.smartpharmacy.utils.AuthUtils;
import com.softourtech.slt.SLTLoader;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EditProfileActivity extends AppCompatActivity implements ProfileAuthHelper.OnAuthCompleteListener {

    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;
    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "EditProfileActivity";
    private static final String DEFAULT_GENDER = "Not Specified";
    private static final String DEFAULT_BIRTHDAY = "01-01-2000";
    private static final long VERIFICATION_TIMEOUT = 10 * 60 * 1000;
    private static final int REQUEST_PHONE_STATE_PERMISSION = 1001;

    private ImageButton backButton;
    private ImageView profileImage, cameraIcon;
    private TextView displayFullNameTextView, verificationStatus, verificationTimer;
    private TextView phoneValidationMessage, emailValidationMessage;
    private EditText fullNameEditText, birthdayEditText, phoneNumberInput, emailInput, usernameEditText;
    private Spinner genderSpinner;
    private Button saveButton, cancelVerificationButton;
    private CountryCodePicker ccp;
    private SwipeRefreshLayout swipeRefreshLayout;
    private CardView verificationControls;
    private SLTLoader sltLoader;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private FirebaseFirestore firestore;
    private StorageReference storageReference;
    private SharedPreferences sharedPreferences;
    private ProfileAuthHelper authHelper;

    private Uri selectedImageUri;
    private String currentImageUrl, currentEmail, currentPhoneNumber;
    private String previousEmail, previousPhoneNumber;
    private String originalUsername;
    private String signInMethod = "email";
    private String pendingEmail = "";
    private String pendingPhoneNumber = "";
    private String lastProcessedPhoneInput = "";

    private ActivityResultLauncher<String> pickImage;
    private CountDownTimer countdownTimer;
    private boolean isProgrammaticChange = false;
    private boolean isTextChanging = false;
    private boolean isPhoneInput = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Initialize SLTLoader with the activity's root view
        View activityRoot = findViewById(android.R.id.content);
        if (activityRoot == null || !(activityRoot instanceof ViewGroup)) {
            Log.e(TAG, "Activity root view not found or not a ViewGroup");
            return;
        }
        sltLoader = new SLTLoader(this, (ViewGroup) activityRoot);

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PHONE_STATE_PERMISSION);
        }

        initializeFirebase();
        initializeUI();
        authHelper = new ProfileAuthHelper(this, this);
        setupListeners();
        setupDynamicInput();
        loadCachedProfileData();
        loadProfileDataFromFirestore();

        originalUsername = sharedPreferences.getString("username", "");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PHONE_STATE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_PHONE_STATE permission granted");
                loadProfileDataFromFirestore();
            } else {
                Log.w(TAG, "READ_PHONE_STATE permission denied");
                showCustomToast("Permission denied. Phone number detection may be less accurate.", false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("selectedImageUri", selectedImageUri);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownTimer != null) countdownTimer.cancel();
        if (sltLoader != null) sltLoader.onDestroy();
    }

    private void initializeFirebase() {
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated. Redirecting to login.");
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        firestore = FirebaseFirestore.getInstance();
        storageReference = FirebaseStorage.getInstance().getReference("profile_images").child(currentUser.getUid());
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentEmail = currentUser.getEmail();
        currentPhoneNumber = currentUser.getPhoneNumber();
        previousEmail = currentEmail != null ? currentEmail : "";
        previousPhoneNumber = currentPhoneNumber != null ? currentPhoneNumber : "";

        Intent intent = getIntent();
        currentImageUrl = intent.getStringExtra("currentImageUrl");
        signInMethod = intent.getStringExtra("signInMethod");
        pendingEmail = intent.getStringExtra("pendingEmail");
        pendingPhoneNumber = intent.getStringExtra("pendingPhoneNumber");

        if (!currentUser.getProviderData().isEmpty()) {
            for (com.google.firebase.auth.UserInfo provider : currentUser.getProviderData()) {
                String providerId = provider.getProviderId();
                if ("phone".equals(providerId)) signInMethod = "phone";
                else if ("password".equals(providerId)) signInMethod = "email";
                else if ("google.com".equals(providerId)) signInMethod = "google";
                else if ("facebook.com".equals(providerId)) signInMethod = "facebook";
                else if ("github.com".equals(providerId)) signInMethod = "github";
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("signInMethod", signInMethod);
            editor.apply();
            Log.d(TAG, "Sign-in method: " + signInMethod);
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
        ccp = findViewById(R.id.ccp);
        emailInput = findViewById(R.id.emailInput);
        usernameEditText = findViewById(R.id.username);
        saveButton = findViewById(R.id.saveButton);
        cancelVerificationButton = findViewById(R.id.cancelVerificationButton);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        verificationControls = findViewById(R.id.verificationControlsCard);
        verificationStatus = findViewById(R.id.verificationStatus);
        verificationTimer = findViewById(R.id.verificationTimer);
        phoneValidationMessage = findViewById(R.id.phoneValidationMessage);
        emailValidationMessage = findViewById(R.id.emailValidationMessage);

        cameraIcon.setVisibility(View.VISIBLE);

        if (ccp != null) {
            ccp.setCustomMasterCountries("BD,MY,SG");
            ccp.setDefaultCountryUsingNameCode("BD");
            ccp.setVisibility(View.GONE);
            ccp.registerCarrierNumberEditText(phoneNumberInput);
            ccp.setNumberAutoFormattingEnabled(false);
            ccp.setFlagSize(24);
            ccp.setArrowSize(8);
            ccp.setContentColor(ContextCompat.getColor(this, android.R.color.black));
        } else {
            Log.e(TAG, "CCP is null in layout");
        }

        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);

        updateVerificationUI();
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());
        cameraIcon.setOnClickListener(v -> pickImage.launch("image/*"));
        birthdayEditText.setOnClickListener(v -> showDatePickerDialog());
        saveButton.setOnClickListener(v -> saveProfileData());
        cancelVerificationButton.setOnClickListener(v -> cancelVerification());
        swipeRefreshLayout.setOnRefreshListener(this::loadProfileDataFromFirestore);

        phoneNumberInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && ccp.getVisibility() == View.VISIBLE) {
                ccp.launchCountrySelectionDialog();
            }
        });

        phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isTextChanging || isProgrammaticChange) return;
                String input = s.toString().trim();

                if (input.equals(lastProcessedPhoneInput)) return;

                isTextChanging = true;
                resetInputBorders();
                adjustPhoneInputPadding();

                if (input.isEmpty()) {
                    isPhoneInput = true;
                    ccp.setVisibility(View.GONE);
                    phoneValidationMessage.setVisibility(View.GONE);
                } else if (AuthUtils.looksLikeEmail(input)) {
                    isPhoneInput = false;
                    ccp.setVisibility(View.GONE);
                    phoneValidationMessage.setText("Please enter a phone number, not an email");
                    phoneValidationMessage.setTextColor(ContextCompat.getColor(EditProfileActivity.this, android.R.color.holo_red_dark));
                    phoneValidationMessage.setVisibility(View.VISIBLE);
                } else {
                    String normalized = AuthUtils.normalizePhoneNumberForBackend(input, ccp, AuthUtils.getSimCountry(EditProfileActivity.this));
                    isPhoneInput = AuthUtils.isValidPhoneNumber(normalized);
                    if (isPhoneInput) {
                        phoneValidationMessage.setText("Valid phone number: " + input);
                        phoneValidationMessage.setTextColor(ContextCompat.getColor(EditProfileActivity.this, android.R.color.holo_green_dark));
                        phoneValidationMessage.setVisibility(View.VISIBLE);
                        if (AuthUtils.updateCountryFlag(normalized, ccp)) {
                            ccp.setVisibility(View.VISIBLE);
                        } else {
                            ccp.setVisibility(View.GONE);
                        }
                    } else {
                        phoneValidationMessage.setText("Invalid phone number: " + input);
                        phoneValidationMessage.setTextColor(ContextCompat.getColor(EditProfileActivity.this, android.R.color.holo_red_dark));
                        phoneValidationMessage.setVisibility(View.VISIBLE);
                        ccp.setVisibility(View.GONE);
                    }
                }
                lastProcessedPhoneInput = input;
                isTextChanging = false;
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String newUsername = s.toString().trim();
                if (!newUsername.isEmpty() && !newUsername.equals(originalUsername)) {
                    checkUsernameAvailability(newUsername);
                } else {
                    usernameEditText.setError(null);
                    resetInputBorders();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void checkUsernameAvailability(String username) {
        firestore.collection("usernames").document(username).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String existingUid = documentSnapshot.getString("uid");
                        if (!currentUser.getUid().equals(existingUid)) {
                            usernameEditText.setError("Username already taken");
                            setErrorBorder(usernameEditText);
                            showCustomToast("Username already taken", false);
                        } else {
                            usernameEditText.setError(null);
                            resetInputBorders();
                        }
                    } else {
                        usernameEditText.setError(null);
                        resetInputBorders();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check username: " + e.getMessage());
                    showCustomToast("Failed to check username availability", false);
                });
    }

    private void setupDynamicInput() {
        phoneNumberInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        ccp.setCountryPreference("BD,MY,SG");
        ccp.setDefaultCountryUsingNameCode("BD");
        ccp.setNumberAutoFormattingEnabled(false);
        ccp.setFlagSize(24);
        ccp.setArrowSize(8);
        ccp.setCcpClickable(false);
        ccp.setVisibility(View.GONE);

        AuthUtils.setupDynamicInput(this, emailInput, null, null, null, emailValidationMessage);
        emailInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        adjustPhoneInputPadding();
        restrictFieldsBasedOnSignInMethod();
    }

    private void adjustPhoneInputPadding() {
        ccp.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ccp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (ccp.getVisibility() == View.VISIBLE) {
                    int ccpWidth = ccp.getWidth();
                    int paddingStart = ccpWidth + (int) (10 * getResources().getDisplayMetrics().density);
                    phoneNumberInput.setPadding(paddingStart, phoneNumberInput.getPaddingTop(),
                            phoneNumberInput.getPaddingEnd(), phoneNumberInput.getPaddingBottom());
                } else {
                    int defaultPadding = (int) (12 * getResources().getDisplayMetrics().density);
                    phoneNumberInput.setPadding(defaultPadding, phoneNumberInput.getPaddingTop(),
                            phoneNumberInput.getPaddingEnd(), phoneNumberInput.getPaddingBottom());
                }
            }
        });
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

    private void loadCachedProfileData() {
        Intent intent = getIntent();
        String cachedFullName = intent.getStringExtra("fullName") != null ? intent.getStringExtra("fullName") : sharedPreferences.getString("fullName", "User");
        String cachedGender = sharedPreferences.getString("gender", DEFAULT_GENDER);
        String cachedBirthday = sharedPreferences.getString("birthday", DEFAULT_BIRTHDAY);
        String cachedPhoneNumber = intent.getStringExtra("phoneNumber") != null ? intent.getStringExtra("phoneNumber") : sharedPreferences.getString("phoneNumber", "");
        String cachedEmail = intent.getStringExtra("email") != null ? intent.getStringExtra("email") : sharedPreferences.getString("email", "");
        originalUsername = intent.getStringExtra("username") != null ? intent.getStringExtra("username") : sharedPreferences.getString("username", "");
        String cachedImageUrl = intent.getStringExtra("currentImageUrl") != null ? intent.getStringExtra("currentImageUrl") : sharedPreferences.getString("imageUrl", "");

        fullNameEditText.setText(cachedFullName);
        genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(cachedGender));
        birthdayEditText.setText(cachedBirthday);

        isProgrammaticChange = true;
        phoneNumberInput.setText(cachedPhoneNumber);
        emailInput.setText(cachedEmail);
        usernameEditText.setText(originalUsername);
        displayFullNameTextView.setText(cachedFullName);
        isProgrammaticChange = false;

        if (!cachedPhoneNumber.isEmpty() && AuthUtils.isValidPhoneNumber(AuthUtils.normalizePhoneNumberForBackend(cachedPhoneNumber, ccp, AuthUtils.getSimCountry(this)))) {
            isPhoneInput = true;
            String normalized = AuthUtils.normalizePhoneNumberForBackend(cachedPhoneNumber, ccp, AuthUtils.getSimCountry(this));
            if (AuthUtils.updateCountryFlag(normalized, ccp)) {
                ccp.setVisibility(View.VISIBLE);
            } else {
                ccp.setVisibility(View.GONE);
            }
            adjustPhoneInputPadding();
        } else {
            ccp.setVisibility(View.GONE);
        }

        loadProfileImage(cachedImageUrl.isEmpty() ? null : Uri.parse(cachedImageUrl));
    }

    private void loadProfileDataFromFirestore() {
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated. Redirecting to login.");
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        showLoader();
        currentUser.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                currentEmail = currentUser.getEmail();
                currentPhoneNumber = currentUser.getPhoneNumber();
            } else {
                Log.e(TAG, "Failed to reload user: " + task.getException().getMessage());
                showCustomToast("Failed to refresh session: " + task.getException().getMessage(), false);
            }

            firestore.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        hideLoader();
                        if (documentSnapshot.exists()) {
                            String fullName = documentSnapshot.getString("fullName") != null ? documentSnapshot.getString("fullName") : "User";
                            String gender = documentSnapshot.getString("gender") != null ? documentSnapshot.getString("gender") : DEFAULT_GENDER;
                            String birthday = documentSnapshot.getString("birthday") != null ? documentSnapshot.getString("birthday") : DEFAULT_BIRTHDAY;
                            String phoneNumber = documentSnapshot.getString("phoneNumber") != null ? documentSnapshot.getString("phoneNumber") : "";
                            String email = documentSnapshot.getString("email") != null ? documentSnapshot.getString("email") : "";
                            originalUsername = documentSnapshot.getString("username") != null ? documentSnapshot.getString("username") : "";
                            pendingEmail = documentSnapshot.getString("pendingEmail") != null ? documentSnapshot.getString("pendingEmail") : "";
                            pendingPhoneNumber = documentSnapshot.getString("pendingPhoneNumber") != null ? documentSnapshot.getString("pendingPhoneNumber") : "";
                            signInMethod = documentSnapshot.getString("signInMethod") != null ? documentSnapshot.getString("signInMethod") : "email";
                            currentImageUrl = documentSnapshot.getString("imageUrl");

                            fullNameEditText.setText(fullName);
                            genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(gender));
                            birthdayEditText.setText(birthday);

                            isProgrammaticChange = true;
                            phoneNumberInput.setText(phoneNumber);
                            emailInput.setText(email);
                            usernameEditText.setText(originalUsername);
                            displayFullNameTextView.setText(fullName);
                            isProgrammaticChange = false;

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("fullName", fullName);
                            editor.putString("gender", gender);
                            editor.putString("birthday", birthday);
                            editor.putString("phoneNumber", phoneNumber);
                            editor.putString("email", email);
                            editor.putString("username", originalUsername);
                            editor.putString("signInMethod", signInMethod);
                            editor.putString("pendingEmail", pendingEmail);
                            editor.putString("pendingPhoneNumber", pendingPhoneNumber);
                            editor.putString("imageUrl", currentImageUrl);
                            editor.apply();

                            currentPhoneNumber = phoneNumber;
                            currentEmail = email;
                            previousEmail = currentEmail;
                            previousPhoneNumber = currentPhoneNumber;

                            if (selectedImageUri == null) {
                                loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
                            }

                            if (!phoneNumber.isEmpty() && AuthUtils.isValidPhoneNumber(AuthUtils.normalizePhoneNumberForBackend(phoneNumber, ccp, AuthUtils.getSimCountry(this)))) {
                                isPhoneInput = true;
                                String normalized = AuthUtils.normalizePhoneNumberForBackend(phoneNumber, ccp, AuthUtils.getSimCountry(this));
                                if (AuthUtils.updateCountryFlag(normalized, ccp)) {
                                    ccp.setVisibility(View.VISIBLE);
                                } else {
                                    ccp.setVisibility(View.GONE);
                                }
                                adjustPhoneInputPadding();
                            } else {
                                ccp.setVisibility(View.GONE);
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
                    })
                    .addOnFailureListener(e -> {
                        hideLoader();
                        Log.e(TAG, "Failed to fetch profile data from Firestore: " + e.getMessage());
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
            Log.e(TAG, "Cannot save profile: User not authenticated.");
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        Log.d(TAG, "Saving profile for user UID: " + currentUser.getUid());

        resetInputBorders();

        String fullName = fullNameEditText.getText().toString().trim();
        String gender = genderSpinner.getSelectedItem() != null ? genderSpinner.getSelectedItem().toString() : DEFAULT_GENDER;
        String birthday = birthdayEditText.getText().toString().trim().isEmpty() ? DEFAULT_BIRTHDAY : birthdayEditText.getText().toString().trim();
        String localPhoneNumber = phoneNumberInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();

        boolean hasError = false;
        if (fullName.isEmpty()) {
            setErrorBorder(fullNameEditText);
            hasError = true;
        }
        if (username.isEmpty()) {
            setErrorBorder(usernameEditText);
            showCustomToast("Username is required", false);
            hasError = true;
        } else if (username.length() < 3) {
            setErrorBorder(usernameEditText);
            showCustomToast("Username must be at least 3 characters", false);
            hasError = true;
        }

        String fullPhoneNumber = localPhoneNumber.isEmpty() ? "" : AuthUtils.normalizePhoneNumberForBackend(localPhoneNumber, ccp, AuthUtils.getSimCountry(this));
        if (!localPhoneNumber.isEmpty()) {
            if (!AuthUtils.isValidPhoneNumber(fullPhoneNumber)) {
                setErrorBorder(phoneNumberInput);
                showCustomToast("Invalid phone number format: " + localPhoneNumber, false);
                hasError = true;
            } else if (AuthUtils.looksLikeEmail(localPhoneNumber)) {
                setErrorBorder(phoneNumberInput);
                showCustomToast("Please enter a phone number, not an email", false);
                hasError = true;
            }
        }

        if ("email".equals(signInMethod) && email.isEmpty()) {
            setErrorBorder(emailInput);
            showCustomToast("Email is required", false);
            hasError = true;
        } else if (!email.isEmpty() && !AuthUtils.isValidEmail(email)) {
            setErrorBorder(emailInput);
            showCustomToast("Invalid email address", false);
            hasError = true;
        }

        if ("phone".equals(signInMethod) && localPhoneNumber.isEmpty()) {
            setErrorBorder(phoneNumberInput);
            showCustomToast("Phone number is required", false);
            hasError = true;
        }

        if (hasError) {
            showCustomToast("Please correct the errors", false);
            return;
        }

        String originalEmail = sharedPreferences.getString("email", "");
        String originalPhoneNumber = sharedPreferences.getString("phoneNumber", "");

        if (!username.equals(originalUsername) && !username.isEmpty()) {
            checkUsernameAndSave(fullName, gender, birthday, fullPhoneNumber, email, username, originalEmail, originalPhoneNumber);
        } else {
            proceedWithSave(fullName, gender, birthday, fullPhoneNumber, email, username, originalEmail, originalPhoneNumber);
        }
    }

    private void checkUsernameAndSave(String fullName, String gender, String birthday, String fullPhoneNumber,
                                      String email, String username, String originalEmail, String originalPhoneNumber) {
        firestore.collection("usernames").document(username).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String existingUid = documentSnapshot.getString("uid");
                        if (!currentUser.getUid().equals(existingUid)) {
                            setErrorBorder(usernameEditText);
                            showCustomToast("Username already taken", false);
                        } else {
                            proceedWithSave(fullName, gender, birthday, fullPhoneNumber, email, username, originalEmail, originalPhoneNumber);
                        }
                    } else {
                        proceedWithSave(fullName, gender, birthday, fullPhoneNumber, email, username, originalEmail, originalPhoneNumber);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Username check failed: " + e.getMessage());
                    showCustomToast("Failed to verify username: " + e.getMessage(), false);
                });
    }

    private void proceedWithSave(String fullName, String gender, String birthday, String fullPhoneNumber,
                                 String email, String username, String originalEmail, String originalPhoneNumber) {
        authHelper.saveProfile(fullName, gender, birthday, fullPhoneNumber, email, username,
                selectedImageUri, currentImageUrl, originalEmail, originalPhoneNumber, originalUsername);
    }

    private void cancelVerification() {
        if ("email".equals(signInMethod) && !pendingEmail.isEmpty()) {
            showLoader();
            firestore.collection("users").document(currentUser.getUid())
                    .update("pendingEmail", null)
                    .addOnSuccessListener(aVoid -> {
                        hideLoader();
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingEmail");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingEmail = "";
                        emailInput.setText(currentEmail);
                        emailInput.setEnabled(true);
                        if (countdownTimer != null) countdownTimer.cancel();
                        updateVerificationUI();
                        showCustomToast("Email verification cancelled.", true);
                    })
                    .addOnFailureListener(e -> {
                        hideLoader();
                        showCustomToast("Failed to cancel verification: " + e.getMessage(), false);
                    });
        } else if ("phone".equals(signInMethod) && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentPhoneNumber)) {
            showLoader();
            firestore.collection("users").document(currentUser.getUid())
                    .update("pendingPhoneNumber", null)
                    .addOnSuccessListener(aVoid -> {
                        hideLoader();
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("pendingPhoneNumber");
                        editor.remove("lastVerificationTime");
                        editor.apply();
                        pendingPhoneNumber = "";
                        phoneNumberInput.setText(currentPhoneNumber);
                        phoneNumberInput.setEnabled(true);
                        if (countdownTimer != null) countdownTimer.cancel();
                        updateVerificationUI();
                        showCustomToast("Phone verification cancelled.", true);
                    })
                    .addOnFailureListener(e -> {
                        hideLoader();
                        showCustomToast("Failed to cancel verification: " + e.getMessage(), false);
                    });
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
            if (countdownTimer != null) countdownTimer.cancel();
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
        if ("email".equals(signInMethod) && !pendingEmail.isEmpty()) {
            showLoader();
            firestore.collection("users").document(currentUser.getUid())
                    .update("pendingEmail", null)
                    .addOnSuccessListener(aVoid -> {
                        hideLoader();
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
                    .addOnFailureListener(e -> {
                        hideLoader();
                        showCustomToast("Failed to cancel verification: " + e.getMessage(), false);
                    });
        } else if ("phone".equals(signInMethod) && !pendingPhoneNumber.isEmpty() && !Objects.equals(pendingPhoneNumber, currentPhoneNumber)) {
            showLoader();
            firestore.collection("users").document(currentUser.getUid())
                    .update("pendingPhoneNumber", null)
                    .addOnSuccessListener(aVoid -> {
                        hideLoader();
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
                    .addOnFailureListener(e -> {
                        hideLoader();
                        showCustomToast("Failed to cancel verification: " + e.getMessage(), false);
                    });
        }
    }

    private void setErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void resetInputBorders() {
        fullNameEditText.setBackgroundResource(R.drawable.edittext_bg);
        birthdayEditText.setBackgroundResource(R.drawable.edittext_bg);
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

        Log.d(TAG, "Showing toast: " + message);
        toast.show();
    }

    private void showLoader() {
        SLTLoader.LoaderConfig config = new SLTLoader.LoaderConfig(com.softourtech.slt.R.raw.loading_global)
                .setWidthDp(40)
                .setHeightDp(40)
                .setUseRoundedBox(true)
                .setOverlayColor(Color.parseColor("#80000000"))
                .setChangeJsonColor(false);
        sltLoader.showCustomLoader(config);
    }

    private void hideLoader() {
        if (sltLoader != null) {
            sltLoader.hideLoader();
        }
    }

    @Override
    public void onAuthStart() {
        showLoader();
        saveButton.setEnabled(false);
    }

    @Override
    public void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                              String email, String username, String originalEmail,
                              String originalPhoneNumber, String originalUsername) {
        hideLoader();
        saveButton.setEnabled(true);
        currentEmail = email;
        currentPhoneNumber = phoneNumber;
        previousEmail = email;
        previousPhoneNumber = phoneNumber;
        this.originalUsername = username;
        displayFullNameTextView.setText(fullName);
        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
        selectedImageUri = null;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("signInMethod", signInMethod);
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
        finish();
    }

    @Override
    public void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                        String currentEmail, String username, String pendingEmail,
                                        String originalEmail, String originalPhoneNumber, String originalUsername) {
        hideLoader();
        saveButton.setEnabled(true);
        this.pendingEmail = pendingEmail;
        emailInput.setText(currentEmail);
        emailInput.setEnabled(false);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("pendingEmail", pendingEmail);
        editor.putLong("lastVerificationTime", System.currentTimeMillis());
        editor.putString("signInMethod", signInMethod);
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

        finish();
    }

    @Override
    public void onPhoneVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                        String currentEmail, String username, String pendingPhoneNumber,
                                        String originalEmail, String originalPhoneNumber, String originalUsername) {
        hideLoader();
        saveButton.setEnabled(true);
        this.pendingPhoneNumber = pendingPhoneNumber;
        phoneNumberInput.setText(currentPhoneNumber);
        phoneNumberInput.setEnabled(false);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("pendingPhoneNumber", pendingPhoneNumber);
        editor.putLong("lastVerificationTime", System.currentTimeMillis());
        editor.putString("signInMethod", signInMethod);
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

        finish();
    }

    @Override
    public void onAuthFailed() {
        hideLoader();
        saveButton.setEnabled(true);
        phoneNumberInput.setText(previousPhoneNumber);
        emailInput.setText(previousEmail);
        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
        selectedImageUri = null;
        showCustomToast("Profile update failed. Please try again.", false);
    }
}