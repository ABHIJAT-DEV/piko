/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.actionbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;

/**
 * Rounds Instagram's own header components (MainFeedActionBar, ExploreActionBar,
 * ProfileActionBar, IgdsActionBar) from inside the components themselves.
 *
 * Called from the components' constructors and from the methods where they swap
 * their own background (e.g. MainFeedActionBar.setFloatingChrome). No view-tree
 * scanning, no overlays: the component paints its own background as a PixelMusic
 * tonal surface with 28dp rounded bottom corners and clips itself to that outline.
 *
 * Floating states are respected: when a component clears its background (Home's
 * floating chrome over the feed) or paints it transparent (Profile over the
 * header area), the rounding is removed/left alone instead of forcing a bar.
 */
@SuppressWarnings("unused")
public final class RoundedHeader {

    private RoundedHeader() {
    }

    public static void apply(View header) {
        if (header == null) {
            return;
        }
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_TOP_HEADER))) {
            return;
        }
        try {
            Drawable background = header.getBackground();
            if (background == null) {
                // Floating state (e.g. Home chrome over the feed): no bar to round,
                // and the content chips must not be clipped to a bar outline.
                header.setClipToOutline(false);
                return;
            }
            if (background instanceof ColorDrawable
                    && Color.alpha(((ColorDrawable) background).getColor()) < 0xFF) {
                // Translucent bar (e.g. Profile over the header area): leave it floating.
                header.setClipToOutline(false);
                return;
            }

            Context context = header.getContext();
            float radius = dp(context, 28);

            GradientDrawable rounded = new GradientDrawable();
            rounded.setShape(GradientDrawable.RECTANGLE);
            rounded.setColor(InstagramPreferenceStyle.surfaceContainerHighColor());
            rounded.setCornerRadii(new float[]{
                    0f, 0f, 0f, 0f,
                    radius, radius, radius, radius
            });

            header.setBackground(rounded);
            header.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            header.setClipToOutline(true);
            header.setElevation(dp(context, 2));
        } catch (Throwable ignored) {
        }
    }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
