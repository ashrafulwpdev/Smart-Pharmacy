package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryListAdapter extends RecyclerView.Adapter<CategoryListAdapter.CategoryViewHolder> {

    private final Context context;
    private List<Category> categoryList;
    private final OnCategoryClickListener onCategoryClickListener;

    public CategoryListAdapter(Context context, List<Category> categoryList, OnCategoryClickListener listener) {
        this.context = context;
        this.categoryList = categoryList != null ? categoryList : new ArrayList<>(); // Prevent null list
        this.onCategoryClickListener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_list, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        if (position >= categoryList.size()) {
            android.util.Log.e("CategoryListAdapter", "Invalid position: " + position + ", size: " + categoryList.size());
            return;
        }

        Category category = categoryList.get(position);
        if (category == null) {
            android.util.Log.e("CategoryListAdapter", "Category at position " + position + " is null");
            return;
        }

        // Set category name with fallback
        if (holder.categoryName != null) {
            holder.categoryName.setText(category.getName() != null ? category.getName() : "Unnamed Category");
        } else {
            android.util.Log.e("CategoryListAdapter", "categoryName is null at position " + position);
        }

        // Set product count
        if (holder.categoryProductCount != null) {
            holder.categoryProductCount.setText(String.format("%d Products", category.getProductCount()));
        } else {
            android.util.Log.e("CategoryListAdapter", "categoryProductCount is null at position " + position);
        }

        // Load category image
        if (holder.categoryImage != null) {
            String imageUrl = category.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                        .load(imageUrl)
                        .placeholder(R.drawable.default_category_image)
                        .error(R.drawable.default_category_image)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(holder.categoryImage);
            } else {
                holder.categoryImage.setImageResource(R.drawable.default_category_image);
            }
        } else {
            android.util.Log.e("CategoryListAdapter", "categoryImage is null at position " + position);
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (onCategoryClickListener != null) {
                onCategoryClickListener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    // Method to update category list
    public void updateCategories(List<Category> newCategoryList) {
        this.categoryList = newCategoryList != null ? newCategoryList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        ImageView categoryImage;
        TextView categoryName;
        TextView categoryProductCount;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryImage = itemView.findViewById(R.id.category_image);
            categoryName = itemView.findViewById(R.id.category_name);
            categoryProductCount = itemView.findViewById(R.id.category_product_count);

            // Log missing views for debugging
            if (categoryImage == null) android.util.Log.e("CategoryListViewHolder", "categoryImage not found");
            if (categoryName == null) android.util.Log.e("CategoryListViewHolder", "categoryName not found");
            if (categoryProductCount == null) android.util.Log.e("CategoryListViewHolder", "categoryProductCount not found");
        }
    }

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }
}