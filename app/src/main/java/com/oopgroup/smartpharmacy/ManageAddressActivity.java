package com.oopgroup.smartpharmacy;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageAddressActivity extends AppCompatActivity {

    private TextView titleTextView;
    private EditText fullNameEditText, phoneNumberEditText, emailEditText, streetAddressEditText, cityEditText, stateEditText, postalCodeEditText, landmarkEditText;
    private Spinner countrySpinner;
    private RadioGroup addressTypeRadioGroup;
    private RadioButton radioHome, radioWork;
    private CheckBox defaultAddressCheckBox;
    private Button saveButton, deleteButton;
    private ImageView backButton;
    private LinearLayout addressListLayout;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String addressId; // For editing/deleting an existing address
    private boolean isEditMode = false;
    private List<Map<String, Object>> addresses; // List to store address data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_address);

        // Initialize views
        titleTextView = findViewById(R.id.titleTextView);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        emailEditText = findViewById(R.id.emailEditText);
        streetAddressEditText = findViewById(R.id.streetAddressEditText);
        cityEditText = findViewById(R.id.cityEditText);
        stateEditText = findViewById(R.id.stateEditText);
        postalCodeEditText = findViewById(R.id.postalCodeEditText);
        landmarkEditText = findViewById(R.id.landmarkEditText);
        countrySpinner = findViewById(R.id.countrySpinner);
        addressTypeRadioGroup = findViewById(R.id.addressTypeRadioGroup);
        radioHome = findViewById(R.id.radioHome);
        radioWork = findViewById(R.id.radioWork);
        defaultAddressCheckBox = findViewById(R.id.defaultAddressCheckBox);
        saveButton = findViewById(R.id.saveButton);
        deleteButton = findViewById(R.id.deleteButton);
        backButton = findViewById(R.id.backButton);
        addressListLayout = findViewById(R.id.addressListLayout);

        // Initialize Firestore and Auth
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize address list
        addresses = new ArrayList<>();

        // Setup Country Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.countries_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);

        // Check if we're in edit mode
        if (getIntent().hasExtra("addressId")) {
            isEditMode = true;
            titleTextView.setText("Edit Address");
            addressId = getIntent().getStringExtra("addressId");
            fullNameEditText.setText(getIntent().getStringExtra("fullName"));
            phoneNumberEditText.setText(getIntent().getStringExtra("phoneNumber"));
            emailEditText.setText(getIntent().getStringExtra("email"));
            streetAddressEditText.setText(getIntent().getStringExtra("streetAddress"));
            cityEditText.setText(getIntent().getStringExtra("city"));
            stateEditText.setText(getIntent().getStringExtra("state"));
            postalCodeEditText.setText(getIntent().getStringExtra("postalCode"));
            landmarkEditText.setText(getIntent().getStringExtra("landmark"));
            String country = getIntent().getStringExtra("country");
            for (int i = 0; i < countrySpinner.getCount(); i++) {
                if (countrySpinner.getItemAtPosition(i).toString().equals(country)) {
                    countrySpinner.setSelection(i);
                    break;
                }
            }
            String addressType = getIntent().getStringExtra("addressType");
            if (addressType != null && addressType.equals("Work")) {
                radioWork.setChecked(true);
            } else {
                radioHome.setChecked(true);
            }
            defaultAddressCheckBox.setChecked(getIntent().getBooleanExtra("isDefault", false));
            deleteButton.setVisibility(View.VISIBLE);
        }

        // Back button listener
        backButton.setOnClickListener(v -> onBackPressed());

        // Save button listener
        saveButton.setOnClickListener(v -> {
            String fullName = fullNameEditText.getText().toString().trim();
            String phoneNumber = phoneNumberEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String streetAddress = streetAddressEditText.getText().toString().trim();
            String city = cityEditText.getText().toString().trim();
            String state = stateEditText.getText().toString().trim();
            String postalCode = postalCodeEditText.getText().toString().trim();
            String landmark = landmarkEditText.getText().toString().trim();
            String country = countrySpinner.getSelectedItem().toString();
            String addressType = radioHome.isChecked() ? "Home" : "Work";
            boolean isDefault = defaultAddressCheckBox.isChecked();

            // Validation
            if (fullName.isEmpty() || phoneNumber.isEmpty() || email.isEmpty() || streetAddress.isEmpty() ||
                    city.isEmpty() || state.isEmpty() || postalCode.isEmpty() || country.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.setError("Invalid email format");
                return;
            }

            saveAddress(fullName, phoneNumber, email, streetAddress, city, state, postalCode, landmark, country, addressType, isDefault);
        });

        // Delete button listener
        deleteButton.setOnClickListener(v -> {
            if (isEditMode && addressId != null) {
                deleteAddress(addressId);
            }
        });

        // Fetch and display existing addresses
        fetchAddresses();
    }

    private void saveAddress(String fullName, String phoneNumber, String email, String streetAddress, String city,
                             String state, String postalCode, String landmark, String country,
                             String addressType, boolean isDefault) {
        String userId = mAuth.getCurrentUser().getUid();
        Map<String, Object> addressData = new HashMap<>();
        addressData.put("fullName", fullName);
        addressData.put("phoneNumber", phoneNumber);
        addressData.put("email", email); // Added email field
        addressData.put("streetAddress", streetAddress);
        addressData.put("city", city);
        addressData.put("state", state);
        addressData.put("postalCode", postalCode);
        addressData.put("landmark", landmark);
        addressData.put("country", country);
        addressData.put("addressType", addressType);
        addressData.put("isDefault", isDefault);

        if (isDefault) {
            // If this address is set as default, unset the default flag for all other addresses
            unsetDefaultForOtherAddresses(userId, addressId);
        }

        if (isEditMode && addressId != null) {
            // Update existing address
            db.collection("users")
                    .document(userId)
                    .collection("addresses")
                    .document(addressId)
                    .set(addressData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Address updated successfully", Toast.LENGTH_SHORT).show();
                        fetchAddresses();
                        clearForm();
                        finish(); // Return to previous screen
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update address: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            // Add new address
            db.collection("users")
                    .document(userId)
                    .collection("addresses")
                    .add(addressData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Address added successfully", Toast.LENGTH_SHORT).show();
                        fetchAddresses();
                        clearForm();
                        finish(); // Return to previous screen
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to add address: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void unsetDefaultForOtherAddresses(String userId, String currentAddressId) {
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .whereEqualTo("isDefault", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        if (!doc.getId().equals(currentAddressId)) {
                            doc.getReference().update("isDefault", false);
                        }
                    }
                });
    }

    private void deleteAddress(String addressId) {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .document(addressId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Address deleted successfully", Toast.LENGTH_SHORT).show();
                    fetchAddresses();
                    clearForm();
                    finish(); // Return to previous screen
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete address: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchAddresses() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    addresses.clear();
                    addressListLayout.removeAllViews();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> addressData = new HashMap<>();
                        addressData.put("id", doc.getId());
                        addressData.put("fullName", doc.getString("fullName"));
                        addressData.put("phoneNumber", doc.getString("phoneNumber"));
                        addressData.put("email", doc.getString("email")); // Added email field
                        addressData.put("streetAddress", doc.getString("streetAddress"));
                        addressData.put("city", doc.getString("city"));
                        addressData.put("state", doc.getString("state"));
                        addressData.put("postalCode", doc.getString("postalCode"));
                        addressData.put("landmark", doc.getString("landmark"));
                        addressData.put("country", doc.getString("country"));
                        addressData.put("addressType", doc.getString("addressType"));
                        addressData.put("isDefault", doc.getBoolean("isDefault"));
                        addresses.add(addressData);
                        addAddressView(addressData);
                    }
                    if (addresses.isEmpty()) {
                        TextView noAddressesText = new TextView(this);
                        noAddressesText.setText("No addresses found.");
                        noAddressesText.setTextSize(16);
                        noAddressesText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                        addressListLayout.addView(noAddressesText);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load addresses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void addAddressView(Map<String, Object> addressData) {
        LinearLayout addressLayout = new LinearLayout(this);
        addressLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addressLayout.setOrientation(LinearLayout.VERTICAL);
        addressLayout.setPadding(16, 16, 16, 16);
        addressLayout.setBackgroundResource(android.R.color.white);
        addressLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{
            setMargins(0, 0, 0, 16);
        }});

        // Address Type and Full Name
        TextView labelTextView = new TextView(this);
        labelTextView.setText(String.format("%s - %s", addressData.get("addressType"), addressData.get("fullName")));
        labelTextView.setTextSize(16);
        labelTextView.setTextColor(getResources().getColor(android.R.color.black));
        labelTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        addressLayout.addView(labelTextView);

        // Address Details
        TextView addressTextView = new TextView(this);
        String addressDetails = String.format("%s, %s, %s, %s, %s",
                addressData.get("streetAddress"),
                addressData.get("city"),
                addressData.get("state"),
                addressData.get("postalCode"),
                addressData.get("country"));
        if (addressData.get("landmark") != null && !addressData.get("landmark").toString().isEmpty()) {
            addressDetails += ", Landmark: " + addressData.get("landmark");
        }
        addressTextView.setText(addressDetails);
        addressTextView.setTextSize(14);
        addressTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        addressLayout.addView(addressTextView);

        // Phone Number
        TextView phoneTextView = new TextView(this);
        phoneTextView.setText("Phone: " + addressData.get("phoneNumber"));
        phoneTextView.setTextSize(14);
        phoneTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        addressLayout.addView(phoneTextView);

        // Email
        TextView emailTextView = new TextView(this);
        emailTextView.setText("Email: " + (addressData.get("email") != null ? addressData.get("email") : "Not provided"));
        emailTextView.setTextSize(14);
        emailTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        addressLayout.addView(emailTextView);

        // Default Address Indicator
        if (Boolean.TRUE.equals(addressData.get("isDefault"))) {
            TextView defaultTextView = new TextView(this);
            defaultTextView.setText("Default Address");
            defaultTextView.setTextSize(14);
            defaultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            addressLayout.addView(defaultTextView);
        }

        // Edit and Delete Buttons
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END);
        buttonLayout.setPadding(0, 8, 0, 0);

        Button editButton = new Button(this);
        editButton.setText("Edit");
        editButton.setTextColor(getResources().getColor(android.R.color.white));
        editButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_blue_dark));
        editButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{
            setMargins(0, 0, 8, 0);
        }});
        editButton.setOnClickListener(v -> {
            isEditMode = true;
            addressId = (String) addressData.get("id");
            titleTextView.setText("Edit Address");
            fullNameEditText.setText((String) addressData.get("fullName"));
            phoneNumberEditText.setText((String) addressData.get("phoneNumber"));
            emailEditText.setText((String) addressData.get("email"));
            streetAddressEditText.setText((String) addressData.get("streetAddress"));
            cityEditText.setText((String) addressData.get("city"));
            stateEditText.setText((String) addressData.get("state"));
            postalCodeEditText.setText((String) addressData.get("postalCode"));
            landmarkEditText.setText((String) addressData.get("landmark"));
            String country = (String) addressData.get("country");
            for (int i = 0; i < countrySpinner.getCount(); i++) {
                if (countrySpinner.getItemAtPosition(i).toString().equals(country)) {
                    countrySpinner.setSelection(i);
                    break;
                }
            }
            String addressType = (String) addressData.get("addressType");
            if (addressType != null && addressType.equals("Work")) {
                radioWork.setChecked(true);
            } else {
                radioHome.setChecked(true);
            }
            defaultAddressCheckBox.setChecked(Boolean.TRUE.equals(addressData.get("isDefault")));
            deleteButton.setVisibility(View.VISIBLE);
        });
        buttonLayout.addView(editButton);

        Button deleteButton = new Button(this);
        deleteButton.setText("Delete");
        deleteButton.setTextColor(getResources().getColor(android.R.color.white));
        deleteButton.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark));
        deleteButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        deleteButton.setOnClickListener(v -> deleteAddress((String) addressData.get("id")));
        buttonLayout.addView(deleteButton);

        addressLayout.addView(buttonLayout);

        addressListLayout.addView(addressLayout);
    }

    private void clearForm() {
        isEditMode = false;
        addressId = null;
        titleTextView.setText("Manage Addresses");
        fullNameEditText.setText("");
        phoneNumberEditText.setText("");
        emailEditText.setText("");
        streetAddressEditText.setText("");
        cityEditText.setText("");
        stateEditText.setText("");
        postalCodeEditText.setText("");
        landmarkEditText.setText("");
        countrySpinner.setSelection(0);
        radioHome.setChecked(true);
        defaultAddressCheckBox.setChecked(false);
        deleteButton.setVisibility(View.GONE);
    }
}