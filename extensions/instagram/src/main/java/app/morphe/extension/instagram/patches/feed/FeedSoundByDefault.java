/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.TypedValue;
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
            "volume_icon"
    };
    private static final String[] MEDIA_CONTAINER_IDS = {
            "media_group",
            "carousel_media_group",
            "zoomable_view_container"
    };
    private static final Map<View, Boolean> ATTACHED_DECOR = new WeakHashMap<>();
    private static boolean sSessionUnmuted = false;

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
            final int minBtn = dp(activity, 16);
            final int maxBtn = dp(activity, 48);
            final int cornerMargin = dp(activity, 36);
            final int minMediaSize = dp(activity, 180);
            final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;

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
                if (sSessionUnmuted) {
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

                View candidate = findAudioButtonById(decor, audioIds, minBtn, maxBtn, 0);
                if (candidate == null) {
                    candidate = findBottomRightAudioButton(
                            decor, mediaIds, screenWidth, minMediaSize, minBtn, maxBtn, cornerMargin, 0
                    );
                }
                if (candidate == null) {
                    return;
                }

                if (isAlreadyUnmuted(candidate)) {
                    sSessionUnmuted = true;
                    return;
                }

                final View clickTarget = resolveClickableTarget(candidate, maxBtn);
                if (clickTarget != null) {
                    sSessionUnmuted = true;
                    clickTarget.post(clickTarget::performClick);
                }
            };

            ViewTreeObserver vto = decor.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(checkAndUnmute::run);
            vto.addOnScrollChangedListener(checkAndUnmute::run);
            decor.postDelayed(checkAndUnmute, 350L);
        } catch (Throwable t) {
            Logger.printException(() -> "FeedSoundByDefault attach failed", t);
        }
    }

    private static View findAudioButtonById(View view, Set<Integer> audioIds, int minBtn, int maxBtn, int depth) {
        if (view == null || view.getVisibility() != View.VISIBLE || depth > 32) {
            return null;
        }
        int id = view.getId();
        if (id != View.NO_ID && audioIds.contains(id)) {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w >= minBtn && w <= maxBtn && h >= minBtn && h <= maxBtn) {
                return view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findAudioButtonById(group.getChildAt(i), audioIds, minBtn, maxBtn, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View findBottomRightAudioButton(
            View view,
            Set<Integer> mediaIds,
            int screenWidth,
            int minMediaSize,
            int minBtn,
            int maxBtn,
            int cornerMargin,
            int depth
    ) {
        if (view == null || view.getVisibility() != View.VISIBLE || depth > 28) {
            return null;
        }
        int id = view.getId();
        if (id != View.NO_ID && mediaIds.contains(id)
                && view.getWidth() >= (int) (screenWidth * 0.90f)
                && view.getHeight() >= minMediaSize
                && view instanceof ViewGroup) {
            Rect mediaRect = new Rect();
            if (view.getGlobalVisibleRect(mediaRect) && mediaRect.height() >= minMediaSize / 2) {
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
                        group.getChildAt(i), mediaIds, screenWidth, minMediaSize, minBtn, maxBtn, cornerMargin, depth + 1
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
        if (depth > 8) {
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
                        && hasAudioOrNeutralDescription(child)) {
                    if (child instanceof ImageView || hasImageViewChild(child)) {
                        View target = resolveClickableTarget(child, maxBtn);
                        if (target != null) {
                            return target;
                        }
                    }
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
        if (p instanceof View) {
            View pv = (View) p;
            if (pv.isClickable() && pv.getWidth() <= maxBtn * 2 && pv.getHeight() <= maxBtn * 2) {
                return pv;
            }
        }
        return null;
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
