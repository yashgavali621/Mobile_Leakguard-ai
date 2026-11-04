package com.leakguard.ai.presentation.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.leakguard.ai.databinding.ItemOnboardingPageBinding;

public class OnboardingPageFragment extends Fragment {

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_BODY = "arg_body";
    private static final String ARG_ICON = "arg_icon";

    private ItemOnboardingPageBinding binding;

    public static OnboardingPageFragment newInstance(OnboardingItem item) {
        OnboardingPageFragment fragment = new OnboardingPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, item.titleRes);
        args.putInt(ARG_BODY, item.bodyRes);
        args.putInt(ARG_ICON, item.iconRes);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = ItemOnboardingPageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        binding.textTitle.setText(args.getInt(ARG_TITLE));
        binding.textBody.setText(args.getInt(ARG_BODY));
        binding.imageOnboarding.setImageResource(args.getInt(ARG_ICON));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
