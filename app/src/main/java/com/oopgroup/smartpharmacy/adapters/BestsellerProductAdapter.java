package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.graphics.Paint;
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
        if (productList == null || position >= productList.size()) {
            android.util.Log.e("Adapter", "Invalid product list or position: " + position);
            return;
        }

        Product product = productList.get(position);

        if (holder.productName != null) {
            holder.productName.setText(product.getName());
        } else {
            android.util.Log.e("Adapter", "productName is null");
        }

        if (holder.productQuantity != null) {
            holder.productQuantity.setText(product.getQuantity());
        } else {
            android.util.Log.e("Adapter", "productQuantity is null");
        }

        if (holder.ratingText != null) {
            holder.ratingText.setText(String.format("★ %.1f (%d Reviews)", (float) product.getRating() / 2, product.getReviewCount()));
        } else {
            android.util.Log.e("Adapter", "ratingText is null");
        }

        if (holder.productPriceDiscounted != null && holder.productPriceOriginal != null) {
            if (product.getDiscountedPrice() > 0.0) {
                // Show both discounted and original price with strikethrough
                holder.productPriceDiscounted.setText(String.format("RM%.2f", product.getDiscountedPrice()));
                holder.productPriceOriginal.setText(String.format("RM%.2f", product.getOriginalPrice()));
                holder.productPriceOriginal.setPaintFlags(holder.productPriceOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.productPriceOriginal.setVisibility(View.VISIBLE);
            } else {
                // Show only the original price without strikethrough
                holder.productPriceDiscounted.setText(String.format("RM%.2f", product.getOriginalPrice()));
                holder.productPriceOriginal.setVisibility(View.GONE);
            }
        } else {
            android.util.Log.e("Adapter", "productPriceDiscounted or productPriceOriginal is null");
        }

        if (holder.productImage != null) {
            Glide.with(context)
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.default_product_image)
                    .error(R.drawable.default_product_image)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.productImage);
        } else {
            android.util.Log.e("Adapter", "productImage is null");
        }

        if (holder.addToCartButton != null) {
            holder.addToCartButton.setOnClickListener(v -> {
                if (onAddToCartClickListener != null) {
                    onAddToCartClickListener.onAddToCartClick(product);
                }
            });
        } else {
            android.util.Log.e("Adapter", "addToCartButton is null");
        }
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    public void updateProductList(List<Product> newProductList) {
        this.productList = newProductList;
        notifyDataSetChanged();
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productName;
        TextView productQuantity;
        TextView productPriceDiscounted;
        TextView productPriceOriginal;
        TextView ratingText;
        Button addToCartButton;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.productImage);
            productName = itemView.findViewById(R.id.productName);
            productQuantity = itemView.findViewById(R.id.productQuantity);
            productPriceDiscounted = itemView.findViewById(R.id.productPriceDiscounted);
            productPriceOriginal = itemView.findViewById(R.id.productPriceOriginal);
            ratingText = itemView.findViewById(R.id.ratingText);
            addToCartButton = itemView.findViewById(R.id.addToCartButton);

            // Log missing views for debugging
            if (productImage == null) android.util.Log.e("ViewHolder", "productImage not found");
            if (productName == null) android.util.Log.e("ViewHolder", "productName not found");
            if (productQuantity == null) android.util.Log.e("ViewHolder", "productQuantity not found");
            if (productPriceDiscounted == null) android.util.Log.e("ViewHolder", "productPriceDiscounted not found");
            if (productPriceOriginal == null) android.util.Log.e("ViewHolder", "productPriceOriginal not found");
            if (ratingText == null) android.util.Log.e("ViewHolder", "ratingText not found");
            if (addToCartButton == null) android.util.Log.e("ViewHolder", "addToCartButton not found");
        }
    }

    public interface OnAddToCartClickListener {
        void onAddToCartClick(Product product);
    }
}