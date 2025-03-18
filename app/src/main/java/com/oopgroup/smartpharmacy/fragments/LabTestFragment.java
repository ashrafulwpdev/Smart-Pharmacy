package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.LabTestGridAdapter;
import com.oopgroup.smartpharmacy.models.LabTest;

import java.util.ArrayList;
import java.util.List;

public class LabTestFragment extends Fragment implements LabTestGridAdapter.OnLabTestClickListener {

    private RecyclerView labTestsRecyclerView;
    private TextView labTestTitle;
    private LabTestGridAdapter labTestAdapter;
    private List<LabTest> labTestList;
    private DatabaseReference labTestsRef;
    private FirebaseAuth mAuth;

    public LabTestFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lab_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        labTestsRef = FirebaseDatabase.getInstance().getReference("labTests");

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please log in to view lab tests.", Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
            return;
        }

        // Initialize UI
        labTestTitle = view.findViewById(R.id.labTestTitle);
        labTestsRecyclerView = view.findViewById(R.id.labTestsRecyclerView);
        labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        // Initialize list and adapter
        labTestList = new ArrayList<>();
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        labTestsRecyclerView.setAdapter(labTestAdapter);

        // Fetch lab tests
        fetchLabTests();
    }

    private void fetchLabTests() {
        labTestsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                labTestList.clear();
                for (DataSnapshot labTestSnapshot : snapshot.getChildren()) {
                    String id = labTestSnapshot.getKey();
                    String name = labTestSnapshot.child("name").getValue(String.class);
                    String imageUrl = labTestSnapshot.child("imageUrl").getValue(String.class);

                    if (name != null && imageUrl != null) {
                        LabTest labTest = new LabTest(id, name, imageUrl);
                        labTestList.add(labTest);
                    }
                }
                labTestAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load lab tests: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onLabTestClick(LabTest labTest) {
        Toast.makeText(requireContext(), "Lab Test clicked: " + labTest.getName(), Toast.LENGTH_SHORT).show();
    }
}