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
import com.oopgroup.smartpharmacy.models.Banner;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {
    private Context context;
    private List<Banner> bannerList;
    private OnOrderNowClickListener listener;

    public BannerAdapter(Context context, List<Banner> bannerList, OnOrderNowClickListener listener) {
        this.context = context;
        this.bannerList = bannerList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_banner, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        Banner banner = bannerList.get(position);

        if (holder.bannerTitle != null) {
            holder.bannerTitle.setText(banner.getTitle());
        } else {
            android.util.Log.e("BannerAdapter", "bannerTitle is null");
        }

        if (holder.bannerDescription != null) {
            holder.bannerDescription.setText(banner.getDescription());
        } else {
            android.util.Log.e("BannerAdapter", "bannerDescription is null");
        }

        if (holder.bannerDiscount != null) {
            holder.bannerDiscount.setText(banner.getDiscount());
        } else {
            android.util.Log.e("BannerAdapter", "bannerDiscount is null");
        }

        if (holder.bannerImage != null) {
            Glide.with(context)
                    .load(banner.getImageUrl())
                    .placeholder(R.drawable.ic_delivery_person) // Match your old drawable
                    .error(R.drawable.ic_delivery_person)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.bannerImage);
        } else {
            android.util.Log.e("BannerAdapter", "bannerImage is null");
        }

        if (holder.orderNowButton != null) {
            holder.orderNowButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOrderNowClick(banner);
                }
            });
        } else {
            android.util.Log.e("BannerAdapter", "orderNowButton is null");
        }
    }

    @Override
    public int getItemCount() {
        return bannerList != null ? bannerList.size() : 0;
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView bannerImage;
        TextView bannerTitle;
        TextView bannerDescription;
        TextView bannerDiscount;
        Button orderNowButton;

        BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            bannerImage = itemView.findViewById(R.id.bannerImage);
            bannerTitle = itemView.findViewById(R.id.bannerTitle);
            bannerDescription = itemView.findViewById(R.id.bannerDescription);
            bannerDiscount = itemView.findViewById(R.id.bannerDiscount);
            orderNowButton = itemView.findViewById(R.id.orderNowButton);

            // Log missing views
            if (bannerImage == null) android.util.Log.e("BannerViewHolder", "bannerImage not found");
            if (bannerTitle == null) android.util.Log.e("BannerViewHolder", "bannerTitle not found");
            if (bannerDescription == null) android.util.Log.e("BannerViewHolder", "bannerDescription not found");
            if (bannerDiscount == null) android.util.Log.e("BannerViewHolder", "bannerDiscount not found");
            if (orderNowButton == null) android.util.Log.e("BannerViewHolder", "orderNowButton not found");
        }
    }

    public interface OnOrderNowClickListener {
        void onOrderNowClick(Banner banner);
    }
}