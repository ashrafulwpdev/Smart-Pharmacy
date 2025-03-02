package com.oopgroup.smartpharmacy;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.bumptech.glide.Glide;
import com.hbb20.CountryCodePicker;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * EditProfileActivity allows users to modify their profile details, including name, gender, birthday,
 * phone number, email, username, and profile image, with data saved to Firebase and cached locally.
 */
public class EditProfileActivity extends AppCompatActivity {

    // Constants
    private static final int IMAGE_SIZE = 102;
    private static final int INNER_SIZE = IMAGE_SIZE - 4;
    private static final int PHONE_MAX_LENGTH = 10;
    private static final String PREFS_NAME = "UserProfile";
    private static final String TAG = "EditProfileActivity";
    private static final String DEFAULT_GENDER = "Not Specified";
    private static final String DEFAULT_BIRTHDAY = "01-01-2000";

    // UI Elements
    private ImageButton backButton;
    private ImageView profileImage, cameraIcon;
    private TextView displayFullNameTextView, displayPhoneNumberTextView;
    private EditText fullNameEditText, birthdayEditText, phoneNumberEditText, emailEditText, usernameEditText;
    private Spinner genderSpinner;
    private Button saveButton;
    private CountryCodePicker ccp;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LottieAnimationView loadingSpinner;

    // Firebase and Storage
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;

    // SharedPreferences for caching
    private SharedPreferences sharedPreferences;

    // Image Picker
    private Uri selectedImageUri;
    private String currentImageUrl, currentEmail, currentPhoneNumber;
    private String previousEmail, previousPhoneNumber;
    private boolean isPhonePrimary;
    private ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            result -> {
                if (result != null) {
                    selectedImageUri = result;
                    loadProfileImage(selectedImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initializeFirebase();
        initializeUI();
        setupListeners();
        loadCachedProfileData();
        loadProfileDataFromFirebase();
    }

    private void initializeFirebase() {
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class)); // Replace with your login activity
            finish();
            return;
        }
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        storageReference = FirebaseStorage.getInstance().getReference("profile_images").child(currentUser.getUid());
        currentEmail = currentUser.getEmail();
        currentPhoneNumber = currentUser.getPhoneNumber();
        previousEmail = currentEmail;
        previousPhoneNumber = currentPhoneNumber;
        isPhonePrimary = currentUser.getProviderData().stream()
                .anyMatch(info -> PhoneAuthProvider.PROVIDER_ID.equals(info.getProviderId()) && info.getPhoneNumber() != null);
    }

    private void initializeUI() {
        backButton = findViewById(R.id.backButton);
        profileImage = findViewById(R.id.profileImage);
        cameraIcon = findViewById(R.id.cameraIcon);
        displayFullNameTextView = findViewById(R.id.displayFullName);
        displayPhoneNumberTextView = findViewById(R.id.displayPhoneNumber);
        fullNameEditText = findViewById(R.id.fullName);
        genderSpinner = findViewById(R.id.gender);
        birthdayEditText = findViewById(R.id.birthday);
        phoneNumberEditText = findViewById(R.id.phoneNumberInput);
        emailEditText = findViewById(R.id.email);
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

        ccp.setCustomMasterCountries("BD,MY,IN,SG");
        ccp.setDefaultCountryUsingNameCode("BD");
        ccp.resetToDefaultCountry();

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);

        phoneNumberEditText.addTextChangedListener(new TextWatcher() {
            private boolean hasShownToast = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String currentText = s.toString();
                if (currentText.length() > PHONE_MAX_LENGTH) {
                    s.delete(PHONE_MAX_LENGTH, s.length());
                    if (!hasShownToast) {
                        showCustomToast("Phone number limited to 10 digits.", false);
                        hasShownToast = true;
                    }
                } else {
                    hasShownToast = false;
                }
            }
        });
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> navigateToProfileActivity());
        cameraIcon.setOnClickListener(v -> pickImage.launch("image/*"));
        birthdayEditText.setOnClickListener(v -> showDatePickerDialog());
        saveButton.setOnClickListener(v -> saveProfileData());
        swipeRefreshLayout.setOnRefreshListener(this::loadProfileDataFromFirebase);
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

        fullNameEditText.setText(cachedFullName);
        genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(cachedGender));
        birthdayEditText.setText(cachedBirthday);
        phoneNumberEditText.setText(cachedPhoneNumber.replaceAll("[^0-9+]", "")); // Strip non-numeric except +
        emailEditText.setText(cachedEmail);
        usernameEditText.setText(cachedUsername);
        displayFullNameTextView.setText(cachedFullName);
        displayPhoneNumberTextView.setText(cachedPhoneNumber.isEmpty() ? "" : cachedPhoneNumber);
        loadProfileImage(cachedImageUrl.isEmpty() ? null : Uri.parse(cachedImageUrl));
    }

    private void loadProfileDataFromFirebase() {
        if (currentUser == null) {
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUser.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                currentEmail = currentUser.getEmail();
                currentPhoneNumber = currentUser.getPhoneNumber();
            }

            databaseReference.child(currentUser.getUid()).get().addOnCompleteListener(dbTask -> {
                if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                    Map<String, Object> userData = (Map<String, Object>) dbTask.getResult().getValue();
                    String fullName = (String) userData.get("fullName");
                    String gender = (String) userData.get("gender");
                    String birthday = (String) userData.get("birthday");
                    String phoneNumber = (String) userData.get("phoneNumber");
                    String email = (String) userData.get("email");
                    String username = (String) userData.get("username");
                    currentImageUrl = (String) userData.get("imageUrl");

                    fullNameEditText.setText(fullName);
                    genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(gender != null ? gender : DEFAULT_GENDER));
                    birthdayEditText.setText(birthday != null ? birthday : DEFAULT_BIRTHDAY);
                    phoneNumberEditText.setText(phoneNumber != null ? phoneNumber.replaceAll("[^0-9]", "") : ""); // Show only digits
                    emailEditText.setText(currentEmail); // Use Auth email
                    usernameEditText.setText(username);
                    displayFullNameTextView.setText(fullName);
                    displayPhoneNumberTextView.setText(phoneNumber != null && !phoneNumber.isEmpty() ? phoneNumber : "");

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fullName", fullName);
                    editor.putString("gender", gender != null ? gender : DEFAULT_GENDER);
                    editor.putString("birthday", birthday != null ? birthday : DEFAULT_BIRTHDAY);
                    editor.putString("phoneNumber", phoneNumber);
                    editor.putString("email", currentEmail);
                    editor.putString("username", username);
                    editor.putString("imageUrl", currentImageUrl != null ? currentImageUrl : "");
                    editor.apply();

                    currentPhoneNumber = phoneNumber;
                    previousEmail = currentEmail;
                    previousPhoneNumber = phoneNumber;

                    loadProfileImage(currentImageUrl != null && !currentImageUrl.isEmpty() ? Uri.parse(currentImageUrl) : null);
                } else {
                    loadProfileImage(null);
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
        profileImage.setImageDrawable(null);
        if (imageUri != null) {
            Glide.with(this)
                    .load(imageUri)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .circleCrop()
                            .override(INNER_SIZE, INNER_SIZE)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                    .error(R.drawable.default_profile)
                    .into(profileImage);
        } else {
            Glide.with(this)
                    .load(R.drawable.default_profile)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .circleCrop()
                            .override(INNER_SIZE, INNER_SIZE))
                    .into(profileImage);
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
            showCustomToast("User not authenticated. Please log in.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String fullName = fullNameEditText.getText().toString().trim();
        String gender = genderSpinner.getSelectedItem() != null ? genderSpinner.getSelectedItem().toString() : DEFAULT_GENDER;
        String birthday = birthdayEditText.getText().toString().trim().isEmpty() ? DEFAULT_BIRTHDAY : birthdayEditText.getText().toString().trim();
        String phoneNumberInput = phoneNumberEditText.getText().toString().trim();
        String phoneNumber = phoneNumberInput.isEmpty() ? "" : ccp.getSelectedCountryCodeWithPlus() + phoneNumberInput; // Only append country code once
        String email = emailEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();

        if (fullName.isEmpty() || email.isEmpty() || username.isEmpty()) {
            showCustomToast("Full name, email, and username are required.", false);
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showCustomToast("Please enter a valid email address.", false);
            return;
        }

        if (!phoneNumberInput.isEmpty() && phoneNumberInput.length() < 8) {
            showCustomToast("Phone number must be at least 8 digits if provided.", false);
            return;
        }

        boolean emailChanged = !email.equalsIgnoreCase(currentEmail);
        boolean phoneChanged = !phoneNumber.equals(currentPhoneNumber == null ? "" : currentPhoneNumber);

        if (emailChanged && !isPhonePrimary) {
            showCustomPasswordDialog(fullName, gender, birthday, phoneNumber, email, username, true);
        } else if (phoneChanged && isPhonePrimary) {
            verifyPhoneNumber(fullName, gender, birthday, phoneNumber, email, username);
        } else {
            saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri == null ? currentImageUrl : null);
        }
    }

    private void showCustomPasswordDialog(String fullName, String gender, String birthday, String phoneNumber, String email, String username, boolean isEmailReauth) {
        View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setGravity(Gravity.CENTER);
        layout.setAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));

        TextView title = new TextView(this);
        title.setText("Authenticate to Proceed");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter your password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(20, 15, 20, 15);
        passwordInput.setBackgroundResource(android.R.drawable.edit_text);
        passwordInput.setTextColor(Color.BLACK);
        passwordInput.setHintTextColor(Color.GRAY);
        layout.addView(passwordInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton("Verify", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(Color.parseColor("#083EC9"));
            positiveButton.setBackgroundResource(android.R.drawable.btn_default_small);
            positiveButton.setPadding(20, 10, 20, 10);
            positiveButton.setOnClickListener(v -> {
                String currentPassword = passwordInput.getText().toString().trim();
                if (currentPassword.isEmpty()) {
                    showCustomToast("Please enter your password.", false);
                } else {
                    AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);
                    reauthenticateAndVerifyEmail(fullName, gender, birthday, phoneNumber, email, username, credential);
                    dialog.dismiss();
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(Color.GRAY);
            negativeButton.setBackgroundResource(android.R.drawable.btn_default_small);
            negativeButton.setPadding(20, 10, 20, 10);
        });

        dialog.show();
    }

    private void verifyPhoneNumber(String fullName, String gender, String birthday, String phoneNumber, String email, String username) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                TimeUnit.SECONDS,
                this,
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                        showCustomOTPDialog(fullName, gender, birthday, phoneNumber, email, username, verificationId);
                    }

                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        reauthenticateAndSave(fullName, gender, birthday, phoneNumber, email, username, credential);
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        showCustomToast("Phone verification failed: " + e.getMessage(), false);
                        revertToPreviousCredentials();
                    }
                }
        );
    }

    private void showCustomOTPDialog(String fullName, String gender, String birthday, String phoneNumber, String email, String username, String verificationId) {
        View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setGravity(Gravity.CENTER);
        layout.setAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));

        TextView title = new TextView(this);
        title.setText("Verify Your Phone");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        EditText otpInput = new EditText(this);
        otpInput.setHint("Enter OTP");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpInput.setPadding(20, 15, 20, 15);
        otpInput.setBackgroundResource(android.R.drawable.edit_text);
        otpInput.setTextColor(Color.BLACK);
        otpInput.setHintTextColor(Color.GRAY);
        layout.addView(otpInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", (d, which) -> {
                    revertToPreviousCredentials();
                    d.dismiss();
                })
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(Color.parseColor("#083EC9"));
            positiveButton.setBackgroundResource(android.R.drawable.btn_default_small);
            positiveButton.setPadding(20, 10, 20, 10);
            positiveButton.setOnClickListener(v -> {
                String otp = otpInput.getText().toString().trim();
                if (otp.isEmpty()) {
                    showCustomToast("Please enter the OTP.", false);
                } else {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                    reauthenticateAndSave(fullName, gender, birthday, phoneNumber, email, username, credential);
                    dialog.dismiss();
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(Color.GRAY);
            negativeButton.setBackgroundResource(android.R.drawable.btn_default_small);
            negativeButton.setPadding(20, 10, 20, 10);
        });

        dialog.show();
    }

    private void reauthenticateAndVerifyEmail(String fullName, String gender, String birthday, String phoneNumber, String email, String username, AuthCredential credential) {
        if (currentUser == null) {
            showCustomToast("User session expired. Please log in again.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();
        saveButton.setEnabled(false);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Re-authentication successful");
                        if (!email.equalsIgnoreCase(currentEmail)) {
                            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener(fetchTask -> {
                                if (fetchTask.isSuccessful() && !fetchTask.getResult().getSignInMethods().isEmpty()) {
                                    loadingSpinner.setVisibility(View.GONE);
                                    saveButton.setEnabled(true);
                                    showCustomToast("This email is already in use by another account.", false);
                                    revertToPreviousCredentials();
                                } else {
                                    currentUser.verifyBeforeUpdateEmail(email)
                                            .addOnSuccessListener(aVoid -> {
                                                loadingSpinner.setVisibility(View.GONE);
                                                saveButton.setEnabled(true);
                                                showCustomToast("A verification email has been sent to " + email + ". Please verify it to update your email.", true);
                                                // Update database with new email immediately
                                                databaseReference.child(currentUser.getUid()).child("email").setValue(email);
                                                saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri == null ? currentImageUrl : null);
                                            })
                                            .addOnFailureListener(e -> {
                                                loadingSpinner.setVisibility(View.GONE);
                                                saveButton.setEnabled(true);
                                                showCustomToast("Failed to send verification email: " + e.getMessage(), false);
                                                revertToPreviousCredentials();
                                            });
                                }
                            });
                        } else {
                            loadingSpinner.setVisibility(View.GONE);
                            saveButton.setEnabled(true);
                            saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri == null ? currentImageUrl : null);
                        }
                    } else {
                        loadingSpinner.setVisibility(View.GONE);
                        saveButton.setEnabled(true);
                        showCustomToast("Re-authentication failed: Incorrect credentials or network issue.", false);
                        revertToPreviousCredentials();
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    showCustomToast("Authentication error: " + e.getMessage(), false);
                    revertToPreviousCredentials();
                });
    }

    private void reauthenticateAndSave(String fullName, String gender, String birthday, String phoneNumber, String email, String username, AuthCredential credential) {
        if (currentUser == null) {
            showCustomToast("User session expired. Please log in again.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();
        saveButton.setEnabled(false);

        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    if (task.isSuccessful()) {
                        currentPhoneNumber = phoneNumber;
                        databaseReference.child(currentUser.getUid()).child("phoneNumber").setValue(phoneNumber);
                        saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri == null ? currentImageUrl : null);
                    } else {
                        showCustomToast("Re-authentication failed: Incorrect credentials or network issue.", false);
                        revertToPreviousCredentials();
                    }
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    showCustomToast("Authentication error: " + e.getMessage(), false);
                    revertToPreviousCredentials();
                });
    }

    private void saveProfileDataToFirebase(String fullName, String gender, String birthday, String phoneNumber, String email, String username, String imageUrl) {
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingSpinner.playAnimation();
        saveButton.setEnabled(false);

        String finalImageUrl = (imageUrl == null || imageUrl.isEmpty()) && selectedImageUri == null ? null : imageUrl;

        if (selectedImageUri != null) {
            StorageReference fileRef = storageReference.child("profile.jpg");
            UploadTask uploadTask = fileRef.putFile(selectedImageUri);
            uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadedImageUrl = uri.toString();
                saveUserDataToDatabase(fullName, gender, birthday, phoneNumber, email, username, downloadedImageUrl);
            })).addOnFailureListener(e -> {
                loadingSpinner.setVisibility(View.GONE);
                saveButton.setEnabled(true);
                showCustomToast("Image upload failed: " + e.getMessage(), false);
                revertToPreviousCredentials();
            });
        } else {
            saveUserDataToDatabase(fullName, gender, birthday, phoneNumber, email, username, finalImageUrl);
        }
    }

    private void saveUserDataToDatabase(String fullName, String gender, String birthday, String phoneNumber, String email, String username, String imageUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("gender", gender);
        user.put("birthday", birthday);
        user.put("phoneNumber", phoneNumber);
        user.put("email", email);
        user.put("username", username);
        user.put("imageUrl", imageUrl);

        databaseReference.child(currentUser.getUid()).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("fullName", fullName);
                    editor.putString("gender", gender);
                    editor.putString("birthday", birthday);
                    editor.putString("phoneNumber", phoneNumber);
                    editor.putString("email", email);
                    editor.putString("username", username);
                    editor.putString("imageUrl", imageUrl);
                    editor.apply();

                    currentPhoneNumber = phoneNumber;
                    currentEmail = email;
                    currentImageUrl = imageUrl;
                    previousEmail = email;
                    previousPhoneNumber = phoneNumber;

                    displayFullNameTextView.setText(fullName);
                    displayPhoneNumberTextView.setText(phoneNumber);
                    loadProfileImage(imageUrl != null && !imageUrl.isEmpty() ? Uri.parse(imageUrl) : null);

                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    showCustomToast("Profile saved successfully", true);
                    navigateToProfileActivity();
                })
                .addOnFailureListener(e -> {
                    loadingSpinner.setVisibility(View.GONE);
                    saveButton.setEnabled(true);
                    showCustomToast("Failed to save profile: " + e.getMessage(), false);
                    revertToPreviousCredentials();
                });
    }

    private void revertToPreviousCredentials() {
        emailEditText.setText(previousEmail);
        phoneNumberEditText.setText(previousPhoneNumber != null ? previousPhoneNumber.replaceAll("[^0-9]", "") : "");
        displayPhoneNumberTextView.setText(previousPhoneNumber != null && !previousPhoneNumber.isEmpty() ? previousPhoneNumber : "");
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
        toast.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth.getCurrentUser() != null) {
            currentUser = auth.getCurrentUser();
            loadProfileDataFromFirebase();
        } else {
            showCustomToast("User session expired. Please log in again.", false);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
}