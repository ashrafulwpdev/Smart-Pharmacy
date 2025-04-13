package com.oopgroup.smartpharmacy.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oopgroup.smartpharmacy.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PaymentMethodsAdapter extends RecyclerView.Adapter<PaymentMethodsAdapter.ViewHolder> {

    private List<Map<String, Object>> methods;
    private final OnRemoveClickListener removeListener;
    private OnItemClickListener itemClickListener;
    private int selectedPosition = -1;

    public interface OnRemoveClickListener {
        void onRemove(Map<String, Object> method);
    }

    public interface OnItemClickListener {
        void onItemClick(Map<String, Object> method, int position);
    }

    public PaymentMethodsAdapter(List<Map<String, Object>> methods, OnRemoveClickListener removeListener) {
        this.methods = methods != null ? methods : new ArrayList<>();
        this.removeListener = removeListener;
        this.itemClickListener = null;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void updateMethods(List<Map<String, Object>> methods) {
        this.methods = methods != null ? methods : new ArrayList<>();
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment_method, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> method = methods.get(position);
        String type = (String) method.get("type");
        holder.tvType.setText(type != null ? type : "Unknown");
        if ("Credit Card".equals(type)) {
            String lastFour = (String) method.get("lastFour");
            String cardHolderName = (String) method.get("cardHolderName");
            holder.ivIcon.setImageResource(R.drawable.card_ic);
            holder.tvDetails.setText(String.format("**** **** **** %s (%s)",
                    lastFour != null ? lastFour : "XXXX",
                    cardHolderName != null ? cardHolderName : "Unknown"));
        } else if ("Paypal".equals(type)) {
            String email = (String) method.get("email");
            holder.ivIcon.setImageResource(R.drawable.paypal);
            holder.tvDetails.setText(email != null ? email : "Unknown");
        } else {
            holder.ivIcon.setImageDrawable(null);
            holder.tvDetails.setText("Unknown");
        }

        // Handle radio button
        holder.radioSelect.setChecked(position == selectedPosition);
        holder.radioSelect.setVisibility(itemClickListener != null ? View.VISIBLE : View.GONE);
        holder.radioSelect.setOnClickListener(v -> {
            if (itemClickListener != null) {
                selectedPosition = holder.getAdapterPosition();
                itemClickListener.onItemClick(method, selectedPosition);
                notifyDataSetChanged();
            }
        });

        // Handle remove button
        if (removeListener != null) {
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v -> removeListener.onRemove(method));
        } else {
            holder.btnRemove.setVisibility(View.GONE);
        }

        // Handle item click
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                selectedPosition = holder.getAdapterPosition();
                itemClickListener.onItemClick(method, selectedPosition);
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return methods.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvType, tvDetails;
        Button btnRemove;
        RadioButton radioSelect;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvType = itemView.findViewById(R.id.tvType);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            radioSelect = itemView.findViewById(R.id.radioSelect);
        }
    }
}