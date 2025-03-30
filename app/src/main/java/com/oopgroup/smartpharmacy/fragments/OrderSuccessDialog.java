package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.oopgroup.smartpharmacy.MainActivity;
import com.oopgroup.smartpharmacy.R;

public class OrderSuccessDialog extends DialogFragment {

    private MaterialButton viewOrdersButton, doneButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false); // Disable any default options menu
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
            // Navigate to OrderFragment
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            // Clear the back stack up to the MainActivity (or CartFragment) to avoid deep nesting
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new OrderFragment())
                    .addToBackStack("OrderFragment")
                    .commit();

            // Show the bottom navigation bar if hidden
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showBottomNav();
            }

            // Dismiss the dialog
            dismiss();
        });

        // Done button listener
        doneButton.setOnClickListener(v -> {
            // Pop the CheckoutFragment from the back stack to return to CartFragment
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            fragmentManager.popBackStack("CheckoutFragment", 0); // Pop back to CheckoutFragment's entry

            // Show the bottom navigation bar if hidden
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).showBottomNav();
            }

            // Dismiss the dialog
            dismiss();
        });
    }
}