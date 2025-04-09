package com.oopgroup.smartpharmacy.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class AuthUtils {
    private static final String TAG = "AuthUtils";
    private static final long DEBOUNCE_DELAY = 300;
    private static final long TOAST_DELAY = 2000;
    private static final int CCP_WIDTH_DP = 48;

    private static final String PHONE_REGEX_BD = "^\\+8801[3-9][0-9]{8}$";
    private static final String PHONE_REGEX_MY = "^\\+601[0-9]{8,9}$";
    private static final String PHONE_REGEX_SG = "^\\+65[89][0-9]{7}$";
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    private static final String LOCAL_REGEX_BD = "^01[3-9][0-9]{8}$";
    private static final String LOCAL_REGEX_MY = "^01[0-9][0-9]{7,8}$";
    private static final String LOCAL_REGEX_SG = "^[89][0-9]{7}$";

    private static final Set<String> BD_PREFIXES = new HashSet<>(Arrays.asList(
            "013", "014", "015", "016", "017", "018", "019"));
    private static final Set<String> MY_PREFIXES = new HashSet<>(Arrays.asList(
            "010", "011", "012", "013", "014", "015", "016", "017", "018", "019"));
    private static final Set<String> SG_PREFIXES = new HashSet<>(Arrays.asList("8", "9"));

    private static final AtomicBoolean isUpdatingCountryCode = new AtomicBoolean(false);
    private static String lockedCountryCode = null;
    private static boolean isProcessingInput = false;

    private static String lastValidatedPhone = "";
    private static boolean lastValidationResult = false;
    private static String lastNormalizedInput = "";
    private static String lastNormalizedResult = "";

    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        if (phone.equals(lastValidatedPhone)) {
            Log.d(TAG, "Using cached validation result for: " + phone + " -> " + lastValidationResult);
            return lastValidationResult;
        }

        String cleanedPhone = phone.replaceAll("[^0-9+]", "");
        if (!cleanedPhone.startsWith("+")) {
            cleanedPhone = "+" + cleanedPhone;
        }
        boolean matchesBD = Pattern.matches(PHONE_REGEX_BD, cleanedPhone);
        boolean matchesMY = Pattern.matches(PHONE_REGEX_MY, cleanedPhone);
        boolean matchesSG = Pattern.matches(PHONE_REGEX_SG, cleanedPhone);
        boolean isValid = matchesBD || matchesMY || matchesSG;

        lastValidatedPhone = phone;
        lastValidationResult = isValid;

        Log.d(TAG, "Validating phone: " + cleanedPhone + " -> BD: " + matchesBD + ", MY: " + matchesMY + ", SG: " + matchesSG + ", Valid: " + isValid);
        return isValid;
    }

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        boolean isValid = Pattern.matches(EMAIL_REGEX, email) && email.contains(".");
        Log.d(TAG, "Validating email: " + email + " -> " + isValid);
        return isValid;
    }

    public static boolean looksLikeEmail(String input) {
        if (input == null || input.isEmpty()) return false;
        boolean looksLike = input.matches("^[A-Za-z].*") || input.contains("@");
        Log.d(TAG, "looksLikeEmail: " + input + " -> " + looksLike);
        return looksLike;
    }

    public static String normalizePhoneNumberForBackend(String input, CountryCodePicker ccp, String simCountry) {
        if (input == null || input.isEmpty()) {
            Log.w(TAG, "Invalid input: " + input);
            return input != null ? input : "";
        }

        if (input.equals(lastNormalizedInput)) {
            Log.d(TAG, "Using cached normalization result for: " + input + " -> " + lastNormalizedResult);
            return lastNormalizedResult;
        }

        String normalized = input.replaceAll("[^0-9+]", "");
        Log.d(TAG, "Normalizing input: " + input + ", cleaned: " + normalized);

        if (normalized.startsWith("+")) {
            if (isValidPhoneNumber(normalized)) {
                lockedCountryCode = getCountryCodeFromNumber(normalized);
                updateCountryFlag(normalized, ccp);
                Log.d(TAG, "Valid input with country code: " + normalized + ", Locked: " + lockedCountryCode);

                lastNormalizedInput = input;
                lastNormalizedResult = normalized;
                return normalized;
            }
            lockedCountryCode = null;
            Log.w(TAG, "Input starts with +, but invalid: " + normalized);

            lastNormalizedInput = input;
            lastNormalizedResult = normalized;
            return normalized;
        }

        if (lockedCountryCode != null) {
            String potentialNumber = lockedCountryCode + normalized;
            if (isValidPhoneNumber(potentialNumber)) {
                updateCountryFlag(potentialNumber, ccp);
                Log.d(TAG, "Using locked country code: " + potentialNumber);

                lastNormalizedInput = input;
                lastNormalizedResult = potentialNumber;
                return potentialNumber;
            }
            lockedCountryCode = null;
            Log.d(TAG, "Unlocked country code due to invalid result: " + potentialNumber);
        }

        String countryCode = ccp != null ? ccp.getSelectedCountryCodeWithPlus() : (simCountry != null ? getCountryCodeFromSim(simCountry) : "+880");
        String detectedCountry = detectCountry(normalized);

        if (Pattern.matches(LOCAL_REGEX_BD, normalized) && detectedCountry.equals("BD")) {
            String result = "+880" + normalized.substring(1);
            if (isValidPhoneNumber(result)) {
                lockedCountryCode = "+880";
                updateCountryFlag(result, ccp);
                Log.d(TAG, "Normalized BD local format: " + result);

                lastNormalizedInput = input;
                lastNormalizedResult = result;
                return result;
            }
        } else if (Pattern.matches(LOCAL_REGEX_MY, normalized) && detectedCountry.equals("MY")) {
            String result = "+60" + normalized.substring(1);
            if (isValidPhoneNumber(result)) {
                lockedCountryCode = "+60";
                updateCountryFlag(result, ccp);
                Log.d(TAG, "Normalized MY local format: " + result);

                lastNormalizedInput = input;
                lastNormalizedResult = result;
                return result;
            }
        } else if (Pattern.matches(LOCAL_REGEX_SG, normalized) && detectedCountry.equals("SG")) {
            String result = "+65" + normalized;
            if (isValidPhoneNumber(result)) {
                lockedCountryCode = "+65";
                updateCountryFlag(result, ccp);
                Log.d(TAG, "Normalized SG local format: " + result);

                lastNormalizedInput = input;
                lastNormalizedResult = result;
                return result;
            }
        }

        String detectedCode = getCountryCode(detectedCountry);
        if (!detectedCountry.equals("Unknown")) {
            String potentialNumber = detectedCode + normalized;
            if (isValidPhoneNumber(potentialNumber)) {
                lockedCountryCode = detectedCode;
                updateCountryFlag(potentialNumber, ccp);
                Log.d(TAG, "Prepended detected country code: " + potentialNumber);

                lastNormalizedInput = input;
                lastNormalizedResult = potentialNumber;
                return potentialNumber;
            }
        }

        Log.d(TAG, "No valid format detected, returning raw input: " + normalized);
        lastNormalizedInput = input;
        lastNormalizedResult = normalized;
        return normalized;
    }

    private static String getCountryCodeFromNumber(String number) {
        if (number.startsWith("+880")) return "+880";
        if (number.startsWith("+60")) return "+60";
        if (number.startsWith("+65")) return "+65";
        return null;
    }

    private static String getCountryCodeFromSim(String simCountry) {
        if (simCountry == null) return "+880";
        switch (simCountry.toUpperCase()) {
            case "BD": return "+880";
            case "MY": return "+60";
            case "SG": return "+65";
            default: return "+880";
        }
    }

    static String detectCountry(String input) {
        if (input == null || input.length() < 1) return "Unknown";
        String prefix1 = input.length() >= 1 ? input.substring(0, 1) : "";
        String prefix2 = input.length() >= 2 ? input.substring(0, 2) : "";
        String prefix3 = input.length() >= 3 ? input.substring(0, 3) : "";

        if (SG_PREFIXES.contains(prefix1) && input.length() <= 8) {
            return "SG";
        }

        if (input.length() >= 2 && prefix2.equals("01")) {
            if (input.length() >= 3) {
                if (BD_PREFIXES.contains(prefix3) && !MY_PREFIXES.contains(prefix3) && input.length() <= 11) {
                    return "BD";
                } else if (MY_PREFIXES.contains(prefix3) && !BD_PREFIXES.contains(prefix3) && input.length() <= 11) {
                    return "MY";
                } else if (BD_PREFIXES.contains(prefix3) && MY_PREFIXES.contains(prefix3)) {
                    return input.length() == 11 ? "BD" : input.length() == 10 ? "MY" : "Unknown";
                }
            }
            return "Unknown";
        }

        return "Unknown";
    }

    private static String getCountryCode(String country) {
        switch (country) {
            case "BD": return "+880";
            case "MY": return "+60";
            case "SG": return "+65";
            default: return "+880";
        }
    }

    private static int getExpectedLength(String country) {
        switch (country) {
            case "BD": return 11;
            case "MY": return 10;
            case "SG": return 8;
            default: return -1;
        }
    }

    private static int getMaxLength(String country) {
        switch (country) {
            case "BD": return 13;
            case "MY": return 12;
            case "SG": return 11;
            default: return 13;
        }
    }

    public static void fetchSimNumber(Activity activity, EditText credInput) {
        if (activity == null || credInput == null) {
            Log.e(TAG, "Activity or EditText null");
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Phone state permission not granted, skipping SIM fetch");
            return;
        }
        retrieveSimNumber(activity, credInput);
    }

    private static void retrieveSimNumber(Activity activity, EditText credInput) {
        try {
            TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                Log.e(TAG, "TelephonyManager unavailable");
                return;
            }
            String simNumber = tm.getLine1Number();
            String simCountry = tm.getSimCountryIso() != null ? tm.getSimCountryIso().toUpperCase() : null;
            if (simNumber != null && !simNumber.isEmpty()) {
                String normalizedSimNumber = normalizePhoneNumberForBackend(simNumber, null, simCountry);
                if (isValidPhoneNumber(normalizedSimNumber)) {
                    credInput.setText(normalizedSimNumber);
                    Log.d(TAG, "Set SIM number: " + normalizedSimNumber);
                } else {
                    Log.w(TAG, "Invalid SIM number: " + normalizedSimNumber);
                }
            } else {
                Log.w(TAG, "SIM number unavailable");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error fetching SIM: " + e.getMessage());
        }
    }

    public static void setupDynamicInput(Activity activity, EditText credInput, CountryCodePicker ccp,
                                         ImageView emailIcon, ImageView phoneIcon, TextView validationMessage) {
        if (activity == null || credInput == null) {
            Log.e(TAG, "Activity or EditText null");
            return;
        }

        credInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        if (ccp != null) {
            ccp.registerCarrierNumberEditText(credInput);
            ccp.setNumberAutoFormattingEnabled(false);
            ccp.setCountryForNameCode(null);
            Log.d(TAG, "Registered EditText with CCP");
        } else {
            Log.w(TAG, "CountryCodePicker is null");
        }

        String simCountry = getSimCountry(activity);
        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] debounceRunnable = new Runnable[1];
        final String[] lastProcessedInputHolder = new String[1];
        lastProcessedInputHolder[0] = "";

        credInput.addTextChangedListener(new TextWatcher() {
            private long lastToastTime = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdatingCountryCode.get() || isProcessingInput) return;
                String currentInput = s.toString().trim();
                if (currentInput.equals(lastProcessedInputHolder[0])) return;

                if (debounceRunnable[0] != null) handler.removeCallbacks(debounceRunnable[0]);
                debounceRunnable[0] = () -> {
                    isProcessingInput = true;
                    processInput(activity, currentInput, credInput, ccp, emailIcon, phoneIcon,
                            validationMessage, simCountry, lastToastTime, t -> lastToastTime = t);
                    lastProcessedInputHolder[0] = currentInput;
                    isProcessingInput = false;
                };
                handler.postDelayed(debounceRunnable[0], DEBOUNCE_DELAY);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    public static String getSimCountry(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "BD"; // Default to BD if permission denied
        }
        try {
            TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String simCountry = tm.getSimCountryIso();
                return simCountry != null && simCountry.length() == 2 ? simCountry.toUpperCase() : "BD";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting SIM country: " + e.getMessage());
        }
        return "BD";
    }

    private static void processInput(Activity activity, String input, EditText credInput, CountryCodePicker ccp,
                                     ImageView emailIcon, ImageView phoneIcon, TextView validationMessage,
                                     String simCountry, long lastToastTime, ToastTimeCallback toastTimeCallback) {
        credInput.setError(null);

        if (input.isEmpty()) {
            lockedCountryCode = null;
            resetUI(emailIcon, phoneIcon, ccp, validationMessage, credInput);
            return;
        }

        if (looksLikeEmail(input)) {
            if (isValidEmail(input)) {
                updateUI(emailIcon, phoneIcon, ccp, validationMessage, credInput, true,
                        "Valid email", android.R.color.holo_green_dark, activity);
            } else {
                updateUI(emailIcon, phoneIcon, ccp, validationMessage, credInput, true,
                        "Typing email...", android.R.color.black, activity);
            }
            return;
        }

        String backendNumber = normalizePhoneNumberForBackend(input, ccp, simCountry);
        if (isValidPhoneNumber(backendNumber)) {
            updateUI(emailIcon, phoneIcon, ccp, validationMessage, credInput, false,
                    "Valid phone number", android.R.color.holo_green_dark, activity);
            return;
        }

        String cleanedInput = input.replaceAll("[^0-9+]", "");
        String detectedCountry = detectCountry(cleanedInput);
        int expectedLength = getExpectedLength(detectedCountry);
        int maxLength = getMaxLength(detectedCountry);
        int currentLength = cleanedInput.length();

        if (cleanedInput.startsWith("+")) maxLength = 13;

        if ((expectedLength > 0 && currentLength >= expectedLength && !isValidPhoneNumber(backendNumber)) ||
                currentLength > maxLength) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastToastTime >= TOAST_DELAY) {
                Toast.makeText(activity, "Invalid phone number", Toast.LENGTH_SHORT).show();
                toastTimeCallback.onToastTimeUpdated(currentTime);
            }
            showValidationError(validationMessage,
                    "Invalid format (e.g., +8801712345678, +60123456789, +6591234567)",
                    android.R.color.holo_red_dark, activity);
            if (ccp != null) {
                ccp.setCountryForNameCode(null);
                ccp.setVisibility(View.GONE);
                adjustPadding(credInput, 12, activity);
            }
        } else {
            updateUI(emailIcon, phoneIcon, ccp, validationMessage, credInput, false,
                    "Typing phone number...", android.R.color.black, activity);
            if (ccp != null && detectedCountry.equals("Unknown")) {
                ccp.setCountryForNameCode(null);
                ccp.setVisibility(View.GONE);
                adjustPadding(credInput, 12, activity);
            }
        }
    }

    private static void updateUI(ImageView emailIcon, ImageView phoneIcon, CountryCodePicker ccp,
                                 TextView validationMessage, EditText credInput, boolean isEmail, String message,
                                 int color, Activity activity) {
        if (emailIcon != null) emailIcon.setVisibility(isEmail ? View.VISIBLE : View.GONE);
        if (phoneIcon != null) phoneIcon.setVisibility(isEmail ? View.GONE : View.VISIBLE);
        if (ccp != null) {
            if (isEmail) {
                ccp.setVisibility(View.GONE);
                adjustPadding(credInput, 12, activity);
            } else {
                String normalizedNumber = normalizePhoneNumberForBackend(credInput.getText().toString(), ccp, getSimCountry(activity));
                if (isValidPhoneNumber(normalizedNumber)) {
                    ccp.setVisibility(View.VISIBLE);
                    updateCountryFlag(normalizedNumber, ccp);
                    adjustPadding(credInput, CCP_WIDTH_DP, activity);
                } else {
                    ccp.setVisibility(View.GONE);
                    adjustPadding(credInput, 12, activity);
                }
            }
        }
        if (validationMessage != null) {
            validationMessage.setText(message);
            validationMessage.setTextColor(ContextCompat.getColor(activity, color));
        }
    }

    private static void adjustPadding(EditText editText, int dp, Activity activity) {
        editText.setPadding(
                (int) (dp * activity.getResources().getDisplayMetrics().density),
                editText.getPaddingTop(),
                editText.getPaddingEnd(),
                editText.getPaddingBottom()
        );
    }

    private static void showValidationError(TextView validationMessage, String message, int color, Activity activity) {
        if (validationMessage != null) {
            validationMessage.setText(message);
            validationMessage.setTextColor(ContextCompat.getColor(activity, color));
        }
    }

    private static void resetUI(ImageView emailIcon, ImageView phoneIcon, CountryCodePicker ccp,
                                TextView validationMessage, EditText credInput) {
        if (emailIcon != null) emailIcon.setVisibility(View.GONE);
        if (phoneIcon != null) phoneIcon.setVisibility(View.GONE);
        if (ccp != null) {
            ccp.setCountryForNameCode(null);
            ccp.setVisibility(View.GONE);
            adjustPadding(credInput, 12, (Activity) credInput.getContext());
        }
        if (validationMessage != null) validationMessage.setText("");
    }

    public static boolean updateCountryFlag(String phoneNumber, CountryCodePicker ccp) {
        if (ccp == null || phoneNumber == null) return false;
        try {
            isUpdatingCountryCode.set(true);
            String normalized = phoneNumber.replaceAll("[^0-9+]", "");
            if (isValidPhoneNumber(normalized)) {
                if (normalized.startsWith("+880")) {
                    ccp.setCountryForNameCode("BD");
                    return true;
                } else if (normalized.startsWith("+60")) {
                    ccp.setCountryForNameCode("MY");
                    return true;
                } else if (normalized.startsWith("+65")) {
                    ccp.setCountryForNameCode("SG");
                    return true;
                }
            }
            ccp.setCountryForNameCode(null);
            return false;
        } finally {
            isUpdatingCountryCode.set(false);
        }
    }

    // Firebase Authentication Methods
    public static void firebaseAuthWithGoogle(Activity activity, String idToken, FirebaseFirestore firestore, AuthCallback callback) {
        if (!validateAuthParams(activity, firestore, callback)) return;
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        performFirebaseAuth(activity, credential, "google", firestore, callback);
    }

    public static void firebaseAuthWithFacebook(Activity activity, String token, FirebaseFirestore firestore, AuthCallback callback) {
        if (!validateAuthParams(activity, firestore, callback)) return;
        AuthCredential credential = FacebookAuthProvider.getCredential(token);
        performFirebaseAuth(activity, credential, "facebook", firestore, callback);
    }

    public static void firebaseAuthWithGitHub(Activity activity, String clientId, FirebaseFirestore firestore, AuthCallback callback) {
        if (!validateAuthParams(activity, firestore, callback)) return;
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com")
                .addCustomParameter("client_id", clientId)
                .setScopes(Arrays.asList("user:email"));
        FirebaseAuth.getInstance()
                .startActivityForSignInWithProvider(activity, provider.build())
                .addOnSuccessListener(authResult -> handleSocialMediaUser(authResult.getUser(), "github", firestore, callback))
                .addOnFailureListener(e -> callback.onFailure("GitHub auth failed: " + e.getMessage()));
    }

    private static boolean validateAuthParams(Activity activity, FirebaseFirestore firestore, AuthCallback callback) {
        if (activity == null || firestore == null || callback == null) {
            Log.e(TAG, "Invalid auth parameters");
            if (callback != null) callback.onFailure("Invalid parameters");
            return false;
        }
        return true;
    }

    private static void performFirebaseAuth(Activity activity, AuthCredential credential, String provider,
                                            FirebaseFirestore firestore, AuthCallback callback) {
        FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            handleSocialMediaUser(user, provider, firestore, callback);
                        } else {
                            callback.onFailure("User null after auth");
                        }
                    } else {
                        callback.onFailure("Auth failed: " + task.getException().getMessage());
                    }
                });
    }

    private static void handleSocialMediaUser(FirebaseUser user, String provider, FirebaseFirestore firestore, AuthCallback callback) {
        if (user == null) {
            callback.onFailure("User null");
            return;
        }

        String email = user.getEmail() != null ? user.getEmail() : "";
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
        String phone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

        firestore.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(user);
                    } else {
                        generateUniqueUsername(displayName, firestore.collection("usernames"), username ->
                                saveUserData(user, firestore, displayName, email, phone, username, provider,
                                        new SaveUserDataCallback() {
                                            @Override
                                            public void onSuccess() {
                                                callback.onSuccess(user);
                                            }

                                            @Override
                                            public void onFailure(String error) {
                                                callback.onFailure(error);
                                            }
                                        }));
                    }
                })
                .addOnFailureListener(e -> callback.onFailure("Database error: " + e.getMessage()));
    }

    public static void firebaseLoginWithGoogle(Activity activity, String idToken, AuthCallback callback) {
        if (activity == null || callback == null) {
            Log.e(TAG, "Invalid parameters for Google login");
            if (callback != null) callback.onFailure("Invalid parameters");
            return;
        }

        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
        if (result != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services unavailable: " + result);
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(activity)
                    .addOnSuccessListener(aVoid -> performGoogleLogin(activity, idToken, callback))
                    .addOnFailureListener(e -> callback.onFailure("Google Play Services unavailable: " + e.getMessage()));
            return;
        }

        performGoogleLogin(activity, idToken, callback);
    }

    private static void performGoogleLogin(Activity activity, String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            Log.d(TAG, "Google login successful, UID: " + user.getUid());
                            callback.onSuccess(user);
                        } else {
                            Log.e(TAG, "Google login succeeded but user is null");
                            callback.onFailure("User null after login");
                        }
                    } else {
                        Log.e(TAG, "Google login failed: " + task.getException().getMessage(), task.getException());
                        callback.onFailure("Google login failed: " + task.getException().getMessage());
                    }
                });
    }

    public static void saveUserData(FirebaseUser user, FirebaseFirestore firestore, String fullName, String email,
                                    String phoneNumber, String username, String signInMethod, SaveUserDataCallback callback) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName != null ? fullName : "User");
        userData.put("email", email != null ? email : "");
        userData.put("phoneNumber", phoneNumber != null ? phoneNumber : "");
        userData.put("gender", "Not specified");
        userData.put("birthday", "01-01-2000");
        userData.put("username", username);
        userData.put("imageUrl", "");
        userData.put("signInMethod", signInMethod != null ? signInMethod : "unknown");
        userData.put("role", "customer");

        Map<String, Object> uniqueData = new HashMap<>();
        uniqueData.put("userId", user.getUid());

        firestore.collection("users").document(user.getUid()).set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User data saved successfully: " + userData.toString());
                    if ("email".equals(signInMethod) && !email.isEmpty()) {
                        firestore.collection("emails").document(email.replace(".", "_")).set(uniqueData)
                                .addOnSuccessListener(aVoid1 -> Log.d(TAG, "Email uniqueness saved: " + email))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to save email: " + e.getMessage()));
                    }
                    if ("phone".equals(signInMethod) && !phoneNumber.isEmpty()) {
                        firestore.collection("phoneNumbers").document(phoneNumber).set(uniqueData)
                                .addOnSuccessListener(aVoid1 -> Log.d(TAG, "Phone uniqueness saved: " + phoneNumber))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to save phone: " + e.getMessage()));
                    }
                    firestore.collection("usernames").document(username).set(uniqueData)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "Username uniqueness saved: " + username);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to save username: " + e.getMessage());
                                callback.onFailure("Failed to save username: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user data: " + e.getMessage());
                    callback.onFailure("Failed to save user data: " + e.getMessage());
                });
    }

    public static void generateUniqueUsername(String fullName, CollectionReference usernamesRef, UsernameCallback callback) {
        String baseUsername = fullName != null ? fullName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase() : "user";
        if (baseUsername.isEmpty()) baseUsername = "user";
        checkUsernameAvailability(baseUsername, 1, usernamesRef, callback);
    }

    public static void checkUsernameAvailability(String baseUsername, int suffix, CollectionReference usernamesRef, UsernameCallback callback) {
        String candidate = suffix == 1 ? baseUsername : baseUsername + (suffix - 1);
        usernamesRef.document(candidate).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onUsernameGenerated(candidate);
                    } else {
                        checkUsernameAvailability(baseUsername, suffix + 1, usernamesRef, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onUsernameGenerated(baseUsername + new java.util.Random().nextInt(1000)));
    }

    // Interfaces
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(String errorMessage);
    }

    public interface SaveUserDataCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface UsernameCallback {
        void onUsernameGenerated(String username);
    }

    private interface ToastTimeCallback {
        void onToastTimeUpdated(long time);
    }
}