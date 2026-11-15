/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.roundedHeader

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode

private const val APPLY_SMALI =
    "invoke-static {p0}, $PATCHES_DESCRIPTOR/actionbar/RoundedHeader;->apply(Landroid/view/View;)V"

private val VIEW_INIT_PARAMETERS =
    listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I")

// Home header.
internal object MainFeedActionBarInitFingerprint : Fingerprint(
    definingClass = "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;",
    name = "<init>",
    returnType = "V",
    parameters = VIEW_INIT_PARAMETERS,
)

// Home header swaps its own background here (solid <-> floating over the feed).
internal object MainFeedFloatingChromeFingerprint : Fingerprint(
    definingClass = "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;",
    name = "setFloatingChrome",
    returnType = "V",
    parameters = listOf("Z"),
)

// Search / Explore header.
internal object ExploreActionBarInitFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/discovery/actionbar/ExploreActionBar;",
    name = "<init>",
    returnType = "V",
    parameters = VIEW_INIT_PARAMETERS,
)

// Explore header re-applies its own background here.
internal object ExploreActionBarRefreshFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/discovery/actionbar/ExploreActionBar;",
    name = "A02",
    returnType = "V",
    parameters = emptyList(),
)

// Profile header.
internal object ProfileActionBarInitFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/profile/actionbar/ProfileActionBar;",
    name = "<init>",
    returnType = "V",
    parameters = VIEW_INIT_PARAMETERS,
)

// The IGDS action bar: Direct inbox/thread headers and most generic screens
// (including Settings) are built on this single component.
internal object IgdsActionBarInitFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/igds/components/actionbar/IgdsActionBar;",
    name = "<init>",
    returnType = "V",
    parameters = VIEW_INIT_PARAMETERS,
)

private fun MutableMethod.addApplyBeforeReturns() {
    val implementation = implementation ?: return
    val returnIndices =
        implementation.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_VOID) index else null
        }
    // Insert from the end so earlier indices stay valid while mutating.
    returnIndices.asReversed().forEach { index -> addInstructions(index, APPLY_SMALI) }
}

@Suppress("unused")
val roundedHeaderPatch =
    patch(
        name = "Rounded top header",
        description = "Rounds the bottom corners of Instagram's own top header components " +
            "(Home, Search, Messages, Profile, Settings) with the PixelMusic tonal surface",
        default = true,
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            listOf(
                MainFeedActionBarInitFingerprint,
                MainFeedFloatingChromeFingerprint,
                ExploreActionBarInitFingerprint,
                ExploreActionBarRefreshFingerprint,
                ProfileActionBarInitFingerprint,
                IgdsActionBarInitFingerprint,
            ).forEach { fingerprint ->
                fingerprint.matchOrNull()?.method?.addApplyBeforeReturns()
            }
        }
    }
