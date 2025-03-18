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

import java.util.List;

public class LabTestGridAdapter extends RecyclerView.Adapter<LabTestGridAdapter.LabTestViewHolder> {

    private Context context;
    private List<LabTest> labTestList;
    private OnLabTestClickListener onLabTestClickListener;

    public LabTestGridAdapter(Context context, List<LabTest> labTestList, OnLabTestClickListener listener) {
        this.context = context;
        this.labTestList = labTestList;
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
        LabTest labTest = labTestList.get(position);
        holder.labTestName.setText(labTest.getName());
        Glide.with(context)
                .load(labTest.getImageUrl())
                .placeholder(R.drawable.default_lab_test_image)
                .error(R.drawable.default_lab_test_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.labTestImage);

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

    public static class LabTestViewHolder extends RecyclerView.ViewHolder {
        ImageView labTestImage;
        TextView labTestName;

        public LabTestViewHolder(@NonNull View itemView) {
            super(itemView);
            labTestImage = itemView.findViewById(R.id.labTestImage);
            labTestName = itemView.findViewById(R.id.labTestName);
        }
    }

    public interface OnLabTestClickListener {
        void onLabTestClick(LabTest labTest);
    }
}