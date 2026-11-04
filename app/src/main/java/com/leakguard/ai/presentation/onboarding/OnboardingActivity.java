package com.leakguard.ai.presentation.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.leakguard.ai.R;
import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.databinding.ActivityOnboardingBinding;
import com.leakguard.ai.presentation.auth.LoginActivity;

import java.util.Arrays;
import java.util.List;

/**
 * 3-page onboarding (product spec section 8). Completion is persisted via
 * SessionManager so it is never shown again unless onboarding is explicitly
 * reset (e.g. from Privacy & Settings in a later phase).
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private SessionManager sessionManager;
    private final List<ImageView> dots = new java.util.ArrayList<>();

    private static final List<OnboardingItem> PAGES = Arrays.asList(
            new OnboardingItem(R.string.onboarding_title_1, R.string.onboarding_body_1, R.drawable.ic_empty_state),
            new OnboardingItem(R.string.onboarding_title_2, R.string.onboarding_body_2, R.drawable.ic_empty_state),
            new OnboardingItem(R.string.onboarding_title_3, R.string.onboarding_body_3, R.drawable.ic_empty_state)
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);

        binding.viewPagerOnboarding.setAdapter(new OnboardingPagerAdapter(this, PAGES));
        setupDots();
        binding.viewPagerOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                boolean isLastPage = position == PAGES.size() - 1;
                binding.buttonNext.setText(isLastPage ? R.string.action_get_started : R.string.action_next);
                binding.textSkip.setVisibility(isLastPage ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
            }
        });

        binding.buttonNext.setOnClickListener(v -> {
            int next = binding.viewPagerOnboarding.getCurrentItem() + 1;
            if (next < PAGES.size()) {
                binding.viewPagerOnboarding.setCurrentItem(next, true);
            } else {
                finishOnboarding();
            }
        });

        binding.textSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void setupDots() {
        binding.dotsIndicator.removeAllViews();
        dots.clear();
        for (int i = 0; i < PAGES.size(); i++) {
            ImageView dot = new ImageView(this);
            int size = (int) (8 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(size, size);
            params.setMargins(size / 2, 0, size / 2, 0);
            dot.setLayoutParams(params);
            dot.setImageResource(i == 0 ? R.drawable.dot_selected : R.drawable.dot_unselected);
            binding.dotsIndicator.addView(dot);
            dots.add(dot);
        }
    }

    private void updateDots(int selectedIndex) {
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setImageResource(i == selectedIndex ? R.drawable.dot_selected : R.drawable.dot_unselected);
        }
    }

    private void finishOnboarding() {
        sessionManager.setOnboardingCompleted(true);
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
