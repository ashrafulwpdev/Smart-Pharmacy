package com.oopgroup.smartpharmacy.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.ManageAddressActivity;
import com.oopgroup.smartpharmacy.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddressDialogFragment extends DialogFragment {

    private RadioGroup addressRadioGroup;
    private Button addNewAddressButton;
    private ImageView closeButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<Map<String, Object>> addresses;
    private ActivityResultLauncher<Intent> manageAddressLauncher;

    public interface OnAddressSelectedListener {
        void onAddressSelected(String addressId, String address);
    }

    private OnAddressSelectedListener listener;

    public void setOnAddressSelectedListener(OnAddressSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_address_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        addressRadioGroup = view.findViewById(R.id.addressRadioGroup);
        addNewAddressButton = view.findViewById(R.id.addNewAddressButton);
        closeButton = view.findViewById(R.id.closeButton);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        addresses = new ArrayList<>();

        manageAddressLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> fetchAddresses());

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getDialog().getWindow().setGravity(Gravity.BOTTOM);
            getDialog().getWindow().getDecorView().setPadding(32, 0, 32, 64);
        }

        closeButton.setOnClickListener(v -> dismiss());

        addNewAddressButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ManageAddressActivity.class);
            manageAddressLauncher.launch(intent);
        });

        fetchAddresses();
    }

    private void fetchAddresses() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    addresses.clear();
                    addressRadioGroup.removeAllViews();
                    int defaultIndex = -1;

                    for (int i = 0; i < queryDocumentSnapshots.size(); i++) {
                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(i);
                        Map<String, Object> addressData = new HashMap<>();
                        addressData.put("id", doc.getId());
                        addressData.put("fullName", doc.getString("fullName"));
                        addressData.put("phoneNumber", doc.getString("phoneNumber"));
                        addressData.put("streetAddress", doc.getString("streetAddress"));
                        addressData.put("city", doc.getString("city"));
                        addressData.put("state", doc.getString("state"));
                        addressData.put("postalCode", doc.getString("postalCode"));
                        addressData.put("landmark", doc.getString("landmark"));
                        addressData.put("country", doc.getString("country"));
                        addressData.put("addressType", doc.getString("addressType"));
                        addressData.put("isDefault", doc.getBoolean("isDefault"));
                        addresses.add(addressData);

                        if (Boolean.TRUE.equals(addressData.get("isDefault"))) {
                            defaultIndex = i;
                        }
                    }

                    if (addresses.isEmpty()) {
                        TextView noAddressesText = new TextView(getContext());
                        noAddressesText.setText("No addresses found. Add a new address.");
                        noAddressesText.setTextSize(16);
                        noAddressesText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                        noAddressesText.setPadding(0, 16, 0, 16);
                        addressRadioGroup.addView(noAddressesText);
                    } else {
                        for (int i = 0; i < addresses.size(); i++) {
                            addAddressView(addresses.get(i), i, i == defaultIndex);
                            if (i < addresses.size() - 1) {
                                View divider = new View(getContext());
                                divider.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                                divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                                addressRadioGroup.addView(divider);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load addresses: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void addAddressView(Map<String, Object> addressData, int index, boolean isDefault) {
        LinearLayout addressLayout = new LinearLayout(getContext());
        addressLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addressLayout.setOrientation(LinearLayout.HORIZONTAL);
        addressLayout.setPadding(0, 16, 0, 16);
        addressLayout.setGravity(Gravity.CENTER_VERTICAL);

        RadioButton radioButton = new RadioButton(getContext());
        radioButton.setId(View.generateViewId());
        radioButton.setChecked(isDefault);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        radioParams.setMargins(0, 0, 16, 0);
        radioButton.setLayoutParams(radioParams);
        addressLayout.addView(radioButton);

        LinearLayout detailsLayout = new LinearLayout(getContext());
        detailsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        detailsLayout.setOrientation(LinearLayout.VERTICAL);
        detailsLayout.setPadding(16, 0, 16, 0);

        TextView labelTextView = new TextView(getContext());
        labelTextView.setText(String.format("%s - %s", addressData.get("addressType"), addressData.get("fullName")));
        labelTextView.setTextSize(16);
        labelTextView.setTextColor(getResources().getColor(android.R.color.black));
        labelTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        detailsLayout.addView(labelTextView);

        TextView addressTextView = new TextView(getContext());
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
        detailsLayout.addView(addressTextView);

        addressLayout.addView(detailsLayout);

        ImageView editButton = new ImageView(getContext());
        LinearLayout.LayoutParams editButtonParams = new LinearLayout.LayoutParams(24, 24);
        editButtonParams.setMargins(8, 0, 16, 0);
        editButton.setLayoutParams(editButtonParams);
        editButton.setImageResource(R.drawable.edit_ic);
        editButton.setContentDescription("Edit Address");
        editButton.setPadding(4, 4, 4, 4);
        editButton.setColorFilter(getResources().getColor(android.R.color.black), android.graphics.PorterDuff.Mode.SRC_IN);

        editButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(getContext(), editButton);
            popupMenu.getMenu().add("Edit");
            popupMenu.getMenu().add("Delete");
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Edit")) {
                    Intent intent = new Intent(getContext(), ManageAddressActivity.class);
                    intent.putExtra("addressId", (String) addressData.get("id"));
                    intent.putExtra("fullName", (String) addressData.get("fullName"));
                    intent.putExtra("phoneNumber", (String) addressData.get("phoneNumber"));
                    intent.putExtra("email", (String) addressData.get("email"));
                    intent.putExtra("streetAddress", (String) addressData.get("streetAddress"));
                    intent.putExtra("city", (String) addressData.get("city"));
                    intent.putExtra("state", (String) addressData.get("state"));
                    intent.putExtra("postalCode", (String) addressData.get("postalCode"));
                    intent.putExtra("landmark", (String) addressData.get("landmark"));
                    intent.putExtra("country", (String) addressData.get("country"));
                    intent.putExtra("addressType", (String) addressData.get("addressType"));
                    intent.putExtra("isDefault", (Boolean) addressData.get("isDefault"));
                    manageAddressLauncher.launch(intent);
                } else if (item.getTitle().equals("Delete")) {
                    deleteAddress((String) addressData.get("id"));
                }
                return true;
            });
            popupMenu.show();
        });

        addressLayout.addView(editButton);
        addressRadioGroup.addView(addressLayout);

        radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && listener != null) {
                String fullAddress = String.format("%s, %s, %s, %s, %s",
                        addressData.get("streetAddress"),
                        addressData.get("city"),
                        addressData.get("state"),
                        addressData.get("postalCode"),
                        addressData.get("country"));
                listener.onAddressSelected((String) addressData.get("id"), fullAddress);
                dismiss();
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
                    Toast.makeText(getContext(), "Address deleted successfully", Toast.LENGTH_SHORT).show();
                    fetchAddresses();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to delete address: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}