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
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
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
 * Styles Instagram's bottom navigation bar as a PixelMusic-inspired floating pill toolbar,
 * and adapts Reels to edge-to-edge full-screen playback.
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

        ViewGroup contentRoot = clearParentBlockAndExpandContent(
                activity,
                tabBar,
                pillHeight,
                bMargin,
                state
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
                int targetTopMargin = state.tabBarDirectInVerticalLinear
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

        applyReelsFullscreenLayout(activity, contentRoot, isReelsSelected, state);

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
     * Clears solid backgrounds and dividers on all ancestor wrappers and expands the main
     * content container behind the floating pill across every tab (including Reels).
     */
    private static ViewGroup clearParentBlockAndExpandContent(
            Activity activity,
            ViewGroup tabBar,
            int pillHeight,
            int bottomMargin,
            BarState state
    ) {
        int maxDividerHeight = dp(activity, 3);
        state.tabBarDirectInVerticalLinear = false;
        ViewGroup contentRoot = null;
        View branch = tabBar;
        ViewParent current = tabBar.getParent();

        for (int depth = 0; depth < 5 && current instanceof ViewGroup; depth++) {
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
                if (contentRoot == null && largeSibling instanceof ViewGroup) {
                    contentRoot = (ViewGroup) largeSibling;
                }
                if (parentGroup instanceof LinearLayout
                        && ((LinearLayout) parentGroup).getOrientation() == LinearLayout.VERTICAL) {
                    if (branch == tabBar) {
                        state.tabBarDirectInVerticalLinear = true;
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
        return contentRoot;
    }

    /**
     * When on the Reels tab: makes the status bar transparent for edge-to-edge video, hides the
     * top "Reels / Friends / +" header bar, and lifts the bottom author/caption/action overlays
     * above the floating navbar while leaving the video and Reel progress bar at the bottom.
     */
    private static void applyReelsFullscreenLayout(
            Activity activity,
            ViewGroup contentRoot,
            boolean isReelsSelected,
            BarState state
    ) {
        Window window = activity.getWindow();
        if (window != null) {
            View decor = window.getDecorView();
            if (isReelsSelected) {
                if (!state.reelsSystemBarsApplied) {
                    state.savedStatusBarColor = window.getStatusBarColor();
                    state.savedSystemUiVisibility = decor.getSystemUiVisibility();
                    if (Build.VERSION.SDK_INT >= 30) {
                        WindowInsetsController ic = window.getInsetsController();
                        if (ic != null) {
                            state.savedAppearance = ic.getSystemBarsAppearance();
                        }
                    }
                    state.reelsSystemBarsApplied = true;
                }
                if (window.getStatusBarColor() != Color.TRANSPARENT) {
                    window.setStatusBarColor(Color.TRANSPARENT);
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    window.setStatusBarContrastEnforced(false);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController ic = window.getInsetsController();
                    if (ic != null) {
                        ic.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    }
                }
                int flags = decor.getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (decor.getSystemUiVisibility() != flags) {
                    decor.setSystemUiVisibility(flags);
                }
                clearTopStatusInsets(contentRoot, state);
            } else if (state.reelsSystemBarsApplied) {
                state.reelsSystemBarsApplied = false;
                window.setStatusBarColor(state.savedStatusBarColor);
                decor.setSystemUiVisibility(state.savedSystemUiVisibility);
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController ic = window.getInsetsController();
                    if (ic != null) {
                        ic.setSystemBarsAppearance(state.savedAppearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    }
                }
                restoreTopStatusInsets(state);
            }
        }

        if (!isReelsSelected) {
            for (View header : state.hiddenReelsHeaders) {
                if (header != null && header.getVisibility() == View.GONE) {
                    header.setVisibility(View.VISIBLE);
                }
            }
            state.hiddenReelsHeaders.clear();
            for (View lifted : state.liftedOverlays) {
                if (lifted != null && lifted.getTranslationY() != 0f) {
                    lifted.setTranslationY(0f);
                }
            }
            state.liftedOverlays.clear();
            return;
        }

        if (contentRoot == null) {
            return;
        }

        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int topThreshold = (int) (screenHeight * 0.18f);
        int bottomThreshold = (int) (screenHeight * 0.78f);
        int maxOverlayHeight = (int) (screenHeight * 0.62f);
        float liftOffset = -dp(activity, 92);

        List<View> headers = new ArrayList<>();
        List<View> overlays = new ArrayList<>();
        Rect rect = new Rect();
        scanReelsHierarchy(contentRoot, topThreshold, bottomThreshold, maxOverlayHeight, rect, headers, overlays);

        for (View header : headers) {
            if (header.getVisibility() != View.GONE) {
                header.setVisibility(View.GONE);
                if (!state.hiddenReelsHeaders.contains(header)) {
                    state.hiddenReelsHeaders.add(header);
                }
            }
        }

        for (View overlay : overlays) {
            if (overlay.getTranslationY() != liftOffset) {
                overlay.setTranslationY(liftOffset);
                if (!state.liftedOverlays.contains(overlay)) {
                    state.liftedOverlays.add(overlay);
                }
            }
        }
    }

    private static void scanReelsHierarchy(
            View view,
            int topThreshold,
            int bottomThreshold,
            int maxOverlayHeight,
            Rect rect,
            List<View> headers,
            List<View> overlays
    ) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof SurfaceView || view instanceof TextureView
                || view instanceof SeekBar || view instanceof ProgressBar) {
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (group.getGlobalVisibleRect(rect)) {
                if (rect.top < topThreshold && rect.bottom <= (int) (topThreshold * 1.4f)
                        && !containsVideoOrProgress(group)
                        && containsReelsHeaderText(group)) {
                    headers.add(group);
                    return;
                }

                int height = group.getHeight();
                if (height > 0 && height <= maxOverlayHeight
                        && rect.bottom >= bottomThreshold
                        && !containsVideoOrProgress(group)
                        && hasOverlayContent(group)) {
                    overlays.add(group);
                    return;
                }
            }

            for (int i = 0; i < group.getChildCount(); i++) {
                scanReelsHierarchy(group.getChildAt(i), topThreshold, bottomThreshold, maxOverlayHeight, rect, headers, overlays);
            }
        }
    }

    private static boolean containsVideoOrProgress(View view) {
        if (view instanceof SurfaceView || view instanceof TextureView
                || view instanceof SeekBar || view instanceof ProgressBar) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsVideoOrProgress(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsReelsHeaderText(View view) {
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            if (cs != null) {
                String text = cs.toString().trim();
                if (text.equalsIgnoreCase("Reels") || text.equalsIgnoreCase("Friends")) {
                    return true;
                }
            }
            return false;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsReelsHeaderText(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOverlayContent(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (child instanceof TextView || child instanceof ImageView) {
                return true;
            }
            if (child instanceof ViewGroup && hasOverlayContent((ViewGroup) child)) {
                return true;
            }
        }
        return false;
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

    private static void clearTopStatusInsets(ViewGroup contentRoot, BarState state) {
        View current = contentRoot;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (current.getPaddingTop() > 0) {
                if (!state.savedTopPaddings.containsKey(current)) {
                    state.savedTopPaddings.put(current, current.getPaddingTop());
                }
                current.setPadding(
                        current.getPaddingLeft(),
                        0,
                        current.getPaddingRight(),
                        current.getPaddingBottom()
                );
            }
            ViewGroup.LayoutParams lp = current.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                if (mlp.topMargin > 0) {
                    if (!state.savedTopMargins.containsKey(current)) {
                        state.savedTopMargins.put(current, mlp.topMargin);
                    }
                    mlp.topMargin = 0;
                    current.setLayoutParams(mlp);
                }
            }
            if (current.getId() == android.R.id.content) {
                break;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
    }

    private static void restoreTopStatusInsets(BarState state) {
        for (Map.Entry<View, Integer> entry : state.savedTopPaddings.entrySet()) {
            View v = entry.getKey();
            if (v != null) {
                v.setPadding(v.getPaddingLeft(), entry.getValue(), v.getPaddingRight(), v.getPaddingBottom());
            }
        }
        state.savedTopPaddings.clear();
        for (Map.Entry<View, Integer> entry : state.savedTopMargins.entrySet()) {
            View v = entry.getKey();
            if (v != null && v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                mlp.topMargin = entry.getValue();
                v.setLayoutParams(mlp);
            }
        }
        state.savedTopMargins.clear();
    }

    private static final class BarState {
        GradientDrawable pillDrawable;
        boolean tabBarDirectInVerticalLinear;
        boolean reelsSystemBarsApplied;
        int savedStatusBarColor;
        int savedSystemUiVisibility;
        int savedAppearance;
        final Map<View, Integer> savedTopPaddings = new WeakHashMap<>();
        final Map<View, Integer> savedTopMargins = new WeakHashMap<>();
        final List<View> hiddenReelsHeaders = new ArrayList<>();
        final List<View> liftedOverlays = new ArrayList<>();
        int lastPillColor;
        int lastOnPillColor;
        int lastChildCount = -1;
        long lastSelectionMask = -1L;
    }
}
