/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

/**
 * Replaces the DM composer sticker button (between gallery and plus) with a deleted-messages
 * icon that opens a PixelMusic-styled bottom sheet listing unsent messages for that chat.
 */
public final class ChatDeletedOverlay {
    private static final String[] STICKER_BUTTON_IDS = {
            "row_thread_composer_button_sticker",
            "row_thread_composer_sticker_button",
            "direct_sticker_button",
            "sticker_button"
    };
    private static final String HOOKED_TAG = "piko_deleted_msg_btn";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ChatDeletedOverlay() {
    }

    public static void attach(View anchor) {
        if (anchor == null || !Pref.saveDeletedMessages()) {
            return;
        }
        MAIN_HANDLER.postDelayed(() -> installComposerButton(anchor), 350L);
    }

    public static void refreshActive() {
        // Bottom sheet reads fresh data from PikoMessageDb when opened.
    }

    private static void installComposerButton(View anchor) {
        try {
            if (!anchor.isAttachedToWindow()) {
                return;
            }
            View root = anchor.getRootView();
            if (root == null) {
                return;
            }
            View stickerBtn = findStickerButton(root);
            if (stickerBtn == null) {
                return;
            }
            applyButtonOverride(stickerBtn);

            if (!Boolean.TRUE.equals(stickerBtn.getTag(0x7f0b9002))) {
                stickerBtn.setTag(0x7f0b9002, Boolean.TRUE);
                stickerBtn.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (!stickerBtn.isAttachedToWindow()) {
                            stickerBtn.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        }
                        if (!HOOKED_TAG.equals(stickerBtn.getContentDescription())) {
                            applyButtonOverride(stickerBtn);
                        }
                        return true;
                    }
                });
            }
        } catch (Throwable t) {
            Logger.printException(() -> "ChatDeletedOverlay.installComposerButton failed", t);
        }
    }

    private static void applyButtonOverride(View stickerBtn) {
        Context context = stickerBtn.getContext();
        int iconTint = UI.getThemedColour("igds_color_primary_icon");
        ImageView targetIcon = findFirstImageView(stickerBtn);
        if (targetIcon != null) {
            Drawable trash = new TrashIconDrawable(context, iconTint);
            targetIcon.setImageDrawable(trash);
            targetIcon.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN));
        }
        stickerBtn.setContentDescription(HOOKED_TAG);
        stickerBtn.setOnClickListener(v -> showDeletedMessagesSheet(v.getContext()));
    }

    private static View findStickerButton(View root) {
        for (String idName : STICKER_BUTTON_IDS) {
            int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
            if (id != 0) {
                View v = root.findViewById(id);
                if (v != null && v.isShown()) {
                    return v;
                }
            }
        }

        EditText composerInput = findComposerEditText(root);
        if (composerInput == null) {
            return null;
        }
        Rect inputRect = new Rect();
        if (!composerInput.getGlobalVisibleRect(inputRect)) {
            return null;
        }

        ViewGroup bar = null;
        View current = composerInput;
        for (int depth = 0; depth < 4 && current.getParent() instanceof ViewGroup; depth++) {
            ViewGroup parent = (ViewGroup) current.getParent();
            if (parent.getWidth() >= root.getWidth() * 0.7f && parent.getChildCount() >= 2) {
                bar = parent;
            }
            current = parent;
        }
        if (bar == null) {
            return null;
        }

        List<View> trailingButtons = new ArrayList<>();
        collectClickableIconViews(bar, inputRect.right - dp(root.getContext(), 8), trailingButtons);
        Collections.sort(trailingButtons, Comparator.comparingInt(v -> {
            Rect r = new Rect();
            v.getGlobalVisibleRect(r);
            return r.left;
        }));

        // Prefer explicit "sticker" description match if available.
        for (View btn : trailingButtons) {
            CharSequence desc = btn.getContentDescription();
            if (desc != null && desc.toString().toLowerCase().contains("sticker")) {
                return btn;
            }
        }
        // Otherwise [Mic, Gallery, Sticker, Plus] -> second from the right.
        if (trailingButtons.size() >= 3) {
            return trailingButtons.get(trailingButtons.size() - 2);
        }
        return null;
    }

    private static EditText findComposerEditText(View view) {
        if (view == null || !view.isShown()) {
            return null;
        }
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                EditText match = findComposerEditText(group.getChildAt(i));
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static void collectClickableIconViews(View view, int minLeft, List<View> out) {
        if (view == null || !view.isShown()) {
            return;
        }
        if (view.isClickable() && !(view instanceof EditText) && findFirstImageView(view) != null) {
            Rect r = new Rect();
            if (view.getGlobalVisibleRect(r) && r.left >= minLeft && r.width() > 0 && r.width() <= dp(view.getContext(), 64)) {
                out.add(view);
                return;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectClickableIconViews(group.getChildAt(i), minLeft, out);
            }
        }
    }

    private static ImageView findFirstImageView(View view) {
        if (view instanceof ImageView) {
            return (ImageView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView match = findFirstImageView(group.getChildAt(i));
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    public static void showDeletedMessagesSheet(Context context) {
        try {
            Activity activity = resolveActivity(context);
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            String threadId = SavedMessagesHook.getActiveThreadId();
            List<String[]> deleted = (threadId != null && !threadId.isEmpty())
                    ? PikoMessageDb.getInstance(activity).getDeletedMessagesForThread(threadId)
                    : new ArrayList<>();

            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            boolean dark = UI.isDarkMode();
            int sheetBg = InstagramPreferenceStyle.backgroundColor();
            int cardBg = InstagramPreferenceStyle.surfaceContainerColor();
            int primaryText = InstagramPreferenceStyle.primaryTextColor();
            int secondaryText = InstagramPreferenceStyle.secondaryTextColor();
            int badgeBg = dark ? 0xFF6E1B1B : 0xFFFFDAD9;
            int badgeFg = dark ? 0xFFFFB4AB : 0xFFB3261E;

            LinearLayout sheet = new LinearLayout(context);
            sheet.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable sheetDrawable = new GradientDrawable();
            sheetDrawable.setShape(GradientDrawable.RECTANGLE);
            float topRadius = dp(context, 28);
            sheetDrawable.setCornerRadii(new float[]{
                    topRadius, topRadius, topRadius, topRadius,
                    0f, 0f, 0f, 0f
            });
            sheetDrawable.setColor(sheetBg);
            sheet.setBackground(sheetDrawable);
            sheet.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 20));

            // Drag handle
            View handle = new View(context);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setShape(GradientDrawable.RECTANGLE);
            handleBg.setCornerRadius(dp(context, 2));
            handleBg.setColor((secondaryText & 0x00FFFFFF) | 0x66000000);
            handle.setBackground(handleBg);
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.bottomMargin = dp(context, 16);
            sheet.addView(handle, handleParams);

            // Header
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(context, 4), 0, dp(context, 4), dp(context, 14));

            FrameLayout iconBadge = new FrameLayout(context);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(badgeBg);
            iconBadge.setBackground(circle);
            ImageView headerIcon = new ImageView(context);
            headerIcon.setImageDrawable(new TrashIconDrawable(context, badgeFg));
            FrameLayout.LayoutParams hiParams = new FrameLayout.LayoutParams(dp(context, 20), dp(context, 20), Gravity.CENTER);
            iconBadge.addView(headerIcon, hiParams);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44));
            badgeParams.setMarginEnd(dp(context, 14));
            header.addView(iconBadge, badgeParams);

            LinearLayout headerText = new LinearLayout(context);
            headerText.setOrientation(LinearLayout.VERTICAL);
            TextView titleView = new TextView(context);
            titleView.setText("Deleted messages");
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setTextColor(primaryText);
            headerText.addView(titleView);

            TextView subView = new TextView(context);
            subView.setText(deleted.isEmpty()
                    ? "No unsent messages in this chat yet"
                    : deleted.size() + (deleted.size() == 1 ? " unsent message" : " unsent messages"));
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            subView.setTextColor(secondaryText);
            headerText.addView(subView);
            header.addView(headerText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            sheet.addView(header);

            ScrollView scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));

            if (deleted.isEmpty()) {
                TextView emptyView = new TextView(context);
                emptyView.setText("When someone unsends a message in this chat, it will appear here with the exact date and time it was deleted.");
                emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                emptyView.setTextColor(secondaryText);
                emptyView.setGravity(Gravity.CENTER);
                emptyView.setPadding(dp(context, 16), dp(context, 28), dp(context, 16), dp(context, 28));
                list.addView(emptyView);
            } else {
                DateFormat dateFmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
                int total = deleted.size();
                for (int i = 0; i < total; i++) {
                    String[] row = deleted.get(i);
                    String content = row[3];
                    String type = row[4];
                    long deletedAt = 0L;
                    try {
                        deletedAt = Long.parseLong(row.length > 7 ? row[7] : row[5]);
                    } catch (NumberFormatException ignored) {
                    }

                    boolean isFirst = (i == 0);
                    boolean isLast = (i == total - 1);

                    LinearLayout card = new LinearLayout(context);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
                    InstagramPreferenceStyle.applyCardBackground(card, isFirst, isLast, true);

                    TextView msgView = new TextView(context);
                    msgView.setText(formatDisplayText(content, type));
                    msgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    msgView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    msgView.setTextColor(primaryText);
                    msgView.setMaxLines(6);
                    msgView.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(msgView);

                    TextView timeView = new TextView(context);
                    String when = deletedAt > 0L ? dateFmt.format(new Date(deletedAt)) : "Unknown time";
                    timeView.setText("Deleted \u2022 " + when);
                    timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                    timeView.setTextColor(badgeFg);
                    timeView.setPadding(0, dp(context, 4), 0, 0);
                    card.addView(timeView);

                    if (content != null && content.startsWith("http")) {
                        final String url = content;
                        card.setOnClickListener(v -> {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            } catch (Throwable ignored) {
                            }
                        });
                    }

                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    if (!isFirst) {
                        cardParams.topMargin = dp(context, 2);
                    }
                    list.addView(card, cardParams);
                }
            }

            int maxListHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.55f);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            sheet.addView(scroll, scrollParams);
            scroll.post(() -> {
                if (scroll.getHeight() > maxListHeight) {
                    ViewGroup.LayoutParams sp = scroll.getLayoutParams();
                    sp.height = maxListHeight;
                    scroll.setLayoutParams(sp);
                }
            });

            dialog.setContentView(sheet);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setDimAmount(0.45f);
                window.setWindowAnimations(android.R.style.Animation_InputMethod);
            }
            dialog.show();
        } catch (Throwable t) {
            Logger.printException(() -> "ChatDeletedOverlay.showDeletedMessagesSheet failed", t);
        }
    }

    private static String formatDisplayText(String content, String type) {
        if (content != null && !content.trim().isEmpty() && !content.startsWith("http")) {
            return content;
        }
        if (type == null || type.isEmpty()) {
            return "[Deleted message]";
        }
        switch (type) {
            case "media":
            case "image":
                return "[Photo]";
            case "raven_media":
            case "visual_media":
                return "[View-once media]";
            case "video":
                return "[Video]";
            case "voice_media":
            case "audio":
                return "[Voice message]";
            case "animated_media":
                return "[GIF]";
            case "reel_share":
                return "[Reel]";
            case "story_share":
                return "[Story]";
            case "media_share":
                return "[Post]";
            default:
                return content != null && !content.isEmpty() ? content : "[" + type + "]";
        }
    }

    private static Activity resolveActivity(Context context) {
        Context c = context;
        for (int i = 0; i < 8 && c instanceof ContextWrapper; i++) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return Utils.getActivity();
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static final class TrashIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int intrinsicSize;

        TrashIconDrawable(Context context, int color) {
            this.intrinsicSize = dp(context, 24);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 1.9f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width();
            float h = b.height();
            float scale = Math.min(w, h) / 24f;
            canvas.save();
            canvas.translate(b.left + (w - 24f * scale) / 2f, b.top + (h - 24f * scale) / 2f);
            canvas.scale(scale, scale);

            // Top bar + handle
            canvas.drawLine(4f, 6.5f, 20f, 6.5f, paint);
            Path handle = new Path();
            handle.moveTo(9f, 6.5f);
            handle.lineTo(9.5f, 4f);
            handle.lineTo(14.5f, 4f);
            handle.lineTo(15f, 6.5f);
            canvas.drawPath(handle, paint);

            // Bin body
            Path body = new Path();
            body.moveTo(6.5f, 6.5f);
            body.lineTo(7.5f, 19.5f);
            body.quadTo(7.6f, 20.5f, 8.8f, 20.5f);
            body.lineTo(15.2f, 20.5f);
            body.quadTo(16.4f, 20.5f, 16.5f, 19.5f);
            body.lineTo(17.5f, 6.5f);
            canvas.drawPath(body, paint);

            // Inner vertical lines
            canvas.drawLine(10.2f, 10.5f, 10.5f, 16.5f, paint);
            canvas.drawLine(13.8f, 10.5f, 13.5f, 16.5f, paint);
            canvas.restore();
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
