package com.leakguard.ai.presentation.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leakguard.ai.databinding.FragmentProfileBinding;
import com.leakguard.ai.domain.model.Profile;
import com.leakguard.ai.presentation.auth.LoginActivity;
import com.leakguard.ai.utils.Resource;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        binding.buttonLogout.setOnClickListener(v -> confirmLogout());

        viewModel.getProfileState().observe(getViewLifecycleOwner(), this::renderProfile);
        viewModel.getLogoutState().observe(getViewLifecycleOwner(), this::renderLogout);

        viewModel.loadProfile();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log out?")
                .setMessage("You'll need to log in again to see your financial data.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> viewModel.logout())
                .show();
    }

    private void renderProfile(Resource<Profile> state) {
        if (binding == null) return;
        switch (state.getStatus()) {
            case LOADING:
                binding.progressProfile.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                binding.progressProfile.setVisibility(View.GONE);
                Profile profile = state.getData();
                if (profile != null) {
                    binding.textFullName.setText(
                            profile.getFullName() != null && !profile.getFullName().isEmpty()
                                    ? profile.getFullName() : "LeakGuard user");
                    binding.textEmail.setText(profile.getEmail() != null ? profile.getEmail() : "");
                    binding.textCurrency.setText(profile.getCurrency() != null ? profile.getCurrency() : "INR");
                    binding.textMonthlyBudget.setText(profile.getMonthlyBudget() != null
                            ? String.format(java.util.Locale.getDefault(), "%.2f", profile.getMonthlyBudget())
                            : "Not set");
                }
                break;
            case ERROR:
                binding.progressProfile.setVisibility(View.GONE);
                binding.textFullName.setText("LeakGuard user");
                binding.textEmail.setText("");
                break;
        }
    }

    private void renderLogout(Resource<Void> state) {
        if (state.isSuccess()) {
            Intent intent = new Intent(requireContext(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
