package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.models.LabTest;

public class LabTestDetailsFragment extends Fragment {

    private static final String ARG_LAB_TEST_ID = "lab_test_id";
    private static final String TAG = "LabTestDetailsFragment";

    private Toolbar toolbar;
    private TextView testListTitleTextView, testListTextView, totalPriceTextView;
    private ImageView labTestImageView;
    private Button bookNowButton;
    private FirebaseFirestore db;
    private LabTest labTest;

    public static LabTestDetailsFragment newInstance(String labTestId) {
        LabTestDetailsFragment fragment = new LabTestDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LAB_TEST_ID, labTestId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lab_test_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar = view.findViewById(R.id.toolbar);
        labTestImageView = view.findViewById(R.id.labTestImageView);
        testListTitleTextView = view.findViewById(R.id.testListTitleTextView);
        testListTextView = view.findViewById(R.id.testListTextView);
        totalPriceTextView = view.findViewById(R.id.totalPriceTextView);
        bookNowButton = view.findViewById(R.id.bookNowButton);

        toolbar.setTitle("");
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        db = FirebaseFirestore.getInstance();

        String labTestId = getArguments() != null ? getArguments().getString(ARG_LAB_TEST_ID) : null;
        if (labTestId == null) {
            Toast.makeText(requireContext(), "Error: Lab test ID not found", Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        fetchLabTestDetails(labTestId);

        bookNowButton.setOnClickListener(v -> {
            if (labTest != null) {
                LabTestBookingFragment bookingFragment = LabTestBookingFragment.newInstance(
                        labTest.getName(),
                        labTest.getProvider() != null ? labTest.getProvider() : "Quest Medical Center", // Now works with getProvider()
                        labTest.getPrice()
                );
                bookingFragment.show(requireActivity().getSupportFragmentManager(), "LabTestBookingFragment");
            } else {
                Toast.makeText(requireContext(), "Lab test details not loaded yet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchLabTestDetails(String labTestId) {
        DocumentReference labTestRef = db.collection("labTests").document(labTestId);
        labTestRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                labTest = documentSnapshot.toObject(LabTest.class);
                if (labTest != null) {
                    toolbar.setTitle(labTest.getName() + " Details");
                    testListTitleTextView.setText(labTest.getName() + " Test List");
                    testListTextView.setText(formatTestList(labTest.getTests()));
                    totalPriceTextView.setText("Total Price: RM " + String.format("%.2f", labTest.getPrice()));

                    if (labTest.getImageUrl() != null && !labTest.getImageUrl().isEmpty()) {
                        Glide.with(requireContext())
                                .load(labTest.getImageUrl())
                                .placeholder(R.drawable.default_lab_test_image)
                                .error(R.drawable.default_lab_test_image)
                                .into(labTestImageView);
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Lab test not found", Toast.LENGTH_LONG).show();
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(requireContext(), "Failed to load lab test details: " + e.getMessage(), Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        });
    }

    private String formatTestList(String tests) {
        if (tests == null || tests.isEmpty()) {
            return "No tests available";
        }
        String[] testArray = tests.split(",");
        StringBuilder formattedTests = new StringBuilder();
        for (String test : testArray) {
            formattedTests.append("• ").append(test.trim()).append("\n");
        }
        return formattedTests.toString();
    }
}