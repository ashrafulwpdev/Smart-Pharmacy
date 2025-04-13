package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.TeamAdapter;
import com.oopgroup.smartpharmacy.models.TeamMember;

import java.util.ArrayList;
import java.util.List;

public class TeamFragment extends Fragment {

    private Toolbar toolbar;
    private RecyclerView teamRecyclerView;
    private TeamAdapter teamAdapter;
    private List<TeamMember> teamMembers;

    public TeamFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_team, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Toolbar
        toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("Our Team");
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Initialize RecyclerView
        teamRecyclerView = view.findViewById(R.id.teamRecyclerView);
        teamMembers = new ArrayList<>();

        // Add your 4 team members here (replace with your real data)
        teamMembers.add(new TeamMember(
                "Islam Md Ashraful",
                "BCS Student",
                "Alice drives the technical vision of SmartPharmacy with her expertise in Android and AI integration.",
                R.drawable.ashraful_pic  // Replace with your photo
        ));
        teamMembers.add(new TeamMember(
                "Bob Carter",
                "UI/UX Designer",
                "Bob designs intuitive interfaces that make SmartPharmacy a joy to use for all users.",
                R.drawable.demo_man  // Replace with your photo
        ));
        teamMembers.add(new TeamMember(
                "Clara Davis",
                "Backend Engineer",
                "Clara ensures our servers are robust and secure, handling all data seamlessly.",
                R.drawable.demo_man  // Replace with your photo
        ));
        teamMembers.add(new TeamMember(
                "David Evans",
                "Project Manager",
                "David keeps the team on track, ensuring timely delivery of our innovative features.",
                R.drawable.demo_man  // Replace with your photo
        ));

        teamAdapter = new TeamAdapter(requireContext(), teamMembers);
        teamRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        teamRecyclerView.setAdapter(teamAdapter);
    }
}