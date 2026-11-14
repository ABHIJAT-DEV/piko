/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.navigation;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Styles Instagram's bottom navigation bar as a PixelMusic-inspired floating pill toolbar.
 */
@SuppressWarnings("unused")
public final class PixelMusicNavBarStyle {
    private static final String[] TAB_IDS = {
            "feed_tab",
            "clips_tab",
            "direct_tab",
            "search_tab",
            "profile_tab"
    };
    private static final Map<ViewGroup, BarState> ATTACHED_BARS = new WeakHashMap<>();

    private PixelMusicNavBarStyle() {
    }

    public static void attach(Activity activity) {
        if (activity == null || ActivityHook.isPiko(activity)) {
            return;
        }
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.NAV_BAR_PIXELMUSIC_STYLE))) {
            return;
        }
        try {
            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (decor == null) {
                return;
            }
            ViewGroup tabBar = findTabBar(decor);
            if (tabBar == null) {
                decor.post(() -> {
                    ViewGroup delayedBar = findTabBar(decor);
                    if (delayedBar != null) {
                        install(activity, delayedBar);
                    }
                });
                return;
            }
            install(activity, tabBar);
        } catch (Throwable t) {
            Logger.printException(() -> "PixelMusicNavBarStyle attach failed", t);
        }
    }

    private static ViewGroup findTabBar(View root) {
        for (String idName : TAB_IDS) {
            int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
            if (id != 0) {
                View tab = root.findViewById(id);
                if (tab != null) {
                    ViewParent parent = tab.getParent();
                    if (parent instanceof ViewGroup) {
                        return (ViewGroup) parent;
                    }
                }
            }
        }
        return null;
    }

    private static void install(Activity activity, ViewGroup tabBar) {
        BarState existing = ATTACHED_BARS.get(tabBar);
        if (existing != null) {
            applyStyle(activity, tabBar, existing);
            return;
        }
        BarState state = new BarState();
        ATTACHED_BARS.put(tabBar, state);
        applyStyle(activity, tabBar, state);

        tabBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!tabBar.isAttachedToWindow()) {
                    return true;
                }
                if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.NAV_BAR_PIXELMUSIC_STYLE))) {
                    return true;
                }
                applyStyle(activity, tabBar, state);
                return true;
            }
        });
    }

    private static void applyStyle(Activity activity, ViewGroup tabBar, BarState state) {
        int primaryColor = resolvePrimaryColor(activity);
        int bgColor = UI.getBackgroundColor();
        int profileTabId = ResourceUtils.getIdentifier(ResourceType.ID, "profile_tab");

        if (!state.containerStyled || state.lastPrimaryColor != primaryColor) {
            state.containerStyled = true;
            state.lastPrimaryColor = primaryColor;

            GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(dp(activity, 32));
            pill.setColor(primaryColor);
            tabBar.setBackground(pill);
            tabBar.setClipToOutline(true);
            tabBar.setElevation(dp(activity, 8));

            int hPad = dp(activity, 8);
            int vPad = dp(activity, 5);
            tabBar.setPadding(hPad, vPad, hPad, vPad);

            ViewGroup.LayoutParams lp = tabBar.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int hMargin = dp(activity, 16);
                int bMargin = dp(activity, 10);
                int tMargin = dp(activity, 4);
                if (mlp.leftMargin != hMargin || mlp.rightMargin != hMargin
                        || mlp.bottomMargin != bMargin || mlp.topMargin != tMargin) {
                    mlp.leftMargin = hMargin;
                    mlp.rightMargin = hMargin;
                    mlp.bottomMargin = bMargin;
                    mlp.topMargin = tMargin;
                    tabBar.setLayoutParams(mlp);
                }
            }

            ViewParent parent = tabBar.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup parentGroup = (ViewGroup) parent;
                parentGroup.setBackgroundColor(Color.TRANSPARENT);
                parentGroup.setClipChildren(false);
                parentGroup.setClipToPadding(false);
            }
        }

        int childCount = tabBar.getChildCount();
        long selectionMask = 0L;
        for (int i = 0; i < childCount && i < 63; i++) {
            View child = tabBar.getChildAt(i);
            if (child != null && (child.isSelected() || child.isActivated())) {
                selectionMask |= (1L << i);
            }
        }

        if (state.lastSelectionMask == selectionMask
                && state.lastBgColor == bgColor
                && state.lastChildCount == childCount) {
            return;
        }
        state.lastSelectionMask = selectionMask;
        state.lastBgColor = bgColor;
        state.lastChildCount = childCount;

        for (int i = 0; i < childCount; i++) {
            View child = tabBar.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            boolean selected = (selectionMask & (1L << i)) != 0L;
            if (selected) {
                GradientDrawable selectedPill = new GradientDrawable();
                selectedPill.setShape(GradientDrawable.RECTANGLE);
                selectedPill.setCornerRadius(dp(activity, 28));
                selectedPill.setColor(bgColor);
                child.setBackground(selectedPill);
            } else {
                child.setBackground(null);
            }

            if (child.getId() != profileTabId) {
                int iconTint = selected ? primaryColor : bgColor;
                tintTabIcons(child, iconTint, dp(activity, 14));
            }
        }
    }

    private static void tintTabIcons(View view, int tintColor, int minIconSizePx) {
        if (view instanceof ImageView) {
            int width = view.getWidth();
            int height = view.getHeight();
            // Skip tiny unread/notification badge dots.
            if ((width == 0 && height == 0) || (width >= minIconSizePx && height >= minIconSizePx)) {
                ((ImageView) view).setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintTabIcons(group.getChildAt(i), tintColor, minIconSizePx);
            }
        }
    }

    private static int resolvePrimaryColor(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                int resId = UI.isDarkMode()
                        ? android.R.color.system_accent1_200
                        : android.R.color.system_accent1_600;
                return context.getColor(resId);
            } catch (Throwable ignored) {
            }
        }
        return InstagramPreferenceStyle.selectionColor();
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static final class BarState {
        boolean containerStyled;
        int lastPrimaryColor;
        int lastBgColor;
        int lastChildCount = -1;
        long lastSelectionMask = -1L;
    }
}
