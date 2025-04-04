package com.oopgroup.smartpharmacy.adminstaff;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.DashboardAdapter;
import com.oopgroup.smartpharmacy.adapters.DashboardStat;

import java.util.ArrayList;
import java.util.List;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";
    private FirebaseFirestore db;
    private RecyclerView recyclerView;
    private DashboardAdapter adapter;
    private List<ListenerRegistration> listenerRegistrations;
    private List<DashboardStat> stats;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        db = FirebaseFirestore.getInstance();
        listenerRegistrations = new ArrayList<>();
        stats = new ArrayList<>();
        setupRecyclerView();
        fetchDashboardData();
        return view;
    }

    private void setupRecyclerView() {
        adapter = new DashboardAdapter(stat -> {
            switch (stat.getTitle()) {
                case "Total Orders": navigateToOrders(); break;
                case "Total Users": navigateToUsers(); break;
                // Add more cases
            }
        });
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerView.setAdapter(adapter);
    }

    private void fetchDashboardData() {
        // Initialize stats
        stats.add(new DashboardStat("Total Orders", "0", R.drawable.ic_orders));
        stats.add(new DashboardStat("Pending Deliveries", "0", R.drawable.ic_pending));
        stats.add(new DashboardStat("Completed Deliveries", "0", R.drawable.ic_completed));
        stats.add(new DashboardStat("Cancelled Orders", "0", R.drawable.ic_cancelled)); // New stat
        stats.add(new DashboardStat("Total Revenue", "RM0", R.drawable.ic_revenue));
        stats.add(new DashboardStat("Active Free Delivery Areas", "0", R.drawable.ic_dash_delivery));
        stats.add(new DashboardStat("Total Users", "0", R.drawable.ic_users));
        stats.add(new DashboardStat("Total Products", "0", R.drawable.ic_products));
        stats.add(new DashboardStat("Active Banners", "0", R.drawable.ic_banners));
        stats.add(new DashboardStat("Total Categories", "0", R.drawable.ic_categories));
        stats.add(new DashboardStat("Total Coupons", "0", R.drawable.ic_coupons));
        stats.add(new DashboardStat("Total Notifications", "0", R.drawable.ic_notifications)); // New stat
        adapter.submitList(stats);

        // Total Orders
        listenerRegistrations.add(db.collection("orders")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Orders", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Total Orders: " + snapshot.size());
                        updateStat("Total Orders", String.valueOf(snapshot.size()));
                    }
                }));

        // Pending Deliveries
        listenerRegistrations.add(db.collection("orders")
                .whereEqualTo("status", "Processing")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Pending Deliveries", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Pending Deliveries: " + snapshot.size());
                        updateStat("Pending Deliveries", String.valueOf(snapshot.size()));
                    }
                }));

        // Completed Deliveries
        listenerRegistrations.add(db.collection("orders")
                .whereEqualTo("status", "Delivered")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Completed Deliveries", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Completed Deliveries: " + snapshot.size());
                        updateStat("Completed Deliveries", String.valueOf(snapshot.size()));
                    }
                }));

        // Cancelled Orders (new stat)
        listenerRegistrations.add(db.collection("orders")
                .whereEqualTo("status", "Cancelled")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Cancelled Orders", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Cancelled Orders: " + snapshot.size());
                        updateStat("Cancelled Orders", String.valueOf(snapshot.size()));
                    }
                }));

        // Total Revenue
        listenerRegistrations.add(db.collection("orders")
                .whereEqualTo("status", "Delivered")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Revenue", e);
                        return;
                    }
                    if (snapshot != null) {
                        double totalRevenue = 0;
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            Double fee = doc.getDouble("totalBreakdown.deliveryFee");
                            totalRevenue += fee != null ? fee : 0;
                        }
                        Log.d(TAG, "Total Revenue: RM" + totalRevenue);
                        updateStat("Total Revenue", "RM" + String.format("%.2f", totalRevenue));
                    }
                }));

        // Active Free Delivery Areas
        listenerRegistrations.add(db.collection("delivery_fees")
                .whereEqualTo("freeDelivery", true)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Active Free Delivery Areas", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Active Free Delivery Areas: " + snapshot.size());
                        updateStat("Active Free Delivery Areas", String.valueOf(snapshot.size()));
                    }
                }));

        // Total Users
        listenerRegistrations.add(db.collection("users")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Users", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Total Users: " + snapshot.size());
                        updateStat("Total Users", String.valueOf(snapshot.size()));
                    }
                }));

        // Total Products
        listenerRegistrations.add(db.collection("products")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Products", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Total Products: " + snapshot.size());
                        updateStat("Total Products", String.valueOf(snapshot.size()));
                    }
                }));

        // Active Banners
        listenerRegistrations.add(db.collection("banners")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Active Banners", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Active Banners: " + snapshot.size());
                        updateStat("Active Banners", String.valueOf(snapshot.size()));
                    }
                }));

        // Total Categories
        listenerRegistrations.add(db.collection("categories")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Categories", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Total Categories: " + snapshot.size());
                        updateStat("Total Categories", String.valueOf(snapshot.size()));
                    }
                }));

        // Total Coupons
        listenerRegistrations.add(db.collection("coupons")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Coupons", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Total Coupons: " + snapshot.size());
                        updateStat("Total Coupons", String.valueOf(snapshot.size()));
                    }
                }));

        // Total Notifications (new stat)
        listenerRegistrations.add(db.collection("notifications")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching Total Notifications", e);
                        return;
                    }
                    if (snapshot != null) {
                        Log.d(TAG, "Total Notifications: " + snapshot.size());
                        updateStat("Total Notifications", String.valueOf(snapshot.size()));
                    }
                }));
    }

    private void updateStat(String title, String value) {
        for (DashboardStat stat : stats) {
            if (stat.getTitle().equals(title)) {
                stat.setValue(value);
                break;
            }
        }
        adapter.submitList(new ArrayList<>(stats));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (ListenerRegistration registration : listenerRegistrations) {
            registration.remove();
        }
    }

    private void navigateToOrders() { /* Navigation logic */ }
    private void navigateToUsers() { /* Navigation logic */ }
}