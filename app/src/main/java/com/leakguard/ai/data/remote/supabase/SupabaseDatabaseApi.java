package com.leakguard.ai.data.remote.supabase;

import com.leakguard.ai.data.remote.dto.DataDtos;
import com.leakguard.ai.data.remote.dto.IntegrationDtos;
import com.leakguard.ai.data.remote.dto.ProfileDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * PostgREST table endpoints - {SUPABASE_URL}/rest/v1/...
 *
 * Every call here is implicitly scoped to the caller's own rows by Row Level
 * Security (auth.uid() = user_id), enforced server-side in Postgres - see
 * supabase/migrations/0001_init_schema.sql. Phase 1 only wires up `profiles`;
 * later phases add TransactionRepository, SubscriptionRepository etc. against
 * the same pattern.
 */
public interface SupabaseDatabaseApi {

    @GET("rest/v1/profiles")
    Call<List<ProfileDto>> getProfile(@Query("user_id") String userIdEq, @Query("select") String select);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/profiles")
    Call<List<ProfileDto>> createProfile(@Body ProfileDto profile);

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    Call<List<ProfileDto>> updateProfile(@Query("user_id") String userIdEq, @Body ProfileDto patch);

    // ---- Email / SMS integration ----

    /** Batch insert; rows already present (same user/source/source_event_id) are skipped and NOT returned. */
    @Headers("Prefer: resolution=ignore-duplicates,return=representation")
    @POST("rest/v1/financial_events?on_conflict=user_id,source,source_event_id")
    Call<List<IntegrationDtos.FinancialEvent>> upsertFinancialEvents(@Body List<IntegrationDtos.FinancialEvent> events);

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/sms_events")
    Call<Void> insertSmsEvents(@Body List<IntegrationDtos.SmsEvent> events);

    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/connected_sources?on_conflict=user_id,source_type")
    Call<Void> upsertConnectedSource(@Body IntegrationDtos.ConnectedSource source);

    // ---- Read endpoints for the Home / Subscriptions / Alerts / AI tabs ----

    @GET("rest/v1/subscriptions")
    Call<List<DataDtos.Subscription>> getSubscriptions(@Query("user_id") String userIdEq, @Query("select") String select,
                                                       @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/alerts")
    Call<List<DataDtos.Alert>> getAlerts(@Query("user_id") String userIdEq, @Query("select") String select,
                                         @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/ai_insights")
    Call<List<DataDtos.Insight>> getInsights(@Query("user_id") String userIdEq, @Query("select") String select,
                                             @Query("order") String order, @Query("limit") int limit);

    @GET("rest/v1/financial_events")
    Call<List<IntegrationDtos.FinancialEvent>> getFinancialEvents(@Query("user_id") String userIdEq, @Query("select") String select,
                                                                  @Query("order") String order, @Query("limit") int limit);
}
