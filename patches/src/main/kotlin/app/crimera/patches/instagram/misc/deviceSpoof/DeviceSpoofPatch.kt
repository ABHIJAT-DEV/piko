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
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val deviceSpoofPatch =
    patch(
        name = "Device spoofing",
        description = "Reports the device model chosen in the Piko settings to Instagram, " +
            "for features gated to other phones such as HDR playback quality.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // InstagramAppShell.onCreate, right after super: the identity is
            // rewritten before Instagram's own startup code reads it. The
            // spoof itself is off by default and configured in Piko settings.
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
