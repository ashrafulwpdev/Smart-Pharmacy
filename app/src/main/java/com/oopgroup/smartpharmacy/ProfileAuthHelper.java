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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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
    private static final long RESEND_TIMEOUT = 10 * 60 * 1000;

    private final Context context;
    private final FirebaseAuth auth;
    private final FirebaseUser currentUser;
    private final OnAuthCompleteListener authCompleteListener;
    private final SharedPreferences authPrefs;
    private final FirebaseFirestore firestore;
    private final StorageReference storageReference;

    public ProfileAuthHelper(Context context, OnAuthCompleteListener listener) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.currentUser = auth.getCurrentUser();
        this.authCompleteListener = listener;
        this.authPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.firestore = FirebaseFirestore.getInstance();
        this.storageReference = FirebaseStorage.getInstance().getReference("profile_images").child(currentUser.getUid());
    }

    public interface OnAuthCompleteListener {
        void onAuthStart();
        void onAuthSuccess(String fullName, String gender, String birthday, String phoneNumber,
                           String email, String username, String originalEmail, String originalPhoneNumber,
                           String originalUsername);
        void onEmailVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                     String currentEmail, String username, String pendingEmail,
                                     String originalEmail, String originalPhoneNumber, String originalUsername);
        void onPhoneVerificationSent(String fullName, String gender, String birthday, String phoneNumber,
                                     String currentEmail, String username, String pendingPhoneNumber,
                                     String originalEmail, String originalPhoneNumber, String originalUsername);
        void onAuthFailed();
    }

    private interface ErrorCallback {
        void onError(String message);
    }

    public void saveProfile(String fullName, String gender, String birthday, String phoneNumber, String email,
                            String username, Uri selectedImageUri, String currentImageUrl,
                            String originalEmail, String originalPhoneNumber, String originalUsername) {
        authCompleteListener.onAuthStart();

        if (currentUser == null) {
            showToast("User not authenticated. Please log in.", false);
            context.startActivity(new Intent(context, LoginActivity.class));
            ((Activity) context).finish();
            return;
        }

        String signInMethod = authPrefs.getString("signInMethod", "email");
        boolean emailChanged = !email.equalsIgnoreCase(originalEmail);
        boolean phoneChanged = !phoneNumber.equals(originalPhoneNumber);
        boolean usernameChanged = !username.equals(originalUsername);

        if (!emailChanged && !phoneChanged && !usernameChanged && selectedImageUri == null) {
            saveToFirestore(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri, currentImageUrl,
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
                saveToFirestore(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri, currentImageUrl,
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
            firestore.collection("emails").document(emailKey).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && !documentSnapshot.getString("uid").equals(currentUser.getUid())) {
                            onFailure.onError("Email '" + email + "' is already in use by another account.");
                        } else {
                            checkPhoneNumber(phoneNumber, username, originalPhoneNumber, originalUsername, onSuccess, onFailure);
                        }
                    })
                    .addOnFailureListener(e -> onFailure.onError("Failed to check email: " + e.getMessage()));
        } else {
            checkPhoneNumber(phoneNumber, username, originalPhoneNumber, originalUsername, onSuccess, onFailure);
        }
    }

    private void checkPhoneNumber(String phoneNumber, String username, String originalPhoneNumber, String originalUsername,
                                  Runnable onSuccess, ErrorCallback onFailure) {
        if (!phoneNumber.equals(originalPhoneNumber) && !phoneNumber.isEmpty()) {
            firestore.collection("phoneNumbers").document(phoneNumber).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && !documentSnapshot.getString("uid").equals(currentUser.getUid())) {
                            onFailure.onError("Phone number '" + phoneNumber + "' is already in use by another account.");
                        } else {
                            checkUsername(username, originalUsername, onSuccess, onFailure);
                        }
                    })
                    .addOnFailureListener(e -> onFailure.onError("Failed to check phone number: " + e.getMessage()));
        } else {
            checkUsername(username, originalUsername, onSuccess, onFailure);
        }
    }

    private void checkUsername(String username, String originalUsername, Runnable onSuccess, ErrorCallback onFailure) {
        if (!username.equals(originalUsername)) {
            firestore.collection("usernames").document(username).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && !documentSnapshot.getString("uid").equals(currentUser.getUid())) {
                            onFailure.onError("Username '" + username + "' is already taken.");
                        } else {
                            onSuccess.run();
                        }
                    })
                    .addOnFailureListener(e -> onFailure.onError("Failed to check username: " + e.getMessage()));
        } else {
            onSuccess.run();
        }
    }

    private void saveToFirestore(String fullName, String gender, String birthday, String phoneNumber,
                                 String email, String username, Uri selectedImageUri, String currentImageUrl,
                                 String originalEmail, String originalPhoneNumber, String originalUsername) {
        if (selectedImageUri != null) {
            StorageReference fileRef = storageReference.child("profile.jpg");
            UploadTask uploadTask = fileRef.putFile(selectedImageUri);
            uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadedImageUrl = uri.toString();
                writeToFirestore(fullName, gender, birthday, phoneNumber, email, username, downloadedImageUrl,
                        originalEmail, originalPhoneNumber, originalUsername);
            })).addOnFailureListener(e -> {
                Log.e(TAG, "Image upload failed: " + e.getMessage());
                showToast("Image upload failed: " + e.getMessage(), false);
                authCompleteListener.onAuthFailed();
            });
        } else {
            writeToFirestore(fullName, gender, birthday, phoneNumber, email, username, currentImageUrl,
                    originalEmail, originalPhoneNumber, originalUsername);
        }
    }

    private void writeToFirestore(String fullName, String gender, String birthday, String phoneNumber,
                                  String email, String username, String imageUrl,
                                  String originalEmail, String originalPhoneNumber, String originalUsername) {
        String uid = currentUser.getUid();
        String signInMethod = authPrefs.getString("signInMethod", "email");

        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("gender", gender);
        userData.put("birthday", birthday);
        userData.put("phoneNumber", phoneNumber);
        userData.put("email", email);
        userData.put("username", username);
        userData.put("signInMethod", signInMethod);
        userData.put("imageUrl", imageUrl != null ? imageUrl : "");

        String pendingEmail = authPrefs.getString("pendingEmail", null);
        String pendingPhoneNumber = authPrefs.getString("pendingPhoneNumber", null);
        if (pendingEmail != null && !email.equals(currentUser.getEmail())) userData.put("pendingEmail", pendingEmail);
        else userData.put("pendingEmail", null);
        if (pendingPhoneNumber != null && !phoneNumber.equals(currentUser.getPhoneNumber())) userData.put("pendingPhoneNumber", pendingPhoneNumber);
        else userData.put("pendingPhoneNumber", null);

        // Fetch all necessary data before the transaction
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String currentPhoneNumber = documentSnapshot.exists() ? documentSnapshot.getString("phoneNumber") : null;
                    String currentEmail = documentSnapshot.exists() ? documentSnapshot.getString("email") : null;
                    String currentUsername = documentSnapshot.exists() ? documentSnapshot.getString("username") : null;

                    // Prepare batch updates based on pre-fetched data
                    Map<String, Object> batchUpdates = new HashMap<>();
                    batchUpdates.put("users/" + uid, userData);

                    // Handle phone number updates
                    if (!phoneNumber.equals(currentPhoneNumber) && !"phone".equals(signInMethod)) {
                        if (currentPhoneNumber != null && !currentPhoneNumber.isEmpty()) {
                            batchUpdates.put("phoneNumbers/" + currentPhoneNumber, null); // Mark for deletion
                        }
                        if (!phoneNumber.isEmpty()) {
                            batchUpdates.put("phoneNumbers/" + phoneNumber, Map.of("uid", uid));
                        }
                    }

                    // Handle email updates
                    if (!email.equals(currentEmail) && !"email".equals(signInMethod)) {
                        if (currentEmail != null && !currentEmail.isEmpty()) {
                            batchUpdates.put("emails/" + currentEmail.replace(".", "_"), null); // Mark for deletion
                        }
                        if (!email.isEmpty()) {
                            batchUpdates.put("emails/" + email.replace(".", "_"), Map.of("uid", uid));
                        }
                    }

                    // Handle username updates
                    if (!username.equals(currentUsername)) {
                        if (currentUsername != null && !currentUsername.isEmpty()) {
                            batchUpdates.put("usernames/" + currentUsername, null); // Mark for deletion
                        }
                        batchUpdates.put("usernames/" + username, Map.of("uid", uid));
                    }

                    // Run transaction with no reads inside
                    firestore.runTransaction(transaction -> {
                        for (Map.Entry<String, Object> entry : batchUpdates.entrySet()) {
                            String path = entry.getKey();
                            Object value = entry.getValue();
                            if (value == null) {
                                Log.d(TAG, "Deleting: " + path);
                                transaction.delete(firestore.document(path));
                            } else {
                                Log.d(TAG, "Setting: " + path);
                                transaction.set(firestore.document(path), value);
                            }
                        }
                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = authPrefs.edit();
                        editor.putString("fullName", fullName);
                        editor.putString("gender", gender);
                        editor.putString("birthday", birthday);
                        editor.putString("phoneNumber", phoneNumber);
                        editor.putString("email", email);
                        editor.putString("username", username);
                        editor.putString("imageUrl", imageUrl != null ? imageUrl : "");
                        if (pendingEmail != null && !email.equals(currentUser.getEmail())) editor.putString("pendingEmail", pendingEmail);
                        else editor.remove("pendingEmail");
                        if (pendingPhoneNumber != null && !phoneNumber.equals(currentUser.getPhoneNumber())) editor.putString("pendingPhoneNumber", pendingPhoneNumber);
                        else editor.remove("pendingPhoneNumber");
                        editor.apply();

                        Log.d(TAG, "Profile saved successfully");
                        authCompleteListener.onAuthSuccess(fullName, gender, birthday, phoneNumber, email, username,
                                originalEmail, originalPhoneNumber, originalUsername);
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Transaction failed: " + e.getMessage(), e);
                        showToast("Failed to save profile: " + e.getMessage(), false);
                        authCompleteListener.onAuthFailed();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch current user data: " + e.getMessage());
                    showToast("Failed to fetch profile data: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
    }

    private void saveProfileWithPendingEmail(String fullName, String gender, String birthday, String phoneNumber,
                                             String pendingEmail, String username, String imageUrl,
                                             String originalEmail, String originalPhoneNumber, String originalUsername) {
        String uid = currentUser.getUid();
        String currentEmail = currentUser.getEmail() != null ? currentUser.getEmail() : originalEmail;

        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("gender", gender);
        userData.put("birthday", birthday);
        userData.put("phoneNumber", phoneNumber);
        userData.put("email", currentEmail);
        userData.put("username", username);
        userData.put("signInMethod", "email");
        userData.put("imageUrl", imageUrl != null ? imageUrl : "");
        userData.put("pendingEmail", pendingEmail);

        firestore.collection("users").document(uid).set(userData)
                .addOnSuccessListener(aVoid -> {
                    SharedPreferences.Editor editor = authPrefs.edit();
                    editor.putString("fullName", fullName);
                    editor.putString("gender", gender);
                    editor.putString("birthday", birthday);
                    editor.putString("phoneNumber", phoneNumber);
                    editor.putString("email", currentEmail);
                    editor.putString("username", username);
                    editor.putString("pendingEmail", pendingEmail);
                    editor.putString("imageUrl", imageUrl != null ? imageUrl : "");
                    editor.apply();

                    Log.d(TAG, "Profile with pending email saved successfully");
                    authCompleteListener.onEmailVerificationSent(fullName, gender, birthday, phoneNumber,
                            currentEmail, username, pendingEmail, originalEmail, originalPhoneNumber, originalUsername);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save profile with pending email: " + e.getMessage());
                    showToast("Failed to save profile: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
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

                                                saveProfileWithPendingEmail(fullName, gender, birthday, phoneNumber,
                                                        email, username, selectedImageUri != null ? null : currentImageUrl,
                                                        originalEmail, originalPhoneNumber, originalUsername);
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to send verification email: " + e.getMessage());
                                                showToast("Failed to send verification email: " + e.getMessage(), false);
                                                authCompleteListener.onAuthFailed();
                                            });
                                }
                            });
                        } else {
                            saveToFirestore(fullName, gender, birthday, phoneNumber, email, username, selectedImageUri,
                                    currentImageUrl, originalEmail, originalPhoneNumber, originalUsername);
                        }
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        showToast("Re-authentication failed: " + errorMsg, false);
                        authCompleteListener.onAuthFailed();
                    }
                });
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

    private void updatePhoneNumber(PhoneAuthCredential credential, String fullName, String gender, String birthday,
                                   String phoneNumber, String email, String username,
                                   String originalEmail, String originalPhoneNumber, String originalUsername) {
        authCompleteListener.onAuthStart();
        currentUser.updatePhoneNumber(credential)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Phone number updated successfully to: " + phoneNumber);
                    saveToFirestore(fullName, gender, birthday, phoneNumber, email, username, null,
                            authPrefs.getString("imageUrl", ""), originalEmail, originalPhoneNumber, originalUsername);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update phone number: " + e.getMessage());
                    showToast("Failed to update phone number: " + e.getMessage(), false);
                    authCompleteListener.onAuthFailed();
                });
    }

    private void showToast(String message, boolean isSuccess) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View toastView = inflater.inflate(R.layout.custom_toast, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toastText.setTextColor(Color.WHITE);
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);

        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);

        int offsetDp = (int) (300 * context.getResources().getDisplayMetrics().density);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offsetDp);

        Log.d(TAG, "Showing toast: " + message);
        toast.show();
    }
}