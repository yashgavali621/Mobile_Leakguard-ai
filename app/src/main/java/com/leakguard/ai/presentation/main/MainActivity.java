package com.leakguard.ai.presentation.main;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.leakguard.ai.R;
import com.leakguard.ai.databinding.ActivityMainBinding;
import com.leakguard.ai.presentation.alerts.AlertsFragment;
import com.leakguard.ai.presentation.dashboard.DashboardFragment;
import com.leakguard.ai.presentation.insights.AIInsightsFragment;
import com.leakguard.ai.presentation.profile.ProfileFragment;
import com.leakguard.ai.presentation.subscriptions.SubscriptionsFragment;

/**
 * Hosts the 5 primary destinations (product spec section 4): Home,
 * Subscriptions, Alerts, AI, Profile. Tabs are kept alive with
 * show()/hide() rather than replace() so switching tabs preserves each
 * fragment's scroll position and in-memory state (section 4: "navigation
 * must preserve state when switching tabs").
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_HOME = "tag_home";
    private static final String TAG_SUBSCRIPTIONS = "tag_subscriptions";
    private static final String TAG_ALERTS = "tag_alerts";
    private static final String TAG_AI = "tag_ai";
    private static final String TAG_PROFILE = "tag_profile";

    private ActivityMainBinding binding;
    private Fragment activeFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            initFragments();
        } else {
            // Fragments were restored by the FragmentManager itself; just
            // re-resolve which one is currently visible.
            for (Fragment f : getSupportFragmentManager().getFragments()) {
                if (f.isVisible()) {
                    activeFragment = f;
                }
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showFragment(TAG_HOME);
            } else if (id == R.id.nav_subscriptions) {
                showFragment(TAG_SUBSCRIPTIONS);
            } else if (id == R.id.nav_alerts) {
                showFragment(TAG_ALERTS);
            } else if (id == R.id.nav_ai) {
                showFragment(TAG_AI);
            } else if (id == R.id.nav_profile) {
                showFragment(TAG_PROFILE);
            }
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.bottomNavigation.getSelectedItemId() != R.id.nav_home) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initFragments() {
        Fragment home = new DashboardFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, home, TAG_HOME)
                .commit();
        activeFragment = home;
    }

    private Fragment getFragment(String tag) {
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    private void showFragment(@NonNull String tag) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment target = getFragment(tag);
        if (target == null) {
            target = createFragment(tag);
            transaction.add(R.id.fragmentContainer, target, tag);
        }
        if (activeFragment != null && activeFragment != target) {
            transaction.hide(activeFragment);
        }
        transaction.show(target);
        transaction.commit();
        activeFragment = target;
    }

    private Fragment createFragment(String tag) {
        switch (tag) {
            case TAG_HOME:
                return new DashboardFragment();
            case TAG_SUBSCRIPTIONS:
                return new SubscriptionsFragment();
            case TAG_ALERTS:
                return new AlertsFragment();
            case TAG_AI:
                return new AIInsightsFragment();
            case TAG_PROFILE:
                return new ProfileFragment();
            default:
                throw new IllegalArgumentException("Unknown tab tag: " + tag);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
