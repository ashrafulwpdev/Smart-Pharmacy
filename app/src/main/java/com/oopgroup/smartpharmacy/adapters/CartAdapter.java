package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.CartItem;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {
    private Context context;
    private List<CartItem> cartItems;
    private OnQuantityChangeListener quantityChangeListener;
    private FirebaseFirestore db;

    public interface OnQuantityChangeListener {
        void onQuantityChanged(CartItem cartItem, int newQuantity);
    }

    public CartAdapter(Context context, List<CartItem> cartItems, OnQuantityChangeListener listener) {
        this.context = context;
        this.cartItems = cartItems;
        this.quantityChangeListener = listener;
        this.db = FirebaseFirestore.getInstance(); // Initialize Firestore
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);

        holder.tvProductName.setText(cartItem.getProductName());
        double price = cartItem.getDiscountedPrice() != 0 ? cartItem.getDiscountedPrice() : cartItem.getOriginalPrice();
        holder.tvProductPrice.setText(String.format("RM %.2f", price));
        holder.tvQuantity.setText(String.valueOf(cartItem.getQuantity()));

        if (cartItem.getImageUrl() != null && !cartItem.getImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(cartItem.getImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .override(100, 100)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.ivProductImage);
        }

        // Ensure the buttons are not null before setting click listeners
        if (holder.btnIncreaseQuantity != null) {
            holder.btnIncreaseQuantity.setOnClickListener(v -> {
                int newQuantity = cartItem.getQuantity() + 1;
                cartItem.setQuantity(newQuantity);
                holder.tvQuantity.setText(String.valueOf(newQuantity));
                quantityChangeListener.onQuantityChanged(cartItem, newQuantity);
            });
        }

        if (holder.btnDecreaseQuantity != null) {
            holder.btnDecreaseQuantity.setOnClickListener(v -> {
                int newQuantity = cartItem.getQuantity() - 1;
                if (newQuantity >= 0) {
                    cartItem.setQuantity(newQuantity);
                    holder.tvQuantity.setText(String.valueOf(newQuantity));
                    if (newQuantity == 0) {
                        int pos = holder.getAdapterPosition();
                        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        // Delete from Firestore when quantity reaches 0
                        db.collection("cart")
                                .document(userId)
                                .collection("items")
                                .document(cartItem.getId())
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    cartItems.remove(pos);
                                    notifyItemRemoved(pos);
                                    notifyItemRangeChanged(pos, cartItems.size());
                                })
                                .addOnFailureListener(e -> {
                                    // Revert UI if deletion fails
                                    cartItem.setQuantity(1); // Reset to 1 if deletion fails
                                    holder.tvQuantity.setText("1");
                                    notifyItemChanged(pos);
                                });
                    }
                    quantityChangeListener.onQuantityChanged(cartItem, newQuantity);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProductImage;
        TextView tvProductName, tvProductPrice, tvQuantity;
        ImageButton btnIncreaseQuantity, btnDecreaseQuantity; // Ensure these are ImageButtons

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProductImage = itemView.findViewById(R.id.iv_product_image);
            tvProductName = itemView.findViewById(R.id.tv_product_name);
            tvProductPrice = itemView.findViewById(R.id.tv_product_price);
            tvQuantity = itemView.findViewById(R.id.tv_quantity);
            btnDecreaseQuantity = itemView.findViewById(R.id.btn_decrease_quantity);
            btnIncreaseQuantity = itemView.findViewById(R.id.btn_increase_quantity);
        }
    }
}
