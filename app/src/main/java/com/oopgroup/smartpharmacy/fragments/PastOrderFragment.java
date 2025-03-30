package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Address;
import com.oopgroup.smartpharmacy.models.Order;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PastOrderFragment extends Fragment {

    private static final String TAG = "PastOrderFragment";

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

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        rvOrders = view.findViewById(R.id.rv_orders);

        orderList = new ArrayList<>();
        orderAdapter = new OrderAdapter(false); // false for past orders
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(orderAdapter);

        fetchOrders();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchOrders();
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void fetchOrders() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("orders")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "Delivered")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    orderList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // Log the raw Firestore data
                        Log.d(TAG, "Raw document data: " + doc.getData());
                        Order order = doc.toObject(Order.class);
                        // Log what the app parsed for isReorder
                        Log.d(TAG, "Parsed isReorder: " + order.isReorder());
                        order.setId(doc.getId());
                        fetchAddressForOrder(order);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch orders: " + e.getMessage());
                    if (e.getMessage().contains("FAILED_PRECONDITION") && e.getMessage().contains("requires an index")) {
                        Toast.makeText(getContext(), "Order history is loading. Please wait a moment and try again.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to fetch orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                        orderList.add(order);
                        Collections.sort(orderList, new Comparator<Order>() {
                            @Override
                            public int compare(Order o1, Order o2) {
                                return o2.getCreatedAt().compareTo(o1.getCreatedAt()); // Latest first
                            }
                        });
                        orderAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch address for order " + order.getId() + ": " + e.getMessage()));
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
                    .inflate(isUpcoming ? R.layout.item_order_upcoming : R.layout.item_order_past, parent, false);
            return new OrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            Order order = orderList.get(position);
            holder.bind(order); // Removed serial number parameter
        }

        @Override
        public int getItemCount() {
            return orderList.size();
        }

        private class OrderViewHolder extends RecyclerView.ViewHolder {

            private ImageView ivProductImage;
            private TextView tvOrderNumber, tvStatus, tvAddress, tvItemCount, tvDeliveryDate, tvTotal;
            private RatingBar ratingBar;
            private Button btnReorder;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.iv_product_image);
                tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvAddress = itemView.findViewById(R.id.tv_address);
                tvItemCount = itemView.findViewById(R.id.tv_item_count);
                tvDeliveryDate = itemView.findViewById(R.id.tv_delivery_date);
                tvTotal = itemView.findViewById(R.id.tv_total);

                if (!isUpcoming) {
                    ratingBar = itemView.findViewById(R.id.rating_bar);
                    btnReorder = itemView.findViewById(R.id.btn_reorder);
                    if (ratingBar != null) {
                        ratingBar.setNumStars(5);
                        ratingBar.setMax(5);
                        ratingBar.setStepSize(1.0f);
                    }
                }
            }

            public void bind(Order order) { // Removed serialNumber parameter
                String orderText = "#" + (order.getOrderId() != null ? order.getOrderId() : order.getId()) +
                        (order.isReorder() ? " (Reorder)" : ""); // Removed serial number
                tvOrderNumber.setText(orderText);
                tvStatus.setText(order.getStatus());
                tvItemCount.setText(order.getItems().size() + " Items");
                String currency = order.getCurrency() != null ? order.getCurrency() : "RM";
                tvTotal.setText(String.format("%s%.2f", currency, order.getGrandTotal()));

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
                                tvAddress.setText(fullAddress.length() > 30 ? fullAddress.substring(0, 30) + "..." : fullAddress);
                            }
                        });

                SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());
                String deliveryDate = "Delivered on " + sdf.format(order.getCreatedAt().toDate());
                tvDeliveryDate.setText(deliveryDate);

                String productId = order.getFirstProductId();
                if (productId != null) {
                    db.collection("products")
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
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to fetch product image: " + e.getMessage());
                                ivProductImage.setImageResource(R.drawable.default_product_image);
                            });
                }

                // In the bind() method of PastOrderFragment's OrderViewHolder:
                if (!isUpcoming && ratingBar != null && btnReorder != null) {
                    // Convert int rating to float for RatingBar
                    float normalizedRating = (float) order.getRating();
                    ratingBar.setRating(normalizedRating);

                    ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
                        if (fromUser) {
                            int newRating = Math.round(rating);
                            Log.d(TAG, "New rating for order " + order.getId() + ": " + newRating);
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
            }

            private void reorder(Order pastOrder) {
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
                        .addToBackStack("PastOrderFragment")
                        .commit();

                Log.d(TAG, "Navigated to CheckoutFragment for reorder of order #" + pastOrder.getOrderId());
            }
        }
    }
}