package com.leakguard.ai.presentation.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.leakguard.ai.R;

/**
 * Reusable "EmptyState" component (design-system item #7 in the product
 * spec's component list). Every list-based screen in the app should use
 * this instead of hand-rolling its own blank view, so empty states stay
 * visually consistent (icon + title + body + optional action).
 */
public class EmptyStateView extends FrameLayout {

    private ImageView image;
    private TextView title;
    private TextView body;
    private MaterialButton action;

    public EmptyStateView(Context context) {
        super(context);
        init(context);
    }

    public EmptyStateView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EmptyStateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_empty_state, this, true);
        image = findViewById(R.id.imageEmptyState);
        title = findViewById(R.id.textEmptyTitle);
        body = findViewById(R.id.textEmptyBody);
        action = findViewById(R.id.buttonEmptyAction);
    }

    public EmptyStateView setTitle(CharSequence text) {
        title.setText(text);
        return this;
    }

    public EmptyStateView setBody(CharSequence text) {
        body.setText(text);
        return this;
    }

    public EmptyStateView setIcon(@DrawableRes int drawableRes) {
        image.setImageResource(drawableRes);
        return this;
    }

    /**
     * Shows an actionable button. Only call this when the destination screen
     * genuinely exists and works - an EmptyState with no wired destination
     * should simply leave the action hidden (its default state) rather than
     * show a button that does nothing.
     */
    public EmptyStateView setAction(CharSequence label, OnClickListener listener) {
        action.setText(label);
        action.setOnClickListener(listener);
        action.setVisibility(VISIBLE);
        return this;
    }

    public EmptyStateView hideAction() {
        action.setVisibility(GONE);
        return this;
    }
}
