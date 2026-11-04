package com.leakguard.ai.data.remote.dto;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/** Read-only row shapes for the tables shown on the Subscriptions / Alerts / AI tabs. */
public final class DataDtos {

    private DataDtos() {
    }

    public static class Subscription {
        @SerializedName("id") public String id;
        @SerializedName("subscription_name") public String subscriptionName;
        @SerializedName("merchant") public String merchant;
        @SerializedName("amount") public Double amount;
        @SerializedName("currency") public String currency;
        @SerializedName("frequency") public String frequency;
        @SerializedName("next_renewal_date") public String nextRenewalDate;
        @SerializedName("status") public String status;
    }

    public static class Alert {
        @SerializedName("id") public String id;
        @SerializedName("alert_type") public String alertType;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("severity") public String severity;
        @SerializedName("amount") public Double amount;
        @SerializedName("is_read") public Boolean isRead;
        @SerializedName("occurred_at") public String occurredAt;
    }

    public static class Insight {
        @SerializedName("id") public String id;
        @SerializedName("insight_text") public String insightText;
        @SerializedName("category") public String category;
        @SerializedName("severity") public String severity;
        @SerializedName("recommendations") public JsonElement recommendations; // jsonb array of strings
        @SerializedName("created_at") public String createdAt;
    }
}
