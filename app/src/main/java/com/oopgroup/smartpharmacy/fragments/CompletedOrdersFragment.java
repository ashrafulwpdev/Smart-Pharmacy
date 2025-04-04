package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Address;
import com.oopgroup.smartpharmacy.models.Order;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompletedOrdersFragment extends Fragment {

    private static final String TAG = "CompletedOrdersFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvOrders;
    private OrderAdapter orderAdapter;
    private List<Order> orderList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration ordersListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please log in to view orders", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        rvOrders = view.findViewById(R.id.rv_orders);

        orderList = new ArrayList<>();
        orderAdapter = new OrderAdapter();
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(orderAdapter);

        fetchOrders();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchOrders();
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

    private void fetchOrders() {
        if (ordersListener != null) {
            ordersListener.remove();
        }

        orderList.clear();
        orderAdapter.notifyDataSetChanged();
        swipeRefreshLayout.setRefreshing(true);

        String userId = auth.getCurrentUser().getUid();
        ordersListener = db.collection("orders")
                .whereEqualTo("userId", userId)
                .whereIn("status", List.of("Delivered", "Cancelled")) // Include both statuses
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    swipeRefreshLayout.setRefreshing(false);
                    if (e != null) {
                        Log.e(TAG, "Failed to fetch orders: " + e.getMessage());
                        Toast.makeText(getContext(), "Failed to fetch orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    orderList.clear();
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Order order = doc.toObject(Order.class);
                            order.setId(doc.getId());
                            if (!orderList.contains(order)) { // Prevent duplicates
                                orderList.add(order);
                                fetchAddressForOrder(order);
                            }
                        }
                        Collections.sort(orderList, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
                        orderAdapter.notifyDataSetChanged();
                    } else {
                        orderAdapter.notifyDataSetChanged();
                        Toast.makeText(getContext(), "No completed or cancelled orders found", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchAddressForOrder(Order order) {
        String userId = auth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("addresses")
                .document(order.getAddressId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Address address = doc.toObject(Address.class);
                        // Address fetched, but we don’t add to orderList here (already added in fetchOrders)
                        orderAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch address for order " + order.getId() + ": " + e.getMessage());
                    orderAdapter.notifyDataSetChanged();
                });
    }

    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_past, parent, false);
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
            private RatingBar ratingBar;
            private MaterialButton btnReorder;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.iv_product_image);
                tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvAddress = itemView.findViewById(R.id.tv_address);
                tvItemCount = itemView.findViewById(R.id.tv_item_count);
                tvDeliveryDate = itemView.findViewById(R.id.tv_delivery_date);
                tvTotal = itemView.findViewById(R.id.tv_total);
                ratingBar = itemView.findViewById(R.id.rating_bar);
                btnReorder = itemView.findViewById(R.id.btn_reorder);
            }

            public void bind(Order order) {
                String orderText = "#" + (order.getOrderId() != null ? order.getOrderId() : order.getId()) +
                        (order.isReorder() ? " (Reorder)" : "");
                tvOrderNumber.setText(orderText);
                tvStatus.setText(order.getStatus());
                tvItemCount.setText(order.getItems().size() + " Items");
                String currency = order.getCurrency() != null ? order.getCurrency() : "RM";
                tvTotal.setText(String.format("%s %.2f", currency, order.getGrandTotal()));

                // Status-specific styling
                if ("Delivered".equals(order.getStatus())) {
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    ratingBar.setVisibility(View.VISIBLE);
                    btnReorder.setVisibility(View.VISIBLE);
                } else if ("Cancelled".equals(order.getStatus())) {
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    ratingBar.setVisibility(View.GONE);
                    btnReorder.setVisibility(View.GONE);
                }

                String userId = auth.getCurrentUser().getUid();
                db.collection("users")
                        .document(userId)
                        .collection("addresses")
                        .document(order.getAddressId())
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                Address address = doc.toObject(Address.class);
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
                                tvAddress.setText("Address not available");
                            }
                        })
                        .addOnFailureListener(e -> tvAddress.setText("Address fetch failed"));

                SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());
                String dateLabel = "Delivered".equals(order.getStatus()) ? "Delivered on " : "Cancelled on ";
                tvDeliveryDate.setText(dateLabel + sdf.format(order.getCreatedAt().toDate()));

                String productId = order.getFirstProductId();
                if (productId != null) {
                    String collection = "LabTest".equals(order.getOrderType()) ? "labTests" : "products";
                    db.collection(collection)
                            .document(productId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String imageUrl = documentSnapshot.getString("imageUrl");
                                    Glide.with(itemView.getContext())
                                            .load(imageUrl)
                                            .placeholder(R.drawable.placeholder_image)
                                            .error(R.drawable.default_product_image)
                                            .into(ivProductImage);
                                } else {
                                    ivProductImage.setImageResource(R.drawable.default_product_image);
                                }
                            })
                            .addOnFailureListener(e -> ivProductImage.setImageResource(R.drawable.default_product_image));
                } else {
                    ivProductImage.setImageResource(R.drawable.default_product_image);
                }

                float normalizedRating = (float) order.getRating();
                ratingBar.setRating(normalizedRating);
                ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
                    if (fromUser && "Delivered".equals(order.getStatus())) {
                        int newRating = Math.round(rating);
                        db.collection("orders")
                                .document(order.getId())
                                .update("rating", newRating)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(itemView.getContext(), "Rating saved: " + newRating, Toast.LENGTH_SHORT).show();
                                    order.setRating(newRating);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to save rating: " + e.getMessage());
                                    Toast.makeText(itemView.getContext(), "Failed to save rating", Toast.LENGTH_SHORT).show();
                                    ratingBar.setRating(normalizedRating);
                                });
                    }
                });

                btnReorder.setOnClickListener(v -> reorder(order));
            }

            private void reorder(Order pastOrder) {
                if (!"Delivered".equals(pastOrder.getStatus())) {
                    Toast.makeText(getContext(), "Cannot reorder a cancelled order", Toast.LENGTH_SHORT).show();
                    return;
                }

                Bundle args = new Bundle();
                args.putString("selected_address_id", pastOrder.getAddressId());
                args.putDouble("grand_total", pastOrder.getGrandTotal());
                args.putDouble("discount", pastOrder.getTotalBreakdown() != null && pastOrder.getTotalBreakdown().containsKey("discount") ? (double) pastOrder.getTotalBreakdown().get("discount") : 0.0);
                args.putDouble("delivery_fee", pastOrder.getTotalBreakdown() != null && pastOrder.getTotalBreakdown().containsKey("deliveryFee") ? (double) pastOrder.getTotalBreakdown().get("deliveryFee") : 0.0);

                List<Map<String, Object>> items = pastOrder.getItems();
                ArrayList<HashMap<String, Object>> serializableItems = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        serializableItems.add(new HashMap<>(item));
                    }
                }
                args.putSerializable("reorder_items", serializableItems);
                args.putString("reorder_payment_method", pastOrder.getPaymentMethod());

                CheckoutFragment checkoutFragment = new CheckoutFragment();
                checkoutFragment.setArguments(args);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, checkoutFragment)
                        .addToBackStack("CompletedOrdersFragment")
                        .commit();

                Log.d(TAG, "Navigated to CheckoutFragment for reorder of order #" + pastOrder.getOrderId());
            }
        }
    }
}