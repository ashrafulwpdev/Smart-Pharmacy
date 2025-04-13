package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;

public class OrderSuccessDialog extends DialogFragment {

    private MaterialButton viewOrdersButton, doneButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order_success_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewOrdersButton = view.findViewById(R.id.viewOrdersButton);
        doneButton = view.findViewById(R.id.doneButton);

        // Set dialog properties
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getDialog().getWindow().setGravity(Gravity.CENTER);
            getDialog().getWindow().getDecorView().setPadding(16, 0, 16, 0);
        }

        // View Orders button listener
        viewOrdersButton.setOnClickListener(v -> {
            // Navigate to InprogressOrdersFragment
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new InprogressOrdersFragment())
                    .commit();

            // Show bottom navigation
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showBottomNav();
            }

            // Dismiss dialog
            dismiss();
        });

        // Done button listener
        doneButton.setOnClickListener(v -> {
            // Navigate to HomeFragment
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();

            // Show bottom navigation
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showBottomNav();
            }

            // Dismiss dialog
            dismiss();
        });
    }
}