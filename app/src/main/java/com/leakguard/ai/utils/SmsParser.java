package com.leakguard.ai.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device SMS classifier. Runs BEFORE anything leaves the phone: OTPs,
 * personal chats, promos and credits are dropped here, and for kept messages
 * only (amount, merchant, event type) is ever uploaded - never the SMS body.
 */
public final class SmsParser {

    public static final class Result {
        public final double amount;
        public final String merchant;            // may be null
        public final String normalizedMerchant;  // may be null
        public final String eventType;           // matches financial_events.event_type
        public final boolean recurring;

        Result(double amount, String merchant, String eventType) {
            this.amount = amount;
            this.merchant = merchant;
            this.normalizedMerchant = normalize(merchant);
            this.eventType = eventType;
            this.recurring = "SUBSCRIPTION".equals(eventType) || "RENEWAL".equals(eventType);
        }
    }

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern PHONE_SENDER = Pattern.compile("^\\+?[0-9][0-9 \\-]{6,}$");
    private static final Pattern OTP = Pattern.compile("\\b(otp|one[- ]?time (password|pin)|verification code)\\b", CI);
    private static final Pattern AMOUNT = Pattern.compile("(?:\\brs\\.?|\\binr|\\u20B9)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI);
    private static final Pattern FAILED = Pattern.compile("\\b(failed|declined|unsuccessful|not processed|could not be processed|insufficient)\\b", CI);
    private static final Pattern PAYISH = Pattern.compile("(debit|payment|mandate|autopay|auto-pay|txn|transaction|purchase|renew)", CI);
    private static final Pattern REFUND = Pattern.compile("\\b(refund|refunded|reversal|reversed)\\b", CI);
    private static final Pattern SUBSCRIPTION = Pattern.compile("\\b(mandate|autopay|auto-pay|auto pay|auto-debit|auto debit|standing instruction|subscription|subscribed|recurring)\\b", CI);
    private static final Pattern RENEW = Pattern.compile("\\brenew\\w*", CI);
    private static final Pattern DEBIT = Pattern.compile("\\b(debited|spent|paid|purchase|withdrawn|sent|charged|payment of|txn of|deducted)\\b", CI);
    private static final Pattern MERCHANT = Pattern.compile("(?:\\bat|\\bto|\\btowards|\\bfor|\\bby|@)\\s+([A-Za-z0-9][A-Za-z0-9 &'*_.@-]{1,45})", CI);
    private static final Pattern STOP = Pattern.compile("\\s+(?:on|via|using|ref|txn|upi|from|has|have|is|was|avl|bal|a/c|acct|ac|vpa|dated|with|will|by|info|call|not|failed|declined|due|insufficient|successfully|processed|credited|debited|renewed|charged|subscription|renewal|autopay|mandate)\\b.*$", CI);
    private static final Pattern REJECT = Pattern.compile("^(your|you|a/c|ac|acct|account|xx|the|my|bank|card|rs|inr|upi|ref|txn|info|\\d)", CI);

    private SmsParser() {
    }

    /** @return null when the message isn't a spend-related financial SMS. */
    public static Result parse(String sender, String body) {
        if (sender == null || body == null || body.length() < 15) return null;
        if (PHONE_SENDER.matcher(sender.trim()).matches()) return null; // banks use alphanumeric IDs
        if (OTP.matcher(body).find()) return null;

        Double amount = firstAmount(body);
        if (amount == null) return null;

        String type;
        if (FAILED.matcher(body).find() && PAYISH.matcher(body).find()) type = "FAILED_PAYMENT";
        else if (REFUND.matcher(body).find()) type = "REFUND";
        else if (SUBSCRIPTION.matcher(body).find()) type = "SUBSCRIPTION";
        else if (RENEW.matcher(body).find()) type = "RENEWAL";
        else if (DEBIT.matcher(body).find()) type = "PAYMENT";
        else return null; // credits, balance alerts, promos

        if ("SUBSCRIPTION".equals(type) && RENEW.matcher(body).find()) type = "RENEWAL";
        return new Result(amount, extractMerchant(body), type);
    }

    public static String normalize(String s) {
        if (s == null) return null;
        String n = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return n.isEmpty() ? null : n;
    }

    private static Double firstAmount(String body) {
        Matcher m = AMOUNT.matcher(body);
        while (m.find()) {
            String before = body.substring(Math.max(0, m.start() - 16), m.start()).toLowerCase(Locale.ROOT);
            if (before.contains("bal") || before.contains("avl") || before.contains("limit")) continue; // skip balances
            try {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                if (v > 0 && v < 10_000_000) return v;
            } catch (NumberFormatException ignored) {
                // try next match
            }
        }
        return null;
    }

    private static String extractMerchant(String body) {
        Matcher m = MERCHANT.matcher(body);
        while (m.find()) {
            String c = STOP.matcher(m.group(1)).replaceFirst("").trim();
            c = c.replaceFirst("(?i)^vpa\\s+", "");
            int at = c.indexOf('@');
            if (at > 0) c = c.substring(0, at);
            c = c.replaceAll("[\\s.,;:*-]+$", "").trim();
            if (c.length() < 2 || REJECT.matcher(c).find()) continue;
            return c.length() > 60 ? c.substring(0, 60) : c;
        }
        return null;
    }
}
