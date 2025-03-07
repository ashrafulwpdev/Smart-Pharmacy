package com.oopgroup.smartpharmacy;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
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
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.hbb20.CountryCodePicker;
import com.bumptech.glide.Glide;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity implements ProfileAuthHelper.OnAuthCompleteListener {

    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;
    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "EditProfileActivity";
    private static final String DEFAULT_GENDER = "Not Specified";
    private static final String DEFAULT_BIRTHDAY = "01-01-2000";

    private ImageButton backButton;
    private ImageView profileImage, cameraIcon;
    private TextView displayFullNameTextView;
    private EditText fullNameEditText, birthdayEditText, phoneNumberInput, emailInput, usernameEditText;
    private Spinner genderSpinner;
    private Button saveButton;
    private CountryCodePicker ccp;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LottieAnimationView loadingSpinner;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private DatabaseReference emailsReference;
    private DatabaseReference phoneNumbersReference;
    private DatabaseReference usernamesReference;
    private StorageReference storageReference;

    private SharedPreferences sharedPreferences;

    private Uri selectedImageUri;
    private String currentImageUrl, currentEmail, currentPhoneNumber;
    private String previousEmail, previousPhoneNumber;
    private String signInMethod;

    private ActivityResultLauncher<String> pickImage;
    private ProfileAuthHelper authHelper;

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

        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && "email".equals(signInMethod) && user.isEmailVerified()) {
                databaseReference.child("pendingEmail").get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        String pendingEmail = task.getResult().getValue(String.class);
                        if (pendingEmail != null && !pendingEmail.equals(currentEmail)) {
                            updateVerifiedEmail(pendingEmail);
                        }
                    }
                });
            }
        });
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
        emailsReference = FirebaseDatabase.getInstance().getReference("emails");
        phoneNumbersReference = FirebaseDatabase.getInstance().getReference("phoneNumbers");
        usernamesReference = FirebaseDatabase.getInstance().getReference("usernames");
        storageReference = FirebaseStorage.getInstance().getReference("profile_images").child(currentUser.getUid());
        currentEmail = currentUser.getEmail();
        currentPhoneNumber = currentUser.getPhoneNumber();
        previousEmail = currentEmail;
        previousPhoneNumber = currentPhoneNumber;
        Log.d(TAG, "Authenticated user UID: " + currentUser.getUid() + ", Email: " + currentEmail + ", Phone: " + currentPhoneNumber);
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
        ccp = findViewById(R.id.ccp);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        loadingSpinner = findViewById(R.id.loadingSpinner);

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

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
                selectedImageUri = null;
            }
            navigateToProfileActivity();
        });
        cameraIcon.setOnClickListener(v -> pickImage.launch("image/*"));
        birthdayEditText.setOnClickListener(v -> showDatePickerDialog());
        saveButton.setOnClickListener(v -> saveProfileData());
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
                        AuthInputHandler.updateCountryFlag(cleanedInput, ccp);
                        String normalized = AuthInputHandler.normalizePhoneNumber(input, ccp);
                        if (AuthInputHandler.isValidPhoneNumber(normalized)) {
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
        if ("google".equals(signInMethod) || "facebook".equals(signInMethod) || "github".equals(signInMethod)) {
            emailInput.setEnabled(false);
            emailInput.setText(currentEmail != null ? currentEmail : "");
            phoneNumberInput.setEnabled(true);
        } else if ("email".equals(signInMethod)) {
            emailInput.setEnabled(true);
            phoneNumberInput.setEnabled(true);
        } else if ("phone".equals(signInMethod)) {
            emailInput.setEnabled(true);
            phoneNumberInput.setEnabled(true);
        }
    }

    private void adjustPhoneInputPadding(View unused) {
        ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ccp.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int paddingStart;
                int defaultPadding = (int) (12 * getResources().getDisplayMetrics().density);

                if (ccp.getVisibility() == View.VISIBLE) {
                    int ccpWidth = ccp.getWidth();
                    paddingStart = ccpWidth + (int) (8 * getResources().getDisplayMetrics().density);
                } else {
                    paddingStart = defaultPadding;
                }

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
        String cachedFullName = sharedPreferences.getString("fullName", "");
        String cachedGender = sharedPreferences.getString("gender", DEFAULT_GENDER);
        String cachedBirthday = sharedPreferences.getString("birthday", DEFAULT_BIRTHDAY);
        String cachedPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        String cachedEmail = sharedPreferences.getString("email", "");
        String cachedUsername = sharedPreferences.getString("username", "");
        String cachedImageUrl = sharedPreferences.getString("imageUrl", "");
        signInMethod = sharedPreferences.getString("signInMethod", "email");

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
            }

            databaseReference.get().addOnCompleteListener(dbTask -> {
                if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                    Map<String, Object> userData = (Map<String, Object>) dbTask.getResult().getValue();
                    String fullName = (String) userData.get("fullName");
                    String gender = (String) userData.get("gender");
                    String birthday = (String) userData.get("birthday");
                    String phoneNumber = (String) userData.get("phoneNumber");
                    String email = (String) userData.get("email");
                    String username = (String) userData.get("username");
                    String pendingEmail = (String) userData.get("pendingEmail");
                    currentImageUrl = (String) userData.get("imageUrl");
                    signInMethod = (String) userData.get("signInMethod");

                    fullNameEditText.setText(fullName);
                    genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(gender != null ? gender : DEFAULT_GENDER));
                    birthdayEditText.setText(birthday != null ? birthday : DEFAULT_BIRTHDAY);
                    phoneNumberInput.setText(phoneNumber);
                    if (pendingEmail != null && !currentUser.isEmailVerified() && "email".equals(signInMethod)) {
                        emailInput.setText(pendingEmail);
                        emailInput.setEnabled(false);
                        showCustomToast("Pending email: " + pendingEmail + " awaits verification.", false);
                    } else {
                        emailInput.setText(email);
                    }
                    usernameEditText.setText(username);
                    displayFullNameTextView.setText(fullName);

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fullName", fullName);
                    editor.putString("gender", gender != null ? gender : DEFAULT_GENDER);
                    editor.putString("birthday", birthday != null ? birthday : DEFAULT_BIRTHDAY);
                    editor.putString("phoneNumber", phoneNumber);
                    editor.putString("email", email);
                    editor.putString("username", username);
                    editor.putString("imageUrl", currentImageUrl != null ? currentImageUrl : "");
                    editor.putString("signInMethod", signInMethod != null ? signInMethod : "email");
                    if (pendingEmail != null) editor.putString("pendingEmail", pendingEmail);
                    editor.apply();

                    currentPhoneNumber = phoneNumber;
                    currentEmail = email;
                    previousEmail = email;
                    previousPhoneNumber = phoneNumber;

                    if (selectedImageUri == null) {
                        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
                    }
                    restrictFieldsBasedOnSignInMethod();
                } else {
                    if (selectedImageUri == null) {
                        loadProfileImage(null);
                    }
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
        if (imageUri != null) {
            Glide.with(this)
                    .load(imageUri)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .circleCrop()
                            .override(INNER_SIZE, INNER_SIZE)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                    .into(profileImage);
        } else if (selectedImageUri != null) {
            Glide.with(this)
                    .load(selectedImageUri)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .circleCrop()
                            .override(INNER_SIZE, INNER_SIZE)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
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
            applyErrorBorder(fullNameEditText);
            hasError = true;
        }
        if (username.isEmpty()) {
            applyErrorBorder(usernameEditText);
            hasError = true;
        }

        String phoneNumber;
        if (!phoneNumberInputText.isEmpty()) {
            phoneNumber = AuthInputHandler.normalizePhoneNumber(phoneNumberInputText, ccp);
            if (!AuthInputHandler.isValidPhoneNumber(phoneNumber)) {
                applyErrorBorder(phoneNumberInput);
                showCustomToast("Invalid phone number format. Examples: +8801712345678 (BD), +60123456789 (MY), +6581234567 (SG)", false);
                hasError = true;
            }
        } else {
            phoneNumber = "";
        }

        if ("email".equals(signInMethod) && email.isEmpty()) {
            applyErrorBorder(emailInput);
            showCustomToast("Email is required for email sign-in accounts.", false);
            hasError = true;
        } else if (!email.isEmpty() && !AuthInputHandler.isValidEmail(email)) {
            applyErrorBorder(emailInput);
            showCustomToast("Please enter a valid email address.", false);
            hasError = true;
        }

        if ("phone".equals(signInMethod) && phoneNumber.isEmpty()) {
            applyErrorBorder(phoneNumberInput);
            showCustomToast("Phone number is required for phone sign-in accounts.", false);
            hasError = true;
        }

        if (hasError) {
            showCustomToast("Please correct the errors in the form", false);
            return;
        }

        boolean emailChanged = !email.equalsIgnoreCase(currentEmail);
        boolean phoneChanged = !phoneNumber.equals(currentPhoneNumber == null ? "" : currentPhoneNumber);
        String originalUsername = sharedPreferences.getString("username", "");
        String originalEmail = sharedPreferences.getString("email", "");
        String originalPhoneNumber = sharedPreferences.getString("phoneNumber", "");
        boolean usernameChanged = !username.equals(originalUsername);

        String finalEmail = email;
        String finalPhoneNumber = phoneNumber;

        if (!emailChanged && !phoneChanged && !usernameChanged) {
            saveProfileDataToFirebase(fullName, gender, birthday, finalPhoneNumber, finalEmail, username, null, originalEmail, originalPhoneNumber, originalUsername);
            return;
        }

        if (emailChanged || phoneChanged || usernameChanged) {
            checkEmailUnique(finalEmail, isEmailUnique -> {
                if (!isEmailUnique) {
                    applyErrorBorder(emailInput);
                    showCustomToast("Email '" + finalEmail + "' is already in use by another account.", false);
                    return;
                }
                checkPhoneNumberUnique(finalPhoneNumber, isPhoneUnique -> {
                    if (!isPhoneUnique) {
                        applyErrorBorder(phoneNumberInput);
                        showCustomToast("Phone number '" + finalPhoneNumber + "' is already in use by another account.", false);
                        return;
                    }
                    if (usernameChanged) {
                        checkUsernameUnique(username, isUsernameUnique -> {
                            if (!isUsernameUnique) {
                                applyErrorBorder(usernameEditText);
                                showCustomToast("Username '" + username + "' is already taken", false);
                                return;
                            }
                            proceedWithSave(fullName, gender, birthday, finalPhoneNumber, finalEmail, username, emailChanged, phoneChanged, originalEmail, originalPhoneNumber, originalUsername);
                        });
                    } else {
                        proceedWithSave(fullName, gender, birthday, finalPhoneNumber, finalEmail, username, emailChanged, phoneChanged, originalEmail, originalPhoneNumber, originalUsername);
                    }
                });
            });
        }
    }

    private void checkEmailUnique(String email, OnEmailCheckListener listener) {
        if (email.isEmpty()) {
            listener.onResult(true);
            return;
        }
        loadingSpinner.setVisibility(View.VISIBLE);
        String emailKey = email.replace(".", "_");
        emailsReference.child(emailKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                loadingSpinner.setVisibility(View.GONE);
                boolean isUnique = !dataSnapshot.exists() || dataSnapshot.getValue(String.class).equals(currentUser.getUid());
                Log.d(TAG, "Email " + email + " is unique: " + isUnique);
                listener.onResult(isUnique);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                loadingSpinner.setVisibility(View.GONE);
                Log.e(TAG, "Email check failed: " + databaseError.getMessage());
                showCustomToast("Failed to check email: " + databaseError.getMessage(), false);
                listener.onResult(false);
            }
        });
    }

    private void checkPhoneNumberUnique(String phoneNumber, OnPhoneCheckListener listener) {
        if (phoneNumber.isEmpty()) {
            listener.onResult(true);
            return;
        }
        loadingSpinner.setVisibility(View.VISIBLE);
        phoneNumbersReference.child(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                loadingSpinner.setVisibility(View.GONE);
                boolean isUnique = !dataSnapshot.exists() || dataSnapshot.getValue(String.class).equals(currentUser.getUid());
                Log.d(TAG, "Phone number " + phoneNumber + " is unique: " + isUnique);
                listener.onResult(isUnique);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                loadingSpinner.setVisibility(View.GONE);
                Log.e(TAG, "Phone number check failed: " + databaseError.getMessage());
                showCustomToast("Failed to check phone number: " + databaseError.getMessage(), false);
                listener.onResult(false);
            }
        });
    }

    private void checkUsernameUnique(String username, OnUsernameCheckListener listener) {
        if (currentUser == null) {
            Log.e(TAG, "Cannot check username: User is not authenticated.");
            showCustomToast("User not authenticated. Please log in.", false);
            listener.onResult(false);
            return;
        }
        loadingSpinner.setVisibility(View.VISIBLE);
        Log.d(TAG, "Checking username: " + username + " for user UID: " + currentUser.getUid());
        usernamesReference.child(username).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                loadingSpinner.setVisibility(View.GONE);
                boolean isUnique = !dataSnapshot.exists() || dataSnapshot.getValue(String.class).equals(currentUser.getUid());
                Log.d(TAG, "Username " + username + " is unique: " + isUnique);
                listener.onResult(isUnique);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                loadingSpinner.setVisibility(View.GONE);
                Log.e(TAG, "Username check failed: " + databaseError.getMessage());
                showCustomToast("Failed to check username: " + databaseError.getMessage(), false);
                listener.onResult(false);
            }
        });
    }

    interface OnEmailCheckListener {
        void onResult(boolean isUnique);
    }

    interface OnPhoneCheckListener {
        void onResult(boolean isUnique);
    }

    interface OnUsernameCheckListener {
        void onResult(boolean isUnique);
    }

    private void proceedWithSave(String fullName, String gender, String birthday, String phoneNumber,
                                 String email, String username, boolean emailChanged, boolean phoneChanged,
                                 String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (emailChanged && "email".equals(signInMethod)) {
            authHelper.showPasswordDialog(fullName, gender, birthday, phoneNumber, email, username, true,
                    originalEmail, originalPhoneNumber, originalUsername);
        } else if (phoneChanged && "phone".equals(signInMethod)) {
            authHelper.verifyPhoneNumber(fullName, gender, birthday, phoneNumber, email, username,
                    originalEmail, originalPhoneNumber, originalUsername);
        } else {
            saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, null,
                    originalEmail, originalPhoneNumber, originalUsername);
        }
    }

    private void applyErrorBorder(EditText editText) {
        editText.setBackgroundResource(R.drawable.edittext_error_bg);
    }

    private void clearErrorBorders() {
        fullNameEditText.setBackgroundResource(R.drawable.edittext_bg);
        phoneNumberInput.setBackgroundResource(R.drawable.edittext_bg);
        emailInput.setBackgroundResource(R.drawable.edittext_bg);
        usernameEditText.setBackgroundResource(R.drawable.edittext_bg);
    }

    private void saveProfileDataToFirebase(String fullName, String gender, String birthday, String phoneNumber,
                                           String email, String username, String imageUrl, String originalEmail,
                                           String originalPhoneNumber, String originalUsername) {
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();
        saveButton.setEnabled(false);

        updateEmailsPhoneNumbersAndUsernames(email, phoneNumber, username, originalEmail, originalPhoneNumber, originalUsername, success -> {
            if (!success) {
                loadingSpinner.setVisibility(View.GONE);
                saveButton.setEnabled(true);
                showCustomToast("Failed to update uniqueness constraints.", false);
                revertToPreviousCredentials();
                return;
            }
            if (selectedImageUri != null) {
                StorageReference fileRef = storageReference.child("profile.jpg");
                UploadTask uploadTask = fileRef.putFile(selectedImageUri);
                uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadedImageUrl = uri.toString();
                    saveUserDataToDatabase(fullName, gender, birthday, phoneNumber, email, username, downloadedImageUrl);
                    selectedImageUri = null;
                })).addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    Log.e(TAG, "Image upload failed: " + e.getMessage());
                    showCustomToast("Image upload failed: " + e.getMessage(), false);
                    revertToPreviousCredentials();
                });
            } else {
                saveUserDataToDatabase(fullName, gender, birthday, phoneNumber, email, username, currentImageUrl);
            }
        });
    }

    private void updateEmailsPhoneNumbersAndUsernames(String newEmail, String newPhoneNumber, String newUsername,
                                                      String originalEmail, String originalPhoneNumber, String originalUsername,
                                                      OnUpdateCompleteListener onComplete) {
        if (originalEmail != null && !originalEmail.isEmpty() && !originalEmail.equalsIgnoreCase(newEmail)) {
            String originalEmailKey = originalEmail.replace(".", "_");
            emailsReference.child(originalEmailKey).removeValue()
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to remove old email: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (originalPhoneNumber != null && !originalPhoneNumber.isEmpty() && !originalPhoneNumber.equals(newPhoneNumber)) {
            phoneNumbersReference.child(originalPhoneNumber).removeValue()
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to remove old phone number: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (originalUsername != null && !originalUsername.isEmpty() && !originalUsername.equals(newUsername)) {
            usernamesReference.child(originalUsername).removeValue()
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to remove old username: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (!newEmail.isEmpty() && !"email".equals(signInMethod)) {
            String emailKey = newEmail.replace(".", "_");
            emailsReference.child(emailKey).setValue(currentUser.getUid())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add new email: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (!newPhoneNumber.isEmpty() && !"phone".equals(signInMethod)) {
            phoneNumbersReference.child(newPhoneNumber).setValue(currentUser.getUid())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add new phone number: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (!newUsername.isEmpty()) {
            usernamesReference.child(newUsername).setValue(currentUser.getUid())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully updated usernames node with new username: " + newUsername);
                        onComplete.onComplete(true);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add new username: " + e.getMessage());
                        onComplete.onComplete(false);
                    });
        } else {
            onComplete.onComplete(true);
        }
    }

    interface OnUpdateCompleteListener {
        void onComplete(boolean success);
    }

    private void saveUserDataToDatabase(String fullName, String gender, String birthday, String phoneNumber,
                                        String email, String username, String imageUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("gender", gender);
        user.put("birthday", birthday);
        user.put("phoneNumber", phoneNumber);
        user.put("email", email);
        user.put("username", username);
        user.put("imageUrl", imageUrl != null ? imageUrl : "");
        user.put("signInMethod", signInMethod);
        String pendingEmail = sharedPreferences.getString("pendingEmail", null);
        if (pendingEmail != null) {
            user.put("pendingEmail", pendingEmail);
        }

        Log.d(TAG, "Writing to database path: users/" + currentUser.getUid());
        databaseReference.setValue(user)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fullName", fullName);
                    editor.putString("gender", gender);
                    editor.putString("birthday", birthday);
                    editor.putString("phoneNumber", phoneNumber);
                    editor.putString("email", email);
                    editor.putString("username", username);
                    editor.putString("imageUrl", imageUrl != null ? imageUrl : "");
                    editor.putString("signInMethod", signInMethod);
                    if (pendingEmail != null) editor.putString("pendingEmail", pendingEmail);
                    editor.apply();

                    currentImageUrl = imageUrl != null ? imageUrl : "";
                    if (!"email".equals(signInMethod)) currentEmail = email;
                    if (!"phone".equals(signInMethod)) currentPhoneNumber = phoneNumber;
                    previousEmail = email;
                    previousPhoneNumber = phoneNumber;

                    displayFullNameTextView.setText(fullName);
                    loadProfileImage(imageUrl != null && !imageUrl.isEmpty() ? Uri.parse(imageUrl) : null);

                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    showCustomToast("Profile saved successfully", true);

                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("imageUrl", imageUrl != null ? imageUrl : "");
                    resultIntent.putExtra("fullName", fullName);
                    resultIntent.putExtra("phoneNumber", phoneNumber);
                    resultIntent.putExtra("email", email);
                    resultIntent.putExtra("username", username);
                    setResult(RESULT_OK, resultIntent);

                    navigateToProfileActivity();
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    Log.e(TAG, "Failed to save profile: " + e.getMessage());
                    showCustomToast("Failed to save profile: " + e.getMessage(), false);
                    revertToPreviousCredentials();
                });
    }

    private void updateVerifiedEmail(String verifiedEmail) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", verifiedEmail);
        updates.put("pendingEmail", null);
        databaseReference.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String emailKey = verifiedEmail.replace(".", "_");
                    emailsReference.child(emailKey).setValue(currentUser.getUid());
                    currentEmail = verifiedEmail;
                    previousEmail = verifiedEmail;
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("email", verifiedEmail);
                    editor.remove("pendingEmail");
                    editor.apply();
                    emailInput.setText(verifiedEmail);
                    emailInput.setEnabled(true);
                    showCustomToast("Email verified and updated.", true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update verified email: " + e.getMessage());
                    showCustomToast("Failed to update email after verification: " + e.getMessage(), false);
                });
    }

    private void revertToPreviousCredentials() {
        phoneNumberInput.setText(previousPhoneNumber);
        emailInput.setText(previousEmail);
        loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
        selectedImageUri = null;
    }

    private void showCustomToast(String message, boolean isSuccess) {
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(toastView);
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
        currentPhoneNumber = phoneNumber;
        saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, null,
                originalEmail, originalPhoneNumber, originalUsername);
    }

    @Override
    public void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                        String currentEmail, String username, String pendingEmail,
                                        String originalEmail, String originalPhoneNumber, String originalUsername) {
        loadingSpinner.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        Map<String, Object> updates = new HashMap<>();
        updates.put("pendingEmail", pendingEmail);
        databaseReference.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("pendingEmail", pendingEmail);
                    editor.apply();
                });
        saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, currentEmail, username, null,
                originalEmail, originalPhoneNumber, originalUsername);
    }

    @Override
    public void onAuthFailed() {
        loadingSpinner.setVisibility(View.GONE);
        saveButton.setEnabled(true);
        revertToPreviousCredentials();
    }
}