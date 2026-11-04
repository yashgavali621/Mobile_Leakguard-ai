package com.leakguard.ai.presentation.onboarding;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

public class OnboardingPagerAdapter extends FragmentStateAdapter {

    private final List<OnboardingItem> items;

    public OnboardingPagerAdapter(@NonNull FragmentActivity activity, List<OnboardingItem> items) {
        super(activity);
        this.items = items;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return OnboardingPageFragment.newInstance(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
