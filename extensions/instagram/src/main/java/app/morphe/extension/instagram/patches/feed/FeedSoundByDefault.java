/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Unmutes Home feed video/music posts by default once per session when the first
 * feed audio toggle button appears, while still allowing manual mute/unmute later.
 */
@SuppressWarnings("unused")
public final class FeedSoundByDefault {
    private static final String[] AUDIO_BUTTON_IDS = {
            "audio_icon",
            "row_feed_audio_button",
            "audio_control_view",
            "media_audio_control",
            "feed_video_audio_button",
            "sound_button",
            "audio_toggle_button",
            "volume_icon",
            "audio_indicator"
    };
    private static final String[] MEDIA_CONTAINER_IDS = {
            "media_group",
            "carousel_media_group",
            "carousel_viewpager",
            "zoomable_view_container"
    };
    private static final Map<View, Boolean> ATTACHED_DECOR = new WeakHashMap<>();
    private static boolean sSessionUnmuted = false;
    private static boolean sClickPending = false;

    private FeedSoundByDefault() {
    }

    public static void attach(Activity activity) {
        if (activity == null || ActivityHook.isPiko(activity)) {
            return;
        }
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.FEED_SOUND_BY_DEFAULT))) {
            return;
        }
        if (sSessionUnmuted) {
            return;
        }
        try {
            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (decor == null || ATTACHED_DECOR.containsKey(decor)) {
                return;
            }
            ATTACHED_DECOR.put(decor, Boolean.TRUE);

            final int feedTabId = ResourceUtils.getIdentifier(ResourceType.ID, "feed_tab");
            final int minBtn = dp(activity, 14);
            final int maxBtn = dp(activity, 56);
            final int cornerMargin = dp(activity, 44);
            final int minMediaSize = dp(activity, 160);
            final int navBarReserve = dp(activity, 80);
            final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            final int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

            final Set<Integer> audioIds = new HashSet<>();
            for (String idName : AUDIO_BUTTON_IDS) {
                int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
                if (id != 0) {
                    audioIds.add(id);
                }
            }

            final Set<Integer> mediaIds = new HashSet<>();
            for (String idName : MEDIA_CONTAINER_IDS) {
                int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
                if (id != 0) {
                    mediaIds.add(id);
                }
            }

            final Runnable checkAndUnmute = () -> {
                if (sSessionUnmuted || sClickPending) {
                    return;
                }
                if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.FEED_SOUND_BY_DEFAULT))) {
                    return;
                }
                if (!decor.isAttachedToWindow() || !activity.hasWindowFocus()) {
                    return;
                }
                if (feedTabId != 0) {
                    View feedTab = decor.findViewById(feedTabId);
                    if (feedTab != null && !feedTab.isSelected() && !feedTab.isActivated()) {
                        return;
                    }
                }

                View candidate = findAudioButtonByIdOrName(decor, audioIds, minBtn, maxBtn, screenHeight - navBarReserve, 0);
                if (candidate == null) {
                    candidate = findBottomRightAudioButton(
                            decor, mediaIds, screenWidth, screenHeight - navBarReserve, minMediaSize, minBtn, maxBtn, cornerMargin, 0
                    );
                }
                if (candidate == null) {
                    return;
                }

                if (isAlreadyUnmuted(candidate)) {
                    sSessionUnmuted = true;
                    return;
                }

                final View target = resolveClickableTarget(candidate, maxBtn);
                sClickPending = true;
                target.postDelayed(() -> {
                    sClickPending = false;
                    if (sSessionUnmuted || !target.isAttachedToWindow() || !target.isShown()) {
                        return;
                    }
                    if (isAlreadyUnmuted(target)) {
                        sSessionUnmuted = true;
                        return;
                    }
                    boolean clicked = target.isClickable() && target.performClick();
                    if (!clicked) {
                        clicked = dispatchTap(target);
                    }
                    if (clicked) {
                        sSessionUnmuted = true;
                    }
                }, 200L);
            };

            ViewTreeObserver vto = decor.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(checkAndUnmute::run);
            vto.addOnScrollChangedListener(checkAndUnmute::run);
            decor.postDelayed(checkAndUnmute, 450L);
        } catch (Throwable t) {
            Logger.printException(() -> "FeedSoundByDefault attach failed", t);
        }
    }

    private static View findAudioButtonByIdOrName(
            View view,
            Set<Integer> audioIds,
            int minBtn,
            int maxBtn,
            int maxBottomY,
            int depth
    ) {
        if (view == null || view.getVisibility() != View.VISIBLE || depth > 32) {
            return null;
        }
        int id = view.getId();
        if (id != View.NO_ID) {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w >= minBtn && w <= maxBtn && h >= minBtn && h <= maxBtn) {
                boolean match = audioIds.contains(id) || hasAudioIdName(view);
                if (match) {
                    Rect r = new Rect();
                    if (view.getGlobalVisibleRect(r) && r.bottom <= maxBottomY && r.top > 0) {
                        return view;
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findAudioButtonByIdOrName(group.getChildAt(i), audioIds, minBtn, maxBtn, maxBottomY, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean hasAudioIdName(View view) {
        try {
            String name = view.getResources().getResourceEntryName(view.getId());
            if (name == null) return false;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("voice") || lower.contains("mic") || lower.contains("tag")
                    || lower.contains("profile") || lower.contains("avatar")
                    || lower.contains("comment") || lower.contains("like")
                    || lower.contains("share") || lower.contains("save")) {
                return false;
            }
            return lower.contains("audio") || lower.contains("sound")
                    || lower.contains("mute") || lower.contains("volume");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static View findBottomRightAudioButton(
            View view,
            Set<Integer> mediaIds,
            int screenWidth,
            int maxBottomY,
            int minMediaSize,
            int minBtn,
            int maxBtn,
            int cornerMargin,
            int depth
    ) {
        if (view == null || view.getVisibility() != View.VISIBLE || depth > 32) {
            return null;
        }
        int id = view.getId();
        if (id != View.NO_ID && mediaIds.contains(id)
                && view.getWidth() >= (int) (screenWidth * 0.85f)
                && view.getHeight() >= minMediaSize
                && view instanceof ViewGroup) {
            Rect mediaRect = new Rect();
            if (view.getGlobalVisibleRect(mediaRect)
                    && mediaRect.height() >= minMediaSize / 2
                    && mediaRect.bottom <= maxBottomY) {
                View found = scanCornerButton((ViewGroup) view, mediaRect, minBtn, maxBtn, cornerMargin, 0);
                if (found != null) {
                    return found;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findBottomRightAudioButton(
                        group.getChildAt(i), mediaIds, screenWidth, maxBottomY, minMediaSize, minBtn, maxBtn, cornerMargin, depth + 1
                );
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View scanCornerButton(
            ViewGroup group,
            Rect mediaRect,
            int minBtn,
            int maxBtn,
            int cornerMargin,
            int depth
    ) {
        if (depth > 10) {
            return null;
        }
        Rect vRect = new Rect();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE || child instanceof TextView) {
                continue;
            }
            int w = child.getWidth();
            int h = child.getHeight();
            if (w >= minBtn && w <= maxBtn && h >= minBtn && h <= maxBtn) {
                if (child.getGlobalVisibleRect(vRect)
                        && vRect.left >= mediaRect.centerX()
                        && vRect.top >= mediaRect.centerY()
                        && (mediaRect.right - vRect.right) <= cornerMargin
                        && (mediaRect.bottom - vRect.bottom) <= cornerMargin
                        && hasAudioOrNeutralDescription(child)
                        && (child instanceof ImageView || hasImageViewChild(child))) {
                    return child;
                }
            }
            if (child instanceof ViewGroup) {
                View found = scanCornerButton((ViewGroup) child, mediaRect, minBtn, maxBtn, cornerMargin, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean dispatchTap(View target) {
        try {
            long downTime = SystemClock.uptimeMillis();
            float x = target.getWidth() / 2f;
            float y = target.getHeight() / 2f;
            MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_UP, x, y, 0);
            boolean handled = target.dispatchTouchEvent(down);
            handled = target.dispatchTouchEvent(up) || handled;
            down.recycle();
            up.recycle();
            return handled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasImageViewChild(View view) {
        if (view instanceof ImageView) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasImageViewChild(g.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAudioOrNeutralDescription(View view) {
        String desc = getEffectiveDescription(view);
        if (desc == null) {
            return true;
        }
        if (desc.contains("tag") || desc.contains("people") || desc.contains("profile")
                || desc.contains("like") || desc.contains("comment") || desc.contains("share")
                || desc.contains("save") || desc.contains("shop") || desc.contains("product")) {
            return false;
        }
        return desc.contains("sound") || desc.contains("audio") || desc.contains("mute")
                || desc.contains("volume") || desc.isEmpty();
    }

    private static boolean isAlreadyUnmuted(View view) {
        String desc = getEffectiveDescription(view);
        if (desc == null) {
            return false;
        }
        return desc.contains("sound is on") || desc.contains("audio is on")
                || desc.contains("turn off sound") || desc.contains("tap to mute");
    }

    private static String getEffectiveDescription(View view) {
        CharSequence cs = view.getContentDescription();
        if (cs != null && cs.length() > 0) {
            return cs.toString().toLowerCase(Locale.ROOT);
        }
        ViewParent p = view.getParent();
        if (p instanceof View) {
            CharSequence pcs = ((View) p).getContentDescription();
            if (pcs != null && pcs.length() > 0) {
                return pcs.toString().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static View resolveClickableTarget(View view, int maxBtn) {
        if (view.isClickable()) {
            return view;
        }
        ViewParent p = view.getParent();
        for (int i = 0; i < 2 && p instanceof View; i++) {
            View pv = (View) p;
            if (pv.isClickable() && pv.getWidth() <= maxBtn * 2 && pv.getHeight() <= maxBtn * 2) {
                return pv;
            }
            p = pv.getParent();
        }
        return view;
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
