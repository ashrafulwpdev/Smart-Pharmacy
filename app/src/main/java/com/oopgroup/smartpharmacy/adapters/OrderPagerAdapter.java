package com.oopgroup.smartpharmacy.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.oopgroup.smartpharmacy.fragments.CompletedOrdersFragment;
import com.oopgroup.smartpharmacy.fragments.InprogressOrdersFragment;
import com.oopgroup.smartpharmacy.fragments.TrackingFragment;

public class OrderPagerAdapter extends FragmentStateAdapter {

    public OrderPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new InprogressOrdersFragment(); // Inprogress
            case 1:
                return new TrackingFragment();      // Status
            case 2:
                return new CompletedOrdersFragment();     // Completed
            default:
                return new InprogressOrdersFragment(); // Fallback
        }
    }

    @Override
    public int getItemCount() {
        return 3; // 3 tabs: Inprogress, Status, Completed
    }
}