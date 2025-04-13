package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.PrescriptionItem;

import java.util.List;

public class PrescriptionResultAdapter extends RecyclerView.Adapter<PrescriptionResultAdapter.ViewHolder> {

    private Context context;
    private List<PrescriptionItem> prescriptionItems;
    private OnAddToCartClickListener addToCartListener;
    private OnSuggestAlternativeClickListener suggestAlternativeListener;

    public PrescriptionResultAdapter(Context context, List<PrescriptionItem> prescriptionItems,
                                     OnAddToCartClickListener addToCartListener,
                                     OnSuggestAlternativeClickListener suggestAlternativeListener) {
        this.context = context;
        this.prescriptionItems = prescriptionItems;
        this.addToCartListener = addToCartListener;
        this.suggestAlternativeListener = suggestAlternativeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_prescription_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PrescriptionItem item = prescriptionItems.get(position);

        holder.medicineNameText.setText(item.getName());
        holder.dosageText.setText("Dosage: " + (item.getDosage() != null ? item.getDosage() : "N/A"));
        holder.frequencyText.setText("Frequency: " + (item.getFrequency() != null ? item.getFrequency() : "N/A"));

        if (item.isInStock()) {
            holder.statusText.setText("Status: In Stock");
            holder.statusText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.priceText.setVisibility(View.VISIBLE);
            double displayPrice = item.getDiscountedPrice() != 0 ? item.getDiscountedPrice() : item.getPrice();
            holder.priceText.setText(String.format("Price: $%.2f", displayPrice));
            holder.addToCartButton.setVisibility(View.VISIBLE);
            holder.addToCartButton.setEnabled(true);
        } else {
            holder.statusText.setText("Status: Out of Stock");
            holder.statusText.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
            holder.priceText.setVisibility(View.GONE);
            holder.addToCartButton.setVisibility(View.GONE);
            holder.addToCartButton.setEnabled(false);
        }

        holder.addToCartButton.setOnClickListener(v -> addToCartListener.onAddToCartClick(item));
        holder.suggestAlternativesButton.setOnClickListener(v -> suggestAlternativeListener.onSuggestAlternativeClick(item));
    }

    @Override
    public int getItemCount() {
        return prescriptionItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView medicineNameText, dosageText, frequencyText, statusText, priceText;
        Button addToCartButton, suggestAlternativesButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            medicineNameText = itemView.findViewById(R.id.medicineNameText);
            dosageText = itemView.findViewById(R.id.dosageText);
            frequencyText = itemView.findViewById(R.id.frequencyText);
            statusText = itemView.findViewById(R.id.statusText);
            priceText = itemView.findViewById(R.id.priceText);
            addToCartButton = itemView.findViewById(R.id.addToCartButton);
            suggestAlternativesButton = itemView.findViewById(R.id.suggestAlternativesButton);
        }
    }

    public interface OnAddToCartClickListener {
        void onAddToCartClick(PrescriptionItem item);
    }

    public interface OnSuggestAlternativeClickListener {
        void onSuggestAlternativeClick(PrescriptionItem item);
    }
}