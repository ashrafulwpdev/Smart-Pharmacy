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

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        TextView toolbarTitle = view.findViewById(R.id.toolbar_title);
        if (toolbarTitle != null) {
            toolbarTitle.setText("Orders History"); // Set dynamically with null check
        } else {
            toolbar.setTitle("Orders History"); // Fallback to default title
        }
        toolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);

        OrderPagerAdapter pagerAdapter = new OrderPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            TextView tabView = new TextView(getContext());
            switch (position) {
                case 0:
                    tabView.setText("Inprogress");
                    break;
                case 1:
                    tabView.setText("Status");
                    break;
                case 2:
                    tabView.setText("Completed");
                    break;
            }
            tabView.setTextSize(16);
            tabView.setTextColor(getResources().getColor(R.color.inactiveTabText, null));
            tabView.setBackgroundResource(R.drawable.tab_background);

            int height = (int) (45 * getResources().getDisplayMetrics().density);
            tabView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            tabView.setPadding(8, 8, 8, 8);
            tabView.setGravity(android.view.Gravity.CENTER);

            tab.setCustomView(tabView);
        }).attach();

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

        TextView firstTabView = (TextView) tabLayout.getTabAt(0).getCustomView();
        if (firstTabView != null) {
            firstTabView.setSelected(true);
            firstTabView.setTextColor(getResources().getColor(android.R.color.white, null));
            firstTabView.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }
}