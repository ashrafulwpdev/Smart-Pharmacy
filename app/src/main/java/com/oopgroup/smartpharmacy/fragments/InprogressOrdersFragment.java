package com.oopgroup.smartpharmacy.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Address;
import com.oopgroup.smartpharmacy.models.Order;
import com.oopgroup.smartpharmacy.models.Tracking;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InprogressOrdersFragment extends Fragment {

    private static final String TAG = "InprogressOrdersFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvOrders;
    private OrderAdapter orderAdapter;
    private List<Order> orderList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        rvOrders = view.findViewById(R.id.rv_orders);

        orderList = new ArrayList<>();
        orderAdapter = new OrderAdapter(true);
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(orderAdapter);

        fetchOrders();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchOrders();
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void fetchOrders() {
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "User not authenticated");
            Toast.makeText(getContext(), "Please log in to view orders", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Fetching orders for userId: " + userId);
        db.collection("orders")
                .whereEqualTo("userId", userId)
                .whereIn("status", List.of("Order Placed", "Payment Processed", "Awaiting Confirmation", "Order Confirmed", "Preparing Order", "Awaiting Pickup", "Shipped", "Out for Delivery"))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed: " + e.getMessage());
                        Toast.makeText(getContext(), "Failed to fetch orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Log.d(TAG, "Snapshot listener triggered, documents: " + (queryDocumentSnapshots != null ? queryDocumentSnapshots.size() : 0));
                    orderList.clear();
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Order order = doc.toObject(Order.class);
                            order.setId(doc.getId());
                            Log.d(TAG, "Order fetched: " + order.getId() + ", Status: " + order.getStatus());
                            fetchAddressAndTrackingForOrder(order);
                        }
                    } else {
                        Log.d(TAG, "No matching orders found");
                        orderAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void fetchAddressAndTrackingForOrder(Order order) {
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Fetching address for order: " + order.getId());
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .document(order.getAddressId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Address address = doc.toObject(Address.class);
                        order.setAddress(address);
                        Log.d(TAG, "Address fetched for order: " + order.getId());
                        fetchEstimatedDaysForOrder(order);
                    } else {
                        Log.w(TAG, "Address not found for order: " + order.getId() + ", addressId: " + order.getAddressId());
                        orderList.add(order);
                        if ("Product".equals(order.getOrderType())) {
                            fetchTrackingForOrder(order);
                        } else {
                            Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                            orderAdapter.notifyDataSetChanged();
                            Log.d(TAG, "Order added (non-Product, no address): " + order.getId());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch address for order " + order.getId() + ": " + e.getMessage());
                    orderList.add(order);
                    if ("Product".equals(order.getOrderType())) {
                        fetchTrackingForOrder(order);
                    } else {
                        Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                        orderAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Order added (non-Product, address fetch failed): " + order.getId());
                    }
                });
    }

    private void fetchEstimatedDaysForOrder(Order order) {
        if (order.getAddress() == null || order.getAddress().getPostalCode() == null) {
            Log.w(TAG, "No postal code available for order: " + order.getId());
            orderList.add(order);
            if ("Product".equals(order.getOrderType())) {
                fetchTrackingForOrder(order);
            } else {
                Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                orderAdapter.notifyDataSetChanged();
            }
            return;
        }

        String postalCode = order.getAddress().getPostalCode();
        Log.d(TAG, "Fetching estimated days for postal code: " + postalCode + " for order: " + order.getId());
        db.collection("delivery_fees")
                .document(postalCode)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Integer estimatedDays = doc.getLong("estimatedDays") != null ? doc.getLong("estimatedDays").intValue() : 0;
                        order.setEstimatedDays(estimatedDays);
                        Log.d(TAG, "Estimated days fetched: " + estimatedDays + " for order: " + order.getId());
                    } else {
                        order.setEstimatedDays(0);
                        Log.w(TAG, "No delivery fee data for postal code: " + postalCode + " for order: " + order.getId());
                    }
                    orderList.add(order);
                    if ("Product".equals(order.getOrderType())) {
                        fetchTrackingForOrder(order);
                    } else {
                        Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                        orderAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch estimated days for order " + order.getId() + ": " + e.getMessage());
                    order.setEstimatedDays(0);
                    orderList.add(order);
                    if ("Product".equals(order.getOrderType())) {
                        fetchTrackingForOrder(order);
                    } else {
                        Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                        orderAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void fetchTrackingForOrder(Order order) {
        Log.d(TAG, "Fetching tracking for order: " + order.getId());
        db.collection("orders")
                .document(order.getId())
                .collection("tracking")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Failed to fetch tracking for " + order.getId() + ": " + e.getMessage());
                        order.setStatus("Order Placed");
                        Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                        orderAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Tracking fetch failed, defaulted to Order Placed: " + order.getId());
                        return;
                    }
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        Tracking tracking = queryDocumentSnapshots.getDocuments().get(0).toObject(Tracking.class);
                        order.setStatus(tracking.getStatus());
                        Log.d(TAG, "Tracking fetched for " + order.getId() + ", Status: " + tracking.getStatus());
                    } else {
                        order.setStatus("Order Placed");
                        Log.d(TAG, "No tracking found for " + order.getId() + ", defaulting to Order Placed");
                    }
                    Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                    orderAdapter.notifyDataSetChanged();
                });
    }

    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

        private final boolean isUpcoming;

        public OrderAdapter(boolean isUpcoming) {
            this.isUpcoming = isUpcoming;
        }

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_inprogress, parent, false);
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
            private TextView tvOrderNumber, tvStatus, tvAddress, tvItemCount, tvDeliveryDate, tvTotal;
            private Button btnTracking, btnCall;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.iv_product_image);
                tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvAddress = itemView.findViewById(R.id.tv_address);
                tvItemCount = itemView.findViewById(R.id.tv_item_count);
                tvDeliveryDate = itemView.findViewById(R.id.tv_delivery_date);
                tvTotal = itemView.findViewById(R.id.tv_total);
                btnTracking = itemView.findViewById(R.id.btn_tracking);
                btnCall = itemView.findViewById(R.id.btn_call);
            }

            public void bind(Order order) {
                tvOrderNumber.setText("#" + (order.getOrderId() != null ? order.getOrderId() : order.getId()));
                tvStatus.setText(order.getStatus());
                tvItemCount.setText(order.getItems() != null ? order.getItems().size() + " Items" : "N/A");
                String currency = order.getCurrency() != null ? order.getCurrency() : "RM";
                tvTotal.setText(String.format("%s %.2f", currency, order.getGrandTotal()));

                if (order.getAddress() != null) {
                    Address address = order.getAddress();
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
                    tvAddress.setText(fullAddress.length() > 30 ? fullAddress.substring(0, 30) + "..." : fullAddress);
                } else {
                    tvAddress.setText("Address not available");
                }

                SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());
                String dateLabel = "Product".equals(order.getOrderType()) ? "Delivery by " : "Visit on ";
                if (order.getEstimatedDays() != null && order.getCreatedAt() != null) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(order.getCreatedAt().toDate());
                    calendar.add(Calendar.DAY_OF_YEAR, order.getEstimatedDays());
                    Date estimatedDeliveryDate = calendar.getTime();
                    tvDeliveryDate.setText(dateLabel + sdf.format(estimatedDeliveryDate));
                } else {
                    tvDeliveryDate.setText(dateLabel + "N/A");
                }

                // Product Image using firstProductId
                String productId = order.getFirstProductId();
                if (productId != null) {
                    String collection = "Product".equals(order.getOrderType()) ? "products" : "labTests";
                    db.collection(collection)
                            .document(productId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String imageUrl = documentSnapshot.getString("imageUrl");
                                    if (imageUrl != null) {
                                        Glide.with(itemView.getContext())
                                                .load(imageUrl)
                                                .placeholder(R.drawable.placeholder_image)
                                                .error(R.drawable.default_product_image)
                                                .into(ivProductImage);
                                    } else {
                                        ivProductImage.setImageResource(R.drawable.default_product_image);
                                    }
                                } else {
                                    ivProductImage.setImageResource(R.drawable.default_product_image);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to fetch image: " + e.getMessage());
                                ivProductImage.setImageResource(R.drawable.default_product_image);
                            });
                } else {
                    ivProductImage.setImageResource(R.drawable.default_product_image);
                }

                if ("Product".equals(order.getOrderType())) {
                    btnTracking.setVisibility(View.VISIBLE);
                    btnTracking.setOnClickListener(v -> {
                        TrackingFragment trackingFragment = new TrackingFragment();
                        Bundle args = new Bundle();
                        args.putString("orderId", order.getId());
                        trackingFragment.setArguments(args);
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, trackingFragment)
                                .addToBackStack("InprogressOrdersFragment")
                                .commit();
                    });
                } else {
                    btnTracking.setVisibility(View.GONE);
                }

                btnCall.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + order.getDeliveryBoyPhone()));
                    startActivity(intent);
                });
            }
        }
    }
}