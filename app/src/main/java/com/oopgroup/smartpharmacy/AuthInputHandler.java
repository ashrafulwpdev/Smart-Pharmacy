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

    private static final String PHONE_REGEX_BD = "^(\\+880|880)[0-9]{10}$"; // +8801712345678
    private static final String PHONE_REGEX_MY = "^(\\+60|60)[0-9]{9,10}$"; // +60123456789
    private static final String PHONE_REGEX_SG = "^(\\+65|65)[0-9]{8}$"; // +6581234567
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    public static boolean isValidPhoneNumber(String phone) {
        String cleanedPhone = phone.replaceAll("[^0-9+]", "");
        boolean isValid = Pattern.matches(PHONE_REGEX_BD, cleanedPhone) ||
                Pattern.matches(PHONE_REGEX_MY, cleanedPhone) ||
                Pattern.matches(PHONE_REGEX_SG, cleanedPhone);
        Log.d(TAG, "Validating phone: " + cleanedPhone + ", isValid: " + isValid);
        return isValid;
    }

    public static boolean isValidEmail(String email) {
        return Pattern.matches(EMAIL_REGEX, email) && email.contains(".");
    }

    public static String normalizePhoneNumber(String input, CountryCodePicker ccp) {
        if (ccp == null) {
            Log.w(TAG, "CCP is null, returning input as-is: " + input);
            return input;
        }

        String normalized = input.replaceAll("[^0-9+]", "");
        String countryCode = ccp.getSelectedCountryCodeWithPlus();

        Log.d(TAG, "Normalizing: " + input + ", CCP: " + countryCode);

        if (normalized.startsWith("+")) {
            if (isValidPhoneNumber(normalized)) {
                return normalized;
            }
        }

        if (normalized.startsWith("0")) {
            normalized = normalized.substring(1);
            updateCountryFlag("0" + normalized, ccp);
            countryCode = ccp.getSelectedCountryCodeWithPlus();
            String result = countryCode + normalized;
            if (isValidPhoneNumber(result)) {
                return result;
            }
        } else if (normalized.startsWith(countryCode.replace("+", ""))) {
            String result = "+" + normalized;
            if (isValidPhoneNumber(result)) {
                return result;
            }
        }

        String result = countryCode + normalized;
        if (isValidPhoneNumber(result)) {
            return result;
        }

        Log.w(TAG, "Normalization failed for: " + input + ", returning original");
        return input;
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
        if (ccp != null) {
            // Explicitly register the EditText with the CCP to avoid "EditText not registered" warning
            ccp.registerCarrierNumberEditText(credInput);
            Log.d(TAG, "Registered EditText " + credInput.getId() + " with CCP");
        } else {
            Log.w(TAG, "CCP is null, skipping registration for EditText " + credInput.getId());
        }

        credInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                credInput.setError(null);

                if (isValidEmail(input)) {
                    if (emailIcon != null) emailIcon.setVisibility(View.VISIBLE);
                    if (phoneIcon != null) phoneIcon.setVisibility(View.GONE);
                    if (ccp != null) ccp.setVisibility(View.GONE);
                    if (validationMessage != null) {
                        validationMessage.setText("Valid email");
                        validationMessage.setTextColor(ContextCompat.getColor(activity, android.R.color.holo_green_dark));
                    }
                } else {
                    String cleanedInput = input.replaceAll("[^0-9+]", "");
                    if (!input.contains("@") && cleanedInput.length() >= 8 && isValidPhoneNumber(cleanedInput)) {
                        if (emailIcon != null) emailIcon.setVisibility(View.GONE);
                        if (phoneIcon != null) phoneIcon.setVisibility(View.VISIBLE);
                        if (ccp != null) {
                            ccp.setVisibility(View.VISIBLE);
                            updateCountryFlag(cleanedInput, ccp);
                            Log.d(TAG, "Updated CCP to: " + ccp.getSelectedCountryCodeWithPlus());
                        }
                        if (validationMessage != null) {
                            validationMessage.setText("Valid phone number");
                            validationMessage.setTextColor(ContextCompat.getColor(activity, android.R.color.holo_green_dark));
                        }
                    } else {
                        if (emailIcon != null) emailIcon.setVisibility(View.GONE);
                        if (phoneIcon != null) phoneIcon.setVisibility(View.GONE);
                        if (ccp != null) ccp.setVisibility(View.GONE);
                        if (validationMessage != null) {
                            if (!input.isEmpty()) {
                                validationMessage.setText("Invalid format (e.g., +8801712345678, +60123456789, +6581234567)");
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

    public static boolean updateCountryFlag(String phoneNumber, CountryCodePicker ccp) {
        if (ccp == null) {
            Log.w(TAG, "CCP is null, cannot update country flag for: " + phoneNumber);
            return false;
        }

        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        if (Pattern.matches("^(\\+880|880)[0-9]{10}$", normalized) || Pattern.matches("^01[3-9][0-9]{8}$", normalized) || Pattern.matches("^1[3-9][0-9]{8}$", normalized)) {
            ccp.setCountryForNameCode("BD");
            return true;
        } else if (Pattern.matches("^(\\+60|60)[0-9]{9,10}$", normalized) || Pattern.matches("^01[0-9][0-9]{7,8}$", normalized)) {
            ccp.setCountryForNameCode("MY");
            return true;
        } else if (Pattern.matches("^(\\+65|65)[0-9]{8}$", normalized) || Pattern.matches("^[89][0-9]{7}$", normalized)) {
            ccp.setCountryForNameCode("SG");
            return true;
        } else {
            Log.d(TAG, "No country match for: " + normalized + ", keeping CCP as: " + ccp.getSelectedCountryCodeWithPlus());
            return false;
        }
    }
}