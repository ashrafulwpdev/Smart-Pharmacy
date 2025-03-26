package com.oopgroup.smartpharmacy.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Banner;
import com.oopgroup.smartpharmacy.models.Category;
import com.oopgroup.smartpharmacy.models.LabTest;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.List;
import java.util.Map;

public class AdminItemAdapter extends RecyclerView.Adapter<AdminItemAdapter.ViewHolder> {
    private static final String TAG = "AdminItemAdapter";

    private List<Object> items;
    private final OnItemClickListener editListener;
    private final OnItemClickListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(Object item);
    }

    public AdminItemAdapter(List<Object> items, OnItemClickListener editListener, OnItemClickListener deleteListener) {
        this.items = items;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object item = items.get(position);

        holder.imageView.setVisibility(View.VISIBLE); // Ensure ImageView is visible by default

        if (item instanceof Banner) {
            Banner banner = (Banner) item;
            holder.titleTextView.setText(banner.getTitle());
            holder.descriptionTextView.setText(banner.getDescription());
            holder.additionalInfoTextView.setText("Discount: " + banner.getDiscount());
            Log.d(TAG, "Loading banner image URL: " + banner.getImageUrl());
            Glide.with(holder.itemView.getContext())
                    .load(banner.getImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(holder.imageView);
        } else if (item instanceof Category) {
            Category category = (Category) item;
            holder.titleTextView.setText(category.getName());
            holder.descriptionTextView.setText("Product Count: " + category.getProductCount());
            holder.additionalInfoTextView.setText("");
            Log.d(TAG, "Loading category image URL: " + category.getImageUrl());
            Glide.with(holder.itemView.getContext())
                    .load(category.getImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(holder.imageView);
        } else if (item instanceof LabTest) {
            LabTest labTest = (LabTest) item;
            holder.titleTextView.setText(labTest.getName());
            holder.descriptionTextView.setText("");
            holder.additionalInfoTextView.setText("");
            Log.d(TAG, "Loading lab test image URL: " + labTest.getImageUrl());
            Glide.with(holder.itemView.getContext())
                    .load(labTest.getImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(holder.imageView);
        } else if (item instanceof Product) {
            Product product = (Product) item;
            holder.titleTextView.setText(product.getName());
            holder.descriptionTextView.setText("Price: $" + product.getPrice());
            holder.additionalInfoTextView.setText("Rating: " + product.getRating() + " (" + product.getReviewCount() + " reviews)");
            Log.d(TAG, "Loading product image URL: " + product.getImageUrl());
            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(holder.imageView);
        } else if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            if (map.containsKey("email")) {
                holder.titleTextView.setText((String) map.get("name"));
                holder.descriptionTextView.setText((String) map.get("email"));
                holder.additionalInfoTextView.setText("");
                holder.imageView.setVisibility(View.GONE);
            } else if (map.containsKey("status")) {
                holder.titleTextView.setText("Order ID: " + map.get("id"));
                holder.descriptionTextView.setText("User ID: " + map.get("userId"));
                holder.additionalInfoTextView.setText("Status: " + map.get("status"));
                holder.imageView.setVisibility(View.GONE);
            }
        }

        holder.editButton.setOnClickListener(v -> {
            if (editListener != null) {
                editListener.onItemClick(item);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        int count = items != null ? items.size() : 0;
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    public void updateItems(List<Object> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleTextView;
        TextView descriptionTextView;
        TextView additionalInfoTextView;
        Button editButton;
        Button deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.itemImage);
            titleTextView = itemView.findViewById(R.id.itemTitle);
            descriptionTextView = itemView.findViewById(R.id.itemDescription);
            additionalInfoTextView = itemView.findViewById(R.id.itemAdditionalInfo);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}