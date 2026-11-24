/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.device;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Device spoofing: rewrites the device identity in {@link Build} at app
 * startup, before Instagram reads it, so the app reports the device chosen
 * in Morphe at patch time instead of the real one.
 *
 * <p>The point is features Instagram gates to other phones, such as HDR
 * playback quality: Meta serves HDR or SDR based on the reported device.
 * Only identity fields are rewritten, but they are rewritten as one
 * complete identity copied verbatim from a real firmware dump of the
 * chosen device (manufacturer, brand, model, device, product, board,
 * build ID, display, incremental and the exact fingerprint string), so
 * every field agrees with every other field the way it does on the real
 * phone. Display and codec capabilities are NOT faked: the spoof can
 * only unlock what the real hardware can actually play.</p>
 *
 * <p>{@link Build.VERSION} is deliberately left real except
 * INCREMENTAL, which is part of the reported identity: faking RELEASE or
 * the SDK level changes API-level behaviour across the whole app. This
 * class never throws.</p>
 *
 * <p>The device to report is read from the configuration baked into the
 * app at patch time ({@code assets/piko/device_spoof.properties}, written
 * by the Device spoofing patch from the model chosen in Morphe), so the
 * spoof is active from the very first launch, before any login. There is
 * deliberately no in-app setting for this: one place decides, and it
 * cannot be lost by a settings reset.</p>
 */
public final class DeviceSpoof {

    private static final String TAG = "Piko";

    private static final String BAKED_ASSET = "piko/device_spoof.properties";

    public static final String PROFILE_REAL = "real";
    public static final String PROFILE_CUSTOM = "custom";

    /**
     * Preset profiles: complete device identities copied verbatim from
     * real firmware dumps (dumps.tadiphone.dev), so the whole reported
     * tuple matches a device that actually exists. Columns: id,
     * manufacturer, brand, model, device, product, board, build ID,
     * incremental, fingerprint, display.
     */
    private static final String[][] PROFILES = {
            {"pixel_9_pro_xl", "Google", "google", "Pixel 9 Pro XL", "komodo", "komodo",
                    "zumapro", "BP1A.250405.005.A1", "13151424",
                    "google/komodo/komodo:15/BP1A.250405.005.A1/13151424:user/release-keys",
                    "BP1A.250405.005.A1"},
            {"pixel_10_pro_xl", "Google", "google", "Pixel 10 Pro XL", "mustang", "mustang",
                    "laguna", "BD3A.251005.003.W3", "14147046",
                    "google/mustang/mustang:16/BD3A.251005.003.W3/14147046:user/release-keys",
                    "BD3A.251005.003.W3"},
            {"galaxy_s25_ultra", "samsung", "samsung", "SM-S938B", "pa3q", "pa3qxxx",
                    "sun", "AP3A.240905.015.A2", "S938BXXU1AYA1",
                    "samsung/pa3qxxx/pa3q:15/AP3A.240905.015.A2/S938BXXU1AYA1:user/release-keys",
                    "AP3A.240905.015.A2.S938BXXU1AYA1"},
            {"realme_p3_pro", "realme", "realme", "RMX5032", "RE6079L1", "RMX5032IN",
                    "volcano", "UKQ1.231108.001", "V.R4T2.1c7a1f4-45f1-111ad",
                    "realme/RMX5032IN/RE6079L1:15/UKQ1.231108.001/V.R4T2.1c7a1f4-45f1-111ad:user/release-keys",
                    "RMX5032IN-user 15 UKQ1.231108.001 V.R4T2.1c7a1f4-45f1-111ad release-keys"},
    };

    private static boolean applied;

    private DeviceSpoof() {
    }

    /**
     * Applies the baked-in profile. Called from InstagramAppShell.onCreate
     * by the Device spoofing patch, before Instagram's own startup code
     * reads the device identity. Idempotent per process.
     */
    public static synchronized void applyEarly() {
        if (applied) return;
        try {
            Application app = currentApplication();
            if (app == null) return;

            String[] profile = resolveBakedProfile(app);
            if (profile == null) return;
            applied = true;

            String manufacturer = profile[0];
            String brand = profile[1];
            String model = profile[2];
            String device = profile[3];
            String product = profile[4];
            String board = profile[5];
            String buildId = profile[6];
            String incremental = profile[7];
            String fingerprint = profile[8];
            String display = profile[9];

            setField("MANUFACTURER", manufacturer);
            setField("BRAND", brand);
            setField("MODEL", model);
            setField("DEVICE", device);
            setField("PRODUCT", product);
            setField("BOARD", board);
            setField("ID", buildId);
            setField("DISPLAY", display);
            // The fingerprint is the exact string the real device reports,
            // never recomposed from this phone's own build values: mixing
            // the two is what gives a spoof away.
            setField("FINGERPRINT", fingerprint);
            setVersionField("INCREMENTAL", incremental);

            Log.i(TAG, "DeviceSpoof: reporting as " + manufacturer + " " + model
                    + " (" + device + ")");
        } catch (Throwable ignored) {
            // Spoofing is best-effort: a failure must never break startup.
        }
    }

    /**
     * The Application object, obtained without the extension framework:
     * during Application.onCreate it already exists in ActivityThread.
     */
    private static Application currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getMethod("currentApplication");
            return (Application) currentApplication.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Returns the full identity to report, or null for no spoof:
     * {manufacturer, brand, model, device, product, board, build ID,
     * incremental, fingerprint, display}. Entries are null where a custom
     * profile does not provide them; nulls are never written.
     */
    private static String[] resolveBakedProfile(Application app) {
        Properties baked = new Properties();
        try (InputStream in = app.getAssets().open(BAKED_ASSET)) {
            baked.load(in);
        } catch (Throwable ignored) {
            return null;
        }
        return profileFor(baked.getProperty("profile", PROFILE_REAL), baked);
    }

    private static String[] profileFor(String id, Properties baked) {
        if (id == null || id.isEmpty() || PROFILE_REAL.equals(id)) return null;
        if (PROFILE_CUSTOM.equals(id)) {
            String customModel = baked.getProperty("custom_model", "");
            if (customModel.trim().isEmpty()) return null;
            String manufacturer = orReal(baked.getProperty("custom_manufacturer", ""), Build.MANUFACTURER);
            String brand = orReal(baked.getProperty("custom_brand", ""), Build.BRAND);
            String device = orReal(baked.getProperty("custom_device", ""), Build.DEVICE);
            String product = orReal(baked.getProperty("custom_product", ""), device);
            String buildId = emptyToNull(baked.getProperty("custom_build_id", ""));
            String incremental = emptyToNull(baked.getProperty("custom_incremental", ""));
            String fingerprint = emptyToNull(baked.getProperty("custom_fingerprint", ""));
            if (fingerprint == null) {
                // No real fingerprint was given for this custom device, so
                // fall back to recomposing one from the reported identity
                // and this phone's real software build.
                fingerprint = brand + "/" + product + "/" + device + ":"
                        + Build.VERSION.RELEASE + "/" + Build.ID + "/"
                        + Build.VERSION.INCREMENTAL + ":"
                        + Build.TYPE + "/" + Build.TAGS;
            }
            return new String[]{manufacturer, brand, customModel.trim(), device,
                    product, null, buildId, incremental, fingerprint, null};
        }
        for (String[] preset : PROFILES) {
            if (preset[0].equals(id)) {
                return new String[]{preset[1], preset[2], preset[3], preset[4], preset[5],
                        preset[6], preset[7], preset[8], preset[9], preset[10]};
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String orReal(String value, String real) {
        return value == null || value.trim().isEmpty() ? real : value.trim();
    }

    private static void setField(String name, String value) {
        setStaticField(Build.class, name, value);
    }

    private static void setVersionField(String name, String value) {
        setStaticField(Build.VERSION.class, name, value);
    }

    private static void setStaticField(Class<?> owner, String name, String value) {
        if (value == null) return;
        try {
            Field field = owner.getField(name);
            field.setAccessible(true);
            // Since Java 9 the reflection filter rejects writes to static
            // final fields even with setAccessible, so clear the runtime
            // "final" via the modifiers field first where possible.
            try {
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            } catch (Throwable ignored) {
                // Field.modifiers is blocked on newer Android versions;
                // the Unsafe fallback below covers that case.
            }
            try {
                field.set(null, value);
                if (value.equals(field.get(null))) return;
            } catch (Throwable ignored) {
                // Fall through to Unsafe.
            }
            setViaUnsafe(field, value);
        } catch (Throwable ignored) {
            // A single un-writable field must not stop the rest.
        }
    }

    private static void setViaUnsafe(Field field, String value) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class)
                .invoke(unsafe, field);
        long offset = (Long) unsafeClass.getMethod("staticFieldOffset", Field.class)
                .invoke(unsafe, field);
        unsafeClass.getMethod("putObject", Object.class, long.class, Object.class)
                .invoke(unsafe, base, offset, value);
    }
}
