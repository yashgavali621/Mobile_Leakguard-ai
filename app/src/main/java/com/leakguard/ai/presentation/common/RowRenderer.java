package com.leakguard.ai.presentation.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.leakguard.ai.R;
import com.leakguard.ai.data.repository.DataRepository.Row;

import java.util.List;

/** Fills a LinearLayout with one card per Row and drives the matching EmptyStateView. */
public final class RowRenderer {

    private RowRenderer() {
    }

    public static void render(LinearLayout container, List<Row> rows) {
        render(container, rows, R.drawable.ic_row_payment);
    }

    public static void render(LinearLayout container, List<Row> rows, @DrawableRes int iconRes) {
        container.removeAllViews();
        int index = 0;
        Context ctx = container.getContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (Row r : rows) {
            View v = inflater.inflate(R.layout.item_row, container, false);
            ((TextView) v.findViewById(R.id.rowTitle)).setText(r.title);
            TextView trailing = v.findViewById(R.id.rowTrailing);
            trailing.setText(r.trailing);
            trailing.setVisibility(r.trailing == null || r.trailing.isEmpty() ? View.GONE : View.VISIBLE);
            TextView sub = v.findViewById(R.id.rowSubtitle);
            sub.setText(r.subtitle);
            sub.setVisibility(r.subtitle == null || r.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            TextView badge = v.findViewById(R.id.rowBadge);
            if (r.badge == null || r.badge.isEmpty()) {
                badge.setVisibility(View.GONE);
            } else {
                int fgRes, bgRes;
                switch (r.tone) {
                    case 1: fgRes = R.color.semantic_info; bgRes = R.color.semantic_info_bg; break;
                    case 2: fgRes = R.color.semantic_success; bgRes = R.color.semantic_success_bg; break;
                    case 3: fgRes = R.color.semantic_warning; bgRes = R.color.semantic_warning_bg; break;
                    case 4: fgRes = R.color.semantic_danger; bgRes = R.color.semantic_danger_bg; break;
                    default: fgRes = R.color.semantic_neutral; bgRes = R.color.semantic_neutral_bg;
                }
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(ContextCompat.getColor(ctx, bgRes));
                bg.setCornerRadius(ctx.getResources().getDimension(R.dimen.radius_pill));
                badge.setBackground(bg);
                badge.setTextColor(ContextCompat.getColor(ctx, fgRes));
                badge.setText(r.badge);
                badge.setVisibility(View.VISIBLE);
            }
            ImageView icon = v.findViewById(R.id.rowIcon);
            icon.setImageResource(iconRes);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(ContextCompat.getColor(ctx, bgResFor(r.tone)));
            icon.setBackground(iconBg);
            icon.setColorFilter(ContextCompat.getColor(ctx, fgResFor(r.tone)));
            v.setAlpha(0f);
            v.setTranslationY(24f);
            v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(index++, 8) * 40L).setDuration(260).start();
            container.addView(v);
        }
    }

    private static int fgResFor(int tone) {
        switch (tone) {
            case 1: return R.color.semantic_info;
            case 2: return R.color.semantic_success;
            case 3: return R.color.semantic_warning;
            case 4: return R.color.semantic_danger;
            default: return R.color.semantic_neutral;
        }
    }

    private static int bgResFor(int tone) {
        switch (tone) {
            case 1: return R.color.semantic_info_bg;
            case 2: return R.color.semantic_success_bg;
            case 3: return R.color.semantic_warning_bg;
            case 4: return R.color.semantic_danger_bg;
            default: return R.color.semantic_neutral_bg;
        }
    }

    /** Hides the empty state when there are rows; shows the load error instead of "no data" if one occurred. */
    public static void applyEmpty(EmptyStateView empty, boolean noRows, String error, String title, String body, String errorTitle) {
        empty.setVisibility(noRows ? View.VISIBLE : View.GONE);
        if (noRows) {
            if (error != null) empty.setTitle(errorTitle).setBody(error);
            else empty.setTitle(title).setBody(body);
        }
    }
}
