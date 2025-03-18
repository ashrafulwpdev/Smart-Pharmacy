package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.oopgroup.smartpharmacy.R;
import java.util.List;

public class AdminItemAdapter extends RecyclerView.Adapter<AdminItemAdapter.ItemViewHolder> {
    private Context context;
    private List<Object> itemList;
    private OnItemActionListener onItemActionListener;
    private String type;

    public AdminItemAdapter(Context context, List<Object> itemList, String type, OnItemActionListener listener) {
        this.context = context;
        this.itemList = itemList;
        this.type = type;
        this.onItemActionListener = listener;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        Object item = itemList.get(position);
        if (type.equals("banner")) {
            com.oopgroup.smartpharmacy.models.Banner banner = (com.oopgroup.smartpharmacy.models.Banner) item;
            holder.itemName.setText(banner.getTitle());
            Glide.with(context).load(banner.getImageUrl()).into(holder.itemImage);
        } else if (type.equals("categories")) {
            com.oopgroup.smartpharmacy.models.Category category = (com.oopgroup.smartpharmacy.models.Category) item;
            holder.itemName.setText(category.getName());
            Glide.with(context).load(category.getImageUrl()).into(holder.itemImage);
        } else if (type.equals("labTests")) {
            com.oopgroup.smartpharmacy.models.LabTest labTest = (com.oopgroup.smartpharmacy.models.LabTest) item;
            holder.itemName.setText(labTest.getName());
            Glide.with(context).load(labTest.getImageUrl()).into(holder.itemImage);
        } else if (type.equals("products")) {
            com.oopgroup.smartpharmacy.models.Product product = (com.oopgroup.smartpharmacy.models.Product) item;
            holder.itemName.setText(product.getName());
            Glide.with(context).load(product.getImageUrl()).into(holder.itemImage);
        }
        holder.editButton.setOnClickListener(v -> onItemActionListener.onEditItem(item));
        holder.deleteButton.setOnClickListener(v -> onItemActionListener.onDeleteItem(item));
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView itemImage;
        TextView itemName;
        Button editButton, deleteButton;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.itemImage);
            itemName = itemView.findViewById(R.id.itemName);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    public interface OnItemActionListener {
        void onEditItem(Object item);
        void onDeleteItem(Object item);
    }
}