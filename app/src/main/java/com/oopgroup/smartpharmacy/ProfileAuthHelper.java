package com.oopgroup.smartpharmacy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProfileAuthHelper {

    private static final String TAG = "ProfileAuthHelper";
    private static final String PREFS_NAME = "AuthPrefs";
    private static final String LAST_VERIFICATION_TIME = "lastVerificationTime";
    private static final long RESEND_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    private final Context context;
    private final FirebaseAuth auth;
    private final FirebaseUser currentUser;
    private final OnAuthCompleteListener authCompleteListener;
    private final SharedPreferences authPrefs;
    private final DatabaseReference databaseReference;
    private final DatabaseReference emailsReference;
    private final DatabaseReference phoneNumbersReference;
    private final DatabaseReference usernamesReference;
    private final StorageReference storageReference;

    public ProfileAuthHelper(Context context, OnAuthCompleteListener listener) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.currentUser = auth.getCurrentUser();
        this.authCompleteListener = listener;
        this.authPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.databaseReference = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        this.emailsReference = FirebaseDatabase.getInstance().getReference("emails");
        this.phoneNumbersReference = FirebaseDatabase.getInstance().getReference("phoneNumbers");
        this.usernamesReference = FirebaseDatabase.getInstance().getReference("usernames");
        this.storageReference = FirebaseStorage.getInstance().getReference("profile_images").child(currentUser.getUid());
    }

    public void saveProfile(String fullName, String gender, String birthday, String phoneNumber, String email,
                            String username, Uri selectedImageUri, String currentImageUrl,
                            String originalEmail, String originalPhoneNumber, String originalUsername) {
        authCompleteListener.onAuthStart();

        String signInMethod = authPrefs.getString("signInMethod", "email");
        boolean emailChanged = !email.equalsIgnoreCase(originalEmail);
        boolean phoneChanged = !phoneNumber.equals(originalPhoneNumber);
        boolean usernameChanged = !username.equals(originalUsername);

        if (!emailChanged && !phoneChanged && !usernameChanged && selectedImageUri == null) {
            saveToDatabase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri, currentImageUrl,
                    originalEmail, originalPhoneNumber, originalUsername);
            return;
        }

        checkUniqueness(email, phoneNumber, username, originalEmail, originalPhoneNumber, originalUsername, () -> {
            if (emailChanged && "email".equals(signInMethod)) {
                showPasswordDialog(fullName, gender, birthday, phoneNumber, email, username, true,
                        selectedImageUri, currentImageUrl, originalEmail, originalPhoneNumber, originalUsername);
            } else if (phoneChanged && "phone".equals(signInMethod)) {
                verifyPhoneNumber(fullName, gender, birthday, phoneNumber, email, username,
                        originalEmail, originalPhoneNumber, originalUsername);
            } else {
                saveToDatabase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri, currentImageUrl,
                        originalEmail, originalPhoneNumber, originalUsername);
            }
        }, errorMessage -> {
            showToast(errorMessage, false);
            authCompleteListener.onAuthFailed();
        });
    }

    private void checkUniqueness(String email, String phoneNumber, String username,
                                 String originalEmail, String originalPhoneNumber, String originalUsername,
                                 Runnable onSuccess, ErrorCallback onFailure) {
        if (!email.equals(originalEmail) && !email.isEmpty()) {
            String emailKey = email.replace(".", "_");
            emailsReference.child(emailKey).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists() && !snapshot.getValue(String.class).equals(currentUser.getUid())) {
                        onFailure.onError("Email '" + email + "' is already in use by another account.");
                    } else {
                        checkPhoneNumber(phoneNumber, username, originalPhoneNumber, originalUsername, onSuccess, onFailure);
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    onFailure.onError("Failed to check email: " + error.getMessage());
                }
            });
        } else {
            checkPhoneNumber(phoneNumber, username, originalPhoneNumber, originalUsername, onSuccess, onFailure);
        }
    }

    private void checkPhoneNumber(String phoneNumber, String username, String originalPhoneNumber, String originalUsername,
                                  Runnable onSuccess, ErrorCallback onFailure) {
        if (!phoneNumber.equals(originalPhoneNumber) && !phoneNumber.isEmpty()) {
            phoneNumbersReference.child(phoneNumber).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists() && !snapshot.getValue(String.class).equals(currentUser.getUid())) {
                        onFailure.onError("Phone number '" + phoneNumber + "' is already in use by another account.");
                    } else {
                        checkUsername(username, originalUsername, onSuccess, onFailure);
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    onFailure.onError("Failed to check phone number: " + error.getMessage());
                }
            });
        } else {
            checkUsername(username, originalUsername, onSuccess, onFailure);
        }
    }

    private void checkUsername(String username, String originalUsername, Runnable onSuccess, ErrorCallback onFailure) {
        if (!username.equals(originalUsername)) {
            usernamesReference.child(username).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    if (snapshot.exists() && !snapshot.getValue(String.class).equals(currentUser.getUid())) {
                        onFailure.onError("Username '" + username + "' is already taken.");
                    } else {
                        onSuccess.run();
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    onFailure.onError("Failed to check username: " + error.getMessage());
                }
            });
        } else {
            onSuccess.run();
        }
    }

    private void saveToDatabase(String fullName, String gender, String birthday, String phoneNumber,
                                String email, String username, Uri selectedImageUri, String currentImageUrl,
                                String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (selectedImageUri != null) {
            StorageReference fileRef = storageReference.child("profile.jpg");
            UploadTask uploadTask = fileRef.putFile(selectedImageUri);
            uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadedImageUrl = uri.toString();
                writeToDatabase(fullName, gender, birthday, phoneNumber, email, username, downloadedImageUrl,
                        originalEmail, originalPhoneNumber, originalUsername);
            })).addOnFailureListener(e -> {
                Log.e(TAG, "Image upload failed: " + e.getMessage());
                showToast("Image upload failed: " + e.getMessage(), false);
                authCompleteListener.onAuthFailed();
            });
        } else {
            writeToDatabase(fullName, gender, birthday, phoneNumber, email, username, currentImageUrl,
                    originalEmail, originalPhoneNumber, originalUsername);
        }
    }

    private void writeToDatabase(String fullName, String gender, String birthday, String phoneNumber,
                                 String email, String username, String imageUrl,
                                 String originalEmail, String originalPhoneNumber, String originalUsername) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("gender", gender);
        user.put("birthday", birthday);
        user.put("phoneNumber", phoneNumber);
        user.put("email", email);
        user.put("username", username);
        user.put("imageUrl", imageUrl != null ? imageUrl : "");
        user.put("signInMethod", authPrefs.getString("signInMethod", "email"));

        String pendingEmail = authPrefs.getString("pendingEmail", null);
        String pendingPhoneNumber = authPrefs.getString("pendingPhoneNumber", null);
        if (pendingEmail != null && !email.equals(currentUser.getEmail())) {
            user.put("pendingEmail", pendingEmail);
            Log.d(TAG, "Preserving pendingEmail: " + pendingEmail);
        } else {
            user.put("pendingEmail", null);
        }
        if (pendingPhoneNumber != null && !phoneNumber.equals(currentUser.getPhoneNumber())) {
            user.put("pendingPhoneNumber", pendingPhoneNumber);
            Log.d(TAG, "Preserving pendingPhoneNumber: " + pendingPhoneNumber);
        } else {
            user.put("pendingPhoneNumber", null);
        }

        updateEmailsPhoneNumbersAndUsernames(email, phoneNumber, username, originalEmail, originalPhoneNumber, originalUsername, success -> {
            if (!success) {
                showToast("Failed to update uniqueness constraints.", false);
                authCompleteListener.onAuthFailed();
                return;
            }

            databaseReference.setValue(user)
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = authPrefs.edit();
                        editor.putString("fullName", fullName);
                        editor.putString("gender", gender);
                        editor.putString("birthday", birthday);
                        editor.putString("phoneNumber", phoneNumber);
                        editor.putString("email", email);
                        editor.putString("username", username);
                        editor.putString("imageUrl", imageUrl != null ? imageUrl : "");
                        if (pendingEmail != null && !email.equals(currentUser.getEmail())) {
                            editor.putString("pendingEmail", pendingEmail);
                        } else {
                            editor.remove("pendingEmail");
                        }
                        if (pendingPhoneNumber != null && !phoneNumber.equals(currentUser.getPhoneNumber())) {
                            editor.putString("pendingPhoneNumber", pendingPhoneNumber);
                        } else {
                            editor.remove("pendingPhoneNumber");
                        }
                        editor.apply();

                        authCompleteListener.onAuthSuccess(fullName, gender, birthday, phoneNumber, email, username,
                                originalEmail, originalPhoneNumber, originalUsername);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save profile: " + e.getMessage());
                        showToast("Failed to save profile: " + e.getMessage(), false);
                        authCompleteListener.onAuthFailed();
                    });
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

        if (!newEmail.isEmpty() && !"email".equals(authPrefs.getString("signInMethod", "email"))) {
            String emailKey = newEmail.replace(".", "_");
            emailsReference.child(emailKey).setValue(currentUser.getUid())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add new email: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (!newPhoneNumber.isEmpty() && !"phone".equals(authPrefs.getString("signInMethod", "email"))) {
            phoneNumbersReference.child(newPhoneNumber).setValue(currentUser.getUid())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add new phone number: " + e.getMessage());
                        onComplete.onComplete(false);
                        return;
                    });
        }

        if (!newUsername.isEmpty()) {
            usernamesReference.child(newUsername).setValue(currentUser.getUid())
                    .addOnSuccessListener(aVoid -> onComplete.onComplete(true))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add new username: " + e.getMessage());
                        onComplete.onComplete(false);
                    });
        } else {
            onComplete.onComplete(true);
        }
    }

    public void showPasswordDialog(String fullName, String gender, String birthday, String phoneNumber,
                                   String email, String username, boolean isEmailReauth,
                                   Uri selectedImageUri, String currentImageUrl,
                                   String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (!(context instanceof Activity)) return;

        View dialogView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setGravity(Gravity.CENTER);
        layout.setAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in));

        TextView title = new TextView(context);
        title.setText("Authenticate to Proceed");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        EditText passwordInput = new EditText(context);
        passwordInput.setHint("Enter your password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(20, 15, 20, 15);
        passwordInput.setBackgroundResource(android.R.drawable.edit_text);
        passwordInput.setTextColor(Color.BLACK);
        passwordInput.setHintTextColor(Color.GRAY);
        layout.addView(passwordInput);

        AlertDialog dialog = new AlertDialog.Builder(context)
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
                    showToast("Please enter your password.", false);
                } else {
                    AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);
                    reauthenticateAndVerifyEmail(fullName, gender, birthday, phoneNumber, email, username,
                            credential, selectedImageUri, currentImageUrl, originalEmail, originalPhoneNumber, originalUsername);
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

    public void verifyPhoneNumber(String fullName, String gender, String birthday, String phoneNumber,
                                  String email, String username, String originalEmail,
                                  String originalPhoneNumber, String originalUsername) {
        long lastVerificationTime = authPrefs.getLong(LAST_VERIFICATION_TIME, 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastVerificationTime < RESEND_TIMEOUT) {
            long timeLeft = RESEND_TIMEOUT - (currentTime - lastVerificationTime);
            long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(timeLeft);
            long secondsLeft = TimeUnit.MILLISECONDS.toSeconds(timeLeft % (60 * 1000));
            showToast("Please wait " + minutesLeft + "m " + secondsLeft + "s before resending.", false);
            showOTPDialog(fullName, gender, birthday, phoneNumber, email, username, null,
                    originalEmail, originalPhoneNumber, originalUsername);
            return;
        }

        authCompleteListener.onAuthStart();
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phoneNumber,
                60,
                TimeUnit.SECONDS,
                (Activity) context,
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        authPrefs.edit().putLong(LAST_VERIFICATION_TIME, System.currentTimeMillis()).putString("pendingPhoneNumber", phoneNumber).apply();
                        authCompleteListener.onPhoneVerificationSent(fullName, gender, birthday, phoneNumber, email, username,
                                phoneNumber, originalEmail, originalPhoneNumber, originalUsername);
                        showOTPDialog(fullName, gender, birthday, phoneNumber, email, username, verificationId,
                                originalEmail, originalPhoneNumber, originalUsername);
                    }

                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        updatePhoneNumber(credential, fullName, gender, birthday, phoneNumber, email, username,
                                originalEmail, originalPhoneNumber, originalUsername);
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        showToast("Phone verification failed: " + e.getMessage(), false);
                        authCompleteListener.onAuthFailed();
                    }
                }
        );
    }

    @SuppressLint("SetTextI18n")
    private void showOTPDialog(String fullName, String gender, String birthday, String phoneNumber,
                               String email, String username, String verificationId,
                               String originalEmail, String originalPhoneNumber, String originalUsername) {
        View dialogView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setGravity(Gravity.CENTER);
        layout.setAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in));

        TextView title = new TextView(context);
        title.setText("Verify Your New Phone");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        EditText otpInput = new EditText(context);
        otpInput.setHint("Enter OTP");
        otpInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpInput.setPadding(20, 15, 20, 15);
        otpInput.setBackgroundResource(android.R.drawable.edit_text);
        otpInput.setTextColor(Color.BLACK);
        otpInput.setHintTextColor(Color.GRAY);
        layout.addView(otpInput);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Confirm", null)
                .setNegativeButton("Cancel", (d, which) -> {
                    authCompleteListener.onAuthFailed();
                    d.dismiss();
                })
                .setNeutralButton("Resend", null)
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
                    showToast("Please enter the OTP.", false);
                } else if (verificationId != null) {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                    updatePhoneNumber(credential, fullName, gender, birthday, phoneNumber, email, username,
                            originalEmail, originalPhoneNumber, originalUsername);
                    dialog.dismiss();
                } else {
                    showToast("No verification in progress.", false);
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(Color.GRAY);
            negativeButton.setBackgroundResource(android.R.drawable.btn_default_small);
            negativeButton.setPadding(20, 10, 20, 10);

            Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            neutralButton.setTextColor(Color.parseColor("#083EC9"));
            neutralButton.setBackgroundResource(android.R.drawable.btn_default_small);
            neutralButton.setPadding(20, 10, 20, 10);
            neutralButton.setOnClickListener(v -> {
                dialog.dismiss();
                verifyPhoneNumber(fullName, gender, birthday, phoneNumber, email, username,
                        originalEmail, originalPhoneNumber, originalUsername);
            });
        });

        dialog.show();
    }

    private void reauthenticateAndVerifyEmail(String fullName, String gender, String birthday,
                                              String phoneNumber, String email, String username,
                                              AuthCredential credential, Uri selectedImageUri, String currentImageUrl,
                                              String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (currentUser == null) {
            showToast("User session expired. Please log in again.", false);
            context.startActivity(new Intent(context, LoginActivity.class));
            ((Activity) context).finish();
            return;
        }

        authCompleteListener.onAuthStart();
        currentUser.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!email.equalsIgnoreCase(currentUser.getEmail())) {
                            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener(fetchTask -> {
                                if (fetchTask.isSuccessful() && !fetchTask.getResult().getSignInMethods().isEmpty()) {
                                    showToast("This email is already in use by another account.", false);
                                    authCompleteListener.onAuthFailed();
                                } else {
                                    currentUser.verifyBeforeUpdateEmail(email)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "Verification email sent to: " + email);
                                                authPrefs.edit().putString("pendingEmail", email)
                                                        .putLong(LAST_VERIFICATION_TIME, System.currentTimeMillis())
                                                        .apply();

                                                if (selectedImageUri != null) {
                                                    StorageReference fileRef = storageReference.child("profile.jpg");
                                                    UploadTask uploadTask = fileRef.putFile(selectedImageUri);
                                                    uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                                                        String downloadedImageUrl = uri.toString();
                                                        saveProfileWithPendingEmail(fullName, gender, birthday, phoneNumber,
                                                                email, username, downloadedImageUrl, originalEmail,
                                                                originalPhoneNumber, originalUsername);
                                                    })).addOnFailureListener(e -> {
                                                        Log.e(TAG, "Image upload failed: " + e.getMessage());
                                                        showToast("Image upload failed: " + e.getMessage(), false);
                                                        authCompleteListener.onAuthFailed();
                                                    });
                                                } else {
                                                    saveProfileWithPendingEmail(fullName, gender, birthday, phoneNumber,
                                                            email, username, currentImageUrl, originalEmail,
                                                            originalPhoneNumber, originalUsername);
                                                }
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to send verification email: " + e.getMessage());
                                                showToast("Failed to send verification email: " + e.getMessage(), false);
                                                authCompleteListener.onAuthFailed();
                                            });
                                }
                            });
                        } else {
                            saveToDatabase(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri,
                                    currentImageUrl, originalEmail, originalPhoneNumber, originalUsername);
                        }
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        showToast("Re-authentication failed: " + errorMsg, false);
                        authCompleteListener.onAuthFailed();
                    }
                });
    }

    private void saveProfileWithPendingEmail(String fullName, String gender, String birthday, String phoneNumber,
                                             String email, String username, String imageUrl,
                                             String originalEmail, String originalPhoneNumber, String originalUsername) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", fullName);
        user.put("gender", gender);
        user.put("birthday", birthday);
        user.put("phoneNumber", phoneNumber);
        user.put("email", currentUser.getEmail()); // Keep current email until verified
        user.put("username", username);
        user.put("imageUrl", imageUrl != null ? imageUrl : "");
        user.put("signInMethod", "email");
        user.put("pendingEmail", email);

        databaseReference.setValue(user)
                .addOnSuccessListener(aVoid -> {
                    authCompleteListener.onEmailVerificationSent(
                            fullName, gender, birthday, phoneNumber,
                            currentUser.getEmail(), username, email,
                            originalEmail, originalPhoneNumber, originalUsername);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update database with pending email: " + e.getMessage());
                    showToast("Failed to save profile: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
    }

    private void updatePhoneNumber(PhoneAuthCredential credential, String fullName, String gender,
                                   String birthday, String phoneNumber, String email, String username,
                                   String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (currentUser == null) {
            showToast("User session expired. Please log in again.", false);
            context.startActivity(new Intent(context, LoginActivity.class));
            ((Activity) context).finish();
            return;
        }

        authCompleteListener.onAuthStart();
        currentUser.updatePhoneNumber(credential)
                .addOnSuccessListener(aVoid -> {
                    authPrefs.edit().remove("pendingPhoneNumber").apply();
                    showToast("Phone number verified and updated.", true);
                    authCompleteListener.onAuthSuccess(fullName, gender, birthday, phoneNumber, email, username,
                            originalEmail, originalPhoneNumber, originalUsername);
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update phone number: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
    }

    public void resendEmailVerification(String email) {
        long lastVerificationTime = authPrefs.getLong(LAST_VERIFICATION_TIME, 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastVerificationTime < RESEND_TIMEOUT) {
            long timeLeft = RESEND_TIMEOUT - (currentTime - lastVerificationTime);
            long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(timeLeft);
            long secondsLeft = TimeUnit.MILLISECONDS.toSeconds(timeLeft % (60 * 1000));
            showToast("Please wait " + minutesLeft + "m " + secondsLeft + "s before resending.", false);
            return;
        }

        authCompleteListener.onAuthStart();
        currentUser.verifyBeforeUpdateEmail(email)
                .addOnSuccessListener(aVoid -> {
                    authPrefs.edit().putLong(LAST_VERIFICATION_TIME, System.currentTimeMillis()).apply();
                    showToast("Verification email resent to " + email, true);
                    authCompleteListener.onEmailVerificationSent(
                            (String) databaseReference.child("fullName").getKey(),
                            (String) databaseReference.child("gender").getKey(),
                            (String) databaseReference.child("birthday").getKey(),
                            (String) databaseReference.child("phoneNumber").getKey(),
                            currentUser.getEmail(),
                            (String) databaseReference.child("username").getKey(),
                            email,
                            currentUser.getEmail(),
                            (String) databaseReference.child("phoneNumber").getKey(),
                            (String) databaseReference.child("username").getKey());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to resend verification email: " + e.getMessage());
                    showToast("Failed to resend verification email: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
    }

    private void showToast(String message, boolean isSuccess) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        View view = LayoutInflater.from(context).inflate(R.layout.custom_toast, null);
        TextView text = view.findViewById(R.id.toast_text);
        text.setText(message);
        text.setTextColor(Color.WHITE);
        view.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);
        toast.setView(view);
        toast.show();
    }

    interface OnUpdateCompleteListener {
        void onComplete(boolean success);
    }

    interface ErrorCallback {
        void onError(String errorMessage);
    }

    public interface OnAuthCompleteListener {
        void onAuthStart();
        void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                           String email, String username, String originalEmail,
                           String originalPhoneNumber, String originalUsername);
        void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                     String currentEmail, String username, String pendingEmail,
                                     String originalEmail, String originalPhoneNumber, String originalUsername);
        void onPhoneVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                     String currentEmail, String username, String pendingPhoneNumber,
                                     String originalEmail, String originalPhoneNumber, String originalUsername);
        void onAuthFailed();
    }
}