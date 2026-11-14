/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.deviceSpoof

import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DEVICE_SPOOF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.option
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val deviceSpoofPatch =
    patch(
        name = "Device spoofing",
        description = "Reports the device model chosen here (or later in the Piko settings) " +
            "to Instagram, for features gated to other phones such as HDR playback quality.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        val deviceModel by option(
            key = "deviceModel",
            default = "galaxy_s26_ultra",
            values = mapOf(
                "Real device (no spoofing)" to "real",
                "Pixel 9 Pro XL" to "pixel_9_pro_xl",
                "Pixel 10 Pro XL" to "pixel_10_pro_xl",
                "Galaxy S26 Ultra" to "galaxy_s26_ultra",
                "Galaxy S25 Ultra" to "galaxy_s25_ultra",
                "Realme P3 Pro" to "realme_p3_pro",
                "Custom" to "custom",
            ),
            title = "Device model",
            description = "The device Instagram is told this phone is. It is baked into the " +
                "patched app and applies from the very first launch, before any login. " +
                "It can later be changed in the Piko settings inside the app.",
            required = true,
        )

        val customManufacturer by stringOption(
            key = "customManufacturer",
            default = "",
            title = "Custom manufacturer",
            description = "Only used when Device model is Custom. Example: Google",
            required = false,
        )

        val customBrand by stringOption(
            key = "customBrand",
            default = "",
            title = "Custom brand",
            description = "Only used when Device model is Custom. Example: google",
            required = false,
        )

        val customModel by stringOption(
            key = "customModel",
            default = "",
            title = "Custom model",
            description = "Only used when Device model is Custom. Example: Pixel 9 Pro XL",
            required = false,
        )

        val customDevice by stringOption(
            key = "customDevice",
            default = "",
            title = "Custom device codename",
            description = "Only used when Device model is Custom. Example: komodo",
            required = false,
        )

        execute {
            // Bake the chosen model into the app as an asset. The extension
            // reads it at startup until the user changes the device in the
            // in-app Piko settings, so the spoof is already active on the
            // first launch and survives a fresh login or cleared settings.
            val config = buildString {
                appendLine("profile=${deviceModel ?: "galaxy_s26_ultra"}")
                appendLine("custom_manufacturer=${customManufacturer ?: ""}")
                appendLine("custom_brand=${customBrand ?: ""}")
                appendLine("custom_model=${customModel ?: ""}")
                appendLine("custom_device=${customDevice ?: ""}")
            }
            resourceFile("assets/piko/device_spoof.properties", copy = false).writeText(config)

            // InstagramAppShell.onCreate, right after super: the identity is
            // rewritten before Instagram's own startup code reads it.
            instagramInitHook.fingerprint.method.apply {
                val superIndex = instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
                addInstruction(
                    superIndex + 1,
                    "invoke-static {}, $DEVICE_SPOOF_DESCRIPTOR->applyEarly()V",
                )
            }
            enableSettings("deviceSpoof")
        }
    }
