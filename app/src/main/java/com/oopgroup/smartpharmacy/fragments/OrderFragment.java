package com.oopgroup.smartpharmacy.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.oopgroup.smartpharmacy.R;
import com.oopgroup.smartpharmacy.adapters.OrderPagerAdapter;

public class OrderFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle("Appointments");
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // Initialize TabLayout and ViewPager
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);

        // Setup ViewPager with adapter
        OrderPagerAdapter pagerAdapter = new OrderPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager and use a custom view for tabs
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            TextView tabView = new TextView(getContext());
            tabView.setText(position == 0 ? "Scheduled" : "Completed");
            tabView.setTextSize(16);
            tabView.setTextColor(getResources().getColor(R.color.inactiveTabText, null));
            tabView.setBackgroundResource(R.drawable.tab_background); // Using the selector

            // Set proper layout to avoid gaps
            int height = (int) (45 * getResources().getDisplayMetrics().density);
            tabView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));

            // Set padding for better UI
            tabView.setPadding(8, 8, 8, 8);
            tabView.setGravity(android.view.Gravity.CENTER);

            tab.setCustomView(tabView);
        }).attach();

        // Update the selected/unselected state
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView tabView = (TextView) tab.getCustomView();
                if (tabView != null) {
                    tabView.setSelected(true);
                    tabView.setTextColor(getResources().getColor(android.R.color.white, null));
                    tabView.setTypeface(Typeface.DEFAULT_BOLD);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                TextView tabView = (TextView) tab.getCustomView();
                if (tabView != null) {
                    tabView.setSelected(false);
                    tabView.setTextColor(getResources().getColor(R.color.inactiveTabText, null));
                    tabView.setTypeface(Typeface.DEFAULT);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Set the first tab as selected by default
        TextView firstTabView = (TextView) tabLayout.getTabAt(0).getCustomView();
        if (firstTabView != null) {
            firstTabView.setSelected(true);
            firstTabView.setTextColor(getResources().getColor(android.R.color.white, null));
            firstTabView.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }
}
