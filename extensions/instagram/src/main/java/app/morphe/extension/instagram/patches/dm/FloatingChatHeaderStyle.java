/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
 * Styles the DM thread header in a floating Gimmi-inspired layout:
 * - Softly faded & blurred top gradient backdrop with no solid rectangle bar
 * - Circular floating Back button on the left
 * - Large centered floating profile avatar with overlapping name pill
 * - Floating dual-action pill on the right with Audio & Video call buttons
 */
@SuppressWarnings("unused")
public final class FloatingChatHeaderStyle {
    private static final String[] THREAD_HEADER_IDS = {
            "direct_thread_header",
            "thread_header",
            "action_bar_container"
    };
    private static final Map<View, HeaderState> ATTACHED_DECOR = new WeakHashMap<>();

    private FloatingChatHeaderStyle() {
    }

    public static void attach(Activity activity) {
        if (activity == null || ActivityHook.isPiko(activity)) {
            return;
        }
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.FLOATING_CHAT_HEADER))) {
            return;
        }
        try {
            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (decor == null) {
                return;
            }
            HeaderState existing = ATTACHED_DECOR.get(decor);
            if (existing != null) {
                return;
            }
            HeaderState state = new HeaderState();
            ATTACHED_DECOR.put(decor, state);

            ViewTreeObserver vto = decor.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(() -> update(activity, decor, state));
            vto.addOnPreDrawListener(() -> {
                if (decor.isAttachedToWindow()) {
                    update(activity, decor, state);
                }
                return true;
            });
        } catch (Throwable t) {
            Logger.printException(() -> "FloatingChatHeaderStyle attach failed", t);
        }
    }

    private static void update(Activity activity, View decor, HeaderState state) {
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.FLOATING_CHAT_HEADER))) {
            hideOverlay(state);
            return;
        }
        if (!isInsideDmThread(decor)) {
            hideOverlay(state);
            return;
        }

        ViewGroup origHeader = findThreadHeader(activity, decor);
        if (origHeader == null || !origHeader.isShown()) {
            hideOverlay(state);
            return;
        }

        makeOriginalHeaderTransparentAndOverlapList(activity, origHeader, state);
        ensureOverlayBuilt(activity, decor, state);
        syncOverlayContent(activity, origHeader, state);
    }

    private static boolean isInsideDmThread(View decor) {
        int composerId = ResourceUtils.getIdentifier(ResourceType.ID, "row_thread_composer_textarea");
        if (composerId != 0) {
            View v = decor.findViewById(composerId);
            if (v != null && v.isShown()) {
                return true;
            }
        }
        EditText et = findVisibleEditTextNearBottom(decor, decor.getHeight(), 0);
        return et != null;
    }

    private static EditText findVisibleEditTextNearBottom(View view, int rootHeight, int depth) {
        if (view == null || !view.isShown() || depth > 24) {
            return null;
        }
        if (view instanceof EditText) {
            Rect r = new Rect();
            if (view.getGlobalVisibleRect(r) && r.top >= (int) (rootHeight * 0.45f)) {
                return (EditText) view;
            }
            return null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                EditText found = findVisibleEditTextNearBottom(g.getChildAt(i), rootHeight, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ViewGroup findThreadHeader(Activity activity, View decor) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int maxTopY = dp(activity, 64);
        int minH = dp(activity, 44);
        int maxH = dp(activity, 128);
        Rect rect = new Rect();

        for (String idName : THREAD_HEADER_IDS) {
            int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
            if (id != 0) {
                View v = decor.findViewById(id);
                if (v instanceof ViewGroup && v.isShown()
                        && v.getWidth() >= (int) (screenWidth * 0.90f)
                        && v.getHeight() >= minH && v.getHeight() <= maxH
                        && v.getGlobalVisibleRect(rect) && rect.top <= maxTopY) {
                    return (ViewGroup) v;
                }
            }
        }
        return null;
    }

    private static void makeOriginalHeaderTransparentAndOverlapList(
            Activity activity,
            ViewGroup origHeader,
            HeaderState state
    ) {
        Drawable bg = origHeader.getBackground();
        if (bg != null && (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != Color.TRANSPARENT)) {
            origHeader.setBackgroundColor(Color.TRANSPARENT);
        }
        origHeader.setElevation(0f);

        for (int i = 0; i < origHeader.getChildCount(); i++) {
            View child = origHeader.getChildAt(i);
            if (child != null && child.getAlpha() != 0f) {
                child.setAlpha(0f);
            }
        }

        ViewParent parent = origHeader.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) parent;
            if (parentGroup.getClipChildren()) {
                parentGroup.setClipChildren(false);
            }
            if (parentGroup.getClipToPadding()) {
                parentGroup.setClipToPadding(false);
            }

            int maxDiv = dp(activity, 3);
            for (int i = 0; i < parentGroup.getChildCount(); i++) {
                View sibling = parentGroup.getChildAt(i);
                if (sibling == null || sibling == origHeader || sibling.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (sibling.getHeight() > 0 && sibling.getHeight() <= maxDiv
                        && sibling.getTop() >= origHeader.getBottom() - maxDiv) {
                    sibling.setVisibility(View.GONE);
                } else if (parentGroup instanceof LinearLayout
                        && ((LinearLayout) parentGroup).getOrientation() == LinearLayout.VERTICAL
                        && sibling.getHeight() > origHeader.getHeight() * 2) {
                    int overlap = -origHeader.getHeight();
                    if (sibling.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) sibling.getLayoutParams();
                        if (mlp.topMargin != overlap) {
                            mlp.topMargin = overlap;
                            sibling.setLayoutParams(mlp);
                        }
                    }
                }
            }
        }
    }

    private static void ensureOverlayBuilt(Activity activity, View decor, HeaderState state) {
        if (!(decor instanceof ViewGroup)) {
            return;
        }
        ViewGroup decorGroup = (ViewGroup) decor;
        if (state.overlayRoot != null) {
            if (state.overlayRoot.getParent() != decorGroup) {
                if (state.overlayRoot.getParent() instanceof ViewGroup) {
                    ((ViewGroup) state.overlayRoot.getParent()).removeView(state.overlayRoot);
                }
                decorGroup.addView(state.overlayRoot);
            }
            if (state.overlayRoot.getVisibility() != View.VISIBLE) {
                state.overlayRoot.setVisibility(View.VISIBLE);
            }
            return;
        }

        boolean dark = UI.isDarkMode();
        int bgBase = InstagramPreferenceStyle.backgroundColor();
        int pillBg = dark ? 0xEE1E2024 : 0xF5FFFFFF;
        int pillBorder = dark ? 0x22FFFFFF : 0x16000000;
        int iconColor = InstagramPreferenceStyle.primaryTextColor();

        FrameLayout root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setClickable(false);
        root.setFocusable(false);

        // Soft faded & slightly blurred top backdrop
        View fadeBackdrop = new View(activity);
        fadeBackdrop.setClickable(false);
        fadeBackdrop.setFocusable(false);
        int cTop = (bgBase & 0x00FFFFFF) | 0xF2000000;
        int cMid = (bgBase & 0x00FFFFFF) | 0xB8000000;
        int cEnd = (bgBase & 0x00FFFFFF);
        GradientDrawable fadeDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{cTop, cMid, cEnd}
        );
        fadeBackdrop.setBackground(fadeDrawable);
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                fadeBackdrop.setRenderEffect(RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {
            }
        }
        root.addView(fadeBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 132),
                Gravity.TOP
        ));

        int topOffset = getStatusBarHeight(activity) + dp(activity, 8);
        int sideMargin = dp(activity, 14);

        // Left circular Back pill
        FrameLayout backPill = createPillContainer(activity, dp(activity, 23), pillBg, pillBorder);
        ImageView backIcon = new ImageView(activity);
        backIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backIcon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        backPill.addView(backIcon, new FrameLayout.LayoutParams(
                dp(activity, 22), dp(activity, 22), Gravity.CENTER
        ));
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(activity, 46), dp(activity, 46));
        backLp.gravity = Gravity.TOP | Gravity.START;
        backLp.leftMargin = sideMargin;
        backLp.topMargin = topOffset;
        root.addView(backPill, backLp);

        // Center big floating avatar + overlapping name pill
        FrameLayout centerWrap = new FrameLayout(activity);
        centerWrap.setClipChildren(false);
        centerWrap.setClipToPadding(false);

        int avatarSize = dp(activity, 64);
        FrameLayout avatarFrame = new FrameLayout(activity);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(pillBg);
        avatarBg.setStroke(dp(activity, 2), bgBase);
        avatarFrame.setBackground(avatarBg);
        avatarFrame.setElevation(dp(activity, 6));
        avatarFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatarFrame.setClipToOutline(true);

        ImageView avatarView = new ImageView(activity);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarFrame.addView(avatarView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams avatarLp = new FrameLayout.LayoutParams(avatarSize, avatarSize);
        avatarLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        centerWrap.addView(avatarFrame, avatarLp);

        TextView namePill = new TextView(activity);
        namePill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        namePill.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        namePill.setTextColor(iconColor);
        namePill.setMaxLines(1);
        namePill.setEllipsize(TextUtils.TruncateAt.END);
        namePill.setMaxWidth(dp(activity, 150));
        namePill.setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 12), dp(activity, 4));
        namePill.setElevation(dp(activity, 7));
        GradientDrawable nameBg = new GradientDrawable();
        nameBg.setShape(GradientDrawable.RECTANGLE);
        nameBg.setCornerRadius(dp(activity, 14));
        nameBg.setColor(pillBg);
        nameBg.setStroke(dp(activity, 1), pillBorder);
        namePill.setBackground(nameBg);

        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        nameLp.topMargin = avatarSize - dp(activity, 10);
        centerWrap.addView(namePill, nameLp);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        centerLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        centerLp.topMargin = topOffset - dp(activity, 2);
        root.addView(centerWrap, centerLp);

        // Right floating dual-FAB pill (Audio call + Video call)
        LinearLayout rightPill = new LinearLayout(activity);
        rightPill.setOrientation(LinearLayout.HORIZONTAL);
        rightPill.setGravity(Gravity.CENTER_VERTICAL);
        rightPill.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
        GradientDrawable rightBg = new GradientDrawable();
        rightBg.setShape(GradientDrawable.RECTANGLE);
        rightBg.setCornerRadius(dp(activity, 23));
        rightBg.setColor(pillBg);
        rightBg.setStroke(dp(activity, 1), pillBorder);
        rightPill.setBackground(rightBg);
        rightPill.setElevation(dp(activity, 5));

        ImageView audioBtn = new ImageView(activity);
        audioBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        audioBtn.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        audioBtn.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        rightPill.addView(audioBtn, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)));

        ImageView videoBtn = new ImageView(activity);
        videoBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        videoBtn.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        videoBtn.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        rightPill.addView(videoBtn, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)));

        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(activity, 46)
        );
        rightLp.gravity = Gravity.TOP | Gravity.END;
        rightLp.rightMargin = sideMargin;
        rightLp.topMargin = topOffset;
        root.addView(rightPill, rightLp);

        decorGroup.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        ));

        state.overlayRoot = root;
        state.backPill = backPill;
        state.backIcon = backIcon;
        state.centerWrap = centerWrap;
        state.avatarView = avatarView;
        state.namePill = namePill;
        state.rightPill = rightPill;
        state.audioBtn = audioBtn;
        state.videoBtn = videoBtn;
    }

    private static void syncOverlayContent(Activity activity, ViewGroup origHeader, HeaderState state) {
        List<ImageView> icons = new ArrayList<>();
        List<TextView> texts = new ArrayList<>();
        collectViews(origHeader, icons, texts);

        Rect r = new Rect();
        Collections.sort(icons, Comparator.comparingInt(iv -> {
            iv.getGlobalVisibleRect(r);
            return r.left;
        }));
        Collections.sort(texts, Comparator.comparingInt(tv -> {
            tv.getGlobalVisibleRect(r);
            return r.top;
        }));

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        ImageView origBack = null;
        ImageView origAvatar = null;
        List<ImageView> rightIcons = new ArrayList<>();

        for (ImageView iv : icons) {
            if (iv.getGlobalVisibleRect(r)) {
                if (r.left < screenWidth * 0.16f && origBack == null) {
                    origBack = iv;
                } else if (r.left < screenWidth * 0.45f && origAvatar == null) {
                    origAvatar = iv;
                } else if (r.left >= screenWidth * 0.50f) {
                    rightIcons.add(iv);
                }
            }
        }

        if (origBack != null && origBack.getDrawable() != null) {
            state.backIcon.setImageDrawable(origBack.getDrawable());
            final ImageView targetBack = origBack;
            state.backPill.setOnClickListener(v -> triggerClick(targetBack, activity));
        } else {
            state.backPill.setOnClickListener(v -> activity.onBackPressed());
        }

        if (origAvatar != null && origAvatar.getDrawable() != null) {
            if (state.avatarView.getDrawable() != origAvatar.getDrawable()) {
                state.avatarView.setImageDrawable(origAvatar.getDrawable());
            }
            final ImageView targetAvatar = origAvatar;
            state.centerWrap.setOnClickListener(v -> triggerClick(targetAvatar, null));
        }

        String title = "";
        for (TextView tv : texts) {
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0) {
                title = cs.toString().trim();
                break;
            }
        }
        if (!title.isEmpty()) {
            state.namePill.setText(title);
            state.namePill.setVisibility(View.VISIBLE);
        } else {
            state.namePill.setVisibility(View.GONE);
        }

        // Rightmost two icons are Audio Call and Video Call
        ImageView origAudio = null;
        ImageView origVideo = null;
        if (rightIcons.size() >= 2) {
            origAudio = rightIcons.get(rightIcons.size() - 2);
            origVideo = rightIcons.get(rightIcons.size() - 1);
        } else if (rightIcons.size() == 1) {
            origVideo = rightIcons.get(0);
        }

        if (origAudio != null && origAudio.getDrawable() != null) {
            state.audioBtn.setVisibility(View.VISIBLE);
            state.audioBtn.setImageDrawable(origAudio.getDrawable());
            final ImageView targetAudio = origAudio;
            state.audioBtn.setOnClickListener(v -> triggerClick(targetAudio, null));
        } else {
            state.audioBtn.setVisibility(View.GONE);
        }

        if (origVideo != null && origVideo.getDrawable() != null) {
            state.videoBtn.setVisibility(View.VISIBLE);
            state.videoBtn.setImageDrawable(origVideo.getDrawable());
            final ImageView targetVideo = origVideo;
            state.videoBtn.setOnClickListener(v -> triggerClick(targetVideo, null));
        } else {
            state.videoBtn.setVisibility(View.GONE);
        }

        state.rightPill.setVisibility(
                (state.audioBtn.getVisibility() == View.VISIBLE || state.videoBtn.getVisibility() == View.VISIBLE)
                        ? View.VISIBLE : View.GONE
        );
    }

    private static void triggerClick(View view, Activity fallbackActivity) {
        View current = view;
        for (int i = 0; i < 3 && current != null; i++) {
            if (current.isClickable() && current.performClick()) {
                return;
            }
            ViewParent p = current.getParent();
            current = p instanceof View ? (View) p : null;
        }
        if (fallbackActivity != null) {
            fallbackActivity.onBackPressed();
        }
    }

    private static void collectViews(View view, List<ImageView> icons, List<TextView> texts) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof ImageView) {
            icons.add((ImageView) view);
            return;
        }
        if (view instanceof TextView) {
            texts.add((TextView) view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectViews(g.getChildAt(i), icons, texts);
            }
        }
    }

    private static FrameLayout createPillContainer(Context context, float radiusPx, int bgColor, int borderColor) {
        FrameLayout frame = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radiusPx);
        bg.setColor(bgColor);
        bg.setStroke(dp(context, 1), borderColor);
        frame.setBackground(bg);
        frame.setElevation(dp(context, 5));
        return frame;
    }

    private static void hideOverlay(HeaderState state) {
        if (state.overlayRoot != null && state.overlayRoot.getVisibility() != View.GONE) {
            state.overlayRoot.setVisibility(View.GONE);
        }
    }

    private static int getStatusBarHeight(Context context) {
        int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? context.getResources().getDimensionPixelSize(resId) : dp(context, 28);
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static final class HeaderState {
        FrameLayout overlayRoot;
        FrameLayout backPill;
        ImageView backIcon;
        FrameLayout centerWrap;
        ImageView avatarView;
        TextView namePill;
        LinearLayout rightPill;
        ImageView audioBtn;
        ImageView videoBtn;
    }
}
