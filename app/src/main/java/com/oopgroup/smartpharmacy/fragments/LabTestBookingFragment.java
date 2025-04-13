package com.oopgroup.smartpharmacy.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.EditProfileActivity;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.fragments.AddressDialogFragment.OnAddressSelectedListener;
import com.oopgroup.smartpharmacy.models.Coupon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LabTestBookingFragment extends DialogFragment {

    private static final String ARG_TEST_NAME = "test_name";
    private static final String ARG_TEST_PROVIDER = "test_provider";
    private static final String ARG_TEST_PRICE = "test_price";
    private static final String CURRENCY = "RM";

    private TextView selectedTestNameTextView, selectedTestProviderTextView, selectedTestPriceTextView;
    private TextView patientNameTextView, locationAddressTextView;
    private EditText promoCodeEditText;
    private ImageView closeButton, removeTestButton, editPatientButton, editLocationButton;
    private TextView addMoreTestButton, applyPromoButton;
    private Button confirmBookNowButton;
    private TextView appliedDiscountTextView;
    private ImageView patientImageView;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String selectedAddressId;
    private double originalTestPrice;
    private double discountedTestPrice;
    private String appliedPromoCode;
    private String profileImageUrl;

    private ActivityResultLauncher<Intent> editProfileLauncher;

    public static LabTestBookingFragment newInstance(String testName, String testProvider, double testPrice) {
        LabTestBookingFragment fragment = new LabTestBookingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TEST_NAME, testName);
        args.putString(ARG_TEST_PROVIDER, testProvider);
        args.putDouble(ARG_TEST_PRICE, testPrice);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialog);

        // Initialize the ActivityResultLauncher
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        String updatedFullName = data.getStringExtra("fullName");
                        String updatedImageUrl = data.getStringExtra("imageUrl");

                        if (updatedFullName != null) {
                            patientNameTextView.setText(updatedFullName);
                        }
                        if (updatedImageUrl != null && !updatedImageUrl.isEmpty()) {
                            profileImageUrl = updatedImageUrl;
                            Glide.with(requireContext())
                                    .load(profileImageUrl)
                                    .apply(new RequestOptions()
                                            .circleCrop()
                                            .placeholder(R.drawable.default_profile)
                                            .error(R.drawable.default_profile)
                                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC))
                                    .into(patientImageView);
                        } else {
                            patientImageView.setImageResource(R.drawable.default_profile);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lab_test_booking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        selectedTestNameTextView = view.findViewById(R.id.selectedTestNameTextView);
        selectedTestProviderTextView = view.findViewById(R.id.selectedTestProviderTextView);
        selectedTestPriceTextView = view.findViewById(R.id.selectedTestPriceTextView);
        patientNameTextView = view.findViewById(R.id.patientNameTextView);
        locationAddressTextView = view.findViewById(R.id.locationAddressTextView);
        promoCodeEditText = view.findViewById(R.id.promoCodeEditText);
        closeButton = view.findViewById(R.id.closeButton);
        removeTestButton = view.findViewById(R.id.removeTestButton);
        editPatientButton = view.findViewById(R.id.editPatientButton);
        editLocationButton = view.findViewById(R.id.editLocationButton);
        addMoreTestButton = view.findViewById(R.id.addMoreTestButton);
        applyPromoButton = view.findViewById(R.id.applyPromoButton);
        confirmBookNowButton = view.findViewById(R.id.confirmBookNowButton);
        appliedDiscountTextView = view.findViewById(R.id.appliedDiscountTextView);
        patientImageView = view.findViewById(R.id.patientImageView);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Validate authentication
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to continue", Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }

        // Get arguments and initialize prices
        Bundle args = getArguments();
        if (args != null) {
            String testName = args.getString(ARG_TEST_NAME);
            String testProvider = args.getString(ARG_TEST_PROVIDER);
            originalTestPrice = args.getDouble(ARG_TEST_PRICE);
            discountedTestPrice = originalTestPrice;

            selectedTestNameTextView.setText(testName);
            selectedTestProviderTextView.setText("By " + testProvider);
            selectedTestPriceTextView.setText(CURRENCY + " " + String.format("%.2f", originalTestPrice));
        } else {
            Toast.makeText(requireContext(), "Lab test details not found", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        // Fetch user data and default address
        fetchUserData();
        fetchDefaultAddress();

        // Set up click listeners
        closeButton.setOnClickListener(v -> dismiss());

        removeTestButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Remove test functionality to be implemented", Toast.LENGTH_SHORT).show();
        });

        addMoreTestButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Add more test functionality to be implemented", Toast.LENGTH_SHORT).show();
        });

        editPatientButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            intent.putExtra("fullName", patientNameTextView.getText().toString());
            intent.putExtra("imageUrl", profileImageUrl);
            editProfileLauncher.launch(intent);
        });

        editLocationButton.setOnClickListener(v -> {
            AddressDialogFragment addressDialog = new AddressDialogFragment();
            addressDialog.setOnAddressSelectedListener((addressId, address) -> {
                selectedAddressId = addressId;
                locationAddressTextView.setText(address);
            });
            addressDialog.show(requireActivity().getSupportFragmentManager(), "AddressDialogFragment");
        });

        applyPromoButton.setOnClickListener(v -> {
            String promoCode = promoCodeEditText.getText().toString().trim();
            if (promoCode.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a promo code", Toast.LENGTH_SHORT).show();
            } else {
                applyPromoCode(promoCode);
            }
        });

        confirmBookNowButton.setOnClickListener(v -> confirmBooking());
    }

    private void fetchUserData() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        profileImageUrl = documentSnapshot.getString("imageUrl");

                        Log.d("LabTestBookingFragment", "Full Name: " + fullName);
                        Log.d("LabTestBookingFragment", "Profile Image URL: " + profileImageUrl);

                        patientNameTextView.setText(fullName != null ? fullName : "Unknown User");

                        Glide.with(requireContext())
                                .load(profileImageUrl)
                                .apply(new RequestOptions()
                                        .circleCrop()
                                        .placeholder(R.drawable.default_profile)
                                        .error(R.drawable.default_profile)
                                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC))
                                .into(patientImageView);
                    } else {
                        Log.e("LabTestBookingFragment", "User document does not exist");
                        Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LabTestBookingFragment", "Failed to load user data: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "Failed to load user data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchDefaultAddress() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .whereEqualTo("isDefault", true)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String streetAddress = doc.getString("streetAddress");
                            String city = doc.getString("city");
                            String state = doc.getString("state");
                            String postalCode = doc.getString("postalCode");
                            String country = doc.getString("country");
                            String landmark = doc.getString("landmark");

                            selectedAddressId = doc.getId();
                            String address = String.format("%s, %s, %s, %s, %s",
                                    streetAddress, city, state, postalCode, country);
                            if (landmark != null && !landmark.isEmpty()) {
                                address += "\nLandmark: " + landmark;
                            }
                            locationAddressTextView.setText(address);
                        }
                    } else {
                        locationAddressTextView.setText("No default address set. Please select an address.");
                        Toast.makeText(requireContext(), "Please set a default address", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LabTestBookingFragment", "Failed to load address: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "Failed to load address: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void applyPromoCode(String promoCode) {
        Log.d("LabTestBookingFragment", "Applying promo code: " + promoCode);
        db.collection("coupons")
                .whereEqualTo("code", promoCode)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Coupon coupon = Coupon.fromMap(doc.getData(), doc.getId());
                            Log.d("LabTestBookingFragment", "Coupon found: " + coupon.getId() + ", Discount: " + coupon.getDiscount() + ", isActive: " + coupon.isActive());

                            if (coupon.getDiscount() > 0) {
                                // Apply the discount (as a percentage)
                                appliedPromoCode = promoCode;
                                double discountPercentage = coupon.getDiscount() / 100.0;
                                discountedTestPrice = originalTestPrice * (1 - discountPercentage);

                                // Update the price display
                                selectedTestPriceTextView.setText(
                                        String.format("Original: %s %.2f\nDiscounted: %s %.2f", CURRENCY, originalTestPrice, CURRENCY, discountedTestPrice)
                                );

                                // Show the applied discount
                                appliedDiscountTextView.setText("Discount Applied: " + coupon.getDiscount() + "%");
                                appliedDiscountTextView.setVisibility(View.VISIBLE);

                                Toast.makeText(requireContext(), "Promo code applied! Discount: " + coupon.getDiscount() + "%", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.d("LabTestBookingFragment", "Invalid discount value: " + coupon.getDiscount());
                                resetPromoCodeUI();
                                Toast.makeText(requireContext(), "Invalid promo code: No discount available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Log.d("LabTestBookingFragment", "No active coupon found for code: " + promoCode);
                        resetPromoCodeUI();
                        Toast.makeText(requireContext(), "Invalid or inactive promo code", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LabTestBookingFragment", "Failed to apply promo code: " + e.getMessage(), e);
                    resetPromoCodeUI();
                    Toast.makeText(requireContext(), "Failed to apply promo code: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void resetPromoCodeUI() {
        discountedTestPrice = originalTestPrice;
        appliedPromoCode = null;
        selectedTestPriceTextView.setText(CURRENCY + " " + String.format("%.2f", originalTestPrice));
        appliedDiscountTextView.setVisibility(View.GONE);
        promoCodeEditText.setText("");
    }

    private void confirmBooking() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to continue", Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedAddressId == null) {
            Toast.makeText(requireContext(), "Please select a pickup address", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle args = getArguments();
        if (args == null) {
            Toast.makeText(requireContext(), "Lab test details not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String testName = args.getString(ARG_TEST_NAME);
        String testProvider = args.getString(ARG_TEST_PROVIDER);

        Bundle checkoutArgs = new Bundle();
        checkoutArgs.putString("selected_address_id", selectedAddressId);
        checkoutArgs.putDouble("grand_total", discountedTestPrice);
        checkoutArgs.putDouble("discount", originalTestPrice - discountedTestPrice);
        checkoutArgs.putDouble("delivery_fee", 0.0);
        checkoutArgs.putString("order_type", "LabTest");

        Map<String, Object> labTestItem = new HashMap<>();
        labTestItem.put("name", testName);
        labTestItem.put("testProvider", testProvider);
        labTestItem.put("price", discountedTestPrice);
        labTestItem.put("quantity", 1);
        labTestItem.put("total", discountedTestPrice);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(labTestItem);
        checkoutArgs.putSerializable("items", (ArrayList<Map<String, Object>>) items);

        if (appliedPromoCode != null) {
            checkoutArgs.putString("promo_code", appliedPromoCode);
        }

        CheckoutFragment checkoutFragment = new CheckoutFragment();
        checkoutFragment.setArguments(checkoutArgs);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, checkoutFragment)
                .addToBackStack(null)
                .commit();

        dismiss();
    }
}