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
 * Only identity fields are rewritten (manufacturer, brand, model, device,
 * product and the recomposed fingerprint). Display and codec capabilities
 * are NOT faked: the spoof can only unlock what the real hardware can
 * actually play.</p>
 *
 * <p>{@link Build.VERSION} is deliberately left real: faking it changes
 * API-level behaviour across the whole app. This class never throws.</p>
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

    /** Preset profiles: id, manufacturer, brand, model, device codename. */
    private static final String[][] PROFILES = {
            // "Pixel XL": the modern XL flagships. The 2016 Pixel XL has an
            // ancient fingerprint and would be a useless spoof target.
            {"pixel_9_pro_xl", "Google", "google", "Pixel 9 Pro XL", "komodo"},
            {"pixel_10_pro_xl", "Google", "google", "Pixel 10 Pro XL", "mustang"},
            {"galaxy_s26_ultra", "samsung", "samsung", "SM-S948B", "e3q"},
            {"galaxy_s25_ultra", "samsung", "samsung", "SM-S938B", "pa3q"},
            {"realme_p3_pro", "realme", "realme", "RMX5032", "RMX5032"},
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

            setField("MANUFACTURER", manufacturer);
            setField("BRAND", brand);
            setField("MODEL", model);
            setField("DEVICE", device);
            setField("PRODUCT", device);
            // Recompose the fingerprint from the spoofed identity and the
            // phone's real software build, so it stays internally consistent.
            setField("FINGERPRINT",
                    brand + "/" + device + "/" + device + ":"
                            + Build.VERSION.RELEASE + "/" + Build.ID + "/"
                            + Build.VERSION.INCREMENTAL + ":"
                            + Build.TYPE + "/" + Build.TAGS);

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

    /** Returns {manufacturer, brand, model, device} or null for no spoof. */
    private static String[] resolveBakedProfile(Application app) {
        Properties baked = new Properties();
        try (InputStream in = app.getAssets().open(BAKED_ASSET)) {
            baked.load(in);
        } catch (Throwable ignored) {
            return null;
        }
        return profileFor(
                baked.getProperty("profile", PROFILE_REAL),
                baked.getProperty("custom_manufacturer", ""),
                baked.getProperty("custom_brand", ""),
                baked.getProperty("custom_model", ""),
                baked.getProperty("custom_device", ""));
    }

    /** Returns {manufacturer, brand, model, device} or null for no spoof. */
    private static String[] profileFor(String id, String customManufacturer,
                                       String customBrand, String customModel,
                                       String customDevice) {
        if (id == null || id.isEmpty() || PROFILE_REAL.equals(id)) return null;
        if (PROFILE_CUSTOM.equals(id)) {
            if (customModel == null || customModel.trim().isEmpty()) return null;
            String manufacturer = orReal(customManufacturer, Build.MANUFACTURER);
            String brand = orReal(customBrand, Build.BRAND);
            String device = orReal(customDevice, Build.DEVICE);
            return new String[]{manufacturer, brand, customModel.trim(), device};
        }
        for (String[] preset : PROFILES) {
            if (preset[0].equals(id)) {
                return new String[]{preset[1], preset[2], preset[3], preset[4]};
            }
        }
        return null;
    }

    private static String orReal(String value, String real) {
        return value == null || value.trim().isEmpty() ? real : value.trim();
    }

    private static void setField(String name, String value) {
        if (value == null) return;
        try {
            Field field = Build.class.getField(name);
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
