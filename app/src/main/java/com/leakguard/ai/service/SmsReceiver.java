package com.leakguard.ai.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.leakguard.ai.data.local.IntegrationPrefs;
import com.leakguard.ai.utils.SmsParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wakes on incoming SMS. It does NO network work itself (a receiver has ~10s):
 * if the message looks like a financial SMS and SMS integration is on, it
 * schedules a one-time sync a few seconds later (after the default SMS app has
 * written the message to the inbox). Message content is never stored here.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        try {
            if (!new IntegrationPrefs(context).isSmsEnabled()) return;
            SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (parts == null || parts.length == 0) return;

            // Re-assemble multipart SMS per sender before classifying.
            Map<String, StringBuilder> bySender = new LinkedHashMap<>();
            for (SmsMessage p : parts) {
                if (p == null || p.getOriginatingAddress() == null) continue;
                StringBuilder sb = bySender.get(p.getOriginatingAddress());
                if (sb == null) {
                    sb = new StringBuilder();
                    bySender.put(p.getOriginatingAddress(), sb);
                }
                if (p.getMessageBody() != null) sb.append(p.getMessageBody());
            }
            for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
                if (SmsParser.parse(e.getKey(), e.getValue().toString()) != null) {
                    IntegrationSyncWorker.runNow(context, 5);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // never crash the broadcast pipeline; the periodic worker will catch up
        }
    }
}
