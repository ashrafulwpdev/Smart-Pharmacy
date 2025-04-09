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
    private OnProductClickListener onProductClickListener;

    public BestsellerProductAdapter(Context context, List<Product> productList, OnAddToCartClickListener listener) {
        this.context = context;
        this.productList = productList;
        this.onAddToCartClickListener = listener;
    }

    public BestsellerProductAdapter(Context context, List<Product> productList,
                                    OnAddToCartClickListener addToCartListener,
                                    OnProductClickListener productClickListener) {
        this.context = context;
        this.productList = productList;
        this.onAddToCartClickListener = addToCartListener;
        this.onProductClickListener = productClickListener;
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

        holder.productName.setText(product.getName() != null ? product.getName() : "Unknown Product");
        holder.productQuantity.setText(product.getQuantity() != null ? product.getQuantity() : "N/A");

        // Rating: Assuming out of 10, display as 5-star scale
        int rating = product.getRating();
        int reviewCount = product.getReviewCount();
        holder.ratingText.setText(String.format("%.1f (%d Reviews)", rating / 2.0f, reviewCount));

        // Price handling
        double price = product.getPrice();
        double discountedPrice = product.getDiscountedPrice();

        if (discountedPrice > 0.0 && discountedPrice < price) {
            holder.productPriceDiscounted.setText(String.format("RM%.2f", discountedPrice));
            holder.productPriceOriginal.setText(String.format("RM%.2f", price));
            holder.productPriceOriginal.setPaintFlags(holder.productPriceOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.productPriceOriginal.setVisibility(View.VISIBLE);
        } else {
            holder.productPriceDiscounted.setText(String.format("RM%.2f", price));
            holder.productPriceOriginal.setVisibility(View.GONE);
        }

        Glide.with(context)
                .load(product.getImageUrl())
                .placeholder(R.drawable.default_product_image)
                .error(R.drawable.default_product_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.productImage);

        holder.addToCartButton.setOnClickListener(v -> {
            if (onAddToCartClickListener != null) {
                double effectivePrice = (discountedPrice > 0.0 && discountedPrice < price) ? discountedPrice : price;
                if (effectivePrice <= 0.0) {
                    android.util.Log.e("Adapter", "Cannot add to cart: Invalid price for " + product.getName());
                    return;
                }
                onAddToCartClickListener.onAddToCartClick(product);
            }
        });

        if (onProductClickListener != null) {
            holder.itemView.setOnClickListener(v -> onProductClickListener.onProductClick(product));
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
        }
    }

    public interface OnAddToCartClickListener {
        void onAddToCartClick(Product product);
    }

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }
}