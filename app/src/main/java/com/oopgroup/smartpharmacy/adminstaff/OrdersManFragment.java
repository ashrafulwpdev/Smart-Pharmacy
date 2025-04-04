package com.oopgroup.smartpharmacy.adminstaff;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Address;
import com.oopgroup.smartpharmacy.models.Order;
import com.oopgroup.smartpharmacy.models.Tracking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrdersManFragment extends Fragment {

    private static final String TAG = "OrdersManFragment";

    private TabLayout tabLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvOrders;
    private OrderAdapter orderAdapter;
    private List<Order> orderList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration ordersListener;

    private static final List<String> ALL_STATUSES = Arrays.asList(
            "Order Placed", "Payment Processed", "Awaiting Confirmation", "Order Confirmed",
            "Preparing Order", "Awaiting Pickup", "Shipped", "Out for Delivery", "Delivered",
            "Completed", "Cancelled"
    );
    private static final List<String> FINAL_STATUSES = Arrays.asList("Delivered", "Completed", "Cancelled");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_orders_man, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please log in to access this page", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        initializeUI(view);

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                        fetchOrders("New Orders");
                    } else {
                        Toast.makeText(getContext(), "Access denied. Admin only.", Toast.LENGTH_SHORT).show();
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check admin status: " + e.getMessage());
                    Toast.makeText(getContext(), "Error verifying admin status", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                });
    }

    private void initializeUI(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        rvOrders = view.findViewById(R.id.rv_orders);

        if (rvOrders == null) {
            Log.e(TAG, "RecyclerView rv_orders is null. Check fragment_orders_man.xml.");
            Toast.makeText(getContext(), "UI initialization failed", Toast.LENGTH_SHORT).show();
            return;
        }

        orderList = new ArrayList<>();
        orderAdapter = new OrderAdapter();
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(orderAdapter);

        setupTabs();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            String selectedTab = tabLayout.getTabAt(tabLayout.getSelectedTabPosition()).getText().toString();
            fetchOrders(selectedTab);
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (ordersListener != null) {
            ordersListener.remove();
            ordersListener = null;
        }
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("New Orders"));
        tabLayout.addTab(tabLayout.newTab().setText("Processing"));
        tabLayout.addTab(tabLayout.newTab().setText("Completed"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                fetchOrders(tab.getText().toString());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                fetchOrders(tab.getText().toString());
            }
        });

        tabLayout.getTabAt(0).select();
    }

    private void fetchOrders(String tabName) {
        if (ordersListener != null) {
            ordersListener.remove();
        }

        orderList.clear();
        orderAdapter.notifyDataSetChanged();
        swipeRefreshLayout.setRefreshing(true);

        Query query;
        switch (tabName) {
            case "New Orders":
                query = db.collection("orders")
                        .whereIn("status", Arrays.asList("Order Placed", "Payment Processed", "Awaiting Confirmation"))
                        .orderBy("createdAt", Query.Direction.DESCENDING);
                break;
            case "Processing":
                query = db.collection("orders")
                        .whereIn("status", Arrays.asList("Order Confirmed", "Preparing Order", "Awaiting Pickup", "Shipped", "Out for Delivery"))
                        .orderBy("createdAt", Query.Direction.DESCENDING);
                break;
            case "Completed":
                query = db.collection("orders")
                        .whereIn("status", Arrays.asList("Delivered", "Completed", "Cancelled"))
                        .orderBy("createdAt", Query.Direction.DESCENDING);
                break;
            default:
                swipeRefreshLayout.setRefreshing(false);
                return;
        }

        ordersListener = query.addSnapshotListener((queryDocumentSnapshots, e) -> {
            swipeRefreshLayout.setRefreshing(false);
            if (e != null) {
                Log.e(TAG, "Listen failed: " + e.getMessage());
                Toast.makeText(getContext(), "Failed to fetch orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            orderList.clear();
            if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    Order order = doc.toObject(Order.class);
                    order.setId(doc.getId());
                    if (!orderList.contains(order)) {
                        orderList.add(order);
                        fetchAddressAndTrackingForOrder(order);
                    }
                }
                Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                orderAdapter.notifyDataSetChanged();
            } else {
                orderAdapter.notifyDataSetChanged();
                Toast.makeText(getContext(), "No orders found for " + tabName, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAddressAndTrackingForOrder(Order order) {
        db.collection("users")
                .document(order.getUserId())
                .collection("addresses")
                .document(order.getAddressId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Address address = doc.toObject(Address.class);
                        order.setAddress(address);
                    }
                    if ("Product".equals(order.getOrderType())) {
                        fetchTrackingForOrder(order);
                    } else {
                        orderAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch address: " + e.getMessage());
                    if ("Product".equals(order.getOrderType())) {
                        fetchTrackingForOrder(order);
                    } else {
                        orderAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void fetchTrackingForOrder(Order order) {
        db.collection("orders")
                .document(order.getId())
                .collection("tracking")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Tracking tracking = queryDocumentSnapshots.getDocuments().get(0).toObject(Tracking.class);
                        order.setStatus(tracking.getStatus());
                    }
                    orderAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch tracking: " + e.getMessage());
                    orderAdapter.notifyDataSetChanged();
                });
    }

    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_admin, parent, false);
            return new OrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            Order order = orderList.get(position);
            holder.bind(order);
        }

        @Override
        public int getItemCount() {
            return orderList.size();
        }

        private class OrderViewHolder extends RecyclerView.ViewHolder {

            private ImageView ivProductImage;
            private TextView tvOrderNumber, tvStatus, tvAddress, tvItemCount, tvTotal, tvPrescription;
            private MaterialButton btnUpdateStatus, btnApprovePrescription, btnRejectPrescription, btnCancelOrder;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.iv_product_image);
                tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvAddress = itemView.findViewById(R.id.tv_address);
                tvItemCount = itemView.findViewById(R.id.tv_item_count);
                tvTotal = itemView.findViewById(R.id.tv_total);
                tvPrescription = itemView.findViewById(R.id.tv_prescription);
                btnUpdateStatus = itemView.findViewById(R.id.btn_update_status);
                btnApprovePrescription = itemView.findViewById(R.id.btn_approve_prescription);
                btnRejectPrescription = itemView.findViewById(R.id.btn_reject_prescription);
                btnCancelOrder = itemView.findViewById(R.id.btn_cancel_order);
            }

            public void bind(Order order) {
                tvOrderNumber.setText("#" + (order.getOrderId() != null ? order.getOrderId() : order.getId()));
                tvStatus.setText(order.getStatus());
                tvItemCount.setText(order.getItems().size() + " Items");
                String currency = order.getCurrency() != null ? order.getCurrency() : "RM";
                tvTotal.setText(String.format("%s %.2f", currency, order.getGrandTotal()));

                // Status color
                switch (order.getStatus()) {
                    case "Delivered":
                    case "Completed":
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        break;
                    case "Cancelled":
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        break;
                    default:
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                        break;
                }

                Address address = order.getAddress();
                if (address != null) {
                    String fullAddress = String.format("%s, %s, %s, %s, %s",
                                    address.getStreetAddress() != null ? address.getStreetAddress() : "",
                                    address.getCity() != null ? address.getCity() : "",
                                    address.getState() != null ? address.getState() : "",
                                    address.getPostalCode() != null ? address.getPostalCode() : "",
                                    address.getCountry() != null ? address.getCountry() : "")
                            .replace(", ,", ",").trim();
                    if (fullAddress.endsWith(",")) {
                        fullAddress = fullAddress.substring(0, fullAddress.length() - 1);
                    }
                    tvAddress.setText(fullAddress);
                } else {
                    db.collection("users")
                            .document(order.getUserId())
                            .collection("addresses")
                            .document(order.getAddressId())
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    Address fetchedAddress = doc.toObject(Address.class);
                                    order.setAddress(fetchedAddress);
                                    String fullAddress = String.format("%s, %s, %s, %s, %s",
                                                    fetchedAddress.getStreetAddress() != null ? fetchedAddress.getStreetAddress() : "",
                                                    fetchedAddress.getCity() != null ? fetchedAddress.getCity() : "",
                                                    fetchedAddress.getState() != null ? fetchedAddress.getState() : "",
                                                    fetchedAddress.getPostalCode() != null ? fetchedAddress.getPostalCode() : "",
                                                    fetchedAddress.getCountry() != null ? fetchedAddress.getCountry() : "")
                                            .replace(", ,", ",").trim();
                                    if (fullAddress.endsWith(",")) {
                                        fullAddress = fullAddress.substring(0, fullAddress.length() - 1);
                                    }
                                    tvAddress.setText(fullAddress);
                                } else {
                                    tvAddress.setText("Address not available");
                                }
                            })
                            .addOnFailureListener(e -> tvAddress.setText("Address fetch failed"));
                }

                String productId = order.getFirstProductId();
                if (productId != null) {
                    String collection = "Product".equals(order.getOrderType()) ? "products" : "labTests";
                    db.collection(collection)
                            .document(productId)
                            .get()
                            .addOnSuccessListener(doc -> {
                                String imageUrl = doc.getString("imageUrl");
                                Glide.with(itemView.getContext())
                                        .load(imageUrl)
                                        .placeholder(R.drawable.placeholder_image)
                                        .error(R.drawable.default_product_image)
                                        .into(ivProductImage);
                            })
                            .addOnFailureListener(e -> ivProductImage.setImageResource(R.drawable.default_product_image));
                } else {
                    ivProductImage.setImageResource(R.drawable.default_product_image);
                }

                // Dynamic button visibility
                String status = order.getStatus();
                if (FINAL_STATUSES.contains(status)) {
                    btnUpdateStatus.setVisibility(View.GONE);
                    btnCancelOrder.setVisibility(View.GONE);
                } else {
                    btnUpdateStatus.setVisibility(View.VISIBLE);
                    btnCancelOrder.setVisibility(View.VISIBLE);
                }

                if (order.getPrescriptionUrl() != null && !order.getPrescriptionUrl().isEmpty()) {
                    if (order.getPrescriptionApproved() == null) {
                        tvPrescription.setText("Prescription: Awaiting Review");
                        btnApprovePrescription.setVisibility(View.VISIBLE);
                        btnRejectPrescription.setVisibility(View.VISIBLE);
                    } else if (order.getPrescriptionApproved()) {
                        tvPrescription.setText("Prescription: Approved");
                        btnApprovePrescription.setVisibility(View.GONE);
                        btnRejectPrescription.setVisibility(View.GONE);
                    } else {
                        tvPrescription.setText("Prescription: Rejected");
                        btnApprovePrescription.setVisibility(View.GONE);
                        btnRejectPrescription.setVisibility(View.GONE);
                    }
                } else {
                    tvPrescription.setText("Prescription: Not Required");
                    btnApprovePrescription.setVisibility(View.GONE);
                    btnRejectPrescription.setVisibility(View.GONE);
                }

                btnUpdateStatus.setOnClickListener(v -> showStatusUpdateDialog(order));
                btnApprovePrescription.setOnClickListener(v -> approvePrescription(order));
                btnRejectPrescription.setOnClickListener(v -> rejectPrescription(order));
                btnCancelOrder.setOnClickListener(v -> cancelOrder(order));
            }

            private void showStatusUpdateDialog(Order order) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Update Order Status");

                List<String> availableStatuses = new ArrayList<>(ALL_STATUSES);
                // Remove the current status and terminal statuses beyond "Delivered"
                availableStatuses.remove(order.getStatus());
                if (FINAL_STATUSES.contains(order.getStatus())) {
                    // If already in a final status, no updates allowed (shouldn't reach here due to button visibility)
                    availableStatuses.clear();
                } else {
                    // Remove "Completed" and "Cancelled" as options, but keep "Delivered" unless current status is "Delivered"
                    availableStatuses.remove("Completed");
                    availableStatuses.remove("Cancelled");
                }

                String[] statusArray = availableStatuses.toArray(new String[0]);
                builder.setItems(statusArray, (dialog, which) -> {
                    String newStatus = statusArray[which];
                    updateOrderStatus(order, newStatus);
                });

                builder.setNegativeButton("Cancel", null);
                builder.show();
            }

            private void updateOrderStatus(Order order, String newStatus) {
                Map<String, Object> trackingData = new HashMap<>();
                trackingData.put("status", newStatus);
                trackingData.put("details", "Status updated by admin");
                trackingData.put("updatedAt", com.google.firebase.Timestamp.now());

                db.collection("orders")
                        .document(order.getId())
                        .collection("tracking")
                        .add(trackingData)
                        .addOnSuccessListener(docRef -> {
                            db.collection("orders")
                                    .document(order.getId())
                                    .update("status", newStatus)
                                    .addOnSuccessListener(aVoid -> {
                                        order.setStatus(newStatus);
                                        notifyItemChanged(getAdapterPosition());
                                        Toast.makeText(getContext(), "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to add tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            private void approvePrescription(Order order) {
                db.collection("orders")
                        .document(order.getId())
                        .update("prescriptionApproved", true)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Prescription approved", Toast.LENGTH_SHORT).show();
                            btnApprovePrescription.setVisibility(View.GONE);
                            btnRejectPrescription.setVisibility(View.GONE);
                            tvPrescription.setText("Prescription: Approved");
                            order.setPrescriptionApproved(true);
                            updateOrderStatus(order, "Order Confirmed");
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to approve: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            private void rejectPrescription(Order order) {
                db.collection("orders")
                        .document(order.getId())
                        .update("prescriptionApproved", false, "status", "Cancelled")
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Prescription rejected, order cancelled", Toast.LENGTH_SHORT).show();
                            order.setStatus("Cancelled");
                            order.setPrescriptionApproved(false);
                            notifyItemChanged(getAdapterPosition());
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to reject: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            private void cancelOrder(Order order) {
                db.collection("orders")
                        .document(order.getId())
                        .update("status", "Cancelled")
                        .addOnSuccessListener(aVoid -> {
                            Map<String, Object> trackingData = new HashMap<>();
                            trackingData.put("status", "Cancelled");
                            trackingData.put("details", "Order cancelled by admin");
                            trackingData.put("updatedAt", com.google.firebase.Timestamp.now());
                            db.collection("orders")
                                    .document(order.getId())
                                    .collection("tracking")
                                    .add(trackingData)
                                    .addOnSuccessListener(docRef -> {
                                        order.setStatus("Cancelled");
                                        notifyItemChanged(getAdapterPosition());
                                        Toast.makeText(getContext(), "Order cancelled", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to add tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to cancel: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
    }
}