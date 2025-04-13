package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.PaymentMethodsAdapter;
import com.oopgroup.smartpharmacy.models.Order;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutFragment extends Fragment {

    private static final String TAG = "CheckoutFragment";
    private static final String CURRENCY = "RM";
    private static final long PAYMENT_SIMULATION_DELAY = 2000;

    private RadioButton radioCreditCard, radioPaypal, radioCashOnDelivery;
    private LinearLayout creditCardFields, savedPaymentMethodsContainer;
    private MaterialButton payNowButton;
    private TextInputEditText etCardNumber, etExpiryDate, etCvv, etCardHolderName;
    private TextInputLayout cardNumberLayout, expiryDateLayout, cvvLayout, cardHolderNameLayout;
    private TextView addressName, addressDetails, addressEmail, addressMobile, tvGrandTotal;
    private Button changeAddressButton;
    private RecyclerView rvSavedPaymentMethods;
    private PaymentMethodsAdapter paymentMethodsAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String selectedAddressId;
    private double grandTotal;
    private double discount;
    private double deliveryFee;
    private List<Map<String, Object>> items;
    private String orderType;
    private String promoCode;
    private String reorderPaymentMethod;
    private String assignedDeliveryManId;
    private boolean orderPlaced = false;
    private Map<String, Object> selectedSavedPaymentMethod;

    public CheckoutFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checkout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        radioCreditCard = view.findViewById(R.id.radioCreditCard);
        radioPaypal = view.findViewById(R.id.radioPaypal);
        radioCashOnDelivery = view.findViewById(R.id.radioCashOnDelivery);
        creditCardFields = view.findViewById(R.id.creditCardFields);
        savedPaymentMethodsContainer = view.findViewById(R.id.savedPaymentMethodsContainer);
        payNowButton = view.findViewById(R.id.payNowButton);
        addressName = view.findViewById(R.id.addressName);
        addressDetails = view.findViewById(R.id.addressDetails);
        addressEmail = view.findViewById(R.id.addressEmail);
        addressMobile = view.findViewById(R.id.addressMobile);
        tvGrandTotal = view.findViewById(R.id.tvGrandTotal);
        changeAddressButton = view.findViewById(R.id.changeAddressButton);
        rvSavedPaymentMethods = view.findViewById(R.id.rvSavedPaymentMethods);
        etCardNumber = view.findViewById(R.id.etCardNumber);
        etExpiryDate = view.findViewById(R.id.etExpiryDate);
        etCvv = view.findViewById(R.id.etCvv);
        etCardHolderName = view.findViewById(R.id.etCardHolderName);
        cardNumberLayout = view.findViewById(R.id.cardNumberLayout);
        expiryDateLayout = view.findViewById(R.id.expiryDateLayout);
        cvvLayout = view.findViewById(R.id.cvvLayout);
        cardHolderNameLayout = view.findViewById(R.id.cardHolderNameLayout);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        // Setup RecyclerView
        rvSavedPaymentMethods.setLayoutManager(new LinearLayoutManager(requireContext()));
        paymentMethodsAdapter = new PaymentMethodsAdapter(new ArrayList<>(), null);
        rvSavedPaymentMethods.setAdapter(paymentMethodsAdapter);

        if (getArguments() != null) {
            selectedAddressId = getArguments().getString("selected_address_id");
            grandTotal = getArguments().getDouble("grand_total", 0.0);
            discount = getArguments().getDouble("discount", 0.0);
            deliveryFee = getArguments().getDouble("delivery_fee", 0.0);
            items = (List<Map<String, Object>>) getArguments().getSerializable("items");
            orderType = getArguments().getString("order_type", "Product");
            promoCode = getArguments().getString("promo_code");
            reorderPaymentMethod = getArguments().getString("reorder_payment_method");
            Log.d(TAG, "Received items: " + (items != null ? items.size() : "null"));
            Log.d(TAG, "Received order_type: " + orderType);
            Log.d(TAG, "Received promo_code: " + promoCode);
            Log.d(TAG, "Received reorder_payment_method: " + reorderPaymentMethod);
        } else {
            grandTotal = 0.0;
            discount = 0.0;
            deliveryFee = 0.0;
            orderType = "Product";
            Log.w(TAG, "No arguments received, falling back to Firestore for address");
        }

        // Set initial UI state before loading data
        creditCardFields.setVisibility(View.GONE);
        savedPaymentMethodsContainer.setVisibility(View.GONE);

        // Handle reorder payment method
        if (reorderPaymentMethod != null) {
            switch (reorderPaymentMethod) {
                case "Credit Card":
                    radioCreditCard.setChecked(true);
                    creditCardFields.setVisibility(View.VISIBLE);
                    savedPaymentMethodsContainer.setVisibility(View.GONE);
                    break;
                case "Paypal":
                    radioPaypal.setChecked(true);
                    creditCardFields.setVisibility(View.GONE);
                    savedPaymentMethodsContainer.setVisibility(View.GONE);
                    break;
                case "Cash on Delivery":
                    radioCashOnDelivery.setChecked(true);
                    creditCardFields.setVisibility(View.GONE);
                    savedPaymentMethodsContainer.setVisibility(View.GONE);
                    break;
                default:
                    // If reorderPaymentMethod is invalid, show saved methods if available
                    savedPaymentMethodsContainer.setVisibility(View.VISIBLE);
                    break;
            }
        } else {
            // No reorder payment method, show saved methods if available
            savedPaymentMethodsContainer.setVisibility(View.VISIBLE);
        }

        fetchAndSetDeliveryAddress();
        assignDeliveryMan();
        tvGrandTotal.setText(String.format("Grand Total: %s %.2f", CURRENCY, grandTotal));
        loadSavedPaymentMethods();

        configureCardNumberInput();
        configureExpiryDateInput();
        configureCvvInput();
        configureCardHolderNameInput();

        toolbar.setNavigationOnClickListener(v -> navigateToHome());

        // Swipe to refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Reset radio buttons and selections
            radioCreditCard.setChecked(false);
            radioPaypal.setChecked(false);
            radioCashOnDelivery.setChecked(false);
            selectedSavedPaymentMethod = null;
            paymentMethodsAdapter.setSelectedPosition(-1);
            creditCardFields.setVisibility(View.GONE);
            savedPaymentMethodsContainer.setVisibility(View.VISIBLE); // Show saved methods by default after refresh

            // Refresh data
            loadSavedPaymentMethods();
            fetchAndSetDeliveryAddress();
            assignDeliveryMan();
            swipeRefreshLayout.setRefreshing(false);
        });

        radioCreditCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                creditCardFields.setVisibility(View.VISIBLE);
                savedPaymentMethodsContainer.setVisibility(View.GONE);
                radioPaypal.setChecked(false);
                radioCashOnDelivery.setChecked(false);
                selectedSavedPaymentMethod = null;
                paymentMethodsAdapter.setSelectedPosition(-1);
            } else if (!radioPaypal.isChecked() && !radioCashOnDelivery.isChecked()) {
                savedPaymentMethodsContainer.setVisibility(View.VISIBLE);
                loadSavedPaymentMethods();
            }
        });

        radioPaypal.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                creditCardFields.setVisibility(View.GONE);
                savedPaymentMethodsContainer.setVisibility(View.GONE);
                radioCreditCard.setChecked(false);
                radioCashOnDelivery.setChecked(false);
                selectedSavedPaymentMethod = null;
                paymentMethodsAdapter.setSelectedPosition(-1);
            } else if (!radioCreditCard.isChecked() && !radioCashOnDelivery.isChecked()) {
                savedPaymentMethodsContainer.setVisibility(View.VISIBLE);
                loadSavedPaymentMethods();
            }
        });

        radioCashOnDelivery.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                creditCardFields.setVisibility(View.GONE);
                savedPaymentMethodsContainer.setVisibility(View.GONE);
                radioCreditCard.setChecked(false);
                radioPaypal.setChecked(false);
                selectedSavedPaymentMethod = null;
                paymentMethodsAdapter.setSelectedPosition(-1);
            } else if (!radioCreditCard.isChecked() && !radioPaypal.isChecked()) {
                savedPaymentMethodsContainer.setVisibility(View.VISIBLE);
                loadSavedPaymentMethods();
            }
        });

        paymentMethodsAdapter.setOnItemClickListener((method, position) -> {
            selectedSavedPaymentMethod = method;
            radioCreditCard.setChecked(false);
            radioPaypal.setChecked(false);
            radioCashOnDelivery.setChecked(false);
            creditCardFields.setVisibility(View.GONE);
            savedPaymentMethodsContainer.setVisibility(View.VISIBLE);
            paymentMethodsAdapter.setSelectedPosition(position);
            Toast.makeText(requireContext(), "Selected: " + getPaymentMethodDisplay(method), Toast.LENGTH_SHORT).show();
        });

        changeAddressButton.setOnClickListener(v -> {
            AddressDialogFragment dialog = new AddressDialogFragment();
            dialog.setOnAddressSelectedListener((addressId, address) -> {
                selectedAddressId = addressId;
                fetchAndSetDeliveryAddress();
                assignDeliveryMan();
            });
            dialog.show(getChildFragmentManager(), "AddressDialogFragment");
        });

        payNowButton.setOnClickListener(v -> {
            if (orderPlaced) {
                Toast.makeText(requireContext(), "Order already placed", Toast.LENGTH_SHORT).show();
                return;
            }
            payNowButton.setEnabled(false);
            payNowButton.setText("Processing...");
            validateAndProcessPayment();
        });

        hideBottomNavigation();
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
                if (input.length() > 16) input = input.substring(0, 16);

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    if (i > 0 && i % 4 == 0) formatted.append(" ");
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

                    cardNumberLayout.setError(null);
                    if (!input.isEmpty()) {
                        if (input.length() < 13) {
                            cardNumberLayout.setError("Card number too short");
                        } else if (!isValidLuhn(input)) {
                            cardNumberLayout.setError("Invalid card number");
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
                if (input.length() > 4) input = input.substring(0, 4);

                StringBuilder formatted = new StringBuilder();
                if (input.length() > 0) {
                    if (input.length() <= 2) {
                        formatted.append(input);
                    } else {
                        String month = input.substring(0, Math.min(2, input.length()));
                        try {
                            int monthInt = Integer.parseInt(month);
                            if (monthInt > 12) month = "12";
                            else if (monthInt < 1) month = "01";
                        } catch (NumberFormatException e) {
                            month = "01";
                        }
                        formatted.append(month);
                        formatted.append("/");
                        if (input.length() > 2) formatted.append(input.substring(2));
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

                    expiryDateLayout.setError(null);
                    if (input.length() == 4) {
                        try {
                            int month = Integer.parseInt(input.substring(0, 2));
                            int year = Integer.parseInt(input.substring(2));
                            int fullYear = 2000 + year;
                            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                            int currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;

                            if (month < 1 || month > 12) {
                                expiryDateLayout.setError("Invalid month");
                            } else if (fullYear < currentYear ||
                                    (fullYear == currentYear && month < currentMonth)) {
                                expiryDateLayout.setError("Card expired");
                            }
                        } catch (NumberFormatException e) {
                            expiryDateLayout.setError("Invalid date format");
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
                cvvLayout.setError(null);
                if (!input.isEmpty() && (input.length() < 3 || input.length() > 4)) {
                    cvvLayout.setError("CVV must be 3 or 4 digits");
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
                cardHolderNameLayout.setError(null);
                if (!input.isEmpty() && !input.matches("[a-zA-Z ]+")) {
                    cardHolderNameLayout.setError("Name must contain only letters and spaces");
                }
            }
        });
    }

    private boolean validateCardDetails(String cardNumber, String expiryDate, String cvv, String cardHolderName) {
        boolean isValid = true;

        String cleanCardNumber = cardNumber.replaceAll("[^0-9]", "");
        if (cleanCardNumber.length() < 13 || cleanCardNumber.length() > 16) {
            cardNumberLayout.setError("Card number must be 13-16 digits");
            isValid = false;
        } else if (!isValidLuhn(cleanCardNumber)) {
            cardNumberLayout.setError("Invalid card number");
            isValid = false;
        }

        if (!expiryDate.matches("\\d{2}/\\d{2}")) {
            expiryDateLayout.setError("Use MM/YY format");
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
                    expiryDateLayout.setError("Invalid month");
                    isValid = false;
                } else if (fullYear < currentYear ||
                        (fullYear == currentYear && month < currentMonth)) {
                    expiryDateLayout.setError("Card expired");
                    isValid = false;
                }
            } catch (Exception e) {
                expiryDateLayout.setError("Invalid date format");
                isValid = false;
            }
        }

        String cleanCvv = cvv.replaceAll("[^0-9]", "");
        if (cleanCvv.length() < 3 || cleanCvv.length() > 4) {
            cvvLayout.setError("CVV must be 3-4 digits");
            isValid = false;
        }

        if (cardHolderName.isEmpty()) {
            cardHolderNameLayout.setError("Cardholder name is required");
            isValid = false;
        } else if (!cardHolderName.matches("[a-zA-Z ]+")) {
            cardHolderNameLayout.setError("Name must contain only letters and spaces");
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
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private void validateAndProcessPayment() {
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "User is not authenticated");
            Toast.makeText(requireContext(), "Please log in to place an order", Toast.LENGTH_SHORT).show();
            resetPayNowButton();
            return;
        }

        String paymentMethod;
        if (selectedSavedPaymentMethod != null) {
            paymentMethod = (String) selectedSavedPaymentMethod.get("type");
        } else if (radioCreditCard.isChecked()) {
            paymentMethod = "Credit Card";
        } else if (radioPaypal.isChecked()) {
            paymentMethod = "Paypal";
        } else if (radioCashOnDelivery.isChecked()) {
            paymentMethod = "Cash on Delivery";
        } else {
            Toast.makeText(requireContext(), "Please select a payment method", Toast.LENGTH_SHORT).show();
            resetPayNowButton();
            return;
        }

        if ("Credit Card".equals(paymentMethod) && selectedSavedPaymentMethod == null) {
            String cardNumber = etCardNumber.getText().toString().trim();
            String expiryDate = etExpiryDate.getText().toString().trim();
            String cvv = etCvv.getText().toString().trim();
            String cardHolderName = etCardHolderName.getText().toString().trim();

            if (!validateCardDetails(cardNumber, expiryDate, cvv, cardHolderName)) {
                resetPayNowButton();
                return;
            }

            saveNewCard(cardNumber, expiryDate, cardHolderName);
        }

        String orderId = generateCustomOrderId();
        Toast.makeText(requireContext(), "Processing payment for Order " + orderId, Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            createOrder(orderId, paymentMethod);
        }, PAYMENT_SIMULATION_DELAY);
    }

    private void saveNewCard(String cardNumber, String expiryDate, String cardHolderName) {
        Map<String, Object> method = new HashMap<>();
        method.put("type", "Credit Card");
        method.put("createdAt", com.google.firebase.Timestamp.now());
        method.put("lastFour", cardNumber.replaceAll("[^0-9]", "").substring(cardNumber.length() - 4));
        method.put("expiryDate", expiryDate);
        method.put("cardHolderName", cardHolderName);
        method.put("token", "simulated_card_" + System.currentTimeMillis());

        String userId = auth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("payment_methods")
                .add(method)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "New card saved with ID: " + documentReference.getId());
                    loadSavedPaymentMethods();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save new card: " + e.getMessage());
                });
    }

    private void loadSavedPaymentMethods() {
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
                    paymentMethodsAdapter.updateMethods(methods);
                    // Only show saved methods if no radio button is checked and methods exist
                    savedPaymentMethodsContainer.setVisibility(
                            methods.isEmpty() || radioCreditCard.isChecked() || radioPaypal.isChecked() || radioCashOnDelivery.isChecked()
                                    ? View.GONE : View.VISIBLE);
                    Log.d(TAG, "Saved payment methods loaded: " + methods.size());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load saved payment methods: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to load payment methods", Toast.LENGTH_SHORT).show();
                    savedPaymentMethodsContainer.setVisibility(View.GONE);
                });
    }

    private String getPaymentMethodDisplay(Map<String, Object> method) {
        String type = (String) method.get("type");
        if ("Credit Card".equals(type)) {
            String lastFour = (String) method.get("lastFour");
            String cardHolderName = (String) method.get("cardHolderName");
            return String.format("Card ending in %s (%s)", lastFour, cardHolderName != null ? cardHolderName : "Unknown");
        } else if ("Paypal".equals(type)) {
            String email = (String) method.get("email");
            return String.format("PayPal (%s)", email != null ? email : "Unknown");
        }
        return type != null ? type : "Unknown";
    }

    private void createOrder(String orderId, String paymentMethod) {
        String userId = auth.getCurrentUser().getUid();

        if ("LabTest".equals(orderType) && items != null && !items.isEmpty()) {
            db.collection("orders")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("orderType", "LabTest")
                    .whereEqualTo("items", items)
                    .whereGreaterThan("createdAt", com.google.firebase.Timestamp.now().toDate().getTime() - 60000)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            Toast.makeText(requireContext(), "Order already placed recently", Toast.LENGTH_SHORT).show();
                            resetPayNowButton();
                            return;
                        }
                        proceedWithOrderCreation(orderId, userId, paymentMethod);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to check for duplicates, proceeding anyway: " + e.getMessage());
                        proceedWithOrderCreation(orderId, userId, paymentMethod);
                    });
        } else {
            proceedWithOrderCreation(orderId, userId, paymentMethod);
        }
    }

    private void proceedWithOrderCreation(String orderId, String userId, String paymentMethod) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setAddressId(selectedAddressId != null ? selectedAddressId : "");
        order.setPaymentMethod(paymentMethod);
        order.setGrandTotal(grandTotal);
        order.setStatus("Order Placed");
        order.setCreatedAt(com.google.firebase.Timestamp.now());
        order.setCurrency(CURRENCY);
        order.setReorder(items != null && !items.isEmpty() && "Product".equals(orderType));
        order.setOrderType(orderType);
        order.setDeliveryBoyName(assignedDeliveryManId != null ? assignedDeliveryManId : "");

        if (items != null && !items.isEmpty()) {
            order.setFirstProductId((String) items.get(0).get("productId"));
        }

        Map<String, Object> totalBreakdown = new HashMap<>();
        totalBreakdown.put("itemTotal", grandTotal - deliveryFee + discount);
        totalBreakdown.put("deliveryFee", deliveryFee);
        totalBreakdown.put("discount", discount);
        totalBreakdown.put("grandTotal", grandTotal);
        if ("LabTest".equals(orderType) && promoCode != null) {
            totalBreakdown.put("promoCode", promoCode);
        }
        order.setTotalBreakdown(totalBreakdown);

        if ("LabTest".equals(orderType)) {
            order.setItems(items);
            saveOrderToFirestore(order, orderId);
            addNotification(userId, "Lab Test Booked", "Your lab test has been booked successfully.", "LabTestBooked");
        } else if (items != null && !items.isEmpty()) {
            order.setItems(items);
            saveOrderToFirestore(order, orderId);
            addNotification(userId, "Order Placed", "Your reorder has been placed successfully.", "OrderPlaced");
        } else {
            db.collection("cart")
                    .document(userId)
                    .collection("items")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<Map<String, Object>> orderItems = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Map<String, Object> orderItem = new HashMap<>();
                            orderItem.put("productId", doc.getString("productId"));
                            orderItem.put("name", doc.getString("productName"));
                            orderItem.put("quantity", doc.getLong("quantity"));
                            orderItem.put("price", doc.getDouble("discountedPrice") != null && doc.getDouble("discountedPrice") != 0 ? doc.getDouble("discountedPrice") : doc.getDouble("originalPrice"));
                            orderItem.put("total", doc.getDouble("total"));
                            orderItems.add(orderItem);
                        }
                        order.setItems(orderItems);
                        if (!orderItems.isEmpty()) {
                            order.setFirstProductId((String) orderItems.get(0).get("productId"));
                        }
                        saveOrderToFirestore(order, orderId);
                        addNotification(userId, "Order Placed", "Your order has been placed successfully.", "OrderPlaced");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch cart items: " + e.getMessage());
                        Toast.makeText(requireContext(), "Failed to fetch cart items", Toast.LENGTH_SHORT).show();
                        resetPayNowButton();
                    });
        }
    }

    private void saveOrderToFirestore(Order order, String orderId) {
        db.collection("orders")
                .document(orderId)
                .set(order)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Order created successfully with ID: " + orderId + " (isReorder: " + order.isReorder() + ", orderType: " + order.getOrderType() + ")");
                    orderPlaced = true;
                    if ("Product".equals(order.getOrderType()) && !order.isReorder()) {
                        clearCartItems();
                    }
                    createTrackingEntry(orderId, "Order Placed", "Your " + (order.getOrderType().equals("LabTest") ? "lab test" : "order") + " has been placed successfully.");
                    showOrderSuccessDialog(orderId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create order: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to create order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetPayNowButton();
                });
    }

    private void showOrderSuccessDialog(String orderId) {
        OrderSuccessDialog dialog = new OrderSuccessDialog();
        dialog.show(getChildFragmentManager(), "OrderSuccessDialog");
    }

    private void resetPayNowButton() {
        payNowButton.setText("Pay Now");
        payNowButton.setEnabled(true);
    }

    private void navigateToHome() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
    }

    private void createTrackingEntry(String orderId, String status, String details) {
        Map<String, Object> tracking = new HashMap<>();
        tracking.put("status", status);
        tracking.put("updatedAt", com.google.firebase.Timestamp.now());
        tracking.put("details", details);

        db.collection("orders")
                .document(orderId)
                .collection("tracking")
                .add(tracking)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Tracking entry created with ID: " + documentReference.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create tracking entry: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to initialize tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void addNotification(String userId, String title, String message, String type) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", type);
        notification.put("createdAt", com.google.firebase.Timestamp.now());
        notification.put("isRead", false);

        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Notification added with ID: " + documentReference.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add notification: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to send notification: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String generateCustomOrderId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault());
        String dateTime = sdf.format(new Date());
        String userPrefix = auth.getCurrentUser().getUid().substring(0, 4);
        int randomNum = (int) (Math.random() * 1000);
        return String.format("ORDER-SPH-%s-%s-%03d", dateTime, userPrefix, randomNum);
    }

    private void fetchAndSetDeliveryAddress() {
        String userId = auth.getCurrentUser().getUid();
        if (selectedAddressId != null) {
            db.collection("users")
                    .document(userId)
                    .collection("addresses")
                    .document(selectedAddressId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            setAddressFields(doc);
                        } else {
                            fetchDefaultAddress();
                        }
                    })
                    .addOnFailureListener(e -> fetchDefaultAddress());
        } else {
            fetchDefaultAddress();
        }
    }

    private void fetchDefaultAddress() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("lastSelectedAddressId")) {
                        selectedAddressId = documentSnapshot.getString("lastSelectedAddressId");
                        fetchAndSetDeliveryAddress();
                    } else {
                        db.collection("users")
                                .document(userId)
                                .collection("addresses")
                                .whereEqualTo("isDefault", true)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(queryDocumentSnapshots -> {
                                    if (!queryDocumentSnapshots.isEmpty()) {
                                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                                        selectedAddressId = doc.getId();
                                        setAddressFields(doc);
                                    } else {
                                        db.collection("users")
                                                .document(userId)
                                                .collection("addresses")
                                                .limit(1)
                                                .get()
                                                .addOnSuccessListener(allAddresses -> {
                                                    if (!allAddresses.isEmpty()) {
                                                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) allAddresses.getDocuments().get(0);
                                                        selectedAddressId = doc.getId();
                                                        setAddressFields(doc);
                                                    } else {
                                                        addressName.setText("No Address");
                                                        addressDetails.setText("Address not set");
                                                        addressEmail.setText("Email: Not provided");
                                                        addressMobile.setText("Mobile: Not provided");
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Failed to fetch addresses: " + e.getMessage());
                                                    addressName.setText("No Address");
                                                    addressDetails.setText("Address not set");
                                                    addressEmail.setText("Email: Not provided");
                                                    addressMobile.setText("Mobile: Not provided");
                                                });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to fetch default address: " + e.getMessage());
                                    addressName.setText("No Address");
                                    addressDetails.setText("Address not set");
                                    addressEmail.setText("Email: Not provided");
                                    addressMobile.setText("Mobile: Not provided");
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user data: " + e.getMessage());
                    addressName.setText("No Address");
                    addressDetails.setText("Address not set");
                    addressEmail.setText("Email: Not provided");
                    addressMobile.setText("Mobile: Not provided");
                });
    }

    private void setAddressFields(DocumentSnapshot doc) {
        String name = doc.getString("fullName");
        String streetAddress = doc.getString("streetAddress");
        String city = doc.getString("city");
        String state = doc.getString("state");
        String postalCode = doc.getString("postalCode");
        String country = doc.getString("country");
        String email = doc.getString("email");
        String mobile = doc.getString("phoneNumber");

        String fullAddress = String.format("%s, %s, %s, %s, %s",
                streetAddress != null ? streetAddress : "",
                city != null ? city : "",
                state != null ? state : "",
                postalCode != null ? postalCode : "",
                country != null ? country : "").replace(", ,", ",").trim();
        if (fullAddress.endsWith(",")) {
            fullAddress = fullAddress.substring(0, fullAddress.length() - 1);
        }

        addressName.setText(name != null ? name : "Unknown");
        addressDetails.setText(fullAddress);
        addressEmail.setText("Email: " + (email != null ? email : "Not provided"));
        addressMobile.setText("Mobile: " + (mobile != null ? mobile : "Not provided"));
    }

    private void assignDeliveryMan() {
        if (selectedAddressId == null || "LabTest".equals(orderType)) {
            assignedDeliveryManId = null;
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .document(selectedAddressId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String postalCode = doc.getString("postalCode");
                        if (postalCode != null) {
                            db.collection("delivery_men")
                                    .whereArrayContains("assignedPostalCodes", postalCode)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        if (!queryDocumentSnapshots.isEmpty()) {
                                            QueryDocumentSnapshot deliveryManDoc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                                            assignedDeliveryManId = deliveryManDoc.getId();
                                            Log.d(TAG, "Assigned delivery man: " + assignedDeliveryManId);
                                        } else {
                                            assignedDeliveryManId = null;
                                            Log.w(TAG, "No delivery man available for postal code: " + postalCode);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        assignedDeliveryManId = null;
                                        Log.e(TAG, "Failed to assign delivery man: " + e.getMessage());
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    assignedDeliveryManId = null;
                    Log.e(TAG, "Failed to fetch address for delivery man: " + e.getMessage());
                });
    }

    private void clearCartItems() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("cart")
                .document(userId)
                .collection("items")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }
                    Log.d(TAG, "Cart items cleared from Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to clear cart items: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to clear cart: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void hideBottomNavigation() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).hideBottomNav();
            Log.d(TAG, "hideBottomNav() called in CheckoutFragment");
        } else {
            Log.e(TAG, "Activity is not MainActivity, cannot hide bottom nav");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        hideBottomNavigation();
    }

    @Override
    public void onResume() {
        super.onResume();
        hideBottomNavigation();
        loadSavedPaymentMethods();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showBottomNav();
        }
    }
}