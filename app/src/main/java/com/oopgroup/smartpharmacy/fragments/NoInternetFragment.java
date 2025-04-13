package com.oopgroup.smartpharmacy.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.utils.NetworkUtils;

public class NoInternetFragment extends Fragment {

    public static NoInternetFragment newInstance() {
        return new NoInternetFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_no_internet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView messageTextView = view.findViewById(R.id.no_internet_message);
        Button retryButton = view.findViewById(R.id.retry_button);

        messageTextView.setText("No internet connection. Please check your network.");

        retryButton.setOnClickListener(v -> {
            if (NetworkUtils.isNetworkAvailable(requireContext())) {
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                messageTextView.setText("Still no internet. Please try again.");
            }
        });
    }
}