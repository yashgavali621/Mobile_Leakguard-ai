package com.leakguard.ai.presentation.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.leakguard.ai.R;
import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.repository.DataRepository;
import com.leakguard.ai.data.repository.ProfileRepository;
import com.leakguard.ai.presentation.common.RowRenderer;
import com.leakguard.ai.databinding.FragmentDashboardBinding;
import com.leakguard.ai.domain.model.Profile;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

/**
 * Home tab. Phase 1 has no financial-event pipeline yet (that's Phase 7+ -
 * bank statement / Gmail / notification ingestion), so this screen is a
 * genuinely honest empty state rather than a dashboard with invented
 * numbers (product spec section 31: "NO hardcoded numbers... if there is
 * no data, show meaningful empty states"). Once transactions/subscriptions
 * exist, this fragment gets a DashboardViewModel that replaces the empty
 * state with real metric cards and charts.
 */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private ProfileRepository profileRepository;
    private DataRepository dataRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        profileRepository = new ProfileRepository(requireContext());
        dataRepository = new DataRepository(requireContext());

        binding.emptyStateView
                .setIcon(R.drawable.ic_empty_dashboard)
                .setTitle(getString(R.string.empty_dashboard_title))
                .setBody(getString(R.string.empty_dashboard_body));
        // No CTA button yet - Bank Statement / Gmail / Notification Access
        // screens land in a later phase. Wiring a button to a screen that
        // doesn't exist yet would be exactly the "dead button" the product
        // spec forbids.

        binding.swipeRefreshDashboard.setOnRefreshListener(() -> {
            loadGreeting();
            loadData();
        });
        loadGreeting();
        loadData();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && binding != null) loadData();
    }

    private void loadData() {
        dataRepository.load(new RepositoryCallback<DataRepository.Snapshot>() {
            @Override
            public void onSuccess(DataRepository.Snapshot s) {
                if (binding == null) return;
                binding.swipeRefreshDashboard.setRefreshing(false);
                boolean hasData = !s.activity.isEmpty() || !s.subscriptions.isEmpty() || !s.alerts.isEmpty();
                binding.statsContainer.setVisibility(hasData ? View.VISIBLE : View.GONE);
                binding.textRecentHeader.setVisibility(s.activity.isEmpty() ? View.GONE : View.VISIBLE);
                binding.statSpend.setText(DataRepository.money(s.spend30d, s.currency));
                binding.statRecurring.setText(String.valueOf(s.subscriptions.size()));
                binding.statAlerts.setText(String.valueOf(s.alerts.size()));
                RowRenderer.render(binding.listContainer, s.activity, R.drawable.ic_row_payment);
                RowRenderer.applyEmpty(binding.emptyStateView, !hasData, s.error,
                        getString(R.string.empty_dashboard_title), getString(R.string.empty_dashboard_body),
                        getString(R.string.load_error_title));
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                if (binding == null) return;
                binding.swipeRefreshDashboard.setRefreshing(false);
                RowRenderer.applyEmpty(binding.emptyStateView, true, message,
                        getString(R.string.empty_dashboard_title), getString(R.string.empty_dashboard_body),
                        getString(R.string.load_error_title));
            }
        });
    }

    private void loadGreeting() {
        String userId = SessionManager.getInstance(requireContext()).getUserId();
        if (userId == null) {
            return;
        }
        profileRepository.getProfile(userId, new RepositoryCallback<Profile>() {
            @Override
            public void onSuccess(Profile data) {
                if (binding == null) return;
                String name = data.getFullName();
                binding.textGreeting.setText(name != null && !name.trim().isEmpty()
                        ? "Welcome back, " + firstName(name)
                        : "Welcome back");
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                if (binding == null) return;
                binding.textGreeting.setText("Welcome back");
            }
        });
    }

    private static String firstName(String fullName) {
        String trimmed = fullName.trim();
        int spaceIndex = trimmed.indexOf(' ');
        return spaceIndex > 0 ? trimmed.substring(0, spaceIndex) : trimmed;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
