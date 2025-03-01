package com.oopgroup.smartpharmacy;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.FirebaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.hbb20.CountryCodePicker;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EditProfileActivity extends AppCompatActivity {

    private ImageButton backButton;
    private ImageView profileImage, cameraIcon;
    private TextView displayFullNameTextView, displayPhoneNumberTextView;
    private EditText fullNameEditText, birthdayEditText, phoneNumberEditText, emailEditText, usernameEditText;
    private Spinner genderSpinner;
    private Button saveButton;
    private CountryCodePicker ccp;

    private SharedPreferences sharedPreferences;
    private Uri selectedImageUri;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            result -> {
                if (result != null) {
                    selectedImageUri = result;
                    profileImage.setImageURI(selectedImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        storageReference = FirebaseStorage.getInstance().getReference("profile_images");

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

        sharedPreferences = getSharedPreferences("UserProfile", MODE_PRIVATE);

        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);
        genderSpinner.setSelection(genderAdapter.getPosition("Male"));

        loadProfileData();

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(EditProfileActivity.this, ProfileActivity.class);
            startActivity(intent);
            finish();
        });

        cameraIcon.setOnClickListener(v -> pickImage.launch("image/*"));

        birthdayEditText.setOnClickListener(v -> showDatePickerDialog());

        saveButton.setOnClickListener(v -> saveProfileData());
    }

    private void loadProfileData() {
        if (currentUser != null) {
            String fullName = sharedPreferences.getString("fullName", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "David Jon");
            String gender = sharedPreferences.getString("gender", "Male");
            String birthday = sharedPreferences.getString("birthday", "05-01-1998");
            String phoneNumber = sharedPreferences.getString("phoneNumber", currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "");
            String email = sharedPreferences.getString("email", currentUser.getEmail() != null ? currentUser.getEmail() : "");
            String username = sharedPreferences.getString("username", "@parves");

            fullNameEditText.setText(fullName);
            genderSpinner.setSelection(((ArrayAdapter<String>) genderSpinner.getAdapter()).getPosition(gender));
            birthdayEditText.setText(birthday);
            phoneNumberEditText.setText(phoneNumber.replaceAll("[^0-9]", ""));
            emailEditText.setText(email);
            usernameEditText.setText(username);
            displayFullNameTextView.setText(fullName);
            displayPhoneNumberTextView.setText((!phoneNumber.isEmpty() ? ccp.getSelectedCountryCodeWithPlus() + " " + phoneNumber.replaceAll("[^0-9]", "") : ""));
        }
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, yearSelected, monthOfYear, dayOfMonth) -> {
                    String date = String.format("%02d-%02d-%d", dayOfMonth, monthOfYear + 1, yearSelected);
                    birthdayEditText.setText(date);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void saveProfileData() {
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullName = fullNameEditText.getText().toString().trim();
        String gender = genderSpinner.getSelectedItem().toString();
        String birthday = birthdayEditText.getText().toString().trim();
        String phoneNumber = ccp.getSelectedCountryCodeWithPlus() + phoneNumberEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();

        if (fullName.isEmpty() || gender.isEmpty() || birthday.isEmpty() || phoneNumberEditText.getText().toString().trim().isEmpty() || email.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean requiresReauth = !currentUser.getEmail().equals(email) || !currentUser.getPhoneNumber().equals(phoneNumber);
        if (requiresReauth) {
            promptForPasswordAndSave(fullName, gender, birthday, phoneNumber, email, username);
        } else {
            saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, null);
        }
    }

    private void promptForPasswordAndSave(String fullName, String gender, String birthday, String phoneNumber, String email, String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter current password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(passwordInput)
                .setTitle("Re-authentication Required")
                .setPositiveButton("OK", (dialog, which) -> {
                    String currentPassword = passwordInput.getText().toString().trim();
                    if (currentPassword.isEmpty()) {
                        Toast.makeText(this, "Please enter your current password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reauthenticateAndSave(fullName, gender, birthday, phoneNumber, email, username, currentPassword);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void reauthenticateAndSave(String fullName, String gender, String birthday, String phoneNumber, String email, String username, String currentPassword) {
        if (currentUser != null) {
            currentUser.reauthenticate(EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword))
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            saveProfileDataToFirebase(fullName, gender, birthday, phoneNumber, email, username, null);
                            updateAuthCredentials(email, phoneNumber);
                        } else {
                            Toast.makeText(this, "Re-authentication failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProfileDataToFirebase(String fullName, String gender, String birthday, String phoneNumber, String email, String username, String imageUrl) {
        if (selectedImageUri != null) {
            StorageReference fileRef = storageReference.child(currentUser.getUid() + ".jpg");
            UploadTask uploadTask = fileRef.putFile(selectedImageUri);
            uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadedImageUrl = uri.toString();
                saveUserDataToDatabase(fullName, gender, birthday, phoneNumber, email, username, downloadedImageUrl);
            })).addOnFailureListener(e -> Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } else {
            saveUserDataToDatabase(fullName, gender, birthday, phoneNumber, email, username, imageUrl);
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
        if (imageUrl != null) user.put("imageUrl", imageUrl);

        databaseReference.child(currentUser.getUid()).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    sharedPreferences.edit()
                            .putString("fullName", fullName)
                            .putString("gender", gender)
                            .putString("birthday", birthday)
                            .putString("phoneNumber", phoneNumber)
                            .putString("email", email)
                            .putString("username", username)
                            .apply();
                    displayFullNameTextView.setText(fullName);
                    displayPhoneNumberTextView.setText(phoneNumber);
                    Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error saving profile: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void updateAuthCredentials(String email, String phoneNumber) {
        if (!currentUser.getEmail().equals(email)) {
            currentUser.updateEmail(email)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Email updated", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Email update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
        if (!currentUser.getPhoneNumber().equals(phoneNumber)) {
            PhoneAuthProvider.getInstance().verifyPhoneNumber(
                    phoneNumber,
                    60,
                    TimeUnit.SECONDS,
                    this,
                    new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(EditProfileActivity.this);
                            final EditText otpInput = new EditText(EditProfileActivity.this);
                            otpInput.setHint("Enter OTP");
                            builder.setView(otpInput)
                                    .setTitle("Verify Phone")
                                    .setPositiveButton("Verify", (dialog, which) -> {
                                        String otp = otpInput.getText().toString().trim();
                                        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                                        currentUser.updatePhoneNumber(credential)
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(EditProfileActivity.this, "Phone number updated", Toast.LENGTH_SHORT).show();
                                                    saveUserDataToDatabaseAfterPhoneUpdate(phoneNumber);
                                                })
                                                .addOnFailureListener(e -> Toast.makeText(EditProfileActivity.this, "Phone update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                    })
                                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                                    .show();
                        }

                        @Override
                        public void onVerificationFailed(FirebaseException e) {
                            Toast.makeText(EditProfileActivity.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onVerificationCompleted(PhoneAuthCredential credential) {
                            currentUser.updatePhoneNumber(credential)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(EditProfileActivity.this, "Phone number updated", Toast.LENGTH_SHORT).show();
                                        saveUserDataToDatabaseAfterPhoneUpdate(phoneNumber);
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(EditProfileActivity.this, "Phone update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }
            );
        }
    }

    private void saveUserDataToDatabaseAfterPhoneUpdate(String phoneNumber) {
        String fullName = fullNameEditText.getText().toString().trim();
        String gender = genderSpinner.getSelectedItem().toString();
        String birthday = birthdayEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();

        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("gender", gender);
        user.put("birthday", birthday);
        user.put("phoneNumber", phoneNumber);
        user.put("email", email);
        user.put("username", username);

        databaseReference.child(currentUser.getUid()).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    sharedPreferences.edit()
                            .putString("phoneNumber", phoneNumber)
                            .apply();
                    displayPhoneNumberTextView.setText(phoneNumber);
                    Toast.makeText(this, "Phone number updated in database", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating database: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}