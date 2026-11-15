/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton.inboxActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

internal object AppStateSyncerFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/realtime/common/appstate/AppStateSyncer;",
    name = "notifyForegroundStateChange",
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        opcode(Opcode.IGET),
    ),
)

internal object NotifGatewayAppStateSyncerFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/rti/notifgateway/NotifGatewayAppStateSyncer;",
    name = "notifyForegroundStateChange",
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        opcode(Opcode.IGET),
    ),
)

@Suppress("unused")
val hideOnlineStatusPatch =
    patch(
        name = "Hide online status",
        description = "Forces app presence state to backgrounded so others can't see when you're active",
        default = true,
    ) {
        dependsOn(settingsPatch, chatActionBarButtonPatch, inboxActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            listOfNotNull(
                AppStateSyncerFingerprint.match(),
                NotifGatewayAppStateSyncerFingerprint.matchOrNull(),
            ).forEach { match ->
                val iget = match.instructionMatches.first()
                val stateRegister = iget.getInstruction<TwoRegisterInstruction>().registerA
                match.method.addInstructions(
                    iget.index + 1,
                    """
                    invoke-static {v$stateRegister}, $PREF_DESCRIPTOR->resolveAppState(I)I
                    move-result v$stateRegister
                    """.trimIndent(),
                )
            }
            enableSettings("hideOnlineStatus")
        }
    }
