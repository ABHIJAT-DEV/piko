/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.actionbar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Styles Instagram top action bars (Home, Search, Chats, Profile, etc.) with PixelMusic's
 * tonal surfaceContainer background and 28dp rounded bottom corners.
 */
@SuppressWarnings("unused")
public final class RoundedHeaderStyle {
    private static final String[] HEADER_IDS = {
            "action_bar_container",
            "action_bar",
            "direct_thread_header",
            "direct_inbox_header",
            "inbox_header",
            "search_bar_container",
            "search_header",
            "app_bar_layout",
            "header_container",
            "top_bar",
            "toolbar"
    };
    private static final Map<View, HeaderState> ATTACHED_DECOR = new WeakHashMap<>();

    private RoundedHeaderStyle() {
    }

    public static void attach(Activity activity) {
        if (activity == null || ActivityHook.isPiko(activity)) {
            return;
        }
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_TOP_HEADER))) {
            return;
        }
        try {
            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (decor == null || ATTACHED_DECOR.containsKey(decor)) {
                return;
            }
            final HeaderState state = new HeaderState();
            ATTACHED_DECOR.put(decor, state);

            final Set<Integer> headerIds = new HashSet<>();
            for (String idName : HEADER_IDS) {
                int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
                if (id != 0) {
                    headerIds.add(id);
                }
            }
            final int clipsTabId = ResourceUtils.getIdentifier(ResourceType.ID, "clips_tab");
            final float radius = dp(activity, 28);
            final int minHeight = dp(activity, 44);
            final int maxHeight = dp(activity, 140);
            final int maxTopY = dp(activity, 64);
            final int maxDividerHeight = dp(activity, 3);

            final Runnable applyPass = () -> {
                if (!decor.isAttachedToWindow()
                        || !Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_TOP_HEADER))) {
                    return;
                }
                if (clipsTabId != 0) {
                    View clipsTab = decor.findViewById(clipsTabId);
                    if (clipsTab != null && (clipsTab.isSelected() || clipsTab.isActivated())) {
                        return;
                    }
                }
                int headerColor = InstagramPreferenceStyle.surfaceContainerHighColor();
                Window window = activity.getWindow();
                if (window != null && window.getStatusBarColor() != headerColor) {
                    window.setStatusBarColor(headerColor);
                }
                View statusBarBg = decor.findViewById(android.R.id.statusBarBackground);
                if (statusBarBg != null) {
                    Drawable sbg = statusBarBg.getBackground();
                    if (!(sbg instanceof ColorDrawable) || ((ColorDrawable) sbg).getColor() != headerColor) {
                        statusBarBg.setBackgroundColor(headerColor);
                    }
                }
                int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                android.graphics.Rect rect = new android.graphics.Rect();
                scanAndStyleHeaders(decor, headerIds, headerColor, radius, screenWidth, minHeight, maxHeight, maxTopY, maxDividerHeight, rect, state, 0);
            };

            ViewTreeObserver vto = decor.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(applyPass::run);
            vto.addOnPreDrawListener(() -> {
                applyPass.run();
                return true;
            });
        } catch (Throwable t) {
            Logger.printException(() -> "RoundedHeaderStyle attach failed", t);
        }
    }

    private static void scanAndStyleHeaders(
            View view,
            Set<Integer> headerIds,
            int headerColor,
            float radius,
            int screenWidth,
            int minHeight,
            int maxHeight,
            int maxTopY,
            int maxDividerHeight,
            android.graphics.Rect rect,
            HeaderState state,
            int depth
    ) {
        if (view == null || view.getVisibility() != View.VISIBLE || depth > 32) {
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int h = group.getHeight();
            int w = group.getWidth();
            if (w >= (int) (screenWidth * 0.90f) && h >= minHeight && h <= maxHeight) {
                boolean idMatch = headerIds.contains(group.getId()) || hasHeaderLikeIdName(group);
                if (idMatch && group.getGlobalVisibleRect(rect) && rect.top <= maxTopY) {
                    styleHeader(group, headerColor, radius, maxDividerHeight, state);
                    return;
                }
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                scanAndStyleHeaders(group.getChildAt(i), headerIds, headerColor, radius, screenWidth, minHeight, maxHeight, maxTopY, maxDividerHeight, rect, state, depth + 1);
            }
        }
    }

    private static boolean hasHeaderLikeIdName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return false;
        }
        try {
            String name = view.getResources().getResourceEntryName(id);
            if (name == null) {
                return false;
            }
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return (lower.contains("action_bar")
                    || lower.contains("toolbar")
                    || lower.contains("top_bar")
                    || lower.contains("app_bar")
                    || lower.contains("thread_header")
                    || lower.contains("inbox_header")
                    || lower.contains("search_header"))
                    && !lower.contains("button")
                    && !lower.contains("icon")
                    && !lower.contains("title")
                    && !lower.contains("text");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void styleHeader(
            ViewGroup header,
            int headerColor,
            float radius,
            int maxDividerHeight,
            HeaderState state
    ) {
        GradientDrawable bg = state.drawables.get(header);
        if (bg == null) {
            bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadii(new float[]{
                    0f, 0f, 0f, 0f,
                    radius, radius, radius, radius
            });
            state.drawables.put(header, bg);
        }
        bg.setColor(headerColor);

        if (header.getBackground() != bg) {
            header.setBackground(bg);
            header.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            header.setClipToOutline(true);
            header.setElevation(dp(header.getContext(), 2));
        }

        // Hide thin divider line below the action bar if present.
        ViewParent parent = header.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) parent;
            if (parentGroup.getClipChildren()) {
                parentGroup.setClipChildren(false);
            }
            if (parentGroup.getClipToPadding()) {
                parentGroup.setClipToPadding(false);
            }
            if (parentGroup instanceof android.widget.LinearLayout
                    && ((android.widget.LinearLayout) parentGroup).getOrientation() == android.widget.LinearLayout.VERTICAL
                    && parentGroup.getPaddingTop() > 0) {
                int pTop = parentGroup.getPaddingTop();
                parentGroup.setPadding(
                        parentGroup.getPaddingLeft(),
                        0,
                        parentGroup.getPaddingRight(),
                        parentGroup.getPaddingBottom()
                );
                header.setPadding(
                        header.getPaddingLeft(),
                        header.getPaddingTop() + pTop,
                        header.getPaddingRight(),
                        header.getPaddingBottom()
                );
                ViewGroup.LayoutParams hlp = header.getLayoutParams();
                if (hlp != null && hlp.height > 0) {
                    hlp.height += pTop;
                    header.setLayoutParams(hlp);
                }
            }
            if (Math.abs(parentGroup.getHeight() - header.getHeight()) <= dp(header.getContext(), 4)) {
                Drawable pbg = parentGroup.getBackground();
                if (pbg != null && (!(pbg instanceof ColorDrawable) || ((ColorDrawable) pbg).getColor() != Color.TRANSPARENT)) {
                    parentGroup.setBackgroundColor(Color.TRANSPARENT);
                }
            }
            for (int i = 0; i < parentGroup.getChildCount(); i++) {
                View sibling = parentGroup.getChildAt(i);
                if (sibling != null && sibling != header && sibling.getVisibility() == View.VISIBLE) {
                    int sh = sibling.getHeight();
                    if (sh > 0 && sh <= maxDividerHeight && sibling.getTop() >= header.getBottom() - maxDividerHeight) {
                        sibling.setVisibility(View.GONE);
                    }
                }
            }
        }
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE) {
                int ch = child.getHeight();
                if (ch > 0 && ch <= maxDividerHeight && child.getBottom() >= header.getHeight() - maxDividerHeight) {
                    child.setVisibility(View.GONE);
                } else {
                    Drawable cbg = child.getBackground();
                    if (cbg instanceof ColorDrawable && ((ColorDrawable) cbg).getColor() != Color.TRANSPARENT) {
                        child.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
            }
        }
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static final class HeaderState {
        final Map<View, GradientDrawable> drawables = new WeakHashMap<>();
    }
}
