package com.leakguard.ai.data.remote.supabase;

import com.leakguard.ai.data.remote.dto.AuthResponse;
import com.leakguard.ai.data.remote.dto.RecoverPasswordRequest;
import com.leakguard.ai.data.remote.dto.RefreshTokenRequest;
import com.leakguard.ai.data.remote.dto.SignInRequest;
import com.leakguard.ai.data.remote.dto.SignUpRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Supabase GoTrue (auth.*) REST endpoints - {SUPABASE_URL}/auth/v1/...
 * See https://supabase.com/docs/reference/auth (Auth is used directly via
 * its REST surface here rather than a JS/Kotlin client SDK, since none of
 * Supabase's official client SDKs target Java/Android).
 */
public interface SupabaseAuthApi {

    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    Call<AuthResponse> signUp(@Body SignUpRequest body);

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token")
    Call<AuthResponse> signIn(@Query("grant_type") String grantType, @Body SignInRequest body);

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token")
    Call<AuthResponse> refreshSession(@Query("grant_type") String grantType, @Body RefreshTokenRequest body);

    @Headers("Content-Type: application/json")
    @POST("auth/v1/recover")
    Call<Void> recoverPassword(@Body RecoverPasswordRequest body);

    @Headers("Content-Type: application/json")
    @POST("auth/v1/logout")
    Call<Void> logout();
}
