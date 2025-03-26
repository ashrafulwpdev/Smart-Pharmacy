package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.LabTestGridAdapter;
import com.oopgroup.smartpharmacy.models.LabTest;

import java.util.ArrayList;
import java.util.List;

public class LabTestFragment extends Fragment implements LabTestGridAdapter.OnLabTestClickListener {

    private static final String TAG = "LabTestFragment";

    private RecyclerView labTestsRecyclerView;
    private TextView labTestTitle;
    private LabTestGridAdapter labTestAdapter;
    private List<LabTest> labTestList;
    private CollectionReference labTestsRef;  // Changed to Firestore CollectionReference
    private FirebaseAuth mAuth;
    private ListenerRegistration labTestsListener;  // Changed to Firestore ListenerRegistration

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
        labTestsRef = FirebaseFirestore.getInstance().collection("labTests");  // Changed to Firestore

        // Initialize UI
        labTestTitle = view.findViewById(R.id.labTestTitle);
        labTestsRecyclerView = view.findViewById(R.id.labTestsRecyclerView);

        if (labTestsRecyclerView == null) {
            Log.e(TAG, "labTestsRecyclerView not found in layout");
            Toast.makeText(requireContext(), "Error: Lab tests view not found", Toast.LENGTH_LONG).show();
            return;
        }

        // Check if user is authenticated
        if (mAuth.getCurrentUser() == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Please log in to view lab tests.", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new LoginFragment())
                        .commit();
            }
            return;
        }

        // Initialize list and adapter
        labTestList = new ArrayList<>();
        labTestAdapter = new LabTestGridAdapter(requireContext(), labTestList, this);
        labTestsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        labTestsRecyclerView.setAdapter(labTestAdapter);
        labTestsRecyclerView.setHasFixedSize(true);

        // Fetch lab tests
        fetchLabTests();
    }

    private void fetchLabTests() {
        labTestsListener = labTestsRef.addSnapshotListener((snapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                Log.e(TAG, "Failed to load lab tests: " + error.getMessage());
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load lab tests: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }

            labTestList.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    String id = doc.getId();
                    String name = doc.getString("name");
                    String imageUrl = doc.getString("imageUrl");

                    if (name != null && imageUrl != null) {
                        LabTest labTest = new LabTest(id, name, imageUrl);
                        labTestList.add(labTest);
                    }
                }
                labTestAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onLabTestClick(LabTest labTest) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Lab Test clicked: " + labTest.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (labTestsListener != null) {
            labTestsListener.remove();  // Firestore cleanup
            labTestsListener = null;
        }
        if (labTestsRecyclerView != null) {
            labTestsRecyclerView.setAdapter(null);
        }
        labTestAdapter = null;
        labTestsRecyclerView = null;
        labTestTitle = null;
    }
}