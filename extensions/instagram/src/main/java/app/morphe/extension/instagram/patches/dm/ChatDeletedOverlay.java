/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
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
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.MediaUrls;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

/**
 * Replaces the DM composer sticker button (between gallery and plus) with a deleted-messages
 * icon that opens a PixelMusic-styled bottom sheet listing unsent messages for that chat,
 * with inline photo/video previews and long-press selection/deletion.
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
    private static final ExecutorService MEDIA_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> THUMB_CACHE = new LruCache<>(24);

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

        for (View btn : trailingButtons) {
            CharSequence desc = btn.getContentDescription();
            if (desc != null && desc.toString().toLowerCase().contains("sticker")) {
                return btn;
            }
        }
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
            final PikoMessageDb db = PikoMessageDb.getInstance(activity);
            final SheetState state = new SheetState();
            loadMessages(activity, db, state);

            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            boolean dark = UI.isDarkMode();
            int sheetBg = InstagramPreferenceStyle.backgroundColor();
            int primaryText = InstagramPreferenceStyle.primaryTextColor();
            int secondaryText = InstagramPreferenceStyle.secondaryTextColor();
            int badgeBg = dark ? 0xFF6E1B1B : 0xFFFFDAD9;
            int badgeFg = dark ? 0xFFFFB4AB : 0xFFB3261E;
            int pillBg = InstagramPreferenceStyle.surfaceContainerHighColor();
            int selectedCardBg = dark ? 0xFF2B2224 : 0xFFFFEDEC;

            LinearLayout sheet = new LinearLayout(activity);
            sheet.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable sheetDrawable = new GradientDrawable();
            sheetDrawable.setShape(GradientDrawable.RECTANGLE);
            float topRadius = dp(activity, 28);
            sheetDrawable.setCornerRadii(new float[]{
                    topRadius, topRadius, topRadius, topRadius,
                    0f, 0f, 0f, 0f
            });
            sheetDrawable.setColor(sheetBg);
            sheet.setBackground(sheetDrawable);
            sheet.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 20));

            // Drag handle
            View handle = new View(activity);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setShape(GradientDrawable.RECTANGLE);
            handleBg.setCornerRadius(dp(activity, 2));
            handleBg.setColor((secondaryText & 0x00FFFFFF) | 0x66000000);
            handle.setBackground(handleBg);
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.bottomMargin = dp(activity, 16);
            sheet.addView(handle, handleParams);

            // Header
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 14));

            FrameLayout iconBadge = new FrameLayout(activity);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(badgeBg);
            iconBadge.setBackground(circle);
            ImageView headerIcon = new ImageView(activity);
            headerIcon.setImageDrawable(new TrashIconDrawable(activity, badgeFg));
            FrameLayout.LayoutParams hiParams = new FrameLayout.LayoutParams(dp(activity, 20), dp(activity, 20), Gravity.CENTER);
            iconBadge.addView(headerIcon, hiParams);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
            badgeParams.setMarginEnd(dp(activity, 14));
            header.addView(iconBadge, badgeParams);

            LinearLayout headerText = new LinearLayout(activity);
            headerText.setOrientation(LinearLayout.VERTICAL);
            TextView titleView = new TextView(activity);
            titleView.setText("Deleted messages");
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setTextColor(primaryText);
            headerText.addView(titleView);

            final TextView subView = new TextView(activity);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            subView.setTextColor(secondaryText);
            headerText.addView(subView);
            header.addView(headerText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final LinearLayout actionsRow = new LinearLayout(activity);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(actionsRow);

            final TextView selectAllBtn = createActionPill(activity, "All", pillBg, primaryText);
            LinearLayout.LayoutParams saParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            saParams.setMarginEnd(dp(activity, 6));
            actionsRow.addView(selectAllBtn, saParams);

            final TextView actionBtn = createActionPill(activity, "Clear", pillBg, badgeFg);
            actionsRow.addView(actionBtn);

            sheet.addView(header);

            ScrollView scroll = new ScrollView(activity);
            scroll.setVerticalScrollBarEnabled(false);
            final LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));

            final Runnable[] renderHolder = new Runnable[1];
            renderHolder[0] = () -> {
                if (state.messages.isEmpty()) {
                    subView.setText("No unsent messages in this chat yet");
                    actionsRow.setVisibility(View.GONE);
                } else if (state.selectionMode) {
                    subView.setText(state.selectedIds.size() + " selected");
                    actionsRow.setVisibility(View.VISIBLE);
                    selectAllBtn.setVisibility(View.VISIBLE);
                    selectAllBtn.setText(state.selectedIds.size() == state.messages.size() ? "None" : "All");
                    actionBtn.setText("Delete (" + state.selectedIds.size() + ")");
                    stylePill(actionBtn, badgeBg, badgeFg);
                } else {
                    if (state.showingAllFallback) {
                        subView.setText("Showing all " + state.messages.size() + " saved unsent messages");
                    } else {
                        subView.setText(state.messages.size() + (state.messages.size() == 1 ? " unsent message" : " unsent messages"));
                    }
                    actionsRow.setVisibility(View.VISIBLE);
                    selectAllBtn.setVisibility(View.GONE);
                    actionBtn.setText("Clear");
                    stylePill(actionBtn, pillBg, badgeFg);
                }

                list.removeAllViews();
                if (state.messages.isEmpty()) {
                    TextView emptyView = new TextView(activity);
                    emptyView.setText("When someone unsends a message in this chat, it will appear here with the exact date and time it was deleted.");
                    emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    emptyView.setTextColor(secondaryText);
                    emptyView.setGravity(Gravity.CENTER);
                    emptyView.setPadding(dp(activity, 16), dp(activity, 28), dp(activity, 16), dp(activity, 28));
                    list.addView(emptyView);
                    return;
                }

                DateFormat dateFmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
                int total = state.messages.size();
                for (int i = 0; i < total; i++) {
                    final String[] row = state.messages.get(i);
                    final String msgId = row[0];
                    final String sender = row[2];
                    final String content = row[3];
                    final String type = row[4];
                    long deletedAt = 0L;
                    try {
                        deletedAt = Long.parseLong(row.length > 7 ? row[7] : row[5]);
                    } catch (NumberFormatException ignored) {
                    }

                    boolean isFirst = (i == 0);
                    boolean isLast = (i == total - 1);
                    boolean isSelected = state.selectionMode && state.selectedIds.contains(msgId);

                    LinearLayout card = new LinearLayout(activity);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
                    InstagramPreferenceStyle.applyCardBackground(card, isFirst, isLast, true);
                    if (isSelected) {
                        applySelectedCardOverlay(activity, card, selectedCardBg, badgeFg, isFirst, isLast);
                    }

                    if (state.showingAllFallback && sender != null && !sender.isEmpty()) {
                        TextView senderView = new TextView(activity);
                        senderView.setText("@" + sender);
                        senderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                        senderView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                        senderView.setTextColor(secondaryText);
                        senderView.setPadding(0, 0, 0, dp(activity, 4));
                        card.addView(senderView);
                    }

                    boolean hasMediaUrl = isRenderableMediaUrl(content);
                    if (hasMediaUrl) {
                        boolean isVideo = isVideoMedia(content, type);
                        View mediaPreview = buildMediaPreview(activity, content, isVideo, dark, () -> {
                            if (state.selectionMode) {
                                toggleSelection(state, msgId, renderHolder[0]);
                            } else if (isVideo) {
                                openVideoPreview(activity, content);
                            } else {
                                openImagePreview(activity, content);
                            }
                        }, v -> {
                            startSelection(state, msgId, renderHolder[0]);
                            return true;
                        });
                        LinearLayout.LayoutParams mpParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(activity, 180)
                        );
                        mpParams.bottomMargin = dp(activity, 8);
                        card.addView(mediaPreview, mpParams);
                    } else {
                        TextView msgView = new TextView(activity);
                        msgView.setText(formatDisplayText(content, type));
                        msgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        msgView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                        msgView.setTextColor(primaryText);
                        msgView.setMaxLines(6);
                        msgView.setEllipsize(TextUtils.TruncateAt.END);
                        card.addView(msgView);
                    }

                    LinearLayout footerRow = new LinearLayout(activity);
                    footerRow.setOrientation(LinearLayout.HORIZONTAL);
                    footerRow.setGravity(Gravity.CENTER_VERTICAL);
                    footerRow.setPadding(0, dp(activity, 4), 0, 0);

                    TextView timeView = new TextView(activity);
                    String when = deletedAt > 0L ? dateFmt.format(new Date(deletedAt)) : "Unknown time";
                    String labelPrefix = hasMediaUrl ? formatDisplayText(content, type) + " \u2022 " : "";
                    timeView.setText(labelPrefix + "Deleted \u2022 " + when);
                    timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                    timeView.setTextColor(badgeFg);
                    footerRow.addView(timeView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    if (isSelected) {
                        TextView checkBadge = new TextView(activity);
                        checkBadge.setText("\u2713");
                        checkBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                        checkBadge.setTypeface(Typeface.DEFAULT_BOLD);
                        checkBadge.setTextColor(badgeFg);
                        footerRow.addView(checkBadge);
                    }
                    card.addView(footerRow);

                    card.setOnLongClickListener(v -> {
                        startSelection(state, msgId, renderHolder[0]);
                        return true;
                    });
                    card.setOnClickListener(v -> {
                        if (state.selectionMode) {
                            toggleSelection(state, msgId, renderHolder[0]);
                        } else if (hasMediaUrl) {
                            if (isVideoMedia(content, type)) {
                                openVideoPreview(activity, content);
                            } else {
                                openImagePreview(activity, content);
                            }
                        } else if (content != null && content.startsWith("http")) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(content));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                            } catch (Throwable ignored) {
                            }
                        }
                    });

                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    if (!isFirst) {
                        cardParams.topMargin = dp(activity, 2);
                    }
                    list.addView(card, cardParams);
                }
            };

            selectAllBtn.setOnClickListener(v -> {
                if (!state.selectionMode) {
                    return;
                }
                if (state.selectedIds.size() == state.messages.size()) {
                    state.selectedIds.clear();
                    state.selectionMode = false;
                } else {
                    state.selectedIds.clear();
                    for (String[] row : state.messages) {
                        state.selectedIds.add(row[0]);
                    }
                }
                renderHolder[0].run();
            });

            actionBtn.setOnClickListener(v -> {
                if (state.messages.isEmpty()) {
                    return;
                }
                if (state.selectionMode) {
                    if (state.selectedIds.isEmpty()) {
                        return;
                    }
                    db.deleteSaved(new ArrayList<>(state.selectedIds));
                    state.selectedIds.clear();
                    state.selectionMode = false;
                    loadMessages(activity, db, state);
                    renderHolder[0].run();
                } else {
                    new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(activity))
                            .setTitle("Clear deleted messages?")
                            .setMessage("Remove all " + state.messages.size() + " saved deleted messages shown here?")
                            .setPositiveButton("Clear", (d, w) -> {
                                List<String> allIds = new ArrayList<>();
                                for (String[] row : state.messages) {
                                    allIds.add(row[0]);
                                }
                                db.deleteSaved(allIds);
                                loadMessages(activity, db, state);
                                renderHolder[0].run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

            renderHolder[0].run();

            int maxListHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.55f);
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

    private static void loadMessages(Activity activity, PikoMessageDb db, SheetState state) {
        List<String[]> allDeleted = db.getDeletedMessages();
        List<String[]> matched = filterByHeaderHint(activity, allDeleted);
        if (!matched.isEmpty()) {
            state.messages = matched;
            state.showingAllFallback = false;
            return;
        }

        String threadId = SavedMessagesHook.getActiveThreadId();
        if (threadId != null && !threadId.isEmpty()) {
            List<String[]> threadMessages = db.getDeletedMessagesForThread(threadId);
            if (!threadMessages.isEmpty()) {
                state.messages = threadMessages;
                state.showingAllFallback = false;
                return;
            }
        }

        state.messages = allDeleted;
        state.showingAllFallback = !allDeleted.isEmpty();
    }

    private static void startSelection(SheetState state, String msgId, Runnable render) {
        state.selectionMode = true;
        if (!state.selectedIds.contains(msgId)) {
            state.selectedIds.add(msgId);
        }
        render.run();
    }

    private static void toggleSelection(SheetState state, String msgId, Runnable render) {
        if (state.selectedIds.contains(msgId)) {
            state.selectedIds.remove(msgId);
            if (state.selectedIds.isEmpty()) {
                state.selectionMode = false;
            }
        } else {
            state.selectedIds.add(msgId);
        }
        render.run();
    }

    private static TextView createActionPill(Context context, String label, int bgColor, int fgColor) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        stylePill(tv, bgColor, fgColor);
        return tv;
    }

    private static void stylePill(TextView tv, int bgColor, int fgColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(tv.getContext(), 16));
        d.setColor(bgColor);
        tv.setBackground(d);
        tv.setTextColor(fgColor);
    }

    private static void applySelectedCardOverlay(
            Context context,
            LinearLayout card,
            int selectedBg,
            int strokeColor,
            boolean isFirst,
            boolean isLast
    ) {
        float outer = dp(context, 20);
        float inner = dp(context, 6);
        float topR = isFirst ? outer : inner;
        float botR = isLast ? outer : inner;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(selectedBg);
        d.setStroke(dp(context, 1.5f), strokeColor);
        d.setCornerRadii(new float[]{topR, topR, topR, topR, botR, botR, botR, botR});
        card.setBackground(d);
    }

    private static boolean isRenderableMediaUrl(String content) {
        if (content == null) return false;
        String lower = content.trim().toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        return !lower.contains("instagram.com/reel/")
                && !lower.contains("instagram.com/p/")
                && !lower.contains("instagram.com/tv/");
    }

    private static boolean isVideoMedia(String url, String type) {
        if ("video".equals(type) || "reel_share".equals(type)) {
            return true;
        }
        return MediaUrls.isVideo(url);
    }

    private static View buildMediaPreview(
            Activity activity,
            String url,
            boolean isVideo,
            boolean dark,
            Runnable onClick,
            View.OnLongClickListener onLongClick
    ) {
        FrameLayout container = new FrameLayout(activity);
        int radius = dp(activity, 14);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(dark ? 0xFF1F2126 : 0xFFE6E8EE);
        container.setBackground(bg);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        container.setClipToOutline(true);

        ImageView imageView = new ImageView(activity);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        if (isVideo) {
            FrameLayout playBadge = new FrameLayout(activity);
            GradientDrawable badgeCircle = new GradientDrawable();
            badgeCircle.setShape(GradientDrawable.OVAL);
            badgeCircle.setColor(0x99000000);
            playBadge.setBackground(badgeCircle);

            TextView playIcon = new TextView(activity);
            playIcon.setText("\u25B6");
            playIcon.setTextColor(Color.WHITE);
            playIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            playIcon.setGravity(Gravity.CENTER);
            playBadge.addView(playIcon, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            container.addView(playBadge, new FrameLayout.LayoutParams(
                    dp(activity, 46), dp(activity, 46), Gravity.CENTER
            ));
        }

        loadMediaThumbnail(url, isVideo, imageView);

        container.setOnClickListener(v -> onClick.run());
        container.setOnLongClickListener(onLongClick);
        return container;
    }

    private static void loadMediaThumbnail(String url, boolean isVideo, ImageView target) {
        String cacheKey = (isVideo ? "v:" : "i:") + url;
        target.setTag(cacheKey);
        Bitmap cached = THUMB_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }
        MEDIA_EXECUTOR.execute(() -> {
            Bitmap bmp = null;
            if (isVideo) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(url, new HashMap<>());
                    bmp = retriever.getFrameAtTime(0);
                } catch (Throwable ignored) {
                } finally {
                    try {
                        retriever.release();
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);
                    try (InputStream is = conn.getInputStream()) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        bmp = BitmapFactory.decodeStream(is, null, opts);
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            if (bmp != null) {
                THUMB_CACHE.put(cacheKey, bmp);
                final Bitmap finalBmp = bmp;
                MAIN_HANDLER.post(() -> {
                    if (cacheKey.equals(target.getTag())) {
                        target.setImageBitmap(finalBmp);
                    }
                });
            }
        });
    }

    private static void openImagePreview(Activity activity, String url) {
        try {
            Dialog preview = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(Color.BLACK);

            ImageView iv = new ImageView(activity);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(iv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            loadMediaThumbnail(url, false, iv);
            root.setOnClickListener(v -> preview.dismiss());

            preview.setContentView(root);
            preview.show();
        } catch (Throwable ignored) {
        }
    }

    private static void openVideoPreview(Activity activity, String url) {
        try {
            Dialog preview = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(Color.BLACK);

            VideoView vv = new VideoView(activity);
            MediaController mc = new MediaController(activity);
            mc.setAnchorView(vv);
            vv.setMediaController(mc);
            vv.setVideoURI(Uri.parse(url));
            vv.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                vv.start();
            });
            vv.setOnErrorListener((mp, what, extra) -> {
                preview.dismiss();
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.setDataAndType(Uri.parse(url), "video/*");
                    activity.startActivity(Intent.createChooser(i, null));
                } catch (Throwable ignored) {
                }
                return true;
            });
            root.addView(vv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));

            preview.setContentView(root);
            preview.show();
        } catch (Throwable ignored) {
        }
    }

    private static List<String[]> filterByHeaderHint(Activity activity, List<String[]> allDeleted) {
        List<String[]> matched = new ArrayList<>();
        if (allDeleted == null || allDeleted.isEmpty() || activity.getWindow() == null) {
            return matched;
        }
        View decor = activity.getWindow().getDecorView();
        List<String> headerTexts = new ArrayList<>();
        collectTopHeaderTexts(decor, dp(activity, 120), headerTexts, 0);
        if (headerTexts.isEmpty()) {
            return matched;
        }
        for (String[] row : allDeleted) {
            String sender = row[2];
            if (sender == null || sender.trim().isEmpty()) {
                continue;
            }
            String senderLower = sender.trim().toLowerCase(Locale.ROOT);
            for (String h : headerTexts) {
                if (h.equals(senderLower) || h.contains(senderLower)) {
                    matched.add(row);
                    break;
                }
            }
        }
        return matched;
    }

    private static void collectTopHeaderTexts(View view, int maxBottomY, List<String> out, int depth) {
        if (view == null || view.getVisibility() != View.VISIBLE || depth > 20) {
            return;
        }
        if (view instanceof TextView) {
            Rect r = new Rect();
            if (view.getGlobalVisibleRect(r) && r.bottom <= maxBottomY) {
                CharSequence cs = ((TextView) view).getText();
                if (cs != null) {
                    String s = cs.toString().trim().toLowerCase(Locale.ROOT);
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectTopHeaderTexts(g.getChildAt(i), maxBottomY, out, depth + 1);
            }
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

    private static final class SheetState {
        List<String[]> messages = new ArrayList<>();
        final Set<String> selectedIds = new LinkedHashSet<>();
        boolean selectionMode;
        boolean showingAllFallback;
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

            canvas.drawLine(4f, 6.5f, 20f, 6.5f, paint);
            Path handle = new Path();
            handle.moveTo(9f, 6.5f);
            handle.lineTo(9.5f, 4f);
            handle.lineTo(14.5f, 4f);
            handle.lineTo(15f, 6.5f);
            canvas.drawPath(handle, paint);

            Path body = new Path();
            body.moveTo(6.5f, 6.5f);
            body.lineTo(7.5f, 19.5f);
            body.quadTo(7.6f, 20.5f, 8.8f, 20.5f);
            body.lineTo(15.2f, 20.5f);
            body.quadTo(16.4f, 20.5f, 16.5f, 19.5f);
            body.lineTo(17.5f, 6.5f);
            canvas.drawPath(body, paint);

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
