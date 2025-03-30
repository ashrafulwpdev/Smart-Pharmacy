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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class UpcomingOrderFragment extends Fragment {

    private static final String TAG = "UpcomingOrderFragment";

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
        orderAdapter = new OrderAdapter(true); // true for upcoming orders
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
                .whereIn("status", List.of("Pending", "Received"))
                .orderBy("createdAt", Query.Direction.DESCENDING) // Sort by newest first
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    orderList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Order order = doc.toObject(Order.class);
                        order.setId(doc.getId()); // Firestore document ID
                        fetchAddressForOrder(order);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch orders: " + e.getMessage());
                    if (e.getMessage().contains("FAILED_PRECONDITION") && e.getMessage().contains("requires an index")) {
                        Toast.makeText(getContext(), "Order list is loading. Please wait a moment and try again.", Toast.LENGTH_LONG).show();
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
                        // Sort by createdAt descending to maintain newest on top
                        Collections.sort(orderList, new Comparator<Order>() {
                            @Override
                            public int compare(Order o1, Order o2) {
                                return o2.getCreatedAt().compareTo(o1.getCreatedAt()); // Newest first
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
            holder.bind(order);
        }

        @Override
        public int getItemCount() {
            return orderList.size();
        }

        private class OrderViewHolder extends RecyclerView.ViewHolder {

            private ImageView ivProductImage;
            private TextView tvOrderNumber, tvStatus, tvAddress, tvItemCount, tvDeliveryDate, tvTotal;
            private Button btnMessage, btnCall;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.iv_product_image);
                tvOrderNumber = itemView.findViewById(R.id.tv_order_number);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvAddress = itemView.findViewById(R.id.tv_address);
                tvItemCount = itemView.findViewById(R.id.tv_item_count);
                tvDeliveryDate = itemView.findViewById(R.id.tv_delivery_date);
                tvTotal = itemView.findViewById(R.id.tv_total);

                if (isUpcoming) {
                    btnMessage = itemView.findViewById(R.id.btn_message);
                    btnCall = itemView.findViewById(R.id.btn_call);
                }
            }

            public void bind(Order order) {
                tvOrderNumber.setText("#" + (order.getOrderId() != null ? order.getOrderId() : order.getId()));
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
                String deliveryDate = "Delivery by " + sdf.format(order.getCreatedAt().toDate());
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

                if (isUpcoming) {
                    btnMessage.setOnClickListener(v -> {
                        Toast.makeText(getContext(), "Message functionality not implemented", Toast.LENGTH_SHORT).show();
                    });

                    btnCall.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_DIAL);
                        intent.setData(Uri.parse("tel:1234567890"));
                        startActivity(intent);
                    });
                }
            }
        }
    }
}