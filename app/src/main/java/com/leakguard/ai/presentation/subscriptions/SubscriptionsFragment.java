package com.leakguard.ai.presentation.subscriptions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.leakguard.ai.R;
import com.leakguard.ai.data.repository.DataRepository;
import com.leakguard.ai.databinding.FragmentSubscriptionsBinding;
import com.leakguard.ai.presentation.common.RowRenderer;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

/** Loads this tab's rows from Supabase (see DataRepository); shows the empty state only when there are none. */
public class SubscriptionsFragment extends Fragment {

    private FragmentSubscriptionsBinding binding;
    private DataRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentSubscriptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new DataRepository(requireContext());
        binding.emptyStateView.setIcon(R.drawable.ic_empty_subscriptions).setTitle(getString(R.string.empty_subscriptions_title)).setBody(getString(R.string.empty_subscriptions_body));
        binding.swipeRefresh.setOnRefreshListener(this::load);
        load();
    }

    /** Tabs are show()/hide()n by MainActivity, so refresh whenever this tab becomes visible again. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && binding != null) load();
    }

    private void load() {
        repository.load(new RepositoryCallback<DataRepository.Snapshot>() {
            @Override
            public void onSuccess(DataRepository.Snapshot s) {
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                RowRenderer.render(binding.listContainer, s.subscriptions, R.drawable.ic_row_subscription);
                RowRenderer.applyEmpty(binding.emptyStateView, s.subscriptions.isEmpty(), s.error,
                        getString(R.string.empty_subscriptions_title), getString(R.string.empty_subscriptions_body), getString(R.string.load_error_title));
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                if (binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                RowRenderer.render(binding.listContainer, java.util.Collections.<DataRepository.Row>emptyList());
                RowRenderer.applyEmpty(binding.emptyStateView, true, message,
                        getString(R.string.empty_subscriptions_title), getString(R.string.empty_subscriptions_body), getString(R.string.load_error_title));
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
