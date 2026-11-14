/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Displays captured deleted messages inside the specific DM chat thread as incoming-style
 * bubbles with a deleted indicator icon and timestamp.
 */
public final class ChatDeletedOverlay {
    private static final String OVERLAY_TAG = "piko_chat_deleted_overlay";
    private static final int MAX_VISIBLE_BUBBLES = 3;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static WeakReference<View> lastAnchor = new WeakReference<>(null);
    private static boolean dismissedForSession;

    private ChatDeletedOverlay() {
    }

    public static void attach(View anchor) {
        if (anchor == null || !Pref.saveDeletedMessages()) {
            return;
        }
        dismissedForSession = false;
        lastAnchor = new WeakReference<>(anchor);
        MAIN_HANDLER.postDelayed(() -> render(anchor), 350L);
    }

    public static void refreshActive() {
        View anchor = lastAnchor.get();
        if (anchor == null || !anchor.isAttachedToWindow() || !Pref.saveDeletedMessages()) {
            return;
        }
        dismissedForSession = false;
        MAIN_HANDLER.post(() -> render(anchor));
    }

    private static void render(View anchor) {
        try {
            if (anchor == null || !anchor.isAttachedToWindow() || dismissedForSession) {
                return;
            }
            View root = anchor.getRootView();
            if (root == null) {
                return;
            }
            View messageList = root.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "message_list"));
            if (messageList == null || !messageList.isAttachedToWindow()) {
                return;
            }

            String threadId = SavedMessagesHook.getActiveThreadId();
            if (threadId == null || threadId.isEmpty()) {
                return;
            }

            Context context = anchor.getContext();
            List<String[]> deleted = PikoMessageDb.getInstance(context).getDeletedMessagesForThread(threadId);
            ViewParent parent = messageList.getParent();
            if (!(parent instanceof ViewGroup)) {
                return;
            }
            ViewGroup container = (ViewGroup) parent;
            View existing = container.findViewWithTag(OVERLAY_TAG);
            if (deleted.isEmpty()) {
                if (existing != null) {
                    container.removeView(existing);
                }
                return;
            }

            if (existing != null) {
                container.removeView(existing);
            }

            LinearLayout wrapper = new LinearLayout(context);
            wrapper.setTag(OVERLAY_TAG);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.START);
            wrapper.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8));

            boolean dark = UI.isDarkMode();
            int bubbleBg = dark ? 0xFF262626 : 0xFFEFEFEF;
            int textColor = dark ? Color.WHITE : 0xFF111111;
            int metaColor = dark ? 0xFFB0B0B0 : 0xFF666666;

            int count = Math.min(deleted.size(), MAX_VISIBLE_BUBBLES);
            // Show oldest of the recent batch on top, newest closest to composer.
            for (int i = count - 1; i >= 0; i--) {
                String[] row = deleted.get(i);
                String content = row[3];
                String type = row[4];
                long ts = 0L;
                try {
                    ts = Long.parseLong(row[5]);
                } catch (NumberFormatException ignored) {
                }

                String displayText = formatDisplayText(content, type);
                String timeText = ts > 0L
                        ? DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(ts))
                        : "";

                LinearLayout bubble = new LinearLayout(context);
                bubble.setOrientation(LinearLayout.HORIZONTAL);
                bubble.setGravity(Gravity.CENTER_VERTICAL);
                bubble.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dp(context, 18));
                bg.setColor(bubbleBg);
                bubble.setBackground(bg);

                TextView msgView = new TextView(context);
                msgView.setText(displayText);
                msgView.setTextColor(textColor);
                msgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
                msgView.setMaxLines(3);
                msgView.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                msgParams.setMarginEnd(dp(context, 8));
                bubble.addView(msgView, msgParams);

                TextView badgeView = new TextView(context);
                badgeView.setText("\uD83D\uDEAB " + timeText);
                badgeView.setTextColor(0xFFE53935);
                badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                badgeView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                bubble.addView(badgeView);

                if (content != null && content.startsWith("http")) {
                    final String mediaUrl = content;
                    bubble.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } catch (Throwable ignored) {
                        }
                    });
                } else {
                    bubble.setOnClickListener(v -> SavedMessagesHook.openDeletedMessages(context));
                }

                LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                bubbleParams.topMargin = dp(context, 4);
                wrapper.addView(bubble, bubbleParams);
            }

            if (deleted.size() > MAX_VISIBLE_BUBBLES) {
                TextView moreView = new TextView(context);
                moreView.setText("+" + (deleted.size() - MAX_VISIBLE_BUBBLES) + " more deleted");
                moreView.setTextColor(metaColor);
                moreView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                moreView.setPadding(dp(context, 6), dp(context, 4), 0, 0);
                moreView.setOnClickListener(v -> SavedMessagesHook.openDeletedMessages(context));
                wrapper.addView(moreView);
            }

            wrapper.setOnLongClickListener(v -> {
                dismissedForSession = true;
                container.removeView(wrapper);
                return true;
            });

            if (container instanceof FrameLayout) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.START
                );
                container.addView(wrapper, lp);
            } else if (container instanceof RelativeLayout) {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                lp.addRule(RelativeLayout.ALIGN_PARENT_START);
                container.addView(wrapper, lp);
            } else {
                container.addView(wrapper);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "ChatDeletedOverlay.render failed", t);
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

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
