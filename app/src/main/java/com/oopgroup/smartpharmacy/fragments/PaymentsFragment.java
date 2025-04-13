package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.PaymentMethodsAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentsFragment extends Fragment {

    private static final String TAG = "PaymentsFragment";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private RecyclerView rvPaymentMethods;
    private PaymentMethodsAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextInputEditText etCardNumber, etExpiryDate, etCvv, etCardHolderName, etPaypalEmail;
    private TextInputLayout tilCardNumber, tilExpiryDate, tilCvv, tilCardHolderName, tilPaypalEmail;
    private Button btnAddPayment, btnSubmitRefund;
    private TextInputEditText etOrderId, etRefundReason;
    private LinearLayout paymentMethodsSection, refundSection, creditCardFields, paypalFields;
    private RadioGroup radioGroupPaymentMethods;
    private RadioButton radioCreditCard, radioPaypal;

    public PaymentsFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_payments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            animateClick(v);
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // Payment Methods UI
        rvPaymentMethods = view.findViewById(R.id.rvPaymentMethods);
        rvPaymentMethods.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PaymentMethodsAdapter(new ArrayList<>(), this::removePaymentMethod);
        rvPaymentMethods.setAdapter(adapter);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        etCardNumber = view.findViewById(R.id.etCardNumber);
        etExpiryDate = view.findViewById(R.id.etExpiryDate);
        etCvv = view.findViewById(R.id.etCvv);
        etCardHolderName = view.findViewById(R.id.etCardHolderName);
        etPaypalEmail = view.findViewById(R.id.etPaypalEmail);
        tilCardNumber = view.findViewById(R.id.tilCardNumber);
        tilExpiryDate = view.findViewById(R.id.tilExpiryDate);
        tilCvv = view.findViewById(R.id.tilCvv);
        tilCardHolderName = view.findViewById(R.id.tilCardHolderName);
        tilPaypalEmail = view.findViewById(R.id.tilPaypalEmail);
        btnAddPayment = view.findViewById(R.id.btnAddPayment);
        radioGroupPaymentMethods = view.findViewById(R.id.radioGroupPaymentMethods);
        radioCreditCard = view.findViewById(R.id.radioCreditCard);
        radioPaypal = view.findViewById(R.id.radioPaypal);
        creditCardFields = view.findViewById(R.id.creditCardFields);
        paypalFields = view.findViewById(R.id.paypalFields);

        // Refund Request UI
        etOrderId = view.findViewById(R.id.etOrderId);
        etRefundReason = view.findViewById(R.id.etRefundReason);
        btnSubmitRefund = view.findViewById(R.id.btnSubmitRefund);

        // Sections
        paymentMethodsSection = view.findViewById(R.id.paymentMethodsSection);
        refundSection = view.findViewById(R.id.refundSection);

        // Validate view bindings
        if (radioGroupPaymentMethods == null) Log.e(TAG, "radioGroupPaymentMethods is null!");
        if (radioCreditCard == null) Log.e(TAG, "radioCreditCard is null!");
        if (radioPaypal == null) Log.e(TAG, "radioPaypal is null!");
        if (creditCardFields == null) Log.e(TAG, "creditCardFields is null!");
        if (paypalFields == null) Log.e(TAG, "paypalFields is null!");
        if (btnAddPayment == null) Log.e(TAG, "btnAddPayment is null!");

        // Initial state
        radioGroupPaymentMethods.clearCheck();
        creditCardFields.setVisibility(View.GONE);
        paypalFields.setVisibility(View.GONE);
        btnAddPayment.setText("Add Payment Method");
        Log.d(TAG, "Initial state: No selection, fields hidden");

        // Configure input fields
        configureCardNumberInput();
        configureExpiryDateInput();
        configureCvvInput();
        configureCardHolderNameInput();

        // Disable emoji handling to prevent interference
        disableEmojiHandling(etCardNumber);
        disableEmojiHandling(etExpiryDate);

        // Load payment methods
        loadPaymentMethods();

        // Swipe to Refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d(TAG, "Swipe to refresh triggered");
            loadPaymentMethods();
            swipeRefreshLayout.setRefreshing(false);
        });

        // Radio Group Listener
        radioGroupPaymentMethods.setOnCheckedChangeListener((group, checkedId) -> {
            Log.d(TAG, "RadioGroup checkedId: " + checkedId);
            if (checkedId == R.id.radioCreditCard) {
                Log.d(TAG, "Credit Card selected");
                creditCardFields.post(() -> {
                    creditCardFields.setVisibility(View.VISIBLE);
                    paypalFields.setVisibility(View.GONE);
                    btnAddPayment.setText("Add Card");
                    paymentMethodsSection.requestLayout();
                });
            } else if (checkedId == R.id.radioPaypal) {
                Log.d(TAG, "PayPal selected");
                paypalFields.post(() -> {
                    paypalFields.setVisibility(View.VISIBLE);
                    creditCardFields.setVisibility(View.GONE);
                    btnAddPayment.setText("Add PayPal");
                    paymentMethodsSection.requestLayout();
                });
            } else {
                Log.d(TAG, "No payment method selected");
                creditCardFields.post(() -> {
                    creditCardFields.setVisibility(View.GONE);
                    paypalFields.setVisibility(View.GONE);
                    btnAddPayment.setText("Add Payment Method");
                    paymentMethodsSection.requestLayout();
                });
            }
        });

        // Fallback: Direct click listeners on RadioButtons
        radioCreditCard.setOnClickListener(v -> {
            Log.d(TAG, "Credit Card clicked directly");
            if (!radioCreditCard.isChecked()) {
                radioGroupPaymentMethods.check(R.id.radioCreditCard);
            }
        });

        radioPaypal.setOnClickListener(v -> {
            Log.d(TAG, "PayPal clicked directly");
            if (!radioPaypal.isChecked()) {
                radioGroupPaymentMethods.check(R.id.radioPaypal);
            }
        });

        // Add payment method
        btnAddPayment.setOnClickListener(v -> {
            animateClick(v);
            int checkedId = radioGroupPaymentMethods.getCheckedRadioButtonId();
            Log.d(TAG, "Add Payment clicked, checkedId: " + checkedId);
            if (checkedId == R.id.radioCreditCard) {
                String cardNumber = etCardNumber.getText().toString().trim().replaceAll("[^0-9]", "");
                String expiryDate = etExpiryDate.getText().toString().trim();
                String cvv = etCvv.getText().toString().trim();
                String cardHolderName = etCardHolderName.getText().toString().trim();

                tilCardNumber.setError(null);
                tilExpiryDate.setError(null);
                tilCvv.setError(null);
                tilCardHolderName.setError(null);

                boolean isValid = true;
                if (cardNumber.isEmpty()) {
                    tilCardNumber.setError("Card number is required");
                    isValid = false;
                }
                if (expiryDate.isEmpty()) {
                    tilExpiryDate.setError("Expiry date is required");
                    isValid = false;
                }
                if (cvv.isEmpty()) {
                    tilCvv.setError("CVV is required");
                    isValid = false;
                }
                if (cardHolderName.isEmpty()) {
                    tilCardHolderName.setError("Cardholder name is required");
                    isValid = false;
                }
                if (!validateCardDetails(cardNumber, expiryDate, cvv)) {
                    isValid = false;
                }

                if (isValid) {
                    btnAddPayment.setEnabled(false); // Disable button
                    btnAddPayment.setText("Adding...");
                    addPaymentMethod("Credit Card");
                }
            } else if (checkedId == R.id.radioPaypal) {
                String paypalEmail = etPaypalEmail.getText().toString().trim();
                tilPaypalEmail.setError(null);

                if (paypalEmail.isEmpty() || !paypalEmail.contains("@")) {
                    tilPaypalEmail.setError("Please enter a valid PayPal email");
                } else {
                    btnAddPayment.setEnabled(false); // Disable button
                    btnAddPayment.setText("Adding...");
                    addPaymentMethod("Paypal");
                }
            } else {
                Toast.makeText(requireContext(), "Please select a payment method", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "No payment method selected");
            }
        });

        // Submit refund request
        btnSubmitRefund.setOnClickListener(v -> {
            animateClick(v);
            btnSubmitRefund.setEnabled(false); // Disable button
            btnSubmitRefund.setText("Submitting...");
            submitRefundRequest();
        });
    }

    private void configureCardNumberInput() {
        etCardNumber.setFilters(new InputFilter[]{new InputFilter.LengthFilter(19)});

        etCardNumber.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;
            private String previousText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String input = s.toString().replaceAll("[^0-9]", "");
                if (input.length() > 16) {
                    input = input.substring(0, 16);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    if (i > 0 && i % 4 == 0) {
                        formatted.append(" ");
                    }
                    formatted.append(input.charAt(i));
                }

                String result = formatted.toString();
                if (!result.equals(previousText)) {
                    etCardNumber.setText(result);
                    try {
                        etCardNumber.setSelection(result.length());
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting selection: " + e.getMessage());
                    }
                    previousText = result;

                    // Real-time validation
                    tilCardNumber.setError(null);
                    if (!input.isEmpty()) {
                        if (input.length() < 13) {
                            tilCardNumber.setError("Card number too short");
                        } else if (!isValidLuhn(input)) {
                            tilCardNumber.setError("Invalid card number");
                        }
                    }
                }

                isFormatting = false;
            }
        });
    }

    private void configureExpiryDateInput() {
        etExpiryDate.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});

        etExpiryDate.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;
            private String previousText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String input = s.toString().replaceAll("[^0-9]", "");
                if (input.length() > 4) {
                    input = input.substring(0, 4);
                }

                StringBuilder formatted = new StringBuilder();
                if (input.length() > 0) {
                    if (input.length() <= 2) {
                        formatted.append(input);
                    } else {
                        String month = input.substring(0, Math.min(2, input.length()));
                        try {
                            int monthInt = Integer.parseInt(month);
                            if (monthInt > 12) {
                                month = "12";
                            } else if (monthInt < 1) {
                                month = "01";
                            }
                        } catch (NumberFormatException e) {
                            month = "01";
                        }
                        formatted.append(month);
                        formatted.append("/");
                        if (input.length() > 2) {
                            formatted.append(input.substring(2));
                        }
                    }
                }

                String result = formatted.toString();
                if (!result.equals(previousText)) {
                    etExpiryDate.setText(result);
                    try {
                        etExpiryDate.setSelection(result.length());
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting selection: " + e.getMessage());
                    }
                    previousText = result;

                    // Real-time validation
                    tilExpiryDate.setError(null);
                    if (input.length() == 4) {
                        try {
                            int month = Integer.parseInt(input.substring(0, 2));
                            int year = Integer.parseInt(input.substring(2));
                            int fullYear = 2000 + year;
                            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                            int currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;

                            if (month < 1 || month > 12) {
                                tilExpiryDate.setError("Invalid month");
                            } else if (fullYear < currentYear ||
                                    (fullYear == currentYear && month < currentMonth)) {
                                tilExpiryDate.setError("Card expired");
                            }
                        } catch (NumberFormatException e) {
                            tilExpiryDate.setError("Invalid date format");
                        }
                    }
                }

                isFormatting = false;
            }
        });
    }

    private void configureCvvInput() {
        etCvv.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});

        etCvv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().replaceAll("[^0-9]", "");
                tilCvv.setError(null);
                if (!input.isEmpty() && (input.length() < 3 || input.length() > 4)) {
                    tilCvv.setError("CVV must be 3 or 4 digits");
                }
            }
        });
    }

    private void configureCardHolderNameInput() {
        etCardHolderName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                tilCardHolderName.setError(null);
                if (!input.isEmpty() && !input.matches("[a-zA-Z ]+")) {
                    tilCardHolderName.setError("Name must contain only letters and spaces");
                }
            }
        });
    }

    private void disableEmojiHandling(TextInputEditText editText) {
        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        });
    }

    private void loadPaymentMethods() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("payment_methods")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Map<String, Object>> methods = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> method = doc.getData();
                        method.put("id", doc.getId());
                        methods.add(method);
                    }
                    adapter.updateMethods(methods);
                    Log.d(TAG, "Payment methods loaded: " + methods.size());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to load payment methods", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error loading payment methods: " + e.getMessage());
                });
    }

    private void addPaymentMethod(String type) {
        Map<String, Object> method = new HashMap<>();
        method.put("type", type);
        method.put("createdAt", com.google.firebase.Timestamp.now());

        if ("Credit Card".equals(type)) {
            String cardNumber = etCardNumber.getText().toString().trim().replaceAll("[^0-9]", "");
            String expiryDate = etExpiryDate.getText().toString().trim();
            String cvv = etCvv.getText().toString().trim();
            String cardHolderName = etCardHolderName.getText().toString().trim();

            method.put("lastFour", cardNumber.substring(cardNumber.length() - 4));
            method.put("expiryDate", expiryDate);
            method.put("cardHolderName", cardHolderName.isEmpty() ? null : cardHolderName);
            method.put("token", "simulated_card_" + System.currentTimeMillis());
        } else if ("Paypal".equals(type)) {
            String paypalEmail = etPaypalEmail.getText().toString().trim();
            method.put("email", paypalEmail);
            method.put("token", "simulated_paypal_" + System.currentTimeMillis());
        }

        String userId = auth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("payment_methods")
                .add(method)
                .addOnSuccessListener(documentReference -> {
                    etCardNumber.setText("");
                    etExpiryDate.setText("");
                    etCvv.setText("");
                    etCardHolderName.setText("");
                    etPaypalEmail.setText("");
                    radioGroupPaymentMethods.clearCheck();
                    creditCardFields.setVisibility(View.GONE);
                    paypalFields.setVisibility(View.GONE);
                    btnAddPayment.setText("Add Payment Method");
                    btnAddPayment.setEnabled(true); // Re-enable button
                    loadPaymentMethods();
                    Toast.makeText(requireContext(), type + " added successfully", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, type + " added successfully, ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    btnAddPayment.setText("Add Payment Method");
                    btnAddPayment.setEnabled(true); // Re-enable button
                    Toast.makeText(requireContext(), "Failed to add " + type, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to add " + type + ": " + e.getMessage());
                });
    }

    private void removePaymentMethod(Map<String, Object> method) {
        String userId = auth.getCurrentUser().getUid();
        String methodId = (String) method.get("id");
        db.collection("users").document(userId).collection("payment_methods").document(methodId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    loadPaymentMethods();
                    Toast.makeText(requireContext(), "Payment method removed", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Payment method removed: " + methodId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to remove payment method", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to remove payment method: " + e.getMessage());
                });
    }

    private void submitRefundRequest() {
        String orderId = etOrderId.getText().toString().trim();
        String reason = etRefundReason.getText().toString().trim();

        if (orderId.isEmpty() || reason.isEmpty()) {
            btnSubmitRefund.setText("Submit Refund");
            btnSubmitRefund.setEnabled(true); // Re-enable button
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Refund request fields incomplete");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        db.collection("orders").document(orderId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && userId.equals(documentSnapshot.getString("userId"))) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("refundStatus", "requested");
                        updates.put("refundReason", reason);
                        db.collection("orders").document(orderId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    etOrderId.setText("");
                                    etRefundReason.setText("");
                                    btnSubmitRefund.setText("Submit Refund");
                                    btnSubmitRefund.setEnabled(true); // Re-enable button
                                    Toast.makeText(requireContext(), "Refund request submitted", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "Refund request submitted for order: " + orderId);
                                })
                                .addOnFailureListener(e -> {
                                    btnSubmitRefund.setText("Submit Refund");
                                    btnSubmitRefund.setEnabled(true); // Re-enable button
                                    Toast.makeText(requireContext(), "Failed to submit refund", Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Failed to submit refund: " + e.getMessage());
                                });
                    } else {
                        btnSubmitRefund.setText("Submit Refund");
                        btnSubmitRefund.setEnabled(true); // Re-enable button
                        Toast.makeText(requireContext(), "Invalid order ID", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "Invalid order ID: " + orderId);
                    }
                })
                .addOnFailureListener(e -> {
                    btnSubmitRefund.setText("Submit Refund");
                    btnSubmitRefund.setEnabled(true); // Re-enable button
                    Toast.makeText(requireContext(), "Failed to verify order", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to verify order: " + e.getMessage());
                });
    }

    private boolean validateCardDetails(String cardNumber, String expiryDate, String cvv) {
        boolean isValid = true;

        // Card Number Validation
        String cleanCardNumber = cardNumber.replaceAll("[^0-9]", "");
        if (cleanCardNumber.length() < 13 || cleanCardNumber.length() > 16) {
            tilCardNumber.setError("Card number must be 13-16 digits");
            isValid = false;
        } else if (!isValidLuhn(cleanCardNumber)) {
            tilCardNumber.setError("Invalid card number");
            isValid = false;
        }

        // Expiry Date Validation
        if (!expiryDate.matches("\\d{2}/\\d{2}")) {
            tilExpiryDate.setError("Use MM/YY format");
            isValid = false;
        } else {
            try {
                String[] parts = expiryDate.split("/");
                int month = Integer.parseInt(parts[0]);
                int year = Integer.parseInt(parts[1]);
                int fullYear = 2000 + year;
                int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                int currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;

                if (month < 1 || month > 12) {
                    tilExpiryDate.setError("Invalid month");
                    isValid = false;
                } else if (fullYear < currentYear ||
                        (fullYear == currentYear && month < currentMonth)) {
                    tilExpiryDate.setError("Card expired");
                    isValid = false;
                }
            } catch (Exception e) {
                tilExpiryDate.setError("Invalid date format");
                isValid = false;
            }
        }

        // CVV Validation
        String cleanCvv = cvv.replaceAll("[^0-9]", "");
        if (cleanCvv.length() < 3 || cleanCvv.length() > 4) {
            tilCvv.setError("CVV must be 3-4 digits");
            isValid = false;
        }

        return isValid;
    }

    private boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(cardNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private void animateClick(View view) {
        Animation animation = AnimationUtils.loadAnimation(requireContext(), R.anim.click_animation);
        view.startAnimation(animation);
    }
}