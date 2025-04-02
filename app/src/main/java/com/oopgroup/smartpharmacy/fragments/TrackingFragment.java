package com.oopgroup.smartpharmacy.fragments;

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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Order;
import com.oopgroup.smartpharmacy.models.Tracking;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrackingFragment extends Fragment {

    private static final String TAG = "TrackingFragment";

    private RecyclerView rvOrders;
    private TextView tvNoData;
    private SwipeRefreshLayout swipeRefreshLayout;
    private OrderTrackingAdapter orderTrackingAdapter;
    private Map<String, List<Tracking>> orderTrackingMap;
    private Map<String, Order> orderMap;
    private FirebaseFirestore db;
    private String orderId; // Specific order ID, null if showing all orders

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tracking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        // Get the orderId from arguments, if provided
        if (getArguments() != null) {
            orderId = getArguments().getString("orderId");
        }

        rvOrders = view.findViewById(R.id.rv_tracking);
        tvNoData = view.findViewById(R.id.tv_no_data);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);

        if (rvOrders == null) {
            Log.e(TAG, "RecyclerView rv_tracking not found in fragment_tracking.xml");
            Toast.makeText(getContext(), "Error: RecyclerView not found", Toast.LENGTH_LONG).show();
            return;
        }

        orderTrackingMap = new HashMap<>();
        orderMap = new HashMap<>();
        orderTrackingAdapter = new OrderTrackingAdapter(orderTrackingMap, orderMap);
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(orderTrackingAdapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchTrackingHistory();
            swipeRefreshLayout.setRefreshing(false);
        });

        fetchTrackingHistory();
    }

    private void fetchTrackingHistory() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "User is not authenticated");
            Toast.makeText(getContext(), "Please log in to view tracking", Toast.LENGTH_SHORT).show();
            tvNoData.setVisibility(View.VISIBLE);
            rvOrders.setVisibility(View.GONE);
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (orderId != null) {
            // Case 1: Fetch tracking for a specific order
            Log.d(TAG, "Fetching tracking for specific order: " + orderId);
            fetchSpecificOrderTracking(userId);
        } else {
            // Case 2: Fetch tracking for all orders
            Log.d(TAG, "Fetching tracking for all orders for user: " + userId);
            fetchAllOrdersTracking(userId);
        }
    }

    private void fetchSpecificOrderTracking(String userId) {
        db.collection("orders")
                .document(orderId)
                .get()
                .addOnSuccessListener(orderDoc -> {
                    if (!orderDoc.exists()) {
                        Log.d(TAG, "Order not found: " + orderId);
                        Toast.makeText(getContext(), "Order not found", Toast.LENGTH_SHORT).show();
                        tvNoData.setVisibility(View.VISIBLE);
                        rvOrders.setVisibility(View.GONE);
                        return;
                    }

                    Order order = orderDoc.toObject(Order.class);
                    order.setId(orderId);
                    orderMap.put(orderId, order);

                    db.collection("orders")
                            .document(orderId)
                            .collection("tracking")
                            .orderBy("updatedAt", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(trackingSnapshots -> {
                                List<Tracking> trackingList = new ArrayList<>();
                                if (trackingSnapshots.isEmpty()) {
                                    Log.d(TAG, "No tracking data for order: " + orderId);
                                    Toast.makeText(getContext(), "No tracking data available", Toast.LENGTH_SHORT).show();
                                } else {
                                    for (QueryDocumentSnapshot trackingDoc : trackingSnapshots) {
                                        Tracking tracking = trackingDoc.toObject(Tracking.class);
                                        tracking.setId(trackingDoc.getId());
                                        trackingList.add(tracking);
                                    }
                                    Collections.sort(trackingList, (t1, t2) -> t2.getUpdatedAt().compareTo(t1.getUpdatedAt()));
                                    orderTrackingMap.put(orderId, trackingList);
                                    orderTrackingAdapter.updateOrderIds();
                                }
                                updateVisibility();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to fetch tracking for order " + orderId + ": " + e.getMessage());
                                Toast.makeText(getContext(), "Failed to fetch tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                tvNoData.setVisibility(View.VISIBLE);
                                rvOrders.setVisibility(View.GONE);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch order " + orderId + ": " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to fetch order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    tvNoData.setVisibility(View.VISIBLE);
                    rvOrders.setVisibility(View.GONE);
                });
    }

    private void fetchAllOrdersTracking(String userId) {
        db.collection("orders")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(orderSnapshots -> {
                    if (orderSnapshots.isEmpty()) {
                        Log.d(TAG, "No orders found for user: " + userId);
                        Toast.makeText(getContext(), "No orders found", Toast.LENGTH_SHORT).show();
                        tvNoData.setVisibility(View.VISIBLE);
                        rvOrders.setVisibility(View.GONE);
                        return;
                    }

                    orderTrackingMap.clear();
                    orderMap.clear();
                    for (QueryDocumentSnapshot orderDoc : orderSnapshots) {
                        String orderId = orderDoc.getId();
                        Order order = orderDoc.toObject(Order.class);
                        order.setId(orderId);
                        orderMap.put(orderId, order);

                        db.collection("orders")
                                .document(orderId)
                                .collection("tracking")
                                .orderBy("updatedAt", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(trackingSnapshots -> {
                                    List<Tracking> trackingList = new ArrayList<>();
                                    if (!trackingSnapshots.isEmpty()) {
                                        for (QueryDocumentSnapshot trackingDoc : trackingSnapshots) {
                                            Tracking tracking = trackingDoc.toObject(Tracking.class);
                                            tracking.setId(trackingDoc.getId());
                                            trackingList.add(tracking);
                                        }
                                        Collections.sort(trackingList, (t1, t2) -> t2.getUpdatedAt().compareTo(t1.getUpdatedAt()));
                                        orderTrackingMap.put(orderId, trackingList);
                                        orderTrackingAdapter.updateOrderIds();
                                    }
                                    updateVisibility();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to fetch tracking for order " + orderId + ": " + e.getMessage());
                                    Toast.makeText(getContext(), "Failed to fetch tracking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch orders: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to fetch orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    tvNoData.setVisibility(View.VISIBLE);
                    rvOrders.setVisibility(View.GONE);
                });
    }

    private void updateVisibility() {
        if (orderTrackingMap.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            rvOrders.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            rvOrders.setVisibility(View.VISIBLE);
        }
    }

    private class OrderTrackingAdapter extends RecyclerView.Adapter<OrderTrackingAdapter.OrderViewHolder> {

        private final Map<String, List<Tracking>> orderTrackingMap;
        private final Map<String, Order> orderMap;
        private List<String> orderIds;
        private final Map<String, Boolean> expandedStates;

        public OrderTrackingAdapter(Map<String, List<Tracking>> orderTrackingMap, Map<String, Order> orderMap) {
            this.orderTrackingMap = orderTrackingMap;
            this.orderMap = orderMap;
            this.orderIds = new ArrayList<>();
            this.expandedStates = new HashMap<>();
        }

        public void updateOrderIds() {
            orderIds.clear();
            orderIds.addAll(orderTrackingMap.keySet());
            Collections.sort(orderIds, (o1, o2) -> o2.compareTo(o1));
            notifyDataSetChanged();
            Log.d(TAG, "Updated orderIds, new size: " + orderIds.size());
        }

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_tracking, parent, false);
            return new OrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            String orderId = orderIds.get(position);
            List<Tracking> trackingList = orderTrackingMap.get(orderId);
            Order order = orderMap.get(orderId);
            holder.bind(orderId, trackingList, order);
        }

        @Override
        public int getItemCount() {
            return orderIds.size();
        }

        private class OrderViewHolder extends RecyclerView.ViewHolder {

            private final TextView tvOrderId;
            private final TextView tvEstimatedDelivery;
            private final ImageView ivOrderImage;
            private final RecyclerView rvTrackingDetails;
            private final TrackingDetailsAdapter trackingDetailsAdapter;
            private boolean isExpanded;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tv_order_id);
                tvEstimatedDelivery = itemView.findViewById(R.id.tv_estimated_delivery);
                ivOrderImage = itemView.findViewById(R.id.iv_order_image);
                rvTrackingDetails = itemView.findViewById(R.id.rv_tracking_details);
                rvTrackingDetails.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                trackingDetailsAdapter = new TrackingDetailsAdapter(new ArrayList<>());
                rvTrackingDetails.setAdapter(trackingDetailsAdapter);

                itemView.setOnClickListener(v -> {
                    isExpanded = !isExpanded;
                    expandedStates.put(tvOrderId.getText().toString(), isExpanded);
                    rvTrackingDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                });
            }

            public void bind(String orderId, List<Tracking> trackingList, Order order) {
                tvOrderId.setText("Order #" + orderId);
                trackingDetailsAdapter.updateTrackingList(trackingList);
                isExpanded = expandedStates.getOrDefault(orderId, TrackingFragment.this.orderId != null); // Expand by default if specific order
                rvTrackingDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

                // Load order image
                String productId = order.getFirstProductId();
                if (productId != null) {
                    db.collection(order.getOrderType().equals("LabTest") ? "labTests" : "products")
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
                                                .into(ivOrderImage);
                                    } else {
                                        ivOrderImage.setImageResource(R.drawable.default_product_image);
                                    }
                                } else {
                                    ivOrderImage.setImageResource(R.drawable.default_product_image);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to fetch image: " + e.getMessage());
                                ivOrderImage.setImageResource(R.drawable.default_product_image);
                            });
                } else {
                    ivOrderImage.setImageResource(R.drawable.default_product_image);
                }

                // Set estimated delivery
                if (order.getCreatedAt() != null) {
                    long createdAtMillis = order.getCreatedAt().toDate().getTime();
                    long estimatedDeliveryMillis = createdAtMillis + (3 * 24 * 60 * 60 * 1000L); // Add 3 days
                    Date estimatedDeliveryDate = new Date(estimatedDeliveryMillis);
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                    tvEstimatedDelivery.setText("Est. Delivery: " + sdf.format(estimatedDeliveryDate));
                } else {
                    tvEstimatedDelivery.setText("Est. Delivery: N/A");
                }
            }
        }
    }

    private class TrackingDetailsAdapter extends RecyclerView.Adapter<TrackingDetailsAdapter.TrackingViewHolder> {

        private List<Tracking> trackingList;

        public TrackingDetailsAdapter(List<Tracking> trackingList) {
            this.trackingList = trackingList;
        }

        public void updateTrackingList(List<Tracking> newTrackingList) {
            this.trackingList = newTrackingList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public TrackingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tracking_detail, parent, false);
            return new TrackingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackingViewHolder holder, int position) {
            Tracking tracking = trackingList.get(position);
            holder.bind(tracking, position);
        }

        @Override
        public int getItemCount() {
            return trackingList.size();
        }

        private class TrackingViewHolder extends RecyclerView.ViewHolder {

            private final View timelineDot;
            private final View timelineLineTop, timelineLineBottom;
            private final TextView tvStatus, tvDetails, tvTimestamp;
            private final ImageView statusIcon;

            public TrackingViewHolder(@NonNull View itemView) {
                super(itemView);
                timelineDot = itemView.findViewById(R.id.timeline_dot);
                timelineLineTop = itemView.findViewById(R.id.timeline_line_top);
                timelineLineBottom = itemView.findViewById(R.id.timeline_line_bottom);
                tvStatus = itemView.findViewById(R.id.tv_status);
                tvDetails = itemView.findViewById(R.id.tv_details);
                tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
                statusIcon = itemView.findViewById(R.id.status_icon);
            }

            public void bind(Tracking tracking, int position) {
                tvStatus.setText(tracking.getStatus());
                tvDetails.setText(tracking.getDetails() != null ? tracking.getDetails() : "");

                if (tracking.getUpdatedAt() != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy, hh:mm a", Locale.getDefault());
                    tvTimestamp.setText(sdf.format(tracking.getUpdatedAt().toDate()));
                } else {
                    tvTimestamp.setText("N/A");
                }

                if (position == 0) {
                    timelineLineTop.setVisibility(View.INVISIBLE);
                } else {
                    timelineLineTop.setVisibility(View.VISIBLE);
                }

                if (position == trackingList.size() - 1) {
                    timelineLineBottom.setVisibility(View.INVISIBLE);
                } else {
                    timelineLineBottom.setVisibility(View.VISIBLE);
                }

                switch (tracking.getStatus().toLowerCase()) {
                    case "order placed":
                        timelineDot.setBackgroundResource(R.drawable.circle_green);
                        statusIcon.setBackgroundResource(R.drawable.ic_cart);
                        break;
                    case "payment processed":
                    case "awaiting confirmation":
                        timelineDot.setBackgroundResource(R.drawable.circle_purple);
                        statusIcon.setBackgroundResource(R.drawable.payment_ic);
                        break;
                    case "order confirmed":
                        timelineDot.setBackgroundResource(R.drawable.circle_orange);
                        statusIcon.setBackgroundResource(R.drawable.ic_check_circle);
                        break;
                    case "preparing order":
                        timelineDot.setBackgroundResource(R.drawable.circle_blue);
                        statusIcon.setBackgroundResource(R.drawable.otp_phone_icon);
                        break;
                    case "awaiting pickup":
                        timelineDot.setBackgroundResource(R.drawable.circle_purple);
                        statusIcon.setBackgroundResource(R.drawable.premium_delete_icon);
                        break;
                    case "shipped":
                        timelineDot.setBackgroundResource(R.drawable.circle_img);
                        statusIcon.setBackgroundResource(R.drawable.ic_add);
                        break;
                    case "out for delivery":
                        timelineDot.setBackgroundResource(R.drawable.circle_default);
                        statusIcon.setBackgroundResource(R.drawable.ic_dialog_close);
                        break;
                    case "delivered":
                        timelineDot.setBackgroundResource(R.drawable.cir_edit_ic);
                        statusIcon.setBackgroundResource(R.drawable.ic_call);
                        break;
                    case "payment collected":
                        timelineDot.setBackgroundResource(R.drawable.circle_orange);
                        statusIcon.setBackgroundResource(R.drawable.ic_check_circle);
                        break;
                    case "completed":
                        timelineDot.setBackgroundResource(R.drawable.cir_edit_ic);
                        statusIcon.setBackgroundResource(R.drawable.ic_add);
                        break;
                    case "cancelled":
                        timelineDot.setBackgroundResource(R.drawable.circle_orange);
                        statusIcon.setBackgroundResource(R.drawable.ic_play);
                        break;
                    default:
                        timelineDot.setBackgroundResource(R.drawable.circle_default);
                        statusIcon.setBackgroundResource(R.drawable.ic_call);
                }
            }
        }
    }
}