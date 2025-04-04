package com.oopgroup.smartpharmacy.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.oopgroup.smartpharmacy.R;

import java.util.ArrayList;
import java.util.List;

public class DashboardAdapter extends RecyclerView.Adapter<DashboardAdapter.StatViewHolder> {

    private List<DashboardStat> stats = new ArrayList<>();
    private final OnStatClickListener onClickListener;

    public interface OnStatClickListener {
        void onStatClick(DashboardStat stat);
    }

    public DashboardAdapter(OnStatClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    @Override
    public StatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dashboard_stat, parent, false);
        return new StatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(StatViewHolder holder, int position) {
        holder.bind(stats.get(position), onClickListener);
    }

    @Override
    public int getItemCount() {
        return stats.size();
    }

    public void submitList(List<DashboardStat> newStats) {
        stats.clear();
        stats.addAll(newStats);
        notifyDataSetChanged();
    }

    static class StatViewHolder extends RecyclerView.ViewHolder {
        ImageView statIcon;
        TextView statTitle, statValue;
        MaterialCardView cardView;

        StatViewHolder(View itemView) {
            super(itemView);
            statIcon = itemView.findViewById(R.id.statIcon);
            statTitle = itemView.findViewById(R.id.statTitle);
            statValue = itemView.findViewById(R.id.statValue);
            cardView = (MaterialCardView) itemView;
        }

        void bind(DashboardStat stat, OnStatClickListener onClickListener) {
            statIcon.setImageResource(stat.getIconRes());
            statTitle.setText(stat.getTitle());
            statValue.setText(stat.getValue());
            cardView.setOnClickListener(v -> onClickListener.onStatClick(stat));
        }
    }
}

