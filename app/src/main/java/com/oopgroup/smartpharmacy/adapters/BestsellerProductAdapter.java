package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.Product;

import java.util.List;

public class BestsellerProductAdapter extends RecyclerView.Adapter<BestsellerProductAdapter.ProductViewHolder> {

    private Context context;
    private List<Product> productList;
    private OnAddToCartClickListener onAddToCartClickListener;

    public BestsellerProductAdapter(Context context, List<Product> productList, OnAddToCartClickListener listener) {
        this.context = context;
        this.productList = productList;
        this.onAddToCartClickListener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bestseller_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.productName.setText(product.getName());
        holder.productPrice.setText(String.format("$%.2f $%.2f", product.getPrice() * 0.8, product.getPrice()));
        holder.ratingText.setText(String.format("★ %.1f (%d Reviews)", (float) product.getRating() / 2, product.getReviewCount()));
        Glide.with(context)
                .load(product.getImageUrl())
                .placeholder(R.drawable.default_product_image)
                .error(R.drawable.default_product_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.productImage);

        holder.addToCartButton.setOnClickListener(v -> {
            if (onAddToCartClickListener != null) {
                onAddToCartClickListener.onAddToCartClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productName;
        TextView productPrice;
        TextView ratingText;
        Button addToCartButton;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.productImage);
            productName = itemView.findViewById(R.id.productName);
            productPrice = itemView.findViewById(R.id.productPrice);
            ratingText = itemView.findViewById(R.id.ratingText);
            addToCartButton = itemView.findViewById(R.id.addToCartButton);
        }
    }

    public interface OnAddToCartClickListener {
        void onAddToCartClick(Product product);
    }
}