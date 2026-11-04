package com.leakguard.ai.data.remote.supabase;

import com.leakguard.ai.data.remote.dto.IntegrationDtos;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** {SUPABASE_URL}/functions/v1/gmail-sync - same host, so AuthInterceptor attaches the user's JWT. */
public interface GmailSyncApi {
    @POST("functions/v1/gmail-sync")
    Call<IntegrationDtos.GmailResponse> call(@Body IntegrationDtos.GmailRequest body);
}
