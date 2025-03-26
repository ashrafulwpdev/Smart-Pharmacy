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
import com.oopgroup.smartpharmacy.models.LabTest;

import java.util.ArrayList;
import java.util.List;

public class LabTestGridAdapter extends RecyclerView.Adapter<LabTestGridAdapter.LabTestViewHolder> {

    private final Context context;
    private List<LabTest> labTestList;
    private final OnLabTestClickListener onLabTestClickListener;

    public LabTestGridAdapter(Context context, List<LabTest> labTestList, OnLabTestClickListener listener) {
        this.context = context;
        this.labTestList = labTestList != null ? labTestList : new ArrayList<>(); // Prevent null list
        this.onLabTestClickListener = listener;
    }

    @NonNull
    @Override
    public LabTestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_lab_test_grid, parent, false);
        return new LabTestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LabTestViewHolder holder, int position) {
        if (position >= labTestList.size()) {
            android.util.Log.e("LabTestGridAdapter", "Invalid position: " + position + ", size: " + labTestList.size());
            return;
        }

        LabTest labTest = labTestList.get(position);
        if (labTest == null) {
            android.util.Log.e("LabTestGridAdapter", "LabTest at position " + position + " is null");
            return;
        }

        // Set lab test name with fallback
        if (holder.labTestName != null) {
            holder.labTestName.setText(labTest.getName() != null ? labTest.getName() : "Unnamed Lab Test");
        } else {
            android.util.Log.e("LabTestGridAdapter", "labTestName is null at position " + position);
        }

        // Load lab test image
        if (holder.labTestImage != null) {
            String imageUrl = labTest.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(context)
                        .load(imageUrl)
                        .placeholder(R.drawable.default_lab_test_image)
                        .error(R.drawable.default_lab_test_image)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(holder.labTestImage);
            } else {
                holder.labTestImage.setImageResource(R.drawable.default_lab_test_image);
            }
        } else {
            android.util.Log.e("LabTestGridAdapter", "labTestImage is null at position " + position);
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (onLabTestClickListener != null) {
                onLabTestClickListener.onLabTestClick(labTest);
            }
        });
    }

    @Override
    public int getItemCount() {
        return labTestList.size();
    }

    // Method to update lab test list
    public void updateLabTests(List<LabTest> newLabTestList) {
        this.labTestList = newLabTestList != null ? newLabTestList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public static class LabTestViewHolder extends RecyclerView.ViewHolder {
        ImageView labTestImage;
        TextView labTestName;

        public LabTestViewHolder(@NonNull View itemView) {
            super(itemView);
            labTestImage = itemView.findViewById(R.id.lab_test_image);
            labTestName = itemView.findViewById(R.id.lab_test_text);

            // Log missing views for debugging
            if (labTestImage == null) android.util.Log.e("LabTestViewHolder", "labTestImage not found");
            if (labTestName == null) android.util.Log.e("LabTestViewHolder", "labTestName not found");
        }
    }

    public interface OnLabTestClickListener {
        void onLabTestClick(LabTest labTest);
    }
}