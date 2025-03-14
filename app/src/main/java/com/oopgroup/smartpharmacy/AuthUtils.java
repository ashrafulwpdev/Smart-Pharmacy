package com.oopgroup.smartpharmacy;

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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
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
    private static final int REQUEST_PHONE_STATE_PERMISSION = 1001;
    private static final long DEBOUNCE_DELAY = 300;
    private static final long TOAST_DELAY = 2000;

    // International format regex
    private static final String PHONE_REGEX_BD = "^\\+8801[3-9][0-9]{8}$"; // 13 digits
    private static final String PHONE_REGEX_MY = "^\\+601[0-9]{8,9}$";   // 11-12 digits
    private static final String PHONE_REGEX_SG = "^\\+65[89][0-9]{7}$";   // 11 digits
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    // Local format regex (without country code)
    private static final String LOCAL_REGEX_BD = "^01[3-9][0-9]{8}$";     // 11 digits
    private static final String LOCAL_REGEX_MY = "^01[0-9][0-9]{7,8}$";   // 10-11 digits
    private static final String LOCAL_REGEX_SG = "^[89][0-9]{7}$";        // 8 digits

    // Prefixes for early detection
    private static final Set<String> BD_PREFIXES = new HashSet<>(Arrays.asList(
            "013", "014", "015", "016", "017", "018", "019"));
    private static final Set<String> MY_PREFIXES = new HashSet<>(Arrays.asList(
            "010", "011", "012", "013", "014", "015", "016", "017", "018", "019"));
    private static final Set<String> SG_PREFIXES = new HashSet<>(Arrays.asList("8", "9"));

    private static final AtomicBoolean isUpdatingCountryCode = new AtomicBoolean(false);
    private static String lockedCountryCode = null;
    private static boolean isProcessingInput = false;

    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        String cleanedPhone = phone.replaceAll("[^0-9+]", "");
        if (!cleanedPhone.startsWith("+")) {
            cleanedPhone = "+" + cleanedPhone;
        }
        boolean matchesBD = Pattern.matches(PHONE_REGEX_BD, cleanedPhone);
        boolean matchesMY = Pattern.matches(PHONE_REGEX_MY, cleanedPhone);
        boolean matchesSG = Pattern.matches(PHONE_REGEX_SG, cleanedPhone);
        boolean isValid = matchesBD || matchesMY || matchesSG;
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

        String normalized = input.replaceAll("[^0-9+]", "");
        Log.d(TAG, "Normalizing input: " + input + ", cleaned: " + normalized);

        // Check if input already has a valid country code
        if (normalized.startsWith("+")) {
            if (isValidPhoneNumber(normalized)) {
                lockedCountryCode = getCountryCodeFromNumber(normalized);
                updateCountryFlag(normalized, ccp);
                Log.d(TAG, "Valid input with country code: " + normalized + ", Locked: " + lockedCountryCode);
                return normalized;
            }
            lockedCountryCode = null;
            Log.w(TAG, "Input starts with +, but invalid: " + normalized);
            return normalized;
        }

        // Use locked country code if valid
        if (lockedCountryCode != null) {
            String potentialNumber = lockedCountryCode + normalized;
            if (isValidPhoneNumber(potentialNumber)) {
                updateCountryFlag(potentialNumber, ccp);
                Log.d(TAG, "Using locked country code: " + potentialNumber);
                return potentialNumber;
            }
            lockedCountryCode = null;
            Log.d(TAG, "Unlocked country code due to invalid result: " + potentialNumber);
        }

        // Determine country code from CCP or SIM only if plausible
        String countryCode = ccp != null ? ccp.getSelectedCountryCodeWithPlus() : (simCountry != null ? getCountryCodeFromSim(simCountry) : "+60");
        String detectedCountry = detectCountry(normalized);

        // Handle local formats with explicit detection
        if (Pattern.matches(LOCAL_REGEX_BD, normalized) && detectedCountry.equals("BD")) {
            String result = "+880" + normalized.substring(1);
            if (isValidPhoneNumber(result)) {
                lockedCountryCode = "+880";
                updateCountryFlag(result, ccp);
                Log.d(TAG, "Normalized BD local format: " + result);
                return result;
            }
        } else if (Pattern.matches(LOCAL_REGEX_MY, normalized) && detectedCountry.equals("MY")) {
            String result = "+60" + normalized.substring(1);
            if (isValidPhoneNumber(result)) {
                lockedCountryCode = "+60";
                updateCountryFlag(result, ccp);
                Log.d(TAG, "Normalized MY local format: " + result);
                return result;
            }
        } else if (Pattern.matches(LOCAL_REGEX_SG, normalized) && detectedCountry.equals("SG")) {
            String result = "+65" + normalized;
            if (isValidPhoneNumber(result)) {
                lockedCountryCode = "+65";
                updateCountryFlag(result, ccp);
                Log.d(TAG, "Normalized SG local format: " + result);
                return result;
            }
        }

        // Try detected country code only if plausible
        String detectedCode = getCountryCode(detectedCountry);
        if (!detectedCountry.equals("Unknown")) {
            String potentialNumber = detectedCode + normalized;
            if (isValidPhoneNumber(potentialNumber)) {
                lockedCountryCode = detectedCode;
                updateCountryFlag(potentialNumber, ccp);
                Log.d(TAG, "Prepended detected country code: " + potentialNumber);
                return potentialNumber;
            }
        }

        Log.d(TAG, "No valid format detected, returning raw input: " + normalized);
        return normalized;
    }

    private static String getCountryCodeFromNumber(String number) {
        if (number.startsWith("+880")) return "+880";
        if (number.startsWith("+60")) return "+60";
        if (number.startsWith("+65")) return "+65";
        return null;
    }

    private static String getCountryCodeFromSim(String simCountry) {
        if (simCountry == null) return "+60";
        switch (simCountry.toUpperCase()) {
            case "BD": return "+880";
            case "MY": return "+60";
            case "SG": return "+65";
            default: return "+60";
        }
    }

    private static String detectCountry(String input) {
        if (input == null || input.length() < 1) return "Unknown";
        String prefix1 = input.length() >= 1 ? input.substring(0, 1) : "";
        String prefix2 = input.length() >= 2 ? input.substring(0, 2) : "";
        String prefix3 = input.length() >= 3 ? input.substring(0, 3) : "";

        // Singapore: Starts with 8 or 9, max 8 digits
        if (SG_PREFIXES.contains(prefix1) && input.length() <= 8) {
            return "SG";
        }

        // Bangladesh or Malaysia: Check prefix and length
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
            return "Unknown"; // Too short to decide
        }

        return "Unknown";
    }

    private static String getCountryCode(String country) {
        switch (country) {
            case "BD": return "+880";
            case "MY": return "+60";
            case "SG": return "+65";
            default: return "+60";
        }
    }

    private static int getExpectedLength(String country) {
        switch (country) {
            case "BD": return 11; // Local: 11, Full: 13
            case "MY": return 10; // Local: 10-11, Full: 11-12
            case "SG": return 8;  // Local: 8, Full: 11
            default: return -1;
        }
    }

    private static int getMaxLength(String country) {
        switch (country) {
            case "BD": return 13; // +880 + 10 digits
            case "MY": return 12; // +60 + 9 digits
            case "SG": return 11; // +65 + 8 digits
            default: return 13;  // Max across all (BD)
        }
    }

    public static void fetchSimNumber(Activity activity, EditText credInput) {
        if (activity == null || credInput == null) {
            Log.e(TAG, "Activity or EditText null");
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    REQUEST_PHONE_STATE_PERMISSION);
        } else {
            retrieveSimNumber(activity, credInput);
        }
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
                    Log.d(TAG, "Set SIM number: " + normalizedSimNumber + ", Country: " + simCountry);
                } else {
                    Log.w(TAG, "Invalid SIM number after normalization: " + normalizedSimNumber);
                }
            } else {
                Log.w(TAG, "Invalid or empty SIM number: " + simNumber);
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
            Log.d(TAG, "Registered EditText with CCP, reset to no country");
        } else {
            Log.e(TAG, "CountryCodePicker is null, flags will not appear");
        }

        if (validationMessage == null) {
            Log.e(TAG, "Validation message TextView is null, feedback will not be displayed");
        }

        String simCountry = getSimCountry(activity);
        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] debounceRunnable = new Runnable[1];
        final String[] lastProcessedInputHolder = new String[1];
        lastProcessedInputHolder[0] = "";

        credInput.addTextChangedListener(new TextWatcher() {
            private long lastToastTime = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                Log.d(TAG, "Before text changed: " + s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdatingCountryCode.get() || isProcessingInput) {
                    Log.d(TAG, "Skipping due to country code update or processing input");
                    return;
                }

                String currentInput = s.toString().trim();
                if (currentInput.equals(lastProcessedInputHolder[0])) {
                    Log.d(TAG, "Skipping unchanged input: " + currentInput);
                    return;
                }

                if (debounceRunnable[0] != null) {
                    handler.removeCallbacks(debounceRunnable[0]);
                }

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
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "After text changed: " + s);
            }
        });
    }

    static String getSimCountry(Activity activity) {
        try {
            TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                String simCountry = tm.getSimCountryIso() != null ? tm.getSimCountryIso().toUpperCase() : null;
                Log.d(TAG, "SIM country detected: " + simCountry);
                return simCountry;
            } else {
                Log.w(TAG, "SIM country not accessible (permission or null)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting SIM country: " + e.getMessage());
        }
        return null;
    }

    private static void processInput(Activity activity, String input, EditText credInput, CountryCodePicker ccp,
                                     ImageView emailIcon, ImageView phoneIcon, TextView validationMessage,
                                     String simCountry, long lastToastTime, ToastTimeCallback toastTimeCallback) {
        credInput.setError(null);

        if (input.isEmpty()) {
            lockedCountryCode = null;
            resetUI(emailIcon, phoneIcon, ccp, validationMessage);
            return;
        }

        if (looksLikeEmail(input)) {
            if (isValidEmail(input)) {
                updateUI(emailIcon, phoneIcon, ccp, validationMessage, true,
                        "Valid email", android.R.color.holo_green_dark, activity);
            } else {
                updateUI(emailIcon, phoneIcon, ccp, validationMessage, true,
                        "Typing email...", android.R.color.black, activity);
            }
            return;
        }

        String backendNumber = normalizePhoneNumberForBackend(input, ccp, simCountry);
        if (isValidPhoneNumber(backendNumber)) {
            updateUI(emailIcon, phoneIcon, ccp, validationMessage, false,
                    "Valid phone number", android.R.color.holo_green_dark, activity);
            return;
        }

        String cleanedInput = input.replaceAll("[^0-9+]", "");
        String detectedCountry = detectCountry(cleanedInput);
        int expectedLength = getExpectedLength(detectedCountry);
        int maxLength = getMaxLength(detectedCountry);
        int currentLength = cleanedInput.length();

        if (cleanedInput.startsWith("+")) {
            maxLength = 13; // Max across all countries with country code
        }

        // Validate length: too short, too long, or complete but invalid
        if ((expectedLength > 0 && currentLength >= expectedLength && !isValidPhoneNumber(backendNumber)) ||
                currentLength > maxLength) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastToastTime >= TOAST_DELAY) {
                Toast.makeText(activity, "Invalid phone number", Toast.LENGTH_SHORT).show();
                toastTimeCallback.onToastTimeUpdated(currentTime);
                Log.d(TAG, "Showing toast: Invalid phone number");
            }
            showValidationError(validationMessage,
                    "Invalid format (e.g., +8801712345678, +60123456789, +6591234567)",
                    android.R.color.holo_red_dark, activity);
            if (ccp != null) {
                ccp.setCountryForNameCode(null);
                ccp.setVisibility(View.VISIBLE);
            }
        } else {
            updateUI(emailIcon, phoneIcon, ccp, validationMessage, false,
                    "Typing phone number...", android.R.color.black, activity);
            if (ccp != null && detectedCountry.equals("Unknown")) {
                ccp.setCountryForNameCode(null);
            }
        }
    }

    private static void updateUI(ImageView emailIcon, ImageView phoneIcon, CountryCodePicker ccp,
                                 TextView validationMessage, boolean isEmail, String message,
                                 int color, Activity activity) {
        if (emailIcon != null) {
            emailIcon.setVisibility(isEmail ? View.VISIBLE : View.GONE);
            Log.d(TAG, "Email icon visibility: " + (isEmail ? "VISIBLE" : "GONE"));
        } else {
            Log.w(TAG, "Email icon is null");
        }
        if (phoneIcon != null) {
            phoneIcon.setVisibility(isEmail ? View.GONE : View.VISIBLE);
            Log.d(TAG, "Phone icon visibility: " + (isEmail ? "GONE" : "VISIBLE"));
        } else {
            Log.w(TAG, "Phone icon is null");
        }
        if (ccp != null) {
            ccp.setVisibility(isEmail ? View.GONE : View.VISIBLE);
            Log.d(TAG, "CCP visibility: " + (isEmail ? "GONE" : "VISIBLE"));
        } else {
            Log.w(TAG, "CCP is null");
        }
        if (validationMessage != null) {
            validationMessage.setText(message);
            validationMessage.setTextColor(ContextCompat.getColor(activity, color));
            Log.d(TAG, "Validation message: " + message + ", color: " + color);
        } else {
            Log.w(TAG, "Validation message is null");
        }
    }

    private static void showValidationError(TextView validationMessage, String message,
                                            int color, Activity activity) {
        if (validationMessage != null) {
            validationMessage.setText(message);
            validationMessage.setTextColor(ContextCompat.getColor(activity, color));
            Log.d(TAG, "Validation error message: " + message + ", color: " + color);
        } else {
            Log.w(TAG, "Validation message is null");
        }
    }

    private static void resetUI(ImageView emailIcon, ImageView phoneIcon, CountryCodePicker ccp,
                                TextView validationMessage) {
        if (emailIcon != null) {
            emailIcon.setVisibility(View.GONE);
            Log.d(TAG, "Reset email icon visibility: GONE");
        }
        if (phoneIcon != null) {
            phoneIcon.setVisibility(View.GONE);
            Log.d(TAG, "Reset phone icon visibility: GONE");
        }
        if (ccp != null) {
            ccp.setCountryForNameCode(null);
            ccp.setVisibility(View.GONE);
            Log.d(TAG, "Reset CCP visibility: GONE");
        }
        if (validationMessage != null) {
            validationMessage.setText("");
            Log.d(TAG, "Reset validation message");
        }
    }

    public static boolean updateCountryFlag(String phoneNumber, CountryCodePicker ccp) {
        if (ccp == null || phoneNumber == null) {
            Log.w(TAG, "CCP or phoneNumber null");
            return false;
        }

        try {
            isUpdatingCountryCode.set(true);
            String normalized = phoneNumber.replaceAll("[^0-9+]", "");
            Log.d(TAG, "Normalized phone for flag update: '" + normalized + "'");

            // Set flag for valid full numbers
            if (isValidPhoneNumber(normalized)) {
                if (normalized.startsWith("+880")) {
                    ccp.setCountryForNameCode("BD");
                    Log.d(TAG, "Set country to BD: " + normalized);
                    return true;
                } else if (normalized.startsWith("+60")) {
                    ccp.setCountryForNameCode("MY");
                    Log.d(TAG, "Set country to MY: " + normalized);
                    return true;
                } else if (normalized.startsWith("+65")) {
                    ccp.setCountryForNameCode("SG");
                    Log.d(TAG, "Set country to SG: " + normalized);
                    return true;
                }
            }

            // Allow partial local numbers with strict prefix matching
            String prefix1 = normalized.length() >= 1 ? normalized.substring(0, 1) : "";
            String prefix3 = normalized.length() >= 3 ? normalized.substring(0, 3) : "";
            if (!normalized.startsWith("+")) {
                if (SG_PREFIXES.contains(prefix1) && normalized.length() <= 8) {
                    ccp.setCountryForNameCode("SG");
                    Log.d(TAG, "Set country to SG (prefix match): " + normalized);
                    return true;
                } else if (BD_PREFIXES.contains(prefix3) && !MY_PREFIXES.contains(prefix3) && normalized.length() <= 11) {
                    ccp.setCountryForNameCode("BD");
                    Log.d(TAG, "Set country to BD (exclusive prefix): " + normalized);
                    return true;
                } else if (MY_PREFIXES.contains(prefix3) && !BD_PREFIXES.contains(prefix3) && normalized.length() <= 11) {
                    ccp.setCountryForNameCode("MY");
                    Log.d(TAG, "Set country to MY (exclusive prefix): " + normalized);
                    return true;
                }
            }

            Log.d(TAG, "No valid or plausible country match for: " + normalized);
            ccp.setCountryForNameCode(null);
            return false;
        } finally {
            isUpdatingCountryCode.set(false);
        }
    }

    // Firebase Authentication Methods
    public static void firebaseAuthWithGoogle(Activity activity, String idToken, DatabaseReference databaseReference,
                                              DatabaseReference emailsReference, DatabaseReference phoneNumbersReference,
                                              DatabaseReference usernamesReference, AuthCallback callback) {
        if (!validateAuthParams(activity, databaseReference, callback)) return;
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        performFirebaseAuth(activity, credential, "google", databaseReference, emailsReference,
                phoneNumbersReference, usernamesReference, callback);
    }

    public static void firebaseAuthWithFacebook(Activity activity, String token, DatabaseReference databaseReference,
                                                DatabaseReference emailsReference, DatabaseReference phoneNumbersReference,
                                                DatabaseReference usernamesReference, AuthCallback callback) {
        if (!validateAuthParams(activity, databaseReference, callback)) return;
        AuthCredential credential = FacebookAuthProvider.getCredential(token);
        performFirebaseAuth(activity, credential, "facebook", databaseReference, emailsReference,
                phoneNumbersReference, usernamesReference, callback);
    }

    public static void firebaseAuthWithGitHub(Activity activity, String clientId, DatabaseReference databaseReference,
                                              DatabaseReference emailsReference, DatabaseReference phoneNumbersReference,
                                              DatabaseReference usernamesReference, AuthCallback callback) {
        if (!validateAuthParams(activity, databaseReference, callback)) return;
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com")
                .addCustomParameter("client_id", clientId)
                .setScopes(Arrays.asList("user:email"));
        FirebaseAuth.getInstance()
                .startActivityForSignInWithProvider(activity, provider.build())
                .addOnSuccessListener(authResult -> handleSocialMediaUser(authResult.getUser(), "github",
                        databaseReference, emailsReference, phoneNumbersReference, usernamesReference, callback))
                .addOnFailureListener(e -> callback.onFailure("GitHub auth failed: " + e.getMessage()));
    }

    private static boolean validateAuthParams(Activity activity, DatabaseReference databaseReference, AuthCallback callback) {
        if (activity == null || databaseReference == null || callback == null) {
            Log.e(TAG, "Invalid auth parameters");
            if (callback != null) callback.onFailure("Invalid parameters");
            return false;
        }
        return true;
    }

    private static void performFirebaseAuth(Activity activity, AuthCredential credential, String provider,
                                            DatabaseReference databaseReference, DatabaseReference emailsReference,
                                            DatabaseReference phoneNumbersReference, DatabaseReference usernamesReference,
                                            AuthCallback callback) {
        FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            handleSocialMediaUser(user, provider, databaseReference, emailsReference,
                                    phoneNumbersReference, usernamesReference, callback);
                        } else {
                            callback.onFailure("User null after auth");
                        }
                    } else {
                        callback.onFailure("Auth failed: " + task.getException().getMessage());
                    }
                });
    }

    private static void handleSocialMediaUser(FirebaseUser user, String provider, DatabaseReference databaseReference,
                                              DatabaseReference emailsReference, DatabaseReference phoneNumbersReference,
                                              DatabaseReference usernamesReference, AuthCallback callback) {
        if (user == null) {
            callback.onFailure("User null");
            return;
        }

        String email = user.getEmail() != null ? user.getEmail() : "";
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
        String phone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";

        databaseReference.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    callback.onSuccess(user);
                } else {
                    checkUniqueness(emailsReference, email.replace(".", "_"), isUnique -> {
                        if (!isUnique) {
                            FirebaseAuth.getInstance().signOut();
                            callback.onFailure("Email '" + email + "' already in use");
                            return;
                        }
                        generateUniqueUsername(displayName, usernamesReference, username ->
                                saveUserData(user, databaseReference, emailsReference, phoneNumbersReference,
                                        usernamesReference, displayName, email, phone, username, provider,
                                        new SaveUserDataCallback() {
                                            @Override
                                            public void onSuccess() { callback.onSuccess(user); }
                                            @Override
                                            public void onFailure(String error) { callback.onFailure(error); }
                                        }));
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onFailure("Database error: " + error.getMessage());
            }
        });
    }

    public static void checkUniqueness(DatabaseReference ref, String key, UniquenessCallback callback) {
        ref.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onCheckComplete(!snapshot.exists());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Uniqueness check failed: " + error.getMessage());
                callback.onCheckComplete(false);
            }
        });
    }

    public static void saveUserData(FirebaseUser user, DatabaseReference databaseReference, DatabaseReference emailsReference,
                                    DatabaseReference phoneNumbersReference, DatabaseReference usernamesReference,
                                    String fullName, String email, String phoneNumber, String username, String signInMethod,
                                    SaveUserDataCallback callback) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName != null ? fullName : "User");
        userData.put("email", email != null ? email : "");
        userData.put("phoneNumber", phoneNumber != null ? phoneNumber : "");
        userData.put("gender", "Not specified");
        userData.put("birthday", "01-01-2000");
        userData.put("username", username != null ? username : "user" + new java.util.Random().nextInt(1000));
        userData.put("imageUrl", "");
        userData.put("signInMethod", signInMethod != null ? signInMethod : "unknown");

        databaseReference.child(user.getUid()).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    if ("email".equals(signInMethod) && email != null && !email.isEmpty()) {
                        emailsReference.child(email.replace(".", "_")).setValue(user.getUid());
                    }
                    if ("phone".equals(signInMethod) && phoneNumber != null && !phoneNumber.isEmpty()) {
                        phoneNumbersReference.child(phoneNumber).setValue(user.getUid());
                    }
                    if (username != null && !username.isEmpty()) {
                        usernamesReference.child(username).setValue(user.getUid());
                    }
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure("Failed to save user data: " + e.getMessage()));
    }

    public static void generateUniqueUsername(String fullName, DatabaseReference usernamesReference, UsernameCallback callback) {
        String baseUsername = fullName != null ? fullName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase() : "user";
        if (baseUsername.isEmpty()) baseUsername = "user";
        checkUsernameAvailability(baseUsername, 1, usernamesReference, callback);
    }

    public static void checkUsernameAvailability(String baseUsername, int suffix, DatabaseReference usernamesReference, UsernameCallback callback) {
        String candidate = suffix == 1 ? baseUsername : baseUsername + (suffix - 1);
        usernamesReference.child(candidate).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onUsernameGenerated(candidate);
                } else {
                    checkUsernameAvailability(baseUsername, suffix + 1, usernamesReference, callback);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onUsernameGenerated(baseUsername + new java.util.Random().nextInt(1000));
            }
        });
    }

    // Interfaces
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(String errorMessage);
    }

    public interface UniquenessCallback {
        void onCheckComplete(boolean isUnique);
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