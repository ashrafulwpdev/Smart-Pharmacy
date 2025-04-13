package com.oopgroup.smartpharmacy.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.TeamMember;

import java.util.List;

public class TeamAdapter extends RecyclerView.Adapter<TeamAdapter.ViewHolder> {

    private Context context;
    private List<TeamMember> teamMembers;

    public TeamAdapter(Context context, List<TeamMember> teamMembers) {
        this.context = context;
        this.teamMembers = teamMembers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_team_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TeamMember member = teamMembers.get(position);

        // Convert 2dp to pixels
        int borderWidth = (int) (2 * context.getResources().getDisplayMetrics().density);

        // Create a circular background with border programmatically
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setShape(GradientDrawable.OVAL);
        borderDrawable.setColor(0xFFEEEEEE);  // Background color (light gray)
        borderDrawable.setStroke(borderWidth, 0xFF083EC9);  // 2dp border, #083EC9 color

        // Apply the background to the ImageView
        holder.teamPhoto.setBackground(borderDrawable);

        // Load image with Glide, applying circular crop
        Glide.with(context)
                .load(member.getPhotoResId())
                .apply(new RequestOptions()
                        .circleCrop()
                        .error(R.drawable.placeholder_image))
                .into(holder.teamPhoto);


        holder.teamName.setText(member.getName());
        holder.teamRole.setText(member.getRole());
        holder.teamDescription.setText(member.getDescription());
    }

    @Override
    public int getItemCount() {
        return teamMembers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView teamPhoto;
        TextView teamName, teamRole, teamDescription;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            teamPhoto = itemView.findViewById(R.id.teamPhoto);
            teamName = itemView.findViewById(R.id.teamName);
            teamRole = itemView.findViewById(R.id.teamRole);
            teamDescription = itemView.findViewById(R.id.teamDescription);
        }
    }
}