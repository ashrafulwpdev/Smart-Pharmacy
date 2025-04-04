package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
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

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;

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

    private RadioButton radioCreditCard, radioPaypal, radioCashOnDelivery;
    private LinearLayout creditCardFields;
    private MaterialButton payNowButton;
    private TextView addressName, addressDetails, addressEmail, addressMobile, tvGrandTotal;
    private Button changeAddressButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String selectedAddressId;
    private double grandTotal;
    private double discount;
    private double deliveryFee;
    private List<Map<String, Object>> reorderItems;
    private String reorderPaymentMethod;
    private String assignedDeliveryManId;

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
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        radioCreditCard = view.findViewById(R.id.radioCreditCard);
        radioPaypal = view.findViewById(R.id.radioPaypal);
        radioCashOnDelivery = view.findViewById(R.id.radioCashOnDelivery);
        creditCardFields = view.findViewById(R.id.creditCardFields);
        payNowButton = view.findViewById(R.id.payNowButton);
        addressName = view.findViewById(R.id.addressName);
        addressDetails = view.findViewById(R.id.addressDetails);
        addressEmail = view.findViewById(R.id.addressEmail);
        addressMobile = view.findViewById(R.id.addressMobile);
        tvGrandTotal = view.findViewById(R.id.tvGrandTotal);
        changeAddressButton = view.findViewById(R.id.changeAddressButton);

        if (getArguments() != null) {
            selectedAddressId = getArguments().getString("selected_address_id");
            grandTotal = getArguments().getDouble("grand_total", 0.0);
            discount = getArguments().getDouble("discount", 0.0);
            deliveryFee = getArguments().getDouble("delivery_fee", 0.0);
            reorderItems = (List<Map<String, Object>>) getArguments().getSerializable("reorder_items");
            reorderPaymentMethod = getArguments().getString("reorder_payment_method");
            Log.d(TAG, "Received reorder_items: " + (reorderItems != null ? reorderItems.size() : "null"));
            Log.d(TAG, "Received reorder_payment_method: " + reorderPaymentMethod);
        } else {
            grandTotal = 0.0;
            discount = 0.0;
            deliveryFee = 0.0;
            Log.w(TAG, "No arguments received, falling back to Firestore for address");
        }

        fetchAndSetDeliveryAddress();
        assignDeliveryMan();
        tvGrandTotal.setText(String.format("Grand Total: %s %.2f", CURRENCY, grandTotal));

        if (reorderPaymentMethod != null) {
            switch (reorderPaymentMethod) {
                case "Credit Card":
                    radioCreditCard.setChecked(true);
                    creditCardFields.setVisibility(View.VISIBLE);
                    break;
                case "Paypal":
                    radioPaypal.setChecked(true);
                    break;
                case "Cash on Delivery":
                    radioCashOnDelivery.setChecked(true);
                    break;
            }
        }

        radioCreditCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            creditCardFields.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                radioPaypal.setChecked(false);
                radioCashOnDelivery.setChecked(false);
            }
        });

        radioPaypal.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                creditCardFields.setVisibility(View.GONE);
                radioCreditCard.setChecked(false);
                radioCashOnDelivery.setChecked(false);
            }
        });

        radioCashOnDelivery.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                creditCardFields.setVisibility(View.GONE);
                radioCreditCard.setChecked(false);
                radioPaypal.setChecked(false);
            }
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
            if (!radioCreditCard.isChecked() && !radioPaypal.isChecked() && !radioCashOnDelivery.isChecked()) {
                Toast.makeText(requireContext(), "Please select a payment method", Toast.LENGTH_SHORT).show();
                return;
            }
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "User is not authenticated");
                Toast.makeText(requireContext(), "Please log in to place an order", Toast.LENGTH_SHORT).show();
                return;
            }
            String orderId = generateCustomOrderId();
            Toast.makeText(requireContext(), "Processing payment for Order " + orderId, Toast.LENGTH_SHORT).show();
            createOrder(orderId);
        });

        hideBottomNavigation();
    }

    private void createOrder(String orderId) {
        String userId = auth.getCurrentUser().getUid();
        String paymentMethod = radioCreditCard.isChecked() ? "Credit Card" :
                radioPaypal.isChecked() ? "Paypal" : "Cash on Delivery";

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("userId", userId);
        order.put("addressId", selectedAddressId != null ? selectedAddressId : "");
        order.put("paymentMethod", paymentMethod);
        order.put("grandTotal", grandTotal);
        order.put("status", "Order Placed");
        order.put("createdAt", com.google.firebase.Timestamp.now());
        order.put("currency", CURRENCY);
        order.put("isReorder", reorderItems != null && !reorderItems.isEmpty());
        order.put("orderType", "Product");
        order.put("deliveryManId", assignedDeliveryManId != null ? assignedDeliveryManId : "");

        Map<String, Object> totalBreakdown = new HashMap<>();
        totalBreakdown.put("itemTotal", grandTotal - deliveryFee + discount);
        totalBreakdown.put("deliveryFee", deliveryFee);
        totalBreakdown.put("discount", discount);
        totalBreakdown.put("grandTotal", grandTotal);
        order.put("totalBreakdown", totalBreakdown);

        if (reorderItems != null && !reorderItems.isEmpty()) {
            order.put("items", reorderItems);
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
                        order.put("items", orderItems);
                        saveOrderToFirestore(order, orderId);
                        addNotification(userId, "Order Placed", "Your order has been placed successfully.", "OrderPlaced");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch cart items: " + e.getMessage());
                        Toast.makeText(requireContext(), "Failed to fetch cart items", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void saveOrderToFirestore(Map<String, Object> order, String orderId) {
        db.collection("orders")
                .document(orderId)
                .set(order)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Order created successfully with ID: " + orderId + " (isReorder: " + order.get("isReorder") + ")");
                    if (reorderItems == null) {
                        clearCartItems();
                    }
                    createTrackingEntry(orderId, "Order Placed", "Your order has been placed successfully.");
                    OrderSuccessDialog dialog = new OrderSuccessDialog();
                    Bundle args = new Bundle();
                    args.putString("orderId", orderId);
                    dialog.setArguments(args);
                    dialog.show(getChildFragmentManager(), "OrderSuccessDialog");

                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new InprogressOrdersFragment())
                            .addToBackStack(null)
                            .commit();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create order: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to create order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
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
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String date = sdf.format(new Date());
        int randomNum = (int) (Math.random() * 10000);
        return String.format("ORDER-SPH-%s-%04d", date, randomNum);
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
                                                });
                                    }
                                });
                    }
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
        if (selectedAddressId == null) {
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
    }
}