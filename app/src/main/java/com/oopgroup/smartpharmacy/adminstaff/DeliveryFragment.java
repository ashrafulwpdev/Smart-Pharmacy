package com.oopgroup.smartpharmacy.adminstaff;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.oopgroup.smartpharmacy.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeliveryFragment extends Fragment {

    private static final String TAG = "DeliveryFragment";

    private TabLayout tabLayout;
    private RecyclerView deliveryRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Button btnAddPersonnel, btnAddFee;
    private TextInputEditText etPersonnelName, etPersonnelContact, etPersonnelPostalCodes;
    private TextInputEditText etFeeCity, etFeePostalCode, etFeeAmount, etFeeDiscount, etFeeEstimatedDays;
    private RadioGroup rgFreeDelivery;
    private TextView emptyView;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private List<DeliveryPersonnel> deliveryPersonnelList;
    private List<DeliveryFee> deliveryFeesList;
    private DeliveryPersonnelAdapter personnelAdapter;
    private DeliveryFeesAdapter feesAdapter;
    private boolean isPersonnelTabSelected = true; // Track current tab

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_delivery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        tabLayout = view.findViewById(R.id.tabLayout);
        deliveryRecyclerView = view.findViewById(R.id.deliveryRecyclerView);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        btnAddPersonnel = view.findViewById(R.id.btn_add_personnel);
        btnAddFee = view.findViewById(R.id.btn_add_fee);
        emptyView = view.findViewById(R.id.emptyView);

        // Personnel input fields
        etPersonnelName = view.findViewById(R.id.et_personnel_name);
        etPersonnelContact = view.findViewById(R.id.et_personnel_contact);
        etPersonnelPostalCodes = view.findViewById(R.id.et_personnel_postal_codes);

        // Fee input fields
        etFeeCity = view.findViewById(R.id.et_fee_city);
        etFeePostalCode = view.findViewById(R.id.et_fee_postal_code);
        etFeeAmount = view.findViewById(R.id.et_fee_amount);
        etFeeDiscount = view.findViewById(R.id.et_fee_discount);
        rgFreeDelivery = view.findViewById(R.id.rg_free_delivery);
        etFeeEstimatedDays = view.findViewById(R.id.et_fee_estimated_days);

        deliveryPersonnelList = new ArrayList<>();
        deliveryFeesList = new ArrayList<>();
        personnelAdapter = new DeliveryPersonnelAdapter(deliveryPersonnelList);
        feesAdapter = new DeliveryFeesAdapter(deliveryFeesList);

        deliveryRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        deliveryRecyclerView.setAdapter(personnelAdapter);

        fetchDeliveryPersonnel();

        btnAddPersonnel.setOnClickListener(v -> addPersonnel());
        btnAddFee.setOnClickListener(v -> addFee());

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isPersonnelTabSelected) {
                fetchDeliveryPersonnel();
            } else {
                fetchDeliveryFees();
            }
            swipeRefreshLayout.setRefreshing(false);
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                isPersonnelTabSelected = (position == 0);
                View personnelSection = view.findViewById(R.id.personnelSection);
                View feesSection = view.findViewById(R.id.feesSection);

                personnelSection.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                btnAddPersonnel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                feesSection.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                btnAddFee.setVisibility(position == 1 ? View.VISIBLE : View.GONE);

                if (position == 0) {
                    deliveryRecyclerView.setAdapter(personnelAdapter);
                    fetchDeliveryPersonnel();
                } else {
                    deliveryRecyclerView.setAdapter(feesAdapter);
                    fetchDeliveryFees();
                }
                updateEmptyViewVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        tabLayout.getTabAt(0).select(); // Default to Personnel tab
    }

    private void fetchDeliveryPersonnel() {
        db.collection("delivery_men")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    deliveryPersonnelList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        DeliveryPersonnel personnel = doc.toObject(DeliveryPersonnel.class);
                        personnel.setId(doc.getId());
                        deliveryPersonnelList.add(personnel);
                    }
                    personnelAdapter.notifyDataSetChanged();
                    updateEmptyViewVisibility();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to fetch personnel: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void fetchDeliveryFees() {
        db.collection("delivery_fees")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    deliveryFeesList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        DeliveryFee fee = doc.toObject(DeliveryFee.class);
                        fee.setId(doc.getId());
                        deliveryFeesList.add(fee);
                    }
                    feesAdapter.notifyDataSetChanged();
                    updateEmptyViewVisibility();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to fetch fees: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateEmptyViewVisibility() {
        if (isPersonnelTabSelected) {
            emptyView.setVisibility(deliveryPersonnelList.isEmpty() ? View.VISIBLE : View.GONE);
            deliveryRecyclerView.setVisibility(deliveryPersonnelList.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            emptyView.setVisibility(deliveryFeesList.isEmpty() ? View.VISIBLE : View.GONE);
            deliveryRecyclerView.setVisibility(deliveryFeesList.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void addPersonnel() {
        String name = etPersonnelName.getText().toString().trim();
        String contact = etPersonnelContact.getText().toString().trim();
        String postalCodes = etPersonnelPostalCodes.getText().toString().trim();

        if (name.isEmpty() || contact.isEmpty() || postalCodes.isEmpty()) {
            Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> deliveryMan = new HashMap<>();
        deliveryMan.put("name", name);
        deliveryMan.put("contact", contact);
        deliveryMan.put("assignedPostalCodes", List.of(postalCodes.split(",")));

        db.collection("delivery_men")
                .add(deliveryMan)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(getContext(), "Delivery man added", Toast.LENGTH_SHORT).show();
                    fetchDeliveryPersonnel();
                    clearPersonnelInputs();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to add: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void addFee() {
        String city = etFeeCity.getText().toString().trim();
        String postalCode = etFeePostalCode.getText().toString().trim();
        String feeStr = etFeeAmount.getText().toString().trim();
        String discountStr = etFeeDiscount.getText().toString().trim();
        String estimatedDaysStr = etFeeEstimatedDays.getText().toString().trim();
        int selectedId = rgFreeDelivery.getCheckedRadioButtonId();
        boolean freeDelivery = selectedId == R.id.rb_free_yes;

        if (city.isEmpty() || postalCode.isEmpty() || feeStr.isEmpty() || estimatedDaysStr.isEmpty()) {
            Toast.makeText(getContext(), "City, postal code, fee, and estimated days are required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double fee = Double.parseDouble(feeStr);
            double discount = discountStr.isEmpty() ? 0.0 : Double.parseDouble(discountStr);
            int estimatedDays = Integer.parseInt(estimatedDaysStr);

            Map<String, Object> deliveryFee = new HashMap<>();
            deliveryFee.put("city", city);
            deliveryFee.put("postalCode", postalCode);
            deliveryFee.put("fee", fee);
            deliveryFee.put("discount", discount);
            deliveryFee.put("freeDelivery", freeDelivery);
            deliveryFee.put("estimatedDays", estimatedDays);

            db.collection("delivery_fees")
                    .document(postalCode)
                    .set(deliveryFee)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Delivery fee added", Toast.LENGTH_SHORT).show();
                        fetchDeliveryFees();
                        clearFeeInputs();
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to add: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearPersonnelInputs() {
        etPersonnelName.setText("");
        etPersonnelContact.setText("");
        etPersonnelPostalCodes.setText("");
    }

    private void clearFeeInputs() {
        etFeeCity.setText("");
        etFeePostalCode.setText("");
        etFeeAmount.setText("");
        etFeeDiscount.setText("");
        rgFreeDelivery.clearCheck();
        etFeeEstimatedDays.setText("");
    }

    public static class DeliveryPersonnel {
        private String name;
        private String contact;
        private List<String> assignedPostalCodes;
        private String id;

        public DeliveryPersonnel() {}

        public String getName() { return name != null ? name : "Unknown"; }
        public void setName(String name) { this.name = name; }
        public String getContact() { return contact != null ? contact : "N/A"; }
        public void setContact(String contact) { this.contact = contact; }
        public List<String> getAssignedPostalCodes() { return assignedPostalCodes != null ? assignedPostalCodes : new ArrayList<>(); }
        public void setAssignedPostalCodes(List<String> assignedPostalCodes) { this.assignedPostalCodes = assignedPostalCodes; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class DeliveryFee {
        private String city;
        private String postalCode;
        private double fee;
        private double discount;
        private boolean freeDelivery;
        private int estimatedDays;
        private String id;

        public DeliveryFee() {}

        public String getCity() { return city != null ? city : "Unknown"; }
        public void setCity(String city) { this.city = city; }
        public String getPostalCode() { return postalCode != null ? postalCode : "Unknown"; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public double getFee() { return fee; }
        public void setFee(double fee) { this.fee = fee; }
        public double getDiscount() { return discount; }
        public void setDiscount(double discount) { this.discount = discount; }
        public boolean isFreeDelivery() { return freeDelivery; }
        public void setFreeDelivery(boolean freeDelivery) { this.freeDelivery = freeDelivery; }
        public int getEstimatedDays() { return estimatedDays; }
        public void setEstimatedDays(int estimatedDays) { this.estimatedDays = estimatedDays; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    private class DeliveryPersonnelAdapter extends RecyclerView.Adapter<DeliveryPersonnelAdapter.ViewHolder> {
        private final List<DeliveryPersonnel> personnelList;

        public DeliveryPersonnelAdapter(List<DeliveryPersonnel> personnelList) {
            this.personnelList = personnelList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_delivery_personnel, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeliveryPersonnel personnel = personnelList.get(position);
            holder.itemTitle.setText(personnel.getName());
            holder.itemDescription.setText("Contact: " + personnel.getContact());
            holder.itemAdditionalInfo.setText("Areas: " + String.join(", ", personnel.getAssignedPostalCodes()));

            holder.editButton.setOnClickListener(v -> editPersonnel(position));
            holder.deleteButton.setOnClickListener(v -> deletePersonnel(position));
        }

        @Override
        public int getItemCount() {
            return personnelList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView itemTitle, itemDescription, itemAdditionalInfo;
            Button editButton, deleteButton;

            ViewHolder(View itemView) {
                super(itemView);
                itemTitle = itemView.findViewById(R.id.itemTitle);
                itemDescription = itemView.findViewById(R.id.itemDescription);
                itemAdditionalInfo = itemView.findViewById(R.id.itemAdditionalInfo);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
            }
        }

        private void editPersonnel(int position) {
            DeliveryPersonnel personnel = deliveryPersonnelList.get(position);
            etPersonnelName.setText(personnel.getName());
            etPersonnelContact.setText(personnel.getContact());
            etPersonnelPostalCodes.setText(String.join(",", personnel.getAssignedPostalCodes()));
            btnAddPersonnel.setText("Update");
            btnAddPersonnel.setOnClickListener(v -> {
                updatePersonnel(personnel.getId());
                btnAddPersonnel.setText("Add Personnel");
                btnAddPersonnel.setOnClickListener(v1 -> addPersonnel());
            });
        }

        private void updatePersonnel(String id) {
            String name = etPersonnelName.getText().toString().trim();
            String contact = etPersonnelContact.getText().toString().trim();
            String postalCodes = etPersonnelPostalCodes.getText().toString().trim();

            if (name.isEmpty() || contact.isEmpty() || postalCodes.isEmpty()) {
                Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> deliveryMan = new HashMap<>();
            deliveryMan.put("name", name);
            deliveryMan.put("contact", contact);
            deliveryMan.put("assignedPostalCodes", List.of(postalCodes.split(",")));

            db.collection("delivery_men").document(id)
                    .set(deliveryMan)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Personnel updated", Toast.LENGTH_SHORT).show();
                        fetchDeliveryPersonnel();
                        clearPersonnelInputs();
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }

        private void deletePersonnel(int position) {
            DeliveryPersonnel personnel = deliveryPersonnelList.get(position);
            db.collection("delivery_men").document(personnel.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        deliveryPersonnelList.remove(position);
                        notifyItemRemoved(position);
                        Toast.makeText(getContext(), "Personnel deleted", Toast.LENGTH_SHORT).show();
                        updateEmptyViewVisibility();
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private class DeliveryFeesAdapter extends RecyclerView.Adapter<DeliveryFeesAdapter.ViewHolder> {
        private final List<DeliveryFee> feesList;

        public DeliveryFeesAdapter(List<DeliveryFee> feesList) {
            this.feesList = feesList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_delivery_fee, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeliveryFee fee = feesList.get(position);
            holder.itemTitle.setText(fee.getCity() + " (" + fee.getPostalCode() + ")");
            String feeText = fee.isFreeDelivery() ? "Free" : String.format("RM %.2f (Discount: RM %.2f)", fee.getFee(), fee.getDiscount());
            holder.itemDescription.setText(feeText);
            holder.itemAdditionalInfo.setText("Est. Days: " + fee.getEstimatedDays());

            holder.editButton.setOnClickListener(v -> editFee(position));
            holder.deleteButton.setOnClickListener(v -> deleteFee(position));
        }

        @Override
        public int getItemCount() {
            return feesList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView itemTitle, itemDescription, itemAdditionalInfo;
            Button editButton, deleteButton;

            ViewHolder(View itemView) {
                super(itemView);
                itemTitle = itemView.findViewById(R.id.itemTitle);
                itemDescription = itemView.findViewById(R.id.itemDescription);
                itemAdditionalInfo = itemView.findViewById(R.id.itemAdditionalInfo);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
            }
        }

        private void editFee(int position) {
            DeliveryFee fee = deliveryFeesList.get(position);
            etFeeCity.setText(fee.getCity());
            etFeePostalCode.setText(fee.getPostalCode());
            etFeeAmount.setText(String.valueOf(fee.getFee()));
            etFeeDiscount.setText(fee.getDiscount() > 0 ? String.valueOf(fee.getDiscount()) : "");
            rgFreeDelivery.check(fee.isFreeDelivery() ? R.id.rb_free_yes : R.id.rb_free_no);
            etFeeEstimatedDays.setText(String.valueOf(fee.getEstimatedDays()));
            btnAddFee.setText("Update");
            btnAddFee.setOnClickListener(v -> {
                updateFee(fee.getId());
                btnAddFee.setText("Add Fee");
                btnAddFee.setOnClickListener(v1 -> addFee());
            });
        }

        private void updateFee(String id) {
            String city = etFeeCity.getText().toString().trim();
            String postalCode = etFeePostalCode.getText().toString().trim();
            String feeStr = etFeeAmount.getText().toString().trim();
            String discountStr = etFeeDiscount.getText().toString().trim();
            String estimatedDaysStr = etFeeEstimatedDays.getText().toString().trim();
            int selectedId = rgFreeDelivery.getCheckedRadioButtonId();
            boolean freeDelivery = selectedId == R.id.rb_free_yes;

            if (city.isEmpty() || postalCode.isEmpty() || feeStr.isEmpty() || estimatedDaysStr.isEmpty()) {
                Toast.makeText(getContext(), "City, postal code, fee, and estimated days are required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double fee = Double.parseDouble(feeStr);
                double discount = discountStr.isEmpty() ? 0.0 : Double.parseDouble(discountStr);
                int estimatedDays = Integer.parseInt(estimatedDaysStr);

                Map<String, Object> deliveryFee = new HashMap<>();
                deliveryFee.put("city", city);
                deliveryFee.put("postalCode", postalCode);
                deliveryFee.put("fee", fee);
                deliveryFee.put("discount", discount);
                deliveryFee.put("freeDelivery", freeDelivery);
                deliveryFee.put("estimatedDays", estimatedDays);

                db.collection("delivery_fees").document(id)
                        .set(deliveryFee)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Fee updated", Toast.LENGTH_SHORT).show();
                            fetchDeliveryFees();
                            clearFeeInputs();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        }

        private void deleteFee(int position) {
            DeliveryFee fee = deliveryFeesList.get(position);
            db.collection("delivery_fees").document(fee.getId())
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        deliveryFeesList.remove(position);
                        notifyItemRemoved(position);
                        Toast.makeText(getContext(), "Fee deleted", Toast.LENGTH_SHORT).show();
                        updateEmptyViewVisibility();
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
}