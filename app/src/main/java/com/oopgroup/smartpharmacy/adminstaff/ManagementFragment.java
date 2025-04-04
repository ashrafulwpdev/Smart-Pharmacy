package com.oopgroup.smartpharmacy.adminstaff;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.oopgroup.smartpharmacy.R;

public class ManagementFragment extends Fragment {
    private AdminDataManager dataManager;
    private AdminUIHelper uiHelper;
    private TabLayout tabLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_management, container, false);

        // Use the overloaded constructor without LoadingSpinnerUtil
        dataManager = new AdminDataManager((AppCompatActivity) getActivity(), ((AdminMainActivity) getActivity()).currentUser);
        uiHelper = new AdminUIHelper((AppCompatActivity) getActivity(), dataManager, view);

        tabLayout = view.findViewById(R.id.tabLayout);

        uiHelper.initializeUI();
        setupTabs();

        return view;
    }

    private void setupTabs() {
        String[] tabTitles = {"Banners", "Categories", "Lab Tests", "Products", "Users", "Notifications", "Scanner", "Coupons"};
        for (String title : tabTitles) {
            tabLayout.addTab(tabLayout.newTab().setText(title));
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                uiHelper.showSection(getSectionName(tab.getPosition()));
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                uiHelper.showSection(getSectionName(tab.getPosition()));
            }
        });

        tabLayout.getTabAt(0).select();
    }

    private String getSectionName(int position) {
        switch (position) {
            case 1: return "categories";
            case 2: return "labTests";
            case 3: return "products";
            case 4: return "users";
            case 5: return "notifications";
            case 6: return "scanner";
            case 7: return "coupons";
            default: return "banner";
        }
    }

    public void handleImageResult(android.net.Uri imageUri) {
        if (uiHelper != null) {
            uiHelper.handleImageResult(imageUri);
        }
    }
}