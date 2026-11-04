package com.leakguard.ai.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.leakguard.ai.data.remote.dto.ProfileDto;
import com.leakguard.ai.data.remote.supabase.ApiErrorParser;
import com.leakguard.ai.data.remote.supabase.RetrofitClient;
import com.leakguard.ai.data.remote.supabase.SupabaseDatabaseApi;
import com.leakguard.ai.domain.model.Profile;
import com.leakguard.ai.utils.Constants;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Owns every call to the public.profiles table. Every request is
 * automatically scoped to the signed-in user by Postgres RLS - the
 * "user_id=eq.<id>" filter below is a defense-in-depth narrowing, not the
 * only thing standing between one user's data and another's.
 */
public class ProfileRepository {

    private final SupabaseDatabaseApi databaseApi;
    private final Context appContext;

    public ProfileRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.databaseApi = RetrofitClient.databaseApi(context);
    }

    public void getProfile(@NonNull String userId, @NonNull RepositoryCallback<Profile> callback) {
        databaseApi.getProfile("eq." + userId, "*").enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ProfileDto>> call, @NonNull Response<List<ProfileDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(ApiErrorParser.parse(response), Resource.ErrorType.SERVER);
                    return;
                }
                if (response.body().isEmpty()) {
                    callback.onError("Profile not found.", Resource.ErrorType.UNKNOWN);
                    return;
                }
                callback.onSuccess(toDomain(response.body().get(0)));
            }

            @Override
            public void onFailure(@NonNull Call<List<ProfileDto>> call, @NonNull Throwable t) {
                callback.onError(networkErrorMessage(t), classifyThrowable(t));
            }
        });
    }

    void createInitialProfile(@NonNull String userId, @NonNull String fullName, @NonNull String email,
                               @NonNull RepositoryCallback<Void> callback) {
        ProfileDto dto = new ProfileDto(userId, fullName, email, Constants.DEFAULT_CURRENCY);
        databaseApi.createProfile(dto).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ProfileDto>> call, @NonNull Response<List<ProfileDto>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(ApiErrorParser.parse(response), Resource.ErrorType.SERVER);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ProfileDto>> call, @NonNull Throwable t) {
                callback.onError(networkErrorMessage(t), classifyThrowable(t));
            }
        });
    }

    public void updateProfile(@NonNull String userId, @NonNull Profile patch,
                               @NonNull RepositoryCallback<Profile> callback) {
        ProfileDto patchDto = fromDomainPatch(patch);
        databaseApi.updateProfile("eq." + userId, patchDto).enqueue(new Callback<List<ProfileDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ProfileDto>> call, @NonNull Response<List<ProfileDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    callback.onError(ApiErrorParser.parse(response), Resource.ErrorType.SERVER);
                    return;
                }
                callback.onSuccess(toDomain(response.body().get(0)));
            }

            @Override
            public void onFailure(@NonNull Call<List<ProfileDto>> call, @NonNull Throwable t) {
                callback.onError(networkErrorMessage(t), classifyThrowable(t));
            }
        });
    }

    private static ProfileDto fromDomainPatch(Profile p) {
        ProfileDto dto = new ProfileDto(p.getUserId(), p.getFullName(), p.getEmail(),
                p.getCurrency() != null ? p.getCurrency() : Constants.DEFAULT_CURRENCY);
        return dto;
    }

    private static Profile toDomain(ProfileDto dto) {
        Profile profile = new Profile(dto.getUserId(), dto.getFullName(), dto.getEmail(), dto.getCurrency());
        profile.setId(dto.getId());
        profile.setAvatarUrl(dto.getAvatarUrl());
        profile.setMonthlyBudget(dto.getMonthlyBudget());
        profile.setOnboardingCompleted(dto.getOnboardingCompleted() != null && dto.getOnboardingCompleted());
        return profile;
    }

    private static String networkErrorMessage(Throwable t) {
        if (t instanceof IOException) {
            return "Couldn't reach the server. Check your connection and try again.";
        }
        return "Something went wrong. Please try again.";
    }

    private static Resource.ErrorType classifyThrowable(Throwable t) {
        return (t instanceof IOException) ? Resource.ErrorType.NETWORK : Resource.ErrorType.UNKNOWN;
    }
}
