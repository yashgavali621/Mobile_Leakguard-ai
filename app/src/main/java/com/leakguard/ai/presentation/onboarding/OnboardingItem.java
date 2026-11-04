package com.leakguard.ai.presentation.onboarding;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class OnboardingItem {

    @StringRes
    public final int titleRes;

    @StringRes
    public final int bodyRes;

    @DrawableRes
    public final int iconRes;

    public OnboardingItem(@StringRes int titleRes, @StringRes int bodyRes, @DrawableRes int iconRes) {
        this.titleRes = titleRes;
        this.bodyRes = bodyRes;
        this.iconRes = iconRes;
    }
}
