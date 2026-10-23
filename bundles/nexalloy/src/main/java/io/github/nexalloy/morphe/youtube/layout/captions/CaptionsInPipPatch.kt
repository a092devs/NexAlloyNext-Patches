package io.github.nexalloy.morphe.youtube.layout.captions

import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.patches.CaptionsInPipPatch
import io.github.nexalloy.FindMethodFunc
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.parameters
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexalloy.morphe.youtube.misc.playservice.is_20_29_or_greater
import io.github.nexalloy.morphe.youtube.misc.playservice.is_21_02_or_greater
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import org.luckypray.dexkit.util.InstanceUtil
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field

// Port of upstream CaptionsInPipPatch.kt (upstream commit 6c817e7da). Upstream mutates the local
// register that carries the isPipActive value into the player state constructor. That is a
// bytecode edit, so the runtime port instead mutates the player state's isPipActive field while
// the captions overlay checks its visibility, which the overlay reads to decide if it is shown.

private const val CAPTIONS_OVERLAY_NAME = "player_overlay_caption"

/**
 * The anchors needed to hide the player state's picture-in-picture from the captions overlay and
 * to detect re-setting of the current caption track. Resolved once through DexKit by
 * [resolveCaptionsInPipAnchors] and converted to reflection members by
 * [resolveCaptionsInPipMembers].
 */
internal object CaptionsInPipAnchors {
    /** Smali type descriptor of the player state class. */
    @Volatile lateinit var stateTypeDescriptor: String

    /** Ordered field names the state's toString() prints. */
    @Volatile lateinit var fieldNames: List<String>

    /** Constructor call argument index of isPipActive, excluding the state instance itself. */
    @Volatile var pipArgIndex: Int = -1

    /** Field of the player state class holding whether picture-in-picture is active. */
    @Volatile lateinit var pipFieldDescriptor: String

    /** Descriptor of the player state constructor. */
    @Volatile lateinit var stateCtorDescriptor: String

    /** Smali type descriptor of the captions overlay implementation class. */
    @Volatile lateinit var captionsOverlayClassDescriptor: String

    /** Interface method names the player overlay layouts call for a visibility check. */
    @Volatile lateinit var overlayCheckNames: Set<String>

    /** 21.02+ only: field of the caption track event handler holding the current track. */
    @Volatile var currentTrackFieldDescriptor: String? = null

    /** 21.02+ only: descriptor of the method that sets the caption track. */
    @Volatile var setTrackMethodDescriptor: String? = null
}

/**
 * Runtime state of the resolved pipeline, read from hook callbacks.
 */
internal object CaptionsInPipRuntime {
    @Volatile var pipArgIndex: Int = -1

    @Volatile var pipField: Field? = null

    /** Whether the fieldTen has been checked against the pip constructor argument. */
    @Volatile var fieldChecked = false

    /** If false, the positional field resolution failed and the field must not be mutated. */
    @Volatile var fieldVerified = false
}

/**
 * Resolves and populates [CaptionsInPipAnchors] within the DexKit query context.
 */
private val resolveCaptionsInPipAnchors: FindMethodFunc = findMethodDirect {
    // The state's toString() prints its fields as a ';'-separated name list.
    val stateToStringMethod = PlayerOverlayStateToStringFingerprint.match().method
    val stateTypeDescriptor = stateToStringMethod.declaredClass!!.descriptor
    val fieldNames = stateToStringMethod.instructions.firstOrNull {
        it.string?.contains("isPipActive") == true
    }?.string!!.split(';')
    // Argument position of isPipActive when creating the player state. The constructor call
    // includes the state instance itself in register 0, which filters out here.
    val pipArgIndex = fieldNames.indexOf("isPipActive")
    if (pipArgIndex < 0) {
        throw Exception("Unexpected player state fields: $fieldNames")
    }

    // Creates the player state and asks an overlay if it is shown. More than one overlay layout
    // does this. The matched instruction data is only inspected here; the visible behavior is
    // changed by mutating the state passed to the captions overlay's visibility check.
    val visibilityFingerprint = Fingerprint(
        filters = listOf(
            methodCall(definingClass = stateTypeDescriptor, name = "<init>"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                parameters = listOf(stateTypeDescriptor),
                returnType = "Z",
                location = MatchAfterImmediately(),
            ),
        ),
    )
    val visibilityMatches = visibilityFingerprint.matchAllOrNull()
        ?: throw Exception("Could not resolve player overlay visibility methods")

    // The captions overlay implementation is found by the class holding its name, since only it
    // returns the constant 'player_overlay_caption'.
    val captionsOverlayClassDescriptor =
        CaptionsOverlayNameFingerprint.match().method.declaredClass!!.descriptor

    // The overlay layouts call one interface method for the visibility check; the captions
    // overlay implementation carries a same-named override of that check.
    val overlayCheckNames = visibilityMatches.mapNotNull {
        it.instructionMatches[1].instruction.methodRef?.name
    }.toSet()

    // Resolve the state constructor and map the isPipActive argument to its field. Positionally
    // (like upstream does), assuming the constructor assigns the boolean parameters to fields in
    // parameter order, and verifying that correspondence at runtime.
    val stateCtorMethod =
        visibilityMatches.first().instructionMatches.first().instruction.methodRef!!
    val ctorParams = stateCtorMethod.parameters
    if (ctorParams.size != fieldNames.size) {
        throw Exception("Unexpected player state constructor parameters: $ctorParams, fields: $fieldNames")
    }
    if (ctorParams[pipArgIndex] != "Z") {
        throw Exception("Unexpected player state isPipActive parameter type: ${ctorParams[pipArgIndex]}")
    }
    val pipBooleanOrdinal = fieldNames.subList(0, pipArgIndex).withIndex()
        .count { (index, _) -> ctorParams[index] == "Z" }
    var seenBooleans = 0
    var pipFieldDescriptor: String? = null
    for (instruction in stateCtorMethod.instructions) {
        if (Opcode.fromInt(instruction.opcode) == Opcode.IPUT_BOOLEAN) {
            if (seenBooleans == pipBooleanOrdinal) {
                pipFieldDescriptor = instruction.fieldRef?.descriptor
                break
            }
            seenBooleans++
        }
    }
    val pipFieldDescriptorResolved =
        pipFieldDescriptor ?: throw Exception("Could not resolve isPipActive field of the player state")

    // The current caption track can be set again when entering or leaving picture-in-picture,
    // which clears the caption on screen until the next one.
    var currentTrackFieldDescriptor: String? = null
    var setTrackMethodDescriptor: String? = null
    if (is_21_02_or_greater) {
        val trackTypeDescriptor =
            CaptionTrackIsDisableOptionFingerprint.match().method.declaredClass!!.descriptor

        val trackEventMatch = getCaptionTrackEventFingerprint(trackTypeDescriptor).match()
        // The first filter match reads the current track field of the event handler.
        currentTrackFieldDescriptor =
            trackEventMatch.instructionMatches.first().instruction.fieldRef!!.descriptor
        // The last filter match sets the new track.
        setTrackMethodDescriptor =
            trackEventMatch.instructionMatches.last().instruction.methodRef!!.descriptor
    }

    CaptionsInPipAnchors.stateTypeDescriptor = stateTypeDescriptor
    CaptionsInPipAnchors.fieldNames = fieldNames
    CaptionsInPipAnchors.pipArgIndex = pipArgIndex
    CaptionsInPipAnchors.pipFieldDescriptor = pipFieldDescriptorResolved
    CaptionsInPipAnchors.stateCtorDescriptor = stateCtorMethod.descriptor
    CaptionsInPipAnchors.captionsOverlayClassDescriptor = captionsOverlayClassDescriptor
    CaptionsInPipAnchors.overlayCheckNames = overlayCheckNames
    CaptionsInPipAnchors.currentTrackFieldDescriptor = currentTrackFieldDescriptor
    CaptionsInPipAnchors.setTrackMethodDescriptor = setTrackMethodDescriptor

    stateToStringMethod
}

/**
 * Converts an "Lcom/example/Class;" smali descriptor to a binary class name.
 */
private fun classBinaryName(descriptor: String) =
    descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

/**
 * Converts the anchor descriptors into reflection members with the app class loader, and hooks
 * the captions overlay visibility check plus the caption track setter.
 */
private fun PatchExecutor.resolveCaptionsInPipMembers() {
    val anchors = CaptionsInPipAnchors

    CaptionsInPipRuntime.pipArgIndex = anchors.pipArgIndex
    CaptionsInPipRuntime.pipField = runCatching {
        InstanceUtil.getFieldInstance(classLoader, DexField(anchors.pipFieldDescriptor))
    }.getOrNull()?.also { it.isAccessible = true }
        ?: throw Exception("Could not resolve isPipActive field: ${anchors.pipFieldDescriptor}")

    // Verify the resolved field matches the pip constructor argument once a state is created,
    // since the positional mapping relies on constructor field assignment order.
    DexMethod(anchors.stateCtorDescriptor).hookMethod {
        after { param ->
            if (!CaptionsInPipRuntime.fieldChecked) {
                CaptionsInPipRuntime.fieldChecked = true
                val expected = param.args.getOrNull(CaptionsInPipRuntime.pipArgIndex) as? Boolean
                val actual = runCatching {
                    CaptionsInPipRuntime.pipField?.getBoolean(param.thisObject)
                }.getOrNull()
                CaptionsInPipRuntime.fieldVerified = expected != null && actual == expected
                if (!CaptionsInPipRuntime.fieldVerified) {
                    Logger.printException {
                        "Player state isPipActive field verification failed"
                    }
                }
            }
        }
    }

    // The captions overlay implementation carries the visibility check override. Resolve it from
    // the loaded implementation class, constrained to the same-named interface method the overlay
    // layouts call.
    val stateClass = classLoader.loadClass(classBinaryName(anchors.stateTypeDescriptor))
    val captionsOverlayClass =
        classLoader.loadClass(classBinaryName(anchors.captionsOverlayClassDescriptor))
    val visibleCandidates = captionsOverlayClass.declaredMethods.filter {
        it.parameterTypes.size == 1 && it.parameterTypes[0] == stateClass &&
                it.returnType == java.lang.Boolean.TYPE
    }
    val visibilityMethod = visibleCandidates.singleOrNull()
        ?: visibleCandidates.filter { it.name in anchors.overlayCheckNames }.singleOrNull()
        ?: throw Exception("Could not resolve captions overlay visibility method: $visibleCandidates")

    // Report the state's picture-in-picture as inactive while the captions overlay checks its
    // visibility, so hiding overlays in picture-in-picture does not hide the captions.
    visibilityMethod.hookMethod {
        before { param ->
            val runtime = CaptionsInPipRuntime
            if (!runtime.fieldVerified) {
                return@before
            }
            val state = param.args.firstOrNull() ?: return@before
            runCatching {
                val field = runtime.pipField ?: return@before
                if (field.getBoolean(state) &&
                    !CaptionsInPipPatch.isPipActive(CAPTIONS_OVERLAY_NAME)
                ) {
                    field.setBoolean(state, false)
                }
            }
        }
    }

    anchors.setTrackMethodDescriptor?.let { setTrackDescriptor ->
        val currentTrackField = runCatching {
            InstanceUtil.getFieldInstance(
                classLoader, DexField(anchors.currentTrackFieldDescriptor!!)
            )
        }.getOrNull()?.also { it.isAccessible = true }
        if (currentTrackField == null) {
            Logger.printException {
                "Could not resolve current caption track field: ${anchors.currentTrackFieldDescriptor}"
            }
            return
        }

        DexMethod(setTrackDescriptor).hookMethod {
            before { param ->
                if (CaptionsInPipPatch.isCurrentTrack(
                        param.args.firstOrNull(), currentTrackField.get(param.thisObject)
                    )
                ) {
                    // Do not set the same caption track again; the caption stays on screen.
                    param.result = Unit
                }
            }
        }
    }
}

val CaptionsInPip = patch(
    name = "Captions in picture-in-picture",
    description = "Adds an option to show captions in picture-in-picture mode.",
) {
    dependsOn(VersionCheck)

    // Caption settings. Used to organize all caption related settings together,
    // mirroring the upstream 'morphe_captions_screen' preference group.
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_captions_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(SwitchPreference("morphe_captions_in_pip")),
        )
    )

    if (!is_20_29_or_greater) {
        // TODO: Versions before 20.29 hide overlays using the player type
        //  (upstream getPlayerTypeOverlayVisibilityFingerprint). Not ported,
        //  as the target device runs YouTube >= 21.05.
        return@patch
    }

    ::resolveCaptionsInPipAnchors.dexMethod

    resolveCaptionsInPipMembers()
}
