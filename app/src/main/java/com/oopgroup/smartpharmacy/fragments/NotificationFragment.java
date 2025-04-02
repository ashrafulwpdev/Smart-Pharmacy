package com.oopgroup.smartpharmacy.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Notification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvNotifications;
    private NotificationAdapter notificationAdapter;
    private List<Object> notificationList;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration notificationListener;
    private int unreadCount = 0;
    private ImageView moreIcon;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        moreIcon = view.findViewById(R.id.more_icon);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        rvNotifications = view.findViewById(R.id.rv_notifications);

        notificationList = new ArrayList<>();
        notificationAdapter = new NotificationAdapter();
        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        rvNotifications.setAdapter(notificationAdapter);

        setupRealTimeListener();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            setupRealTimeListener();
            swipeRefreshLayout.setRefreshing(false);
        });

        moreIcon.setOnClickListener(v -> showPopupMenu(v));
    }

    private void setupRealTimeListener() {
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Current user ID: " + userId); // Debug log for user ID
        if (notificationListener != null) {
            notificationListener.remove();
        }

        notificationListener = db.collection("notifications")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to fetch notifications: " + error.getMessage());
                        Toast.makeText(getContext(), "Failed to fetch notifications: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<Notification> tempList = new ArrayList<>();
                    if (snapshot != null) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            Notification notification = doc.toObject(Notification.class);
                            notification.setId(doc.getId());
                            tempList.add(notification);
                        }
                    }

                    notificationList.clear();
                    unreadCount = 0;
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    String today = sdf.format(new Date());
                    String yesterday = sdf.format(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));

                    boolean hasToday = false, hasYesterday = false, hasOlder = false;
                    for (Notification notification : tempList) {
                        Log.d(TAG, "Notification ID: " + notification.getId() + ", isRead: " + notification.isRead() + ", userId: " + notification.getUserId());
                        if (!notification.isRead()) {
                            unreadCount++;
                        }
                        String notificationDate = sdf.format(notification.getCreatedAt().toDate());
                        if (notificationDate.equals(today)) {
                            if (!hasToday) {
                                notificationList.add("Today");
                                hasToday = true;
                            }
                            notificationList.add(notification);
                        } else if (notificationDate.equals(yesterday)) {
                            if (!hasYesterday) {
                                notificationList.add("Yesterday");
                                hasYesterday = true;
                            }
                            notificationList.add(notification);
                        } else {
                            if (!hasOlder) {
                                notificationList.add("Older");
                                hasOlder = true;
                            }
                            notificationList.add(notification);
                        }
                    }
                    Log.d(TAG, "Total unread count after refresh: " + unreadCount);
                    notificationAdapter.notifyDataSetChanged();
                });
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.notification_options_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_clear_all) {
                clearAllNotifications();
                return true;
            } else if (itemId == R.id.action_mark_all_read) {
                markAllAsRead();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void clearAllNotifications() {
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Clearing all notifications for userId: " + userId);
        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }
                    Log.d(TAG, "All notifications cleared");
                    Toast.makeText(getContext(), "All notifications cleared", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to clear notifications: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to clear notifications: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void markAllAsRead() {
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Marking all notifications as read for userId: " + userId);
        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().update("isRead", true);
                    }
                    Log.d(TAG, "All notifications marked as read");
                    Toast.makeText(getContext(), "All notifications marked as read", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark all as read: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to mark all as read: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }

    private class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_NOTIFICATION = 1;

        @Override
        public int getItemViewType(int position) {
            return notificationList.get(position) instanceof String ? TYPE_HEADER : TYPE_NOTIFICATION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_notification_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_notification, parent, false);
                return new NotificationViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((String) notificationList.get(position));
            } else {
                ((NotificationViewHolder) holder).bind((Notification) notificationList.get(position), position);
            }
        }

        @Override
        public int getItemCount() {
            return notificationList.size();
        }

        private class HeaderViewHolder extends RecyclerView.ViewHolder {
            private TextView tvHeader;

            public HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvHeader = itemView.findViewById(R.id.tv_header);
            }

            public void bind(String header) {
                tvHeader.setText(header);
            }
        }

        private class NotificationViewHolder extends RecyclerView.ViewHolder {
            private ImageView ivIcon;
            private TextView tvTitle, tvMessage, tvTime, tvUnreadBadge;

            public NotificationViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_icon);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvMessage = itemView.findViewById(R.id.tv_message);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvUnreadBadge = itemView.findViewById(R.id.tv_unread_badge);
            }

            @SuppressLint("NotifyDataSetChanged")
            public void bind(Notification notification, int position) {
                tvTitle.setText(notification.getTitle());
                tvMessage.setText(notification.getMessage());

                long diffInMillis = System.currentTimeMillis() - notification.getCreatedAt().toDate().getTime();
                long diffInMinutes = diffInMillis / (1000 * 60);
                if (diffInMinutes < 60) {
                    tvTime.setText(diffInMinutes + " min ago");
                } else if (diffInMinutes < 1440) {
                    tvTime.setText((diffInMinutes / 60) + " hours ago");
                } else {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                    tvTime.setText(sdf.format(notification.getCreatedAt().toDate()));
                }

                switch (notification.getType()) {
                    case "TestBooking":
                        ivIcon.setImageResource(R.drawable.ic_notification_pink);
                        break;
                    case "OrderPlaced":
                        ivIcon.setImageResource(R.drawable.ic_notification_orange);
                        break;
                    case "LabTestCompleted":
                    case "AdminNotice":
                    case "PharmacyNotice":
                    case "OrderReminder":
                    default:
                        ivIcon.setImageResource(R.drawable.ic_notification_blue);
                }

                if (!notification.isRead()) {
                    Log.d(TAG, "Unread notification at position " + position + ", count: " + unreadCount);
                    tvUnreadBadge.setVisibility(View.VISIBLE);
                    tvUnreadBadge.setText(String.valueOf(unreadCount));
                } else {
                    tvUnreadBadge.setVisibility(View.GONE);
                }

                itemView.setOnClickListener(v -> {
                    if (!notification.isRead()) {
                        Log.d(TAG, "Attempting to mark notification as read: " + notification.getId() + " for userId: " + notification.getUserId());
                        notification.setRead(true);
                        unreadCount--;
                        db.collection("notifications")
                                .document(notification.getId())
                                .update("isRead", true)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Notification marked as read, new unread count: " + unreadCount);
                                    notificationAdapter.notifyDataSetChanged();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update isRead: " + e.getMessage());
                                    notification.setRead(false);
                                    unreadCount++;
                                    Toast.makeText(getContext(), "Failed to mark as read: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    notificationAdapter.notifyDataSetChanged();
                                });
                    }
                });
            }
        }
    }
}