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
import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.smali.ExternalLabel
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

internal object AppStateGetterIsForegroundedFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/realtime/common/appstate/AppStateGetter;",
    name = "isAppForegrounded",
    returnType = "Z",
    parameters = emptyList(),
)

internal object RealtimeUpdateAppStateFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/realtimeclient/RealtimeClientManager;",
    name = "updateAppState",
    returnType = "V",
    parameters = listOf("Z"),
)

internal object RealtimeUpdateAppStateInternalFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/realtimeclient/RealtimeClientManager;",
    name = "updateAppStateInternal",
    returnType = "V",
    parameters = listOf("Z"),
)

internal object PresenceKillSwitchExactFingerprint : Fingerprint(
    definingClass = "LX/06hr;",
    name = "A02",
    returnType = "Z",
    parameters = listOf("Lcom/instagram/common/session/UserSession;"),
)

internal object PresenceKillSwitchFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Lcom/instagram/common/session/UserSession;"),
    filters = listOf(
        literal(36325433230445512L),
    ),
)

internal object DgwPresencePublishExactFingerprint : Fingerprint(
    definingClass = "LX/06mn;",
    name = "A06",
    returnType = "V",
    parameters = listOf("LX/06jA;"),
)

internal object DgwPresencePublishFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        string("request_id: "),
        string(", presence_status:"),
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
            // 1. Force AppStateSyncer & NotifGatewayAppStateSyncer to report Background (1).
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

            // 2. Make native JNI AppStateGetter.isAppForegrounded() return false when hidden.
            AppStateGetterIsForegroundedFingerprint.matchOrNull()?.method?.apply {
                val freeReg = findFreeRegister(0, parameterRegisterStart(this))
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->hideOnlineStatus()Z
                    move-result v$freeReg
                    if-eqz v$freeReg, :piko_allow_fg
                    const/4 v$freeReg, 0x0
                    return v$freeReg
                    """.trimIndent(),
                    ExternalLabel("piko_allow_fg", getInstruction(0)),
                )
            }

            // 3. Force RealtimeClientManager.updateAppState(Z) & updateAppStateInternal(Z) to false.
            listOfNotNull(
                RealtimeUpdateAppStateFingerprint.matchOrNull(),
                RealtimeUpdateAppStateInternalFingerprint.matchOrNull(),
            ).forEach { match ->
                match.method.apply {
                    val isForegroundReg = parameterRegisterStart(this) + 1
                    addInstructions(
                        0,
                        """
                        invoke-static {v$isForegroundReg}, $PREF_DESCRIPTOR->resolveForegroundBool(Z)Z
                        move-result v$isForegroundReg
                        """.trimIndent(),
                    )
                }
            }

            // 4. Force the DGW/MQTT presence kill-switch MobileConfig check to return true.
            val killSwitchMethod = PresenceKillSwitchExactFingerprint.matchOrNull()?.method
                ?: PresenceKillSwitchFingerprint.matchOrNull()?.method
            killSwitchMethod?.apply {
                val freeReg = findFreeRegister(0, parameterRegisterStart(this))
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->hideOnlineStatus()Z
                    move-result v$freeReg
                    if-eqz v$freeReg, :piko_allow_presence_mc
                    const/4 v$freeReg, 0x1
                    return v$freeReg
                    """.trimIndent(),
                    ExternalLabel("piko_allow_presence_mc", getInstruction(0)),
                )
            }

            // 5. Belt-and-braces: short-circuit the DGW presence publisher method directly.
            val dgwPublishMethod = DgwPresencePublishExactFingerprint.matchOrNull()?.method
                ?: DgwPresencePublishFingerprint.matchOrNull()?.method
            dgwPublishMethod?.apply {
                val freeReg = findFreeRegister(0, parameterRegisterStart(this))
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->hideOnlineStatus()Z
                    move-result v$freeReg
                    if-eqz v$freeReg, :piko_allow_dgw_publish
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_allow_dgw_publish", getInstruction(0)),
                )
            }

            enableSettings("hideOnlineStatus")
        }
    }
