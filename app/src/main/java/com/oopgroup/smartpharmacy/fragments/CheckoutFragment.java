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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.CartItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckoutFragment extends Fragment {

    private static final String TAG = "CheckoutFragment";
    private static final String CURRENCY = "RM"; // Currency symbol

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

    public CheckoutFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checkout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Toolbar setup
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Initialize views
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

        // Get arguments
        if (getArguments() != null) {
            selectedAddressId = getArguments().getString("selected_address_id");
            grandTotal = getArguments().getDouble("grand_total", 0.0);
            discount = getArguments().getDouble("discount", 0.0);
            deliveryFee = getArguments().getDouble("delivery_fee", 0.0);
            Log.d(TAG, "Received selected_address_id: " + selectedAddressId);
            Log.d(TAG, "Received grand_total: " + grandTotal);
            Log.d(TAG, "Received discount: " + discount);
            Log.d(TAG, "Received delivery_fee: " + deliveryFee);
        } else {
            grandTotal = 0.0;
            discount = 0.0;
            deliveryFee = 0.0;
            Log.w(TAG, "No arguments received, falling back to Firestore for address");
        }

        // Fetch and set address
        fetchAndSetDeliveryAddress();
        tvGrandTotal.setText(String.format("Grand Total: %s %.2f", CURRENCY, grandTotal));

        // Set up radio button group behavior
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

        // Change address button
        changeAddressButton.setOnClickListener(v -> {
            AddressDialogFragment dialog = new AddressDialogFragment();
            dialog.setOnAddressSelectedListener((addressId, address) -> {
                selectedAddressId = addressId;
                fetchAndSetDeliveryAddress();
            });
            dialog.show(getChildFragmentManager(), "AddressDialogFragment");
        });

        // Pay Now button click
        payNowButton.setOnClickListener(v -> {
            if (!radioCreditCard.isChecked() && !radioPaypal.isChecked() && !radioCashOnDelivery.isChecked()) {
                Toast.makeText(requireContext(), "Please select a payment method", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(), "Payment processing...", Toast.LENGTH_SHORT).show();

            // Fetch cart items and create the order
            fetchCartItemsAndCreateOrder();
        });

        hideBottomNavigation();
    }

    private void fetchCartItemsAndCreateOrder() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("cart")
                .document(userId)
                .collection("items")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<CartItem> cartItems = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        CartItem cartItem = doc.toObject(CartItem.class);
                        cartItems.add(cartItem);
                    }

                    // Validate the grand total
                    double calculatedTotal = calculateOrderTotal(cartItems);
                    if (Math.abs(calculatedTotal - grandTotal) > 0.01) {
                        Log.w(TAG, "Grand total mismatch! Calculated: " + calculatedTotal + ", Received: " + grandTotal);
                        Toast.makeText(requireContext(), "Total amount mismatch, please try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createOrder(cartItems);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch cart items for order: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to fetch cart items: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private double calculateOrderTotal(List<CartItem> cartItems) {
        double itemTotal = 0.0;
        for (CartItem item : cartItems) {
            itemTotal += item.getTotal();
        }
        // Add delivery fee and subtract discount
        double total = itemTotal + deliveryFee - discount;
        return total > 0 ? total : 0; // Ensure total is not negative
    }

    private void createOrder(List<CartItem> cartItems) {
        String userId = auth.getCurrentUser().getUid();
        String paymentMethod = radioCreditCard.isChecked() ? "Credit Card" :
                radioPaypal.isChecked() ? "Paypal" : "Cash on Delivery";

        // Calculate item total for breakdown
        double itemTotal = 0.0;
        for (CartItem item : cartItems) {
            itemTotal += item.getTotal();
        }

        // Prepare the order data
        Map<String, Object> order = new HashMap<>();
        order.put("userId", userId);
        order.put("addressId", selectedAddressId);
        order.put("paymentMethod", paymentMethod);
        order.put("grandTotal", grandTotal);
        order.put("status", "Pending");
        order.put("createdAt", com.google.firebase.Timestamp.now());
        order.put("currency", CURRENCY); // Add currency symbol to the order

        // Add total breakdown
        Map<String, Object> totalBreakdown = new HashMap<>();
        totalBreakdown.put("itemTotal", itemTotal);
        totalBreakdown.put("deliveryFee", deliveryFee);
        totalBreakdown.put("discount", discount);
        totalBreakdown.put("grandTotal", grandTotal);
        order.put("totalBreakdown", totalBreakdown);

        // Convert cart items to a list of maps for Firestore
        List<Map<String, Object>> orderItems = new ArrayList<>();
        for (CartItem item : cartItems) {
            Map<String, Object> orderItem = new HashMap<>();
            orderItem.put("productId", item.getProductId());
            orderItem.put("name", item.getProductName());
            orderItem.put("quantity", item.getQuantity());
            orderItem.put("price", item.getDiscountedPrice() != 0 ? item.getDiscountedPrice() : item.getOriginalPrice());
            orderItem.put("total", item.getTotal());
            orderItems.add(orderItem);
        }
        order.put("items", orderItems);

        // Save the order to Firestore
        db.collection("orders")
                .add(order)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Order created successfully with ID: " + documentReference.getId());
                    // Clear the cart after the order is created
                    clearCartItems();
                    // Show the success dialog
                    OrderSuccessDialog dialog = new OrderSuccessDialog();
                    dialog.show(getChildFragmentManager(), "OrderSuccessDialog");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create order: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to create order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
        // First, try to load the last selected address
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("lastSelectedAddressId")) {
                        selectedAddressId = documentSnapshot.getString("lastSelectedAddressId");
                        fetchAndSetDeliveryAddress();
                    } else {
                        // Fallback to default address
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
                                        // Fallback to first available address
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
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to fetch default address: " + e.getMessage());
                                    addressName.setText("Error");
                                    addressDetails.setText("Error loading address");
                                    addressEmail.setText("Email: Error");
                                    addressMobile.setText("Mobile: Error");
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user document: " + e.getMessage());
                    fetchDefaultAddressFallback();
                });
    }

    private void fetchDefaultAddressFallback() {
        String userId = auth.getCurrentUser().getUid();
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
                        addressName.setText("Error");
                        addressDetails.setText("Error loading address");
                        addressEmail.setText("Email: Error");
                        addressMobile.setText("Mobile: Error");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch default address (fallback): " + e.getMessage());
                    addressName.setText("Error");
                    addressDetails.setText("Error loading address");
                    addressEmail.setText("Email: Error");
                    addressMobile.setText("Mobile: Error");
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