package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Wire formats for the Email/SMS integration (financial_events, sms_events,
 * connected_sources via PostgREST, and the gmail-sync Edge Function).
 * Null fields are omitted by Gson, so PostgREST applies column defaults.
 */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    public static class FinancialEvent {
        @SerializedName("id") public String id; // response only
        @SerializedName("user_id") public String userId;
        @SerializedName("source") public String source;
        @SerializedName("source_event_id") public String sourceEventId;
        @SerializedName("event_type") public String eventType;
        @SerializedName("merchant") public String merchant;
        @SerializedName("normalized_merchant") public String normalizedMerchant;
        @SerializedName("amount") public Double amount;
        @SerializedName("currency") public String currency;
        @SerializedName("transaction_date") public String transactionDate; // yyyy-MM-dd
        @SerializedName("confidence") public Double confidence;
        @SerializedName("is_recurring") public Boolean isRecurring;
    }

    public static class SmsEvent {
        @SerializedName("user_id") public String userId;
        @SerializedName("sender") public String sender;
        @SerializedName("merchant") public String merchant;
        @SerializedName("amount") public Double amount;
        @SerializedName("event_type") public String eventType;
        @SerializedName("received_at") public String receivedAt; // ISO-8601 UTC
        @SerializedName("processed") public Boolean processed;
        @SerializedName("financial_event_id") public String financialEventId;
    }

    public static class ConnectedSource {
        @SerializedName("user_id") public String userId;
        @SerializedName("source_type") public String sourceType;
        @SerializedName("status") public String status;
        @SerializedName("last_sync_at") public String lastSyncAt;

        public ConnectedSource(String userId, String sourceType, String status, String lastSyncAt) {
            this.userId = userId;
            this.sourceType = sourceType;
            this.status = status;
            this.lastSyncAt = lastSyncAt;
        }
    }

    public static class GmailRequest {
        @SerializedName("action") public final String action;
        @SerializedName("code") public final String code;

        public GmailRequest(String action, String code) {
            this.action = action;
            this.code = code;
        }
    }

    public static class GmailResponse {
        @SerializedName("ok") public Boolean ok;
        @SerializedName("connected") public Boolean connected;
        @SerializedName("scanned") public Integer scanned;
        @SerializedName("imported") public Integer imported;
        @SerializedName("error") public String error;
    }
}
