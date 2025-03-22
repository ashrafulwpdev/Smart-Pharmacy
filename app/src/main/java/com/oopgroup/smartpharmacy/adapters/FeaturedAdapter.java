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

import java.util.List;

public class FeaturedAdapter extends RecyclerView.Adapter<FeaturedAdapter.FeaturedViewHolder> {

    private Context context;
    private List<Category> featuredList;
    private OnFeaturedClickListener onFeaturedClickListener;

    public FeaturedAdapter(Context context, List<Category> featuredList, OnFeaturedClickListener listener) {
        this.context = context;
        this.featuredList = featuredList;
        this.onFeaturedClickListener = listener;
    }

    @NonNull
    @Override
    public FeaturedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_featured, parent, false);
        return new FeaturedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeaturedViewHolder holder, int position) {
        Category category = featuredList.get(position);
        holder.featuredName.setText(category.getName());

        String imageUrl = category.getImageUrl();
        try {
            // Check if imageUrl is a drawable resource ID (as a string)
            int resourceId = Integer.parseInt(imageUrl);
            Glide.with(context)
                    .load(resourceId)
                    .placeholder(R.drawable.default_category_image)
                    .error(R.drawable.default_category_image)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.featuredImage);
        } catch (NumberFormatException e) {
            // If not a resource ID, treat it as a URL or fallback to placeholder
            Glide.with(context)
                    .load(imageUrl.isEmpty() ? R.drawable.default_category_image : imageUrl)
                    .placeholder(R.drawable.default_category_image)
                    .error(R.drawable.default_category_image)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.featuredImage);
        }

        holder.itemView.setOnClickListener(v -> {
            if (onFeaturedClickListener != null) {
                onFeaturedClickListener.onFeaturedClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return featuredList.size();
    }

    public static class FeaturedViewHolder extends RecyclerView.ViewHolder {
        ImageView featuredImage;
        TextView featuredName;

        public FeaturedViewHolder(@NonNull View itemView) {
            super(itemView);
            featuredImage = itemView.findViewById(R.id.featured_image);
            featuredName = itemView.findViewById(R.id.featured_text);
        }
    }

    public interface OnFeaturedClickListener {
        void onFeaturedClick(Category category);
    }
}