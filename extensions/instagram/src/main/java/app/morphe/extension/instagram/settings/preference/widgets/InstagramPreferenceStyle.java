/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.customise.font.CustomFontContext;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.shared.ResourceUtils;

public final class InstagramPreferenceStyle {
    static final long NAVIGATION_DRAG_DURATION_MS = 120L;
    private static final String TAG_TITLE = "piko_instagram_pref_title";
    private static final String TAG_SUMMARY = "piko_instagram_pref_summary";
    private static final String TAG_TEXT_COLUMN = "piko_instagram_pref_text_column";
    private static final String TAG_RESULT_CATEGORY = "piko_instagram_search_result_category";
    private static final String TAG_RESULT_CATEGORY_ROW = "piko_instagram_search_result_category_row";
    private static final String TAG_RESULT_CATEGORY_ICON = "piko_instagram_search_result_category_icon";
    private static final String TAG_SWITCH = "piko_instagram_pref_switch";
    private static final String TAG_TRAILING = "piko_instagram_pref_trailing";
    private static final String IGDS_SWITCH_CLASS_NAME =
            "com.instagram.igds.components.switchbutton.IgdsSwitch";

    public static final int TRAILING_NONE = 0;
    public static final int TRAILING_SWITCH = 1;
    public static final int TRAILING_CHEVRON = 2;
    private static final int TRAILING_CHECK = 3;

    private InstagramPreferenceStyle() {
    }

    private static String chevronDrawableName(int layoutDirection) {
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
                ? UI.DRAWABLE_CHEVRON_RIGHT_RTL
                : UI.DRAWABLE_CHEVRON_RIGHT;
    }

    private static int topPaddingDp(int trailingType, boolean hasSummary) {
        return hasSummary || trailingType == TRAILING_SWITCH ? 10 : 15;
    }

    private static int bottomPaddingDp(int trailingType, boolean hasSummary) {
        return hasSummary || trailingType == TRAILING_SWITCH ? 20 : 15;
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static void applyToolbarLayout(
            Context context,
            LinearLayout toolbar,
            ImageView back,
            TextView title,
            boolean isRootSettings
    ) {
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(context, 15), dp(context, 10),
                dp(context, 15), dp(context, 8));
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 70)));

        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPaddingRelative(0, 0, dp(context, 16), 0);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                dp(context, 44), dp(context, 44));
        backParams.gravity = Gravity.CENTER_VERTICAL;
        back.setLayoutParams(backParams);

        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, isRootSettings ? 25 : 20);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        if (!isRootSettings) {
            title.setAutoSizeTextTypeUniformWithConfiguration(
                    18, 20, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        titleParams.leftMargin = dp(context, 7);
        title.setLayoutParams(titleParams);
    }

    /** Matches platform preference dialogs to Instagram's resolved theme. */
    public static Context dialogContext(Context context) {
        int themeRes = UI.isDarkMode()
                ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light;
        return new CustomFontContext(context, themeRes);
    }

    public static int backgroundColor() {
        return UI.getBackgroundColor();
    }

    public static int surfaceContainerColor() {
        if (UI.isDarkMode()) {
            return backgroundColor() == Color.BLACK ? 0xFF141518 : 0xFF1E2024;
        }
        return 0xFFF2F3F7;
    }

    public static int surfaceContainerHighColor() {
        if (UI.isDarkMode()) {
            return backgroundColor() == Color.BLACK ? 0xFF1F2126 : 0xFF282A2F;
        }
        return 0xFFE6E8EE;
    }

    public static int pressedBackgroundColor() {
        return UI.isDarkMode() ? 0x26FFFFFF : 0x1A000000;
    }

    public static int searchBackgroundColor() {
        return surfaceContainerHighColor();
    }

    public static int[] categoryColors(String key) {
        boolean dark = UI.isDarkMode();
        if (key == null) {
            return dark
                    ? new int[]{0xFF004A77, 0xFFC2E7FF}
                    : new int[]{0xFFD7E3FF, 0xFF005AC1};
        }
        switch (key) {
            case "piko_frag_ads":
                return dark ? new int[]{0xFF6E1B1B, 0xFFFFDAD9} : new int[]{0xFFFFDAD9, 0xFF6E1B1B};
            case "piko_frag_filter_content":
                return dark ? new int[]{0xFF005049, 0xFF88FFD9} : new int[]{0xFF88FFD9, 0xFF005049};
            case "piko_frag_ghost":
                return dark ? new int[]{0xFF3B4869, 0xFFD9E2FF} : new int[]{0xFFD9E2FF, 0xFF27304E};
            case "piko_frag_dm":
                return dark ? new int[]{0xFF004A77, 0xFFC2E7FF} : new int[]{0xFFD7E3FF, 0xFF005AC1};
            case "piko_frag_story":
                return dark ? new int[]{0xFF7D5260, 0xFFFFD8E4} : new int[]{0xFFFFD8E4, 0xFF631835};
            case "piko_frag_links":
                return dark ? new int[]{0xFF004F58, 0xFF88FAFF} : new int[]{0xFFCCE8EA, 0xFF004F58};
            case "piko_frag_distraction_free":
                return dark ? new int[]{0xFF1E4E3E, 0xFFA7F0D0} : new int[]{0xFFA7F0D0, 0xFF00513B};
            case "piko_frag_misc":
                return dark ? new int[]{0xFF633B48, 0xFFFFD8EC} : new int[]{0xFFFFD8EC, 0xFF631B4B};
            case "piko_frag_download_media":
                return dark ? new int[]{0xFF004D61, 0xFFACEFEE} : new int[]{0xFFACEFEE, 0xFF002022};
            case "piko_frag_instants":
                return dark ? new int[]{0xFF6E4E13, 0xFFFFDEAC} : new int[]{0xFFFFDEAC, 0xFF281900};
            case "piko_frag_action_bar":
                return dark ? new int[]{0xFF4A3C6E, 0xFFE8DDFF} : new int[]{0xFFE8DDFF, 0xFF342557};
            case "piko_frag_nav_btns":
                return dark ? new int[]{0xFF004A77, 0xFFC2E7FF} : new int[]{0xFFD7E3FF, 0xFF005AC1};
            case "piko_frag_dev_options":
                return dark ? new int[]{0xFF324F34, 0xFFCBEFD0} : new int[]{0xFFCBEFD0, 0xFF042106};
            case "piko_frag_about":
            default:
                return dark ? new int[]{0xFF3F474D, 0xFFDEE3EB} : new int[]{0xFFEFF1F7, 0xFF44474F};
        }
    }

    public static void applyCardShapeForPreference(Preference preference, View view) {
        if (preference == null || view == null) {
            return;
        }
        boolean isFirst = true;
        boolean isLast = true;
        PreferenceGroup parent = preference.getParent();
        if (parent != null) {
            int count = parent.getPreferenceCount();
            int index = -1;
            for (int i = 0; i < count; i++) {
                if (parent.getPreference(i) == preference) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                isFirst = index == 0 || parent.getPreference(index - 1) instanceof PreferenceCategory;
                isLast = index == count - 1 || parent.getPreference(index + 1) instanceof PreferenceCategory;
            }
        }
        applyCardBackground(view, isFirst, isLast, preference.isSelectable());
    }

    public static void applyCardBackground(View view, boolean isFirst, boolean isLast, boolean clickable) {
        Context context = view.getContext();
        float outer = dp(context, 24);
        float inner = dp(context, 6);
        float top = isFirst ? outer : inner;
        float bottom = isLast ? outer : inner;
        float[] radii = new float[]{
                top, top, top, top,
                bottom, bottom, bottom, bottom
        };

        GradientDrawable content = new GradientDrawable();
        content.setColor(surfaceContainerColor());
        content.setCornerRadii(radii);

        if (clickable) {
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadii(radii);
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(pressedBackgroundColor()),
                    content,
                    mask
            );
            view.setBackground(ripple);
        } else {
            view.setBackground(content);
        }
        view.setClipToOutline(true);
    }

    public static int primaryTextColor() {
        return UI.getThemedColour("igds_color_primary_text");
    }

    public static int secondaryTextColor() {
        return UI.getThemedColour("igds_color_secondary_text");
    }

    public static int disabledTextColor() {
        return UI.getThemedColour("igds_color_separator");
    }

    private static void alignTextToLocaleStart(TextView textView) {
        textView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    }

    public static int selectionColor() {
        return ResourceUtils.getColor("igds_primary_button", 0xff0095f6);
    }

    public static void applySystemBarStyle(Activity activity) {
        activity.getWindow().setStatusBarColor(backgroundColor());
        activity.getWindow().setNavigationBarColor(backgroundColor());

        int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
        if (UI.isDarkMode()) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static View createPreferenceView(Context context, int trailingType) {
        return createPreferenceView(context,trailingType,null);
    }

    public static View createPreferenceView(Context context, int trailingType, String iconResName) {
        PreferenceRow row = new PreferenceRow(context, trailingType);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (iconResName != null) {
            row.setIcon(iconResName);
        }

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setTag(TAG_TEXT_COLUMN);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMarginEnd(dp(context, 12));
        row.addView(textColumn, textParams);

        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(primaryTextColor());
        title.setIncludeFontPadding(false);
        title.setSingleLine(false);
        alignTextToLocaleStart(title);
        textColumn.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(context);
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        summary.setTextColor(secondaryTextColor());
        summary.setLineSpacing(dp(context, 1), 1.0f);
        alignTextToLocaleStart(summary);
        summary.setPadding(0, dp(context, 3), 0, 0);
        textColumn.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (trailingType == TRAILING_SWITCH) {
            CompoundButton switchView = createNativeSwitch(nativeSwitchContext(context));
            switchView.setClickable(false);
            switchView.setFocusable(false);
            switchView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            switchView.setTag(TAG_SWITCH);
            row.addView(switchView, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 32)));
        } else if (trailingType == TRAILING_CHEVRON) {
            ImageView trailing = new ImageView(context);
            trailing.setTag(TAG_TRAILING);
            UI.setThemedIcon(
                    trailing,
                    chevronDrawableName(
                            context.getResources().getConfiguration().getLayoutDirection()
                    ),
                    "igds_color_secondary_icon"
            );
            trailing.setScaleType(ImageView.ScaleType.CENTER);
            row.addView(trailing, new LinearLayout.LayoutParams(dp(context, 20), dp(context, 28)));
        }

        return row;
    }

    public static View createSearchResultView(Context context) {
        View view = createPreferenceView(context, TRAILING_CHEVRON);

        TextView description = view.findViewWithTag(TAG_SUMMARY);
        if (description != null) {
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
        }

        LinearLayout textColumn = view.findViewWithTag(TAG_TEXT_COLUMN);
        if (textColumn != null) {
            LinearLayout categoryRow = new LinearLayout(context);
            categoryRow.setTag(TAG_RESULT_CATEGORY_ROW);
            categoryRow.setOrientation(LinearLayout.HORIZONTAL);
            categoryRow.setGravity(Gravity.CENTER_VERTICAL);
            categoryRow.setVisibility(View.GONE);
            textColumn.addView(categoryRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            ImageView categoryIcon = new ImageView(context);
            categoryIcon.setTag(TAG_RESULT_CATEGORY_ICON);
            categoryIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            categoryIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dp(context, 16),
                    dp(context, 16)
            );
            iconParams.setMarginEnd(dp(context, 6));
            categoryRow.addView(categoryIcon, iconParams);

            TextView category = new TextView(context);
            category.setTag(TAG_RESULT_CATEGORY);
            category.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            category.setTextColor(secondaryTextColor());
            category.setIncludeFontPadding(true);
            category.setLineSpacing(dp(context, 1), 1.0f);
            alignTextToLocaleStart(category);
            category.setMaxLines(1);
            category.setEllipsize(TextUtils.TruncateAt.END);
            categoryRow.addView(category, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
        }

        return view;
    }

    public static void bindText(Preference preference, View view) {
        Context context = view.getContext();
        boolean enabled = preference.isEnabled();
        boolean chevron = view instanceof PreferenceRow
                && ((PreferenceRow) view).trailingType == TRAILING_CHEVRON;
        boolean useNormalColors = enabled || chevron;

        TextView title = view.findViewWithTag(TAG_TITLE);
        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        View trailing = view.findViewWithTag(TAG_TRAILING);

        int titleColor = useNormalColors ? primaryTextColor() : disabledTextColor();
        int summaryColor = useNormalColors ? secondaryTextColor() : disabledTextColor();

        if (title != null) {
            title.setText(preference.getTitle());
            title.setTextColor(titleColor);
            title.setEnabled(enabled);
        }

        if (summary != null) {
            CharSequence summaryText = preference.getSummary();
            boolean hasSummary = !TextUtils.isEmpty(summaryText);
            summary.setText(hasSummary ? summaryText : "");
            summary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            summary.setTextColor(summaryColor);
            summary.setEnabled(enabled);
            if (view instanceof PreferenceRow) {
                ((PreferenceRow) view).setHasSummary(hasSummary);
            }
        }

        if (trailing != null) {
            trailing.setEnabled(enabled);
            if (trailing instanceof ImageView) {
                int trailingColor = useNormalColors
                        ? UI.getThemedColour("igds_color_secondary_icon")
                        : disabledTextColor();
                ((ImageView) trailing).setColorFilter(new PorterDuffColorFilter(
                        trailingColor,
                        PorterDuff.Mode.SRC_ATOP
                ));
            }
            trailing.invalidate();
        }

        if (chevron) {
            view.setAlpha(enabled ? 1.0f : 0.5f);
        }
        view.setEnabled(enabled);
        applyCardShapeForPreference(preference, view);
    }

    public static void bindSearchResult(
            Preference preference,
            View view,
            CharSequence categoryText,
            String categoryIconResourceName
    ) {
        bindText(preference, view);

        Context context = view.getContext();
        TextView description = view.findViewWithTag(TAG_SUMMARY);
        View categoryRow = view.findViewWithTag(TAG_RESULT_CATEGORY_ROW);
        ImageView categoryIcon = view.findViewWithTag(TAG_RESULT_CATEGORY_ICON);
        TextView category = view.findViewWithTag(TAG_RESULT_CATEGORY);
        boolean hasDescription = description != null
                && description.getVisibility() == View.VISIBLE;
        boolean hasCategory = !TextUtils.isEmpty(categoryText);
        boolean hasCategoryIcon = hasCategory
                && !TextUtils.isEmpty(categoryIconResourceName);

        if (categoryRow != null) {
            categoryRow.setVisibility(hasCategory ? View.VISIBLE : View.GONE);
            categoryRow.setPaddingRelative(
                    0,
                    dp(context, hasDescription ? 4 : 10),
                    0,
                    0
            );
        }

        if (categoryIcon != null) {
            categoryIcon.setVisibility(hasCategoryIcon ? View.VISIBLE : View.GONE);
            if (hasCategoryIcon) {
                UI.setThemedIcon(categoryIcon, categoryIconResourceName);
            }
        }

        if (category != null) {
            category.setText(hasCategory ? categoryText : "");
            category.setTextColor(preference.isEnabled()
                    ? secondaryTextColor()
                    : disabledTextColor());
            category.setEnabled(preference.isEnabled());
        }

        if (view instanceof PreferenceRow) {
            PreferenceRow row = (PreferenceRow) view;
            row.setHasSummary(hasDescription || hasCategory);
            int verticalPadding = dp(context, 15);
            row.setPadding(row.getPaddingLeft(), verticalPadding,
                    row.getPaddingRight(), verticalPadding);
        }
    }

    public static void setTrailingVisible(View view, boolean visible) {
        View trailing = view.findViewWithTag(TAG_TRAILING);
        if (trailing != null) {
            trailing.setVisibility(visible ? View.VISIBLE : View.GONE);
            trailing.invalidate();
        }
    }

    public static void bindIcon(View view, String iconResName) {
        bindIcon(view, iconResName, null);
    }

    public static void bindIcon(View view, String iconResName, String preferenceKey) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setIcon(iconResName, preferenceKey);
        }
    }

    public static void setPressedHighlightEnabled(View view, boolean enabled) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setPressedHighlightEnabled(enabled);
        }
    }

    public static CompoundButton findSwitch(View view) {
        return view.findViewWithTag(TAG_SWITCH);
    }

    public static View createNavigationCheckRow(
            Context context,
            CharSequence title,
            String iconName,
            boolean checked
    ) {
        PreferenceRow row = new PreferenceRow(context, TRAILING_CHECK);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 56));
        row.setPadding(dp(context, 12), dp(context, 4), dp(context, 8), dp(context, 4));

        CheckBox checkBox = new CheckBox(context);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setGravity(Gravity.CENTER);
        checkBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        checkBox.setChecked(checked);
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 48)));

        ImageView iconView = new ImageView(context);
        UI.setThemedIcon(iconView, iconName);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(context, 24),
                dp(context, 24)
        );
        iconParams.setMarginStart(dp(context, 8));
        iconParams.setMarginEnd(dp(context, 12));
        row.addView(iconView, iconParams);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleView.setTextColor(primaryTextColor());
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        titleParams.setMarginEnd(dp(context, 8));
        row.addView(titleView, titleParams);
        row.setHighlightView(titleView);

        FrameLayout dragTarget = new FrameLayout(context);
        dragTarget.setTag(TAG_TRAILING);
        dragTarget.setContentDescription(title);
        DragHandleView dragIcon = new DragHandleView(context);
        dragIcon.setAlpha(0.72f);
        FrameLayout.LayoutParams dragIconParams = new FrameLayout.LayoutParams(
                dp(context, 24),
                dp(context, 24),
                Gravity.CENTER
        );
        dragTarget.addView(dragIcon, dragIconParams);
        row.addView(dragTarget, new LinearLayout.LayoutParams(
                dp(context, 48),
                dp(context, 48)
        ));

        row.setSwitchAccessibilityChecked(checked);
        row.setContentDescription(title);

        return row;
    }

    public static boolean isNavigationDragHandleHit(View row, float rawX, float rawY) {
        View dragTarget = row.findViewWithTag(TAG_TRAILING);
        if (dragTarget == null || dragTarget.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        dragTarget.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX < location[0] + dragTarget.getWidth()
                && rawY >= location[1]
                && rawY < location[1] + dragTarget.getHeight();
    }

    public static void setNavigationRowDragging(
            View view,
            boolean dragging,
            boolean animate
    ) {
        if (view instanceof PreferenceRow
                && ((PreferenceRow) view).trailingType == TRAILING_CHECK) {
            ((PreferenceRow) view).setDragState(dragging, animate);
        }
    }

    public static void setNativeSwitchChecked(
            CompoundButton switchView,
            boolean checked,
            boolean animate
    ) {
        if (!animate || switchView.isChecked() == checked) {
            switchView.setChecked(checked);
            return;
        }

        try {
            switchView.getClass()
                    .getMethod("setCheckedAnimated", boolean.class)
                    .invoke(switchView, checked);
        } catch (ReflectiveOperationException ignored) {
            switchView.setChecked(checked);
        }
    }

    private static Context nativeSwitchContext(Context context) {
        Configuration overrideConfiguration = new Configuration();
        overrideConfiguration.uiMode = UI.isDarkMode()
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;

        ContextThemeWrapper switchContext = new ContextThemeWrapper(context, 0);
        switchContext.applyOverrideConfiguration(overrideConfiguration);
        switchContext.getTheme().setTo(context.getTheme());
        return switchContext;
    }

    public static boolean useMaterialSwitchStyle(View view, boolean original) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity && ActivityHook.isPiko((Activity) context)) {
                return true;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity && ActivityHook.isPiko((Activity) context)) {
            return true;
        }
        return original;
    }

    private static CompoundButton createNativeSwitch(Context context) {
        try {
            CompoundButton switchView = Class
                    .forName(IGDS_SWITCH_CLASS_NAME, true, context.getClassLoader())
                    .asSubclass(CompoundButton.class)
                    .getConstructor(Context.class)
                    .newInstance(context);
            return switchView;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException("Unable to create Instagram's IgdsSwitch", exception);
        }
    }

    public static void bindSwitchAccessibility(View view, boolean checked) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setSwitchAccessibilityChecked(checked);
        }
    }

    public static boolean consumeSwitchClickAllowed(View view) {
        if (view instanceof PreferenceRow) {
            return ((PreferenceRow) view).consumeSwitchClickAllowed();
        }
        return true;
    }

    private static class PreferenceRow extends LinearLayout {
        private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect switchHitRect = new Rect();
        private final int trailingType;
        private View highlightView;
        private boolean pressedHighlightEnabled;
        private boolean pressedHighlightAllowed;
        private boolean switchClickAllowed = true;
        private boolean drawPressedHighlight;
        private boolean drawDragHighlight;
        private int dragOverlayColor;
        private boolean switchAccessibilityChecked;
        private FrameLayout iconContainer;
        private ImageView iconView;


        PreferenceRow(Context context, int trailingType) {
            this(context, trailingType, null);
        }

        PreferenceRow(Context context, int trailingType, String iconResName) {
            super(context);
            this.trailingType = trailingType;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setWillNotDraw(false);
            if (trailingType == TRAILING_CHECK) {
                setOutlineProvider(ViewOutlineProvider.BOUNDS);
                setClipToOutline(false);
            }
        }

        private void initIconView(Context context) {
            iconContainer = new FrameLayout(context);
            int badgeSize = dp(context, 48);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(badgeSize, badgeSize);
            containerParams.setMarginEnd(dp(context, 16));
            iconContainer.setLayoutParams(containerParams);

            iconView = new ImageView(context);
            int iconSize = dp(context, 22);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            iconView.setLayoutParams(iconParams);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconContainer.addView(iconView);

            addView(iconContainer, 0);
        }

        void setIcon(String iconResName) {
            setIcon(iconResName, null);
        }

        void setIcon(String iconResName, String preferenceKey) {
            setPadding(dp(getContext(), 16), dp(getContext(), 14), dp(getContext(), 16), dp(getContext(), 14));
            if (iconResName == null) {
                if (iconContainer != null) {
                    iconView.setImageDrawable(null);
                    iconContainer.setVisibility(View.GONE);
                }
                return;
            }
            if (iconContainer == null) {
                initIconView(getContext());
            }
            int[] colors = categoryColors(preferenceKey);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(colors[0]);
            iconContainer.setBackground(circle);

            UI.setThemedIcon(iconView, iconResName);
            iconView.setColorFilter(new PorterDuffColorFilter(colors[1], PorterDuff.Mode.SRC_ATOP));
            iconContainer.setVisibility(View.VISIBLE);
        }

        void setHighlightView(View highlightView) {
            this.highlightView = highlightView;
            pressedHighlightEnabled = true;
        }

        void setPressedHighlightEnabled(boolean enabled) {
            pressedHighlightEnabled = enabled;
            if (!enabled) {
                pressedHighlightAllowed = false;
                setDrawPressedHighlight(false);
            }
        }

        void setSwitchAccessibilityChecked(boolean checked) {
            switchAccessibilityChecked = checked;
        }

        void setDragState(boolean dragging, boolean animateState) {
            drawDragHighlight = dragging;
            dragOverlayColor = UI.isDarkMode() ? 0xff3f3f3f : 0x0d000000;
            if (dragging && !UI.isDarkMode()) {
                TypedArray colors = getContext().obtainStyledAttributes(
                        new int[]{android.R.attr.colorBackgroundFloating}
                );
                setBackgroundColor(colors.getColor(0, backgroundColor()) | 0xff000000);
                colors.recycle();
            } else {
                setBackground(null);
            }
            invalidate();

            animate().cancel();
            float translationZ = dragging
                    ? dp(getContext(), 5f) : 0f;
            if (animateState) {
                animate()
                        .translationZ(translationZ)
                        .setDuration(NAVIGATION_DRAG_DURATION_MS)
                        .start();
            } else {
                setTranslationZ(translationZ);
            }
        }

        void setHasSummary(boolean hasSummary) {
            int verticalPadding = dp(getContext(), hasSummary ? 14 : 16);
            setMinimumHeight(dp(getContext(), iconContainer != null && iconContainer.getVisibility() == View.VISIBLE ? 76 : (hasSummary ? 70 : 58)));
            setPadding(getPaddingLeft(), verticalPadding, getPaddingRight(), verticalPadding);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            if (trailingType == TRAILING_SWITCH || trailingType == TRAILING_CHECK) {
                event.setClassName(trailingType == TRAILING_SWITCH
                        ? "android.widget.Switch"
                        : "android.widget.CheckBox");
                event.setChecked(switchAccessibilityChecked);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (trailingType == TRAILING_SWITCH || trailingType == TRAILING_CHECK) {
                info.setClassName(trailingType == TRAILING_SWITCH
                        ? "android.widget.Switch"
                        : "android.widget.CheckBox");
                info.setCheckable(true);
                info.setChecked(switchAccessibilityChecked);
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressedHighlightAllowed = shouldDrawPressedHighlight(event.getX(), event.getY());
                switchClickAllowed = shouldHandleSwitchClick(event.getY());
            } else if (action == MotionEvent.ACTION_CANCEL) {
                pressedHighlightAllowed = false;
                setDrawPressedHighlight(false);
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            setDrawPressedHighlight(pressed && pressedHighlightAllowed);
            if (!pressed) {
                pressedHighlightAllowed = false;
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawDragHighlight) {
                pressedPaint.setStyle(Paint.Style.FILL);
                pressedPaint.setColor(dragOverlayColor);
                canvas.drawRect(0, 0, getWidth(), getHeight(), pressedPaint);
            }
            super.dispatchDraw(canvas);
        }

        private boolean shouldDrawPressedHighlight(float x, float y) {
            if (!pressedHighlightEnabled || !isEnabled()) {
                return false;
            }

            if (trailingType != TRAILING_SWITCH) {
                return true;
            }

            if (highlightView == null || y < highlightTop() || y > highlightBottom()) {
                return false;
            }

            // Highlight the switch row when its text area is pressed, but never when the
            // switch itself is tapped; the switch provides its own touch feedback.
            View excludedView = findViewWithTag(TAG_SWITCH);
            if (excludedView == null) {
                return true;
            }

            int horizontalSlop = dp(getContext(), 10);
            int verticalSlop = dp(getContext(), 3);
            switchHitRect.set(0, 0, excludedView.getWidth(), excludedView.getHeight());
            offsetDescendantRectToMyCoords(excludedView, switchHitRect);
            switchHitRect.inset(-horizontalSlop, -verticalSlop);
            return !switchHitRect.contains(Math.round(x), Math.round(y));
        }

        private boolean shouldHandleSwitchClick(float y) {
            return trailingType != TRAILING_SWITCH || highlightView == null || (y >= highlightTop() && y <= highlightBottom());
        }

        private boolean consumeSwitchClickAllowed() {
            boolean allowed = switchClickAllowed;
            switchClickAllowed = true;
            return allowed;
        }

        private int highlightTop() {
            if (highlightView == null) {
                return 0;
            }
            return Math.max(0, highlightView.getTop() - dp(getContext(), 10));
        }

        private int highlightBottom() {
            if (highlightView == null) {
                return getHeight();
            }
            return Math.min(getHeight(), highlightView.getBottom() + dp(getContext(), 10));
        }

        private void setDrawPressedHighlight(boolean drawPressedHighlight) {
            if (this.drawPressedHighlight == drawPressedHighlight) {
                return;
            }
            this.drawPressedHighlight = drawPressedHighlight;
            invalidate();
        }
    }

    private static final class DragHandleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DragHandleView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float lineWidth = dp(getContext(), 16f);
            float stroke = dp(getContext(), 2f);
            float left = (getWidth() - lineWidth) / 2.0f;
            float right = left + lineWidth;
            float radius = stroke / 2.0f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(secondaryTextColor());
            for (int line = 0; line < 3; line++) {
                float centerY = dp(
                        getContext(),
                        8f + 4f * line
                );
                canvas.drawRoundRect(
                        left,
                        centerY - radius,
                        right,
                        centerY + radius,
                        radius,
                        radius,
                        paint
                );
            }
        }
    }
}
