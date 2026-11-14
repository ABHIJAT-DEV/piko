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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.theme.MaterialYouTheme;
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
    private static final int BW_DARK = 0xFF141416;
    private static final int BW_LIGHT = 0xFFF5F5F7;
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
        boolean materialYou = MaterialYouTheme.isMaterialYouEnabled();
        boolean dark = UI.isDarkMode();
        int pillColor = resolvePillColor(activity, materialYou, dark);
        int onPillColor = resolveOnPillColor(materialYou, dark);
        int profileTabId = ResourceUtils.getIdentifier(ResourceType.ID, "profile_tab");
        int clipsTabId = ResourceUtils.getIdentifier(ResourceType.ID, "clips_tab");
        int pillHeight = dp(activity, 64);
        int hMargin = dp(activity, 16);
        int bMargin = dp(activity, 12);

        int childCount = tabBar.getChildCount();
        long selectionMask = 0L;
        boolean isReelsSelected = false;
        for (int i = 0; i < childCount && i < 63; i++) {
            View child = tabBar.getChildAt(i);
            if (child != null && (child.isSelected() || child.isActivated())) {
                selectionMask |= (1L << i);
                if (clipsTabId != 0 && child.getId() == clipsTabId) {
                    isReelsSelected = true;
                }
            }
        }

        boolean backgroundOverwritten = state.pillDrawable == null
                || tabBar.getBackground() != state.pillDrawable;
        boolean themeChanged = state.lastPillColor != pillColor
                || state.lastOnPillColor != onPillColor;

        if (backgroundOverwritten || themeChanged) {
            if (state.pillDrawable == null) {
                state.pillDrawable = new GradientDrawable();
                state.pillDrawable.setShape(GradientDrawable.RECTANGLE);
            }
            state.pillDrawable.setCornerRadius(dp(activity, 32));
            state.pillDrawable.setColor(pillColor);

            tabBar.setBackground(state.pillDrawable);
            tabBar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            tabBar.setClipToOutline(true);
            tabBar.setElevation(dp(activity, 8));
            tabBar.setMinimumHeight(pillHeight);

            int pad = dp(activity, 8);
            if (tabBar.getPaddingLeft() != pad || tabBar.getPaddingTop() != pad
                    || tabBar.getPaddingRight() != pad || tabBar.getPaddingBottom() != pad) {
                tabBar.setPadding(pad, pad, pad, pad);
            }
        }

        boolean tabBarIsDirectContentSibling = clearParentBlockAndAdjustContent(
                activity,
                tabBar,
                pillHeight,
                bMargin,
                isReelsSelected
        );

        ViewGroup.LayoutParams lp = tabBar.getLayoutParams();
        if (lp != null) {
            boolean lpChanged = false;
            if (lp.height != pillHeight) {
                lp.height = pillHeight;
                lpChanged = true;
            }
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int targetTopMargin = (!isReelsSelected && tabBarIsDirectContentSibling)
                        ? -(pillHeight + bMargin)
                        : dp(activity, 4);
                if (mlp.leftMargin != hMargin || mlp.rightMargin != hMargin
                        || mlp.bottomMargin != bMargin || mlp.topMargin != targetTopMargin) {
                    mlp.leftMargin = hMargin;
                    mlp.rightMargin = hMargin;
                    mlp.bottomMargin = bMargin;
                    mlp.topMargin = targetTopMargin;
                    lpChanged = true;
                }
            }
            if (lpChanged) {
                tabBar.setLayoutParams(lp);
            }
        }

        boolean needsTabRestyle = backgroundOverwritten
                || themeChanged
                || state.lastSelectionMask != selectionMask
                || state.lastChildCount != childCount;

        if (!needsTabRestyle) {
            for (int i = 0; i < childCount && i < 63; i++) {
                View child = tabBar.getChildAt(i);
                if (child != null && child.getVisibility() == View.VISIBLE) {
                    boolean selected = (selectionMask & (1L << i)) != 0L;
                    if (selected && !(child.getBackground() instanceof InsetDrawable)) {
                        needsTabRestyle = true;
                        break;
                    }
                }
            }
        }

        if (!needsTabRestyle) {
            return;
        }

        state.lastPillColor = pillColor;
        state.lastOnPillColor = onPillColor;
        state.lastSelectionMask = selectionMask;
        state.lastChildCount = childCount;

        int insetH = dp(activity, 4);
        int insetV = dp(activity, 2);

        for (int i = 0; i < childCount; i++) {
            View child = tabBar.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            boolean selected = (selectionMask & (1L << i)) != 0L;
            if (selected) {
                GradientDrawable selectedPill = new GradientDrawable();
                selectedPill.setShape(GradientDrawable.RECTANGLE);
                selectedPill.setCornerRadius(dp(activity, 24));
                selectedPill.setColor(onPillColor);
                child.setBackground(new InsetDrawable(selectedPill, insetH, insetV, insetH, insetV));
            } else {
                child.setBackground(null);
            }

            if (child.getId() != profileTabId) {
                int iconTint = selected ? pillColor : onPillColor;
                tintTabIcons(child, iconTint, dp(activity, 14));
            }
        }
    }

    /**
     * Clears solid backgrounds and dividers on ancestor wrappers and expands the main content
     * container behind the floating pill on non-Reels tabs. On Reels, keeps the content above
     * the pill so username, caption, and action buttons stay visible while the progress bar
     * remains at the bottom of the Reel.
     */
    private static boolean clearParentBlockAndAdjustContent(
            Activity activity,
            ViewGroup tabBar,
            int pillHeight,
            int bottomMargin,
            boolean isReelsSelected
    ) {
        int maxDividerHeight = dp(activity, 3);
        boolean tabBarDirectInVerticalLinear = false;
        View branch = tabBar;
        ViewParent current = tabBar.getParent();
        int wrapperBg = isReelsSelected ? Color.BLACK : Color.TRANSPARENT;

        for (int depth = 0; depth < 4 && current instanceof ViewGroup; depth++) {
            ViewGroup parentGroup = (ViewGroup) current;
            Drawable bg = parentGroup.getBackground();
            int targetBg = depth == 0 ? wrapperBg : Color.TRANSPARENT;
            if (bg == null && targetBg != Color.TRANSPARENT) {
                parentGroup.setBackgroundColor(targetBg);
            } else if (bg != null && (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != targetBg)) {
                parentGroup.setBackgroundColor(targetBg);
            }
            parentGroup.setClipChildren(false);
            parentGroup.setClipToPadding(false);

            View largeSibling = null;
            for (int i = 0; i < parentGroup.getChildCount(); i++) {
                View sibling = parentGroup.getChildAt(i);
                if (sibling == null || sibling == branch || sibling.getVisibility() != View.VISIBLE) {
                    continue;
                }
                int h = sibling.getHeight();
                ViewGroup.LayoutParams slp = sibling.getLayoutParams();
                int lpH = slp != null ? slp.height : -1;
                if (depth == 0 && ((h > 0 && h <= maxDividerHeight) || (lpH > 0 && lpH <= maxDividerHeight))) {
                    sibling.setVisibility(View.GONE);
                } else if (h > pillHeight * 2) {
                    largeSibling = sibling;
                }
            }

            if (largeSibling != null) {
                if (parentGroup instanceof LinearLayout
                        && ((LinearLayout) parentGroup).getOrientation() == LinearLayout.VERTICAL) {
                    if (branch == tabBar) {
                        tabBarDirectInVerticalLinear = true;
                    } else if (branch.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams blp = (ViewGroup.MarginLayoutParams) branch.getLayoutParams();
                        int targetTop = isReelsSelected ? 0 : -(pillHeight + bottomMargin + dp(activity, 8));
                        if (blp.topMargin != targetTop) {
                            blp.topMargin = targetTop;
                            branch.setLayoutParams(blp);
                        }
                    }
                } else if (!isReelsSelected) {
                    ViewGroup.LayoutParams clp = largeSibling.getLayoutParams();
                    if (clp instanceof RelativeLayout.LayoutParams) {
                        RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) clp;
                        if (rlp.getRule(RelativeLayout.ABOVE) != 0) {
                            rlp.removeRule(RelativeLayout.ABOVE);
                            rlp.bottomMargin = 0;
                            largeSibling.setLayoutParams(rlp);
                        }
                    } else if (clp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) clp;
                        if (mlp.bottomMargin > 0) {
                            mlp.bottomMargin = 0;
                            largeSibling.setLayoutParams(mlp);
                        }
                    }
                }
            }

            branch = parentGroup;
            current = parentGroup.getParent();
        }
        return tabBarDirectInVerticalLinear;
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

    private static int resolvePillColor(Context context, boolean materialYou, boolean dark) {
        if (materialYou && Build.VERSION.SDK_INT >= 31) {
            try {
                int resId = dark
                        ? android.R.color.system_accent1_200
                        : android.R.color.system_accent1_600;
                return context.getColor(resId);
            } catch (Throwable ignored) {
            }
        }
        return dark ? BW_LIGHT : BW_DARK;
    }

    private static int resolveOnPillColor(boolean materialYou, boolean dark) {
        if (materialYou) {
            return UI.getBackgroundColor();
        }
        return dark ? BW_DARK : Color.WHITE;
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static final class BarState {
        GradientDrawable pillDrawable;
        int lastPillColor;
        int lastOnPillColor;
        int lastChildCount = -1;
        long lastSelectionMask = -1L;
    }
}
