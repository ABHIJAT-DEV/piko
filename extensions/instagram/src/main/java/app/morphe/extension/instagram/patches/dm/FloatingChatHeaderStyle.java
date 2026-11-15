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
import android.widget.RelativeLayout;
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

            // Layout passes only. The previous version also ran update() from an
            // OnPreDrawListener — i.e. on every single frame — and update() walks
            // view trees, sorts views by global rects, and mutates layout params.
            // That is what made chat scrolling stutter: the work ran per frame,
            // not per navigation.
            ViewTreeObserver vto = decor.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(() -> update(activity, decor, state));
        } catch (Throwable t) {
            Logger.printException(() -> "FloatingChatHeaderStyle attach failed", t);
        }
    }

    private static void update(Activity activity, View decor, HeaderState state) {
        if (!Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.FLOATING_CHAT_HEADER))) {
            if (state.surgeryHeader != null && !state.surgeryHeader.isShown()) {
                restoreThreadLayout(state);
            }
            hideOverlay(state);
            return;
        }
        View composer = findDmComposer(decor);
        if (composer == null) {
            // Not in a thread. If a previous thread's layout surgery is still
            // applied and that thread is no longer on screen, undo it now —
            // off-screen, where the relayout is invisible. (While the thread
            // is still shown — e.g. mid-transition behind a profile — its
            // layout is left exactly as the animation expects it.)
            if (state.surgeryHeader != null && !state.surgeryHeader.isShown()) {
                restoreThreadLayout(state);
            }
            hideOverlay(state);
            return;
        }

        // Scope the header search to the thread fragment's own subtree. The old
        // code searched the whole decor and accepted the generic
        // "action_bar_container" id, so when another screen (e.g. a profile)
        // was pushed on top of the chat in the same activity, it could grab
        // THAT screen's header, blank its children, shift its content with
        // negative margins, and draw the chat overlay over it — the glitchy,
        // stuck-loading profile. A header outside the composer's fragment
        // subtree is never the thread header.
        View searchRoot = findFragmentRoot(composer, decor);
        ViewGroup origHeader = findThreadHeader(activity, searchRoot);
        if (origHeader == null || !origHeader.isShown()) {
            if (state.surgeryHeader != null && !state.surgeryHeader.isShown()) {
                restoreThreadLayout(state);
            }
            hideOverlay(state);
            return;
        }
        // Identity check: the header must belong to the same fragment as the
        // composer. If the search ever surfaces another screen's header,
        // refuse to touch it.
        if (findFragmentRoot(origHeader, decor) != searchRoot) {
            hideOverlay(state);
            return;
        }

        makeOriginalHeaderTransparentAndOverlapList(activity, origHeader, state, searchRoot);
        ensureOverlayBuilt(activity, decor, state);
        syncOverlayContent(activity, origHeader, state);
    }

    private static View findDmComposer(View decor) {
        int composerId = ResourceUtils.getIdentifier(ResourceType.ID, "row_thread_composer_textarea");
        if (composerId != 0) {
            View v = decor.findViewById(composerId);
            if (v != null && v.isShown()) {
                return v;
            }
        }
        return findVisibleEditTextNearBottom(decor, decor.getHeight(), 0);
    }

    /**
     * The fragment root for a view: the highest ancestor that still belongs to
     * the view's own fragment — i.e. the child of the fragment container.
     * The container is the view whose parent is android.R.id.content; the
     * fragment root is the container's child on this view's chain. Stopping
     * one level higher (at the container) was the v1 mistake: the container
     * is shared by every fragment in the activity, so searches and surgery
     * scoped to it can still reach a sibling screen (the profile).
     */
    private static View findFragmentRoot(View view, View decor) {
        View current = view;
        ViewParent parent = current.getParent();
        while (parent instanceof ViewGroup && parent != decor) {
            ViewGroup parentGroup = (ViewGroup) parent;
            if (parentGroup.getId() == android.R.id.content) {
                // Parent is the content view itself: current is the container
                // (shallow hosting) — one more step down is not knowable, so
                // keep current as the root.
                break;
            }
            ViewParent grandparent = parentGroup.getParent();
            if (grandparent instanceof ViewGroup
                    && ((ViewGroup) grandparent).getId() == android.R.id.content) {
                // parentGroup is the fragment container; current (its child on
                // this chain) is the fragment's root view.
                break;
            }
            current = parentGroup;
            parent = current.getParent();
        }
        return current;
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

    private static ViewGroup findThreadHeader(Activity activity, View searchRoot) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int maxTopY = dp(activity, 64);
        int minH = dp(activity, 44);
        int maxH = dp(activity, 128);
        Rect rect = new Rect();

        for (String idName : THREAD_HEADER_IDS) {
            int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
            if (id != 0) {
                View v = searchRoot.findViewById(id);
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
            HeaderState state,
            View fragmentRoot
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

        // Re-assert the un-clipped state while in the thread. Clipping is
        // switched back on at tap-time (see restoreClipping), so on return
        // passes it has to be disabled again — a pure drawing-flag change,
        // no layout involved.
        if (!state.clippingDisabled && !state.surgeryUnclipped.isEmpty()) {
            for (ViewGroup group : state.surgeryUnclipped) {
                group.setClipChildren(false);
                group.setClipToPadding(false);
            }
            state.clippingDisabled = true;
        }

        // The parent/sibling surgery below rewrites layout params (negative top
        // margins so the list slides under the floating header). Doing that on
        // every update() forces fresh layout passes and fights the scroll.
        // The hierarchy is built once per thread screen, so run it once per
        // header instance; the cheap transparency/alpha re-asserts above still
        // run every pass.
        if (state.surgeryHeader == origHeader) {
            return;
        }
        state.surgeryHeader = origHeader;

        int maxDiv = dp(activity, 3);
        int maxHeaderH = dp(activity, 140);
        View branch = origHeader;
        ViewParent parent = origHeader.getParent();

        // Climb only INSIDE the thread fragment. The v1 code climbed up to 4
        // parents unconditionally, disabling clipping on containers shared
        // with other screens — with clipping off and the list pulled up by a
        // negative margin, the chat's top messages painted outside the
        // fragment and showed through/behind the next screen (the profile)
        // while it opened. Every mutation is recorded so it can be reverted
        // the moment the user leaves the chat (see restoreThreadLayout).
        for (int depth = 0; depth < 4 && parent instanceof ViewGroup && branch != fragmentRoot; depth++) {
            ViewGroup parentGroup = (ViewGroup) parent;
            if (parentGroup.getClipChildren()) {
                parentGroup.setClipChildren(false);
                state.surgeryUnclipped.add(parentGroup);
            }
            if (parentGroup.getClipToPadding()) {
                parentGroup.setClipToPadding(false);
                if (!state.surgeryUnclipped.contains(parentGroup)) {
                    state.surgeryUnclipped.add(parentGroup);
                }
            }

            if (branch != origHeader && branch.getHeight() > 0 && branch.getHeight() <= maxHeaderH) {
                Drawable bbg = branch.getBackground();
                if (bbg != null && (!(bbg instanceof ColorDrawable) || ((ColorDrawable) bbg).getColor() != Color.TRANSPARENT)) {
                    branch.setBackgroundColor(Color.TRANSPARENT);
                }
            }

            for (int i = 0; i < parentGroup.getChildCount(); i++) {
                View sibling = parentGroup.getChildAt(i);
                if (sibling == null || sibling == branch || sibling.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (sibling.getHeight() > 0 && sibling.getHeight() <= maxDiv
                        && sibling.getTop() >= branch.getBottom() - maxDiv) {
                    sibling.setVisibility(View.GONE);
                } else if (sibling.getHeight() > origHeader.getHeight() * 2) {
                    disableClippingTree(sibling, state);
                    int branchH = branch.getHeight() > 0 ? branch.getHeight() : origHeader.getHeight();
                    ViewGroup.LayoutParams slp = sibling.getLayoutParams();
                    if (slp instanceof RelativeLayout.LayoutParams) {
                        RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) slp;
                        if (rlp.getRule(RelativeLayout.BELOW) != 0) {
                            recordListSibling(state, sibling, rlp.topMargin, rlp.getRule(RelativeLayout.BELOW));
                            rlp.removeRule(RelativeLayout.BELOW);
                            rlp.topMargin = 0;
                            sibling.setLayoutParams(rlp);
                        }
                    } else if (parentGroup instanceof LinearLayout
                            && ((LinearLayout) parentGroup).getOrientation() == LinearLayout.VERTICAL
                            && slp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) slp;
                        if (mlp.topMargin != -branchH) {
                            recordListSibling(state, sibling, mlp.topMargin, 0);
                            mlp.topMargin = -branchH;
                            sibling.setLayoutParams(mlp);
                        }
                    } else if (slp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) slp;
                        if (sibling.getTop() > dp(activity, 8) && mlp.topMargin >= 0) {
                            recordListSibling(state, sibling, mlp.topMargin, 0);
                            mlp.topMargin = -branchH;
                            sibling.setLayoutParams(mlp);
                        }
                    }
                }
            }

            branch = parentGroup;
            parent = parentGroup.getParent();
        }
        state.clippingDisabled = true;
    }

    private static void recordListSibling(HeaderState state, View sibling, int topMargin, int belowRule) {
        if (state.surgeryListSibling == null) {
            state.surgeryListSibling = sibling;
            state.savedTopMargin = topMargin;
            state.savedBelowRule = belowRule;
        }
    }

    /**
     * Undo the layout surgery: put the message list back at its stock position
     * and re-enable clipping everywhere it was disabled. Called the moment the
     * user leaves the chat (avatar tap to profile, or back), BEFORE the click
     * is forwarded — so during the transition to the next screen the chat
     * behind is in its stock layout: no pulled-up messages near the top and
     * nothing painting outside the fragment. When the user returns to the
     * chat, update() re-applies the surgery (surgeryHeader is cleared here).
     */
    private static void restoreThreadLayout(HeaderState state) {
        try {
            View sibling = state.surgeryListSibling;
            if (sibling != null) {
                ViewGroup.LayoutParams slp = sibling.getLayoutParams();
                if (slp instanceof RelativeLayout.LayoutParams) {
                    RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) slp;
                    if (state.savedBelowRule != 0) {
                        rlp.addRule(RelativeLayout.BELOW, state.savedBelowRule);
                    }
                    rlp.topMargin = state.savedTopMargin;
                    sibling.setLayoutParams(rlp);
                } else if (slp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) slp;
                    mlp.topMargin = state.savedTopMargin;
                    sibling.setLayoutParams(mlp);
                }
                enableClippingTree(sibling);
                state.surgeryListSibling = null;
                state.savedBelowRule = 0;
            }
            for (ViewGroup group : state.surgeryUnclipped) {
                group.setClipChildren(true);
                group.setClipToPadding(true);
            }
            state.surgeryUnclipped.clear();
            state.surgeryHeader = null;
            state.clippingDisabled = false;
        } catch (Throwable ignored) {
        }
    }

    private static void enableClippingTree(View view) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        group.setClipChildren(true);
        group.setClipToPadding(true);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup && (child.getClass().getName().contains("RecyclerView")
                    || child.getClass().getName().contains("SwipeRefresh"))) {
                ViewGroup cg = (ViewGroup) child;
                cg.setClipChildren(true);
                cg.setClipToPadding(true);
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
        int cTop = (bgBase & 0x00FFFFFF) | 0xF5000000;
        int cMid1 = (bgBase & 0x00FFFFFF) | 0xCC000000;
        int cMid2 = (bgBase & 0x00FFFFFF) | 0x66000000;
        int cEnd = (bgBase & 0x00FFFFFF);
        GradientDrawable fadeDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{cTop, cMid1, cMid2, cEnd}
        );
        fadeBackdrop.setBackground(fadeDrawable);
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                fadeBackdrop.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {
            }
        }
        root.addView(fadeBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 152),
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
        // Default back behaviour; syncOverlayContent overrides this with the
        // original header's back button when one is found.
        backPill.setOnClickListener(v -> {
            hideOverlay(state);
            restoreClipping(state);
            activity.onBackPressed();
        });

        // Center big floating avatar + overlapping name pill
        FrameLayout centerWrap = new FrameLayout(activity);
        centerWrap.setClipChildren(false);
        centerWrap.setClipToPadding(false);

        int avatarSize = dp(activity, 80);
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
        nameLp.topMargin = avatarSize - dp(activity, 12);
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

        // Measure each view's global position ONCE. The old code called
        // getGlobalVisibleRect() inside the sort comparators (a parent-chain
        // walk per comparison) and again in the selection loop.
        Rect r = new Rect();
        Map<ImageView, Integer> iconLeft = new java.util.IdentityHashMap<>();
        for (ImageView iv : icons) {
            if (iv.getGlobalVisibleRect(r)) {
                iconLeft.put(iv, r.left);
            }
        }
        icons.removeIf(iv -> !iconLeft.containsKey(iv));
        Collections.sort(icons, Comparator.comparingInt(iconLeft::get));

        Map<TextView, Integer> textTop = new java.util.IdentityHashMap<>();
        for (TextView tv : texts) {
            if (tv.getGlobalVisibleRect(r)) {
                textTop.put(tv, r.top);
            }
        }
        Collections.sort(texts, Comparator.comparingInt(tv -> textTop.getOrDefault(tv, Integer.MAX_VALUE)));

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        ImageView origBack = null;
        ImageView origAvatar = null;
        List<ImageView> rightIcons = new ArrayList<>();

        for (ImageView iv : icons) {
            int left = iconLeft.get(iv);
            if (left < screenWidth * 0.16f && origBack == null) {
                origBack = iv;
            } else if (left < screenWidth * 0.45f && origAvatar == null) {
                origAvatar = iv;
            } else if (left >= screenWidth * 0.50f) {
                rightIcons.add(iv);
            }
        }

        // Everything below is idempotent: drawables, texts and click listeners
        // are only touched when the source view actually changed, so repeated
        // layout passes (keyboard, scroll-driven relayouts) cost almost nothing
        // and never churn listeners mid-gesture.
        if (origBack != null && origBack.getDrawable() != null) {
            if (state.lastBack != origBack) {
                state.lastBack = origBack;
                state.backIcon.setImageDrawable(origBack.getDrawable());
                final ImageView targetBack = origBack;
                state.backPill.setOnClickListener(v -> {
                    hideOverlay(state);
                    restoreClipping(state);
                    triggerClick(targetBack, activity);
                });
            }
        } else if (state.lastBack != null) {
            state.lastBack = null;
            state.backPill.setOnClickListener(v -> {
                hideOverlay(state);
                restoreClipping(state);
                activity.onBackPressed();
            });
        }

        if (origAvatar != null && origAvatar.getDrawable() != null) {
            if (state.avatarView.getDrawable() != origAvatar.getDrawable()) {
                state.avatarView.setImageDrawable(origAvatar.getDrawable());
            }
            if (state.lastAvatar != origAvatar) {
                state.lastAvatar = origAvatar;
                final ImageView targetAvatar = origAvatar;
                state.centerWrap.setOnClickListener(v -> {
                    // Hide our overlay and put the chat layout back to stock
                    // before the profile opens, so nothing of the chat is
                    // pulled up or painting outside its area behind it.
                    hideOverlay(state);
                    restoreClipping(state);
                    triggerClick(targetAvatar, null);
                });
            }
        }

        String title = "";
        for (TextView tv : texts) {
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0) {
                title = cs.toString().trim();
                break;
            }
        }
        if (!title.equals(state.lastTitle)) {
            state.lastTitle = title;
            if (!title.isEmpty()) {
                state.namePill.setText(title);
                state.namePill.setVisibility(View.VISIBLE);
            } else {
                state.namePill.setVisibility(View.GONE);
            }
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
            if (state.lastAudio != origAudio) {
                state.lastAudio = origAudio;
                state.audioBtn.setImageDrawable(origAudio.getDrawable());
                final ImageView targetAudio = origAudio;
                state.audioBtn.setOnClickListener(v -> triggerClick(targetAudio, null));
            }
        } else {
            state.lastAudio = null;
            state.audioBtn.setVisibility(View.GONE);
        }

        if (origVideo != null && origVideo.getDrawable() != null) {
            state.videoBtn.setVisibility(View.VISIBLE);
            if (state.lastVideo != origVideo) {
                state.lastVideo = origVideo;
                state.videoBtn.setImageDrawable(origVideo.getDrawable());
                final ImageView targetVideo = origVideo;
                state.videoBtn.setOnClickListener(v -> triggerClick(targetVideo, null));
            }
        } else {
            state.lastVideo = null;
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

    private static void disableClippingTree(View view, HeaderState state) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        if (group.getClipChildren()) {
            group.setClipChildren(false);
        }
        if (group.getClipToPadding()) {
            group.setClipToPadding(false);
        }
        if (!state.surgeryUnclipped.contains(group)) {
            state.surgeryUnclipped.add(group);
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup && (child.getClass().getName().contains("RecyclerView")
                    || child.getClass().getName().contains("SwipeRefresh"))) {
                ViewGroup cg = (ViewGroup) child;
                if (cg.getClipChildren()) {
                    cg.setClipChildren(false);
                }
                if (cg.getClipToPadding()) {
                    cg.setClipToPadding(false);
                }
                if (!state.surgeryUnclipped.contains(cg)) {
                    state.surgeryUnclipped.add(cg);
                }
            }
        }
    }

    /**
     * Tap-time handoff: re-enable clipping ONLY. Instagram slides the thread
     * out with a property animation (the whole app has zero
     * FragmentTransaction.setCustomAnimations calls — the slide is IG's own
     * view-animation machinery, which never relayouts). Restoring layout
     * params at tap time (the v2 approach) forced a relayout of the exiting
     * hierarchy in the same frame the slide started — that was the remaining
     * jump/glitch. Clip flags are pure drawing state: flipping them costs no
     * layout, and with clipping back on, the sliding messages stay inside
     * the thread's bounds instead of painting over the incoming profile.
     * The margins themselves are restored later, off-screen (see update()).
     */
    private static void restoreClipping(HeaderState state) {
        if (!state.clippingDisabled) {
            return;
        }
        for (ViewGroup group : state.surgeryUnclipped) {
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }
        state.clippingDisabled = false;
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
        // Idempotency bookkeeping: which header instance already had the
        // layout surgery, and which original views the overlay currently
        // mirrors (so repeated update() passes don't redo the work).
        View surgeryHeader;
        View surgeryListSibling;
        int savedTopMargin;
        int savedBelowRule;
        final java.util.List<ViewGroup> surgeryUnclipped = new ArrayList<>();
        boolean clippingDisabled;
        ImageView lastBack;
        ImageView lastAvatar;
        ImageView lastAudio;
        ImageView lastVideo;
        String lastTitle;
    }
}
