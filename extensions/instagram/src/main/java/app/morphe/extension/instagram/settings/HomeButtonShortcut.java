/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import app.morphe.extension.instagram.settings.preference.fragments.FragmentHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Opens Piko settings when the user long-presses the Home tab in Instagram's bottom navigation bar.
 */
@SuppressWarnings("unused")
public final class HomeButtonShortcut {
    private static final String[] HOME_TAB_IDS = {"feed_tab", "home_tab"};

    private HomeButtonShortcut() {
    }

    public static void attach(Activity activity) {
        if (activity == null || ActivityHook.isPiko(activity)) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        Window.Callback current = window.getCallback();
        if (current == null || (Proxy.isProxyClass(current.getClass())
                && Proxy.getInvocationHandler(current) instanceof TouchWatcher)) {
            return;
        }
        window.setCallback((Window.Callback) Proxy.newProxyInstance(
                Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class},
                new TouchWatcher(activity, current)
        ));
    }

    private static View findHomeTabView(Activity activity) {
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) {
            return null;
        }
        for (String idName : HOME_TAB_IDS) {
            int id = ResourceUtils.getIdentifier(ResourceType.ID, idName);
            if (id != 0) {
                View tab = decor.findViewById(id);
                if (tab != null && tab.isShown()) {
                    return tab;
                }
            }
        }
        String homeLabel = str("piko_navigation_tab_home");
        int minBottom = (int) (decor.getHeight() * 0.75f);
        return findTabByDescription(decor, homeLabel, minBottom);
    }

    private static View findTabByDescription(View view, String homeLabel, int minBottom) {
        if (!view.isShown()) {
            return null;
        }
        CharSequence desc = view.getContentDescription();
        if (desc != null && view.isClickable()) {
            String text = desc.toString().trim();
            if (text.equalsIgnoreCase("Home")
                    || (homeLabel != null && !homeLabel.isEmpty() && text.equalsIgnoreCase(homeLabel))) {
                Rect bounds = new Rect();
                if (view.getGlobalVisibleRect(bounds) && bounds.bottom >= minBottom) {
                    return view;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findTabByDescription(group.getChildAt(i), homeLabel, minBottom);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static final class TouchWatcher implements InvocationHandler {
        private final Activity activity;
        private final Window.Callback target;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Rect bounds = new Rect();
        private boolean tracking;
        private boolean longPressTriggered;
        private float downX;
        private float downY;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!tracking) {
                    return;
                }
                tracking = false;
                longPressTriggered = true;
                View homeTab = findHomeTabView(activity);
                if (homeTab != null) {
                    homeTab.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                long now = SystemClock.uptimeMillis();
                MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0);
                try {
                    target.dispatchTouchEvent(cancel);
                } catch (Throwable ignored) {
                } finally {
                    cancel.recycle();
                }
                FragmentHook.startSettings();
            }
        };

        TouchWatcher(Activity activity, Window.Callback target) {
            this.activity = activity;
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (arguments != null && arguments.length == 1
                    && arguments[0] instanceof MotionEvent
                    && "dispatchTouchEvent".equals(method.getName())) {
                try {
                    if (handleTouch((MotionEvent) arguments[0])) {
                        return Boolean.TRUE;
                    }
                } catch (Throwable t) {
                    Logger.printException(() -> "HomeButtonShortcut touch failed", t);
                }
            }
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private boolean handleTouch(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                longPressTriggered = false;
                View homeTab = findHomeTabView(activity);
                if (homeTab != null && homeTab.getGlobalVisibleRect(bounds)
                        && bounds.contains((int) event.getRawX(), (int) event.getRawY())) {
                    tracking = true;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    handler.removeCallbacks(longPressRunnable);
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                } else {
                    tracking = false;
                }
            } else if (action == MotionEvent.ACTION_MOVE && tracking) {
                float slop = ViewConfiguration.get(activity).getScaledTouchSlop();
                if (Math.hypot(event.getRawX() - downX, event.getRawY() - downY) > slop) {
                    tracking = false;
                    handler.removeCallbacks(longPressRunnable);
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(longPressRunnable);
                tracking = false;
                if (longPressTriggered) {
                    longPressTriggered = false;
                    return action == MotionEvent.ACTION_UP;
                }
            }
            return false;
        }
    }
}
