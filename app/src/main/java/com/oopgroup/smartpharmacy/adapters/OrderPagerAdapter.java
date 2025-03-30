package com.oopgroup.smartpharmacy.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.oopgroup.smartpharmacy.fragments.PastOrderFragment;
import com.oopgroup.smartpharmacy.fragments.UpcomingOrderFragment;

public class OrderPagerAdapter extends FragmentStateAdapter {

    public OrderPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new UpcomingOrderFragment();
        } else {
            return new PastOrderFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Two tabs: Upcoming and Past
    }
}