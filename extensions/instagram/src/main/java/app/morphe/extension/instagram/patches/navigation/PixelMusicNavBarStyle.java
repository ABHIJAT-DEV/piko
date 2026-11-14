/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.navigation;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
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
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

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
 * Styles Instagram's bottom navigation bar as a PixelMusic-inspired floating pill toolbar
 * with blobby spring selection animations.
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
    private static final float SELECTED_TAB_WEIGHT = 1.45f;
    private static final float UNSELECTED_TAB_WEIGHT = 1.0f;
    private static final long BLOB_ANIM_DURATION_MS = 420L;
    private static final ArgbEvaluator ARGB_EVALUATOR = new ArgbEvaluator();
    private static final Interpolator SPRING_INTERPOLATOR = t -> {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return (float) (1.0 - Math.exp(-6.5 * t) * Math.cos(10.5 * t));
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
        int pillHeight = dp(activity, 64);
        int hMargin = dp(activity, 16);
        int bMargin = dp(activity, 12);

        boolean backgroundOverwritten = state.pillDrawable == null
                || tabBar.getBackground() != state.pillDrawable;
        boolean themeChanged = state.lastPrimaryColor != primaryColor
                || state.lastBgColor != bgColor;

        if (backgroundOverwritten || themeChanged) {
            if (state.pillDrawable == null) {
                state.pillDrawable = new GradientDrawable();
                state.pillDrawable.setShape(GradientDrawable.RECTANGLE);
            }
            state.pillDrawable.setCornerRadius(dp(activity, 32));
            state.pillDrawable.setColor(primaryColor);

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

        boolean tabBarIsDirectContentSibling = clearParentBlockAndExpandContent(activity, tabBar, pillHeight, bMargin);

        ViewGroup.LayoutParams lp = tabBar.getLayoutParams();
        if (lp != null) {
            boolean lpChanged = false;
            if (lp.height != pillHeight) {
                lp.height = pillHeight;
                lpChanged = true;
            }
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int targetTopMargin = tabBarIsDirectContentSibling ? -(pillHeight + bMargin) : dp(activity, 4);
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

        int childCount = tabBar.getChildCount();
        long selectionMask = 0L;
        for (int i = 0; i < childCount && i < 63; i++) {
            View child = tabBar.getChildAt(i);
            if (child != null && (child.isSelected() || child.isActivated())) {
                selectionMask |= (1L << i);
            }
        }

        boolean selectionChanged = state.lastSelectionMask != -1L
                && state.lastSelectionMask != selectionMask;
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

        long previousMask = state.lastSelectionMask;
        state.lastPrimaryColor = primaryColor;
        state.lastBgColor = bgColor;
        state.lastSelectionMask = selectionMask;
        state.lastChildCount = childCount;

        if (selectionChanged) {
            tabBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            animateBlobbySelection(activity, tabBar, state, previousMask, selectionMask, primaryColor, bgColor, profileTabId);
        } else {
            applyStaticSelection(activity, tabBar, selectionMask, primaryColor, bgColor, profileTabId);
        }
    }

    private static void applyStaticSelection(
            Activity activity,
            ViewGroup tabBar,
            long selectionMask,
            int primaryColor,
            int bgColor,
            int profileTabId
    ) {
        int insetH = dp(activity, 4);
        int insetV = dp(activity, 2);
        int childCount = tabBar.getChildCount();
        boolean isLinear = tabBar instanceof LinearLayout;

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
                selectedPill.setColor(bgColor);
                child.setBackground(new InsetDrawable(selectedPill, insetH, insetV, insetH, insetV));
            } else {
                child.setBackground(null);
            }

            if (isLinear && child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) child.getLayoutParams();
                float targetWeight = selected ? SELECTED_TAB_WEIGHT : UNSELECTED_TAB_WEIGHT;
                if (clp.weight != targetWeight) {
                    clp.weight = targetWeight;
                    child.setLayoutParams(clp);
                }
            }

            child.setScaleX(1f);
            child.setScaleY(1f);

            if (child.getId() != profileTabId) {
                int iconTint = selected ? primaryColor : bgColor;
                tintTabIcons(child, iconTint, dp(activity, 14));
            }
        }
    }

    private static void animateBlobbySelection(
            Activity activity,
            ViewGroup tabBar,
            BarState state,
            long previousMask,
            long selectionMask,
            int primaryColor,
            int bgColor,
            int profileTabId
    ) {
        if (state.blobAnimator != null) {
            state.blobAnimator.cancel();
        }

        int insetH = dp(activity, 4);
        int insetV = dp(activity, 2);
        int childCount = tabBar.getChildCount();
        boolean isLinear = tabBar instanceof LinearLayout;
        int minIconSizePx = dp(activity, 14);

        final float[] startWeights = new float[childCount];
        final float[] endWeights = new float[childCount];

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
                selectedPill.setColor(bgColor);
                child.setBackground(new InsetDrawable(selectedPill, insetH, insetV, insetH, insetV));
            } else {
                child.setBackground(null);
            }

            if (isLinear && child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) child.getLayoutParams();
                startWeights[i] = clp.weight > 0f ? clp.weight : UNSELECTED_TAB_WEIGHT;
                endWeights[i] = selected ? SELECTED_TAB_WEIGHT : UNSELECTED_TAB_WEIGHT;
            }
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(BLOB_ANIM_DURATION_MS);
        animator.setInterpolator(SPRING_INTERPOLATOR);
        animator.addUpdateListener(anim -> {
            float fraction = (float) anim.getAnimatedValue();
            float clampedFraction = Math.max(0f, Math.min(1f, fraction));
            boolean layoutChanged = false;

            for (int i = 0; i < childCount && i < tabBar.getChildCount(); i++) {
                View child = tabBar.getChildAt(i);
                if (child == null || child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                boolean wasSelected = (previousMask & (1L << i)) != 0L;
                boolean nowSelected = (selectionMask & (1L << i)) != 0L;

                if (isLinear && child.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) child.getLayoutParams();
                    float w = startWeights[i] + (endWeights[i] - startWeights[i]) * fraction;
                    if (w < 0.7f) w = 0.7f;
                    clp.weight = w;
                    layoutChanged = true;
                }

                if (nowSelected && !wasSelected) {
                    float scale = 0.88f + 0.12f * fraction;
                    child.setScaleX(scale);
                    child.setScaleY(scale);
                } else {
                    child.setScaleX(1f);
                    child.setScaleY(1f);
                }

                if (child.getId() != profileTabId) {
                    int startColor = wasSelected ? primaryColor : bgColor;
                    int endColor = nowSelected ? primaryColor : bgColor;
                    if (startColor != endColor) {
                        int blended = (int) ARGB_EVALUATOR.evaluate(clampedFraction, startColor, endColor);
                        tintTabIcons(child, blended, minIconSizePx);
                    } else {
                        tintTabIcons(child, endColor, minIconSizePx);
                    }
                }
            }
            if (layoutChanged) {
                tabBar.requestLayout();
            }
        });
        state.blobAnimator = animator;
        animator.start();
    }

    /**
     * Clears solid backgrounds and dividers on ancestor wrappers and expands the main content
     * container behind the floating pill so no sharp-edged block appears behind it.
     *
     * @return true if tabBar itself is a direct child of a vertical LinearLayout alongside content.
     */
    private static boolean clearParentBlockAndExpandContent(
            Activity activity,
            ViewGroup tabBar,
            int pillHeight,
            int bottomMargin
    ) {
        int maxDividerHeight = dp(activity, 3);
        boolean tabBarDirectInVerticalLinear = false;
        View branch = tabBar;
        ViewParent current = tabBar.getParent();

        for (int depth = 0; depth < 4 && current instanceof ViewGroup; depth++) {
            ViewGroup parentGroup = (ViewGroup) current;
            Drawable bg = parentGroup.getBackground();
            if (bg != null && (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != Color.TRANSPARENT)) {
                parentGroup.setBackgroundColor(Color.TRANSPARENT);
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
                        int overlapTop = -(pillHeight + bottomMargin + dp(activity, 8));
                        if (blp.topMargin != overlapTop) {
                            blp.topMargin = overlapTop;
                            branch.setLayoutParams(blp);
                        }
                    }
                } else {
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
        GradientDrawable pillDrawable;
        ValueAnimator blobAnimator;
        int lastPrimaryColor;
        int lastBgColor;
        int lastChildCount = -1;
        long lastSelectionMask = -1L;
    }
}
