package com.oopgroup.smartpharmacy;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.hbb20.CountryCodePicker;
import java.util.regex.Pattern;

public class AuthInputHandler {
    private static final String TAG = "AuthInputHandler";
    private static final int REQUEST_PHONE_STATE_PERMISSION = 1001;

    // Regex for phone and email validation
    // Supports BD (+880, 10 digits), MY (+60, 9-10 digits), SG (+65, 8 digits)
    private static final String PHONE_REGEX = "^(\\+?(880|60|65))?([0-9]{8,10})$";
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    public static boolean isValidPhoneNumber(String phone) {
        String cleanedPhone = phone.replaceAll("[^0-9+]", "");
        boolean isValid = Pattern.matches(PHONE_REGEX, cleanedPhone);
        Log.d(TAG, "Validating phone: " + cleanedPhone + ", isValid: " + isValid);
        return isValid;
    }

    public static boolean isValidEmail(String email) {
        return Pattern.matches(EMAIL_REGEX, email) && email.contains(".");
    }

    public static String normalizePhoneNumber(String input, CountryCodePicker ccp) {
        String normalized = input.replaceAll("[^0-9+]", "");
        String countryCode = ccp.getSelectedCountryCodeWithPlus();

        Log.d(TAG, "Normalizing: " + input + ", CCP: " + countryCode);

        // Already in E.164 format
        if (normalized.startsWith("+")) {
            return normalized; // e.g., +60123456789
        }

        // Use CCP country code as the primary guide
        if (normalized.startsWith("0")) {
            normalized = normalized.substring(1); // Remove leading 0
            return countryCode + normalized; // e.g., 0123456789 → +60123456789 if CCP is MY
        } else if (normalized.startsWith(countryCode.replace("+", ""))) {
            return "+" + normalized; // e.g., 60123456789 → +60123456789
        } else {
            return countryCode + normalized; // Fallback: prepend CCP code
        }
    }

    public static void fetchSimNumber(Activity activity, EditText credInput) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PHONE_STATE_PERMISSION);
        } else {
            retrieveSimNumber(activity, credInput);
        }
    }

    private static void retrieveSimNumber(Activity activity, EditText credInput) {
        try {
            TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
            String simNumber = tm.getLine1Number();
            if (simNumber != null && !simNumber.isEmpty()) {
                simNumber = simNumber.replaceAll("[^0-9+]", "");
                if (isValidPhoneNumber(simNumber)) {
                    credInput.setText(simNumber);
                } else {
                    Log.d(TAG, "SIM number found but invalid: " + simNumber);
                }
            } else {
                Log.d(TAG, "SIM number not available.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied to read SIM number: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error fetching SIM number: " + e.getMessage());
        }
    }

    public static void setupDynamicInput(Activity activity, EditText credInput, CountryCodePicker ccp, ImageView emailIcon, ImageView phoneIcon, TextView validationMessage) {
        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                credInput.setError(null);

                if (isValidEmail(input)) {
                    emailIcon.setVisibility(View.VISIBLE);
                    phoneIcon.setVisibility(View.GONE);
                    ccp.setVisibility(View.GONE);
                    if (validationMessage != null) {
                        validationMessage.setText("Valid email");
                        validationMessage.setTextColor(ContextCompat.getColor(activity, android.R.color.holo_green_dark));
                    }
                } else {
                    String cleanedInput = input.replaceAll("[^0-9+]", "");
                    if (!input.contains("@") && cleanedInput.length() >= 8 && isValidPhoneNumber(cleanedInput)) {
                        emailIcon.setVisibility(View.GONE);
                        phoneIcon.setVisibility(View.VISIBLE);
                        ccp.setVisibility(View.VISIBLE);
                        updateCountryFlag(cleanedInput, ccp);
                        Log.d(TAG, "Updated CCP to: " + ccp.getSelectedCountryCodeWithPlus());
                        if (validationMessage != null) {
                            validationMessage.setText("Valid phone number");
                            validationMessage.setTextColor(ContextCompat.getColor(activity, android.R.color.holo_green_dark));
                        }
                    } else {
                        emailIcon.setVisibility(View.GONE);
                        phoneIcon.setVisibility(View.GONE);
                        ccp.setVisibility(View.GONE);
                        if (validationMessage != null) {
                            if (!input.isEmpty()) {
                                validationMessage.setText("Invalid format (e.g., +60123456789, 0123456789, +6581234567, or email@example.com)");
                                validationMessage.setTextColor(ContextCompat.getColor(activity, android.R.color.holo_red_dark));
                            } else {
                                validationMessage.setText("");
                            }
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    public static void updateCountryFlag(String phoneNumber, CountryCodePicker ccp) {
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        if (normalized.startsWith("+880") || normalized.startsWith("880") || normalized.matches("^01[3-9][0-9]{8}$")) {
            ccp.setCountryForNameCode("BD");
        } else if (normalized.startsWith("+60") || normalized.startsWith("60") || normalized.matches("^01[0-9][0-9]{7,8}$")) {
            ccp.setCountryForNameCode("MY");
        } else if (normalized.startsWith("+65") || normalized.startsWith("65") || normalized.matches("^[89][0-9]{6}$")) {
            ccp.setCountryForNameCode("SG");
        }
    }
}