/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.util.TypedValue;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Clips Home feed post media (photos, carousels, and videos) with rounded corners.
 */
@SuppressWarnings("unused")
public final class RoundedFeedStyle {
    private static final String[] MEDIA_CONTAINER_IDS = {
            "media_group",
            "carousel_media_group",
            "carousel_viewpager",
            "zoomable_view_container",
            "row_feed_photo_imageview"
    };
    private static final Map<View, Boolean> ATTACHED_DECOR = new WeakHashMap<>();

    private RoundedFeedStyle() {
    }

    public static void attach(Activity activity) {
        if (activity == null || ActivityHook.isPiko(activity)) {
            return;
        }
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_FEED_MEDIA))) {
            return;
        }
        try {
            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (decor == null || ATTACHED_DECOR.containsKey(decor)) {
                return;
            }
            ATTACHED_DECOR.put(decor, Boolean.TRUE);

            final int cornerRadius = dp(activity, 20);
            final int horizontalInset = dp(activity, 8);
            final int minMediaSize = dp(activity, 180);
            final int feedTabId = ResourceUtils.getIdentifier(ResourceType.ID, "feed_tab");

            final ViewOutlineProvider provider = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if (w > horizontalInset * 2 && h > 0) {
                        outline.setRoundRect(horizontalInset, 0, w - horizontalInset, h, cornerRadius);
                    }
                }
            };

            decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (!decor.isAttachedToWindow()) {
                        return true;
                    }
                    if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_FEED_MEDIA))) {
                        return true;
                    }
                    if (feedTabId != 0) {
                        View feedTab = decor.findViewById(feedTabId);
                        if (feedTab != null && !feedTab.isSelected() && !feedTab.isActivated()) {
                            return true;
                        }
                    }
                    int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                    applyToFeedMedia(decor, provider, screenWidth, screenHeight, minMediaSize);
                    return true;
                }
            });
        } catch (Throwable t) {
            Logger.printException(() -> "RoundedFeedStyle attach failed", t);
        }
    }

    private static void applyToFeedMedia(
            View root,
            ViewOutlineProvider provider,
            int screenWidth,
            int screenHeight,
            int minMediaSize
    ) {
        boolean matchedById = false;
        for (String idName : MEDIA_CONTAINER_IDS) {
            int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
            if (id != 0) {
                View target = root.findViewById(id);
                if (target != null && target.isShown()
                        && target.getWidth() >= (int) (screenWidth * 0.90f)
                        && target.getHeight() >= minMediaSize) {
                    clipRounded(target, provider);
                    matchedById = true;
                }
            }
        }
        if (matchedById) {
            // Also scan visible RecyclerView items for Litho-rendered feed posts.
            scanForMediaViews(root, provider, screenWidth, screenHeight, minMediaSize, 0);
        } else {
            scanForMediaViews(root, provider, screenWidth, screenHeight, minMediaSize, 0);
        }
    }

    private static void scanForMediaViews(
            View view,
            ViewOutlineProvider provider,
            int screenWidth,
            int screenHeight,
            int minMediaSize,
            int depth
    ) {
        if (view == null || !view.isShown() || depth > 14) {
            return;
        }
        int w = view.getWidth();
        int h = view.getHeight();
        if (w >= (int) (screenWidth * 0.92f)
                && h >= minMediaSize
                && h <= (int) (screenHeight * 0.85f)
                && (view instanceof ImageView || view instanceof TextureView)) {
            clipRounded(view, provider);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanForMediaViews(group.getChildAt(i), provider, screenWidth, screenHeight, minMediaSize, depth + 1);
            }
        }
    }

    private static void clipRounded(View view, ViewOutlineProvider provider) {
        if (view.getOutlineProvider() != provider) {
            view.setOutlineProvider(provider);
        }
        if (!view.getClipToOutline()) {
            view.setClipToOutline(true);
        }
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
