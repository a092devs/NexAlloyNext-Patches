package io.github.nexalloy.morphe.youtube.layout.hide.ambientmode

import android.view.View
import app.morphe.extension.youtube.patches.AmbientModePatch as ExtensionAmbientModePatch
import app.morphe.extension.youtube.settings.Settings
import io.github.nexalloy.FindMethodFunc
import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.hookMethod
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findFieldFromToString
import io.github.nexalloy.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexalloy.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexalloy.morphe.youtube.insertLiteralOverride
import io.github.nexalloy.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexalloy.patch
import org.luckypray.dexkit.util.InstanceUtil
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

/**
 * CinematicContainerRenderer field 3 (presentation style) DYNAMIC_BLURRED:
 * the background takes the colors of the video.
 */
private const val PRESENTATION_STYLE_DYNAMIC_BLURRED = 5

/**
 * Default CinematicContainerRenderer proto, with presentation style DYNAMIC_BLURRED and
 * field 11 (ambient mode on by default) = true.
 */
private const val DEFAULT_CINEMATIC_CONTAINER_RENDERER =
    "EgsQ94CqmwMdAAAAABIGEPeAqpsDEgsQgICAmAMdAACAPxgFIpEBCICAgPgPEICAgPgPGg0IkE4QiCcdAACAPyAUJQAAIEAoATUA" +
            "AIA/PQAAgkNFmpmZP00AAMA/VQAAgD9YAWpSDQAAIEAVAAAgQB0AACBAJQAAIEAyPBIREPeAqpsDHQAAAAAI94CqmwMSERCA" +
            "gICYAx0AAIA/CICAgJgDCAIdAAAAPyUAAAA/LQAAgD81AACAPw=="

/**
 * Anchors pulled at patch time from the cinematic container (upstream commit 0eda16c28),
 * converted to reflection members before hooking the renderer state constructor.
 */
internal object CinematicContainerForceAnchors {
    @Volatile var rendererClassDescriptor: String? = null

    @Volatile var hasBitsFieldDescriptor: String? = null

    @Volatile var styleHasBitMask: Int = 0

    @Volatile var styleFieldDescriptor: String? = null

    @Volatile var configFieldDescriptor: String? = null

    @Volatile var enabledByDefaultFieldDescriptor: String? = null

    @Volatile var stateTypeDescriptor: String? = null

    @Volatile var waitingStateFieldDescriptor: String? = null

    @Volatile var loadedStateFieldDescriptor: String? = null
}

private val resolveCinematicContainerForceAnchors: FindMethodFunc = findMethodDirect {
    val rendererData = CinematicContainerRendererFingerprint.match().method
    val rendererType = rendererData.declaredClass!!.descriptor
    CinematicContainerForceAnchors.rendererClassDescriptor = rendererType

    val styleMatches = cinematicContainerPresentationStyleFingerprint(rendererType).match().instructionMatches
    CinematicContainerForceAnchors.hasBitsFieldDescriptor = styleMatches[0].instruction.fieldRef!!.descriptor
    CinematicContainerForceAnchors.styleHasBitMask = styleMatches[1].instruction.literal!!.toInt()
    CinematicContainerForceAnchors.styleFieldDescriptor = styleMatches[3].instruction.fieldRef!!.descriptor

    val configField = cinematicContainerConfigFingerprint(rendererType)
        .match().instructionMatches.last().instruction.fieldRef!!
    CinematicContainerForceAnchors.configFieldDescriptor = configField.descriptor

    val enabledByDefaultField = cinematicContainerEnabledByDefaultFingerprint(configField.typeName)
        .match().instructionMatches[3].instruction.fieldRef!!
    CinematicContainerForceAnchors.enabledByDefaultFieldDescriptor = enabledByDefaultField.descriptor

    val withStateToString = CinematicContainerRendererWithStateFingerprint.match().method
    val stateType = withStateToString.findFieldFromToString("rendererLoadedState=").typeName
    CinematicContainerForceAnchors.stateTypeDescriptor = stateType

    val stateMatches = cinematicContainerRendererLoadedStateFingerprint(stateType).match().instructionMatches
    CinematicContainerForceAnchors.waitingStateFieldDescriptor = stateMatches[1].instruction.fieldRef!!.descriptor
    CinematicContainerForceAnchors.loadedStateFieldDescriptor = stateMatches[3].instruction.fieldRef!!.descriptor

    rendererData
}

private fun PatchExecutor.installForceAmbientModeRenderer() {
    val anchors = CinematicContainerForceAnchors

    val rendererClass = classLoader.loadClass(anchors.rendererClassDescriptor!!)
    val hasBitsField = InstanceUtil.getFieldInstance(classLoader, DexField(anchors.hasBitsFieldDescriptor!!))
    val styleField = InstanceUtil.getFieldInstance(classLoader, DexField(anchors.styleFieldDescriptor!!))
    val configField = InstanceUtil.getFieldInstance(classLoader, DexField(anchors.configFieldDescriptor!!))
    val enabledByDefaultField =
        InstanceUtil.getFieldInstance(classLoader, DexField(anchors.enabledByDefaultFieldDescriptor!!))

    // The static self-typed field holds the default renderer instance.
    val defaultRendererField = rendererClass.declaredFields.first {
        Modifier.isStatic(it.modifiers) && it.type == rendererClass
    }

    val stateClass = classLoader.loadClass(anchors.stateTypeDescriptor!!)
    val waitingState = InstanceUtil.getFieldInstance(classLoader, DexField(anchors.waitingStateFieldDescriptor!!))
        .get(null)!!
    val loadedState = InstanceUtil.getFieldInstance(classLoader, DexField(anchors.loadedStateFieldDescriptor!!))
        .get(null)!!

    // Runtime side note: the class of the wrapped renderer differs per host flavor, so both
    // methods are found from the argument's runtime class instead of being field constants.
    val rendererWithStateClass = classLoader.loadClass(
        CinematicContainerRendererWithStateFingerprint.dexMethod.declaredClassName
    )
    val constructor = rendererWithStateClass.declaredConstructors.first {
        it.parameterTypes.size == 2 && it.parameterTypes[0] == stateClass
    }
    val optionalClass = constructor.parameterTypes[1]
    val optionalPresentMethod = optionalClass.getMethod("isPresent")
    val optionalGetMethod = optionalClass.getMethod("get")
    val optionalOfMethod = optionalClass.getMethod("of", Object::class.java)
    val parseFromMethod = rendererClass.getDeclaredMethod("parseFrom", ByteArray::class.java)

    constructor.hookMethod {
        before { param ->
            // Mutating the arguments replicates upstream's p1/p2 reassignment at the very
            // start of the constructor, before the constructor stores them.
            runCatching {
                val state = param.args[0]
                val optionalArgument = param.args[1] ?: return@runCatching

                // patch_forceAmbientModeRenderer: only forced while ambient mode is not disabled
                // in the settings and the state is still waiting for a /next response.
                if (ExtensionAmbientModePatch.disableAmbientMode(true) && state === waitingState) {
                    var renderer = if (optionalPresentMethod.invoke(optionalArgument) as Boolean) {
                        optionalGetMethod.invoke(optionalArgument)
                    } else {
                        // No /next response yet: fall back on the default renderer.
                        val data =
                            android.util.Base64.decode(DEFAULT_CINEMATIC_CONTAINER_RENDERER, android.util.Base64.DEFAULT)
                        parseFromMethod.invoke(null, data)
                    }

                    // Force presentation style DYNAMIC_BLURRED, and mark the corresponding
                    // has-bit so the style survives serialization.
                    hasBitsField.setInt(
                        renderer, hasBitsField.getInt(renderer) or anchors.styleHasBitMask
                    )
                    styleField.setInt(renderer, PRESENTATION_STYLE_DYNAMIC_BLURRED)

                    // Only the default: the flyout menu toggle is saved separately and still applies.
                    val config = configField.get(renderer)
                    if (config != null) {
                        enabledByDefaultField.setBoolean(config, true)
                    }

                    param.args[1] = optionalOfMethod.invoke(null, renderer)
                }

                // patch_getAmbientModeRendererState: any present renderer (possibly the forced
                // one just created above) reports as loaded.
                if (optionalPresentMethod.invoke(param.args[1]) as Boolean) {
                    param.args[0] = loadedState
                }
            }
        }
    }
}

/**
 * Adds options to bypass power saving restrictions for Ambient mode and disable it entirely
 * or in fullscreen.
 *
 * Ported from Morphe v1.45.0 ambientModePatch and v1.48.0 (upstream commit 0eda16c28):
 *  - "disable Ambient mode" forces the ambient experiment flags (45376186, 45372814) to
 *    follow the Morphe setting,
 *  - "Bypass Ambient mode restrictions" makes power save mode reports false;
 *  - "Disable Ambient mode in fullscreen" restores an opaque background after the player view
 *    lays out, reverting YouTube's fullscreen dim;
 *  - the /next response renderer is always forced to DYNAMIC_BLURRED and enabled by default,
 *    unless ambient mode is disabled in the Morphe settings.
 */
val AmbientMode = patch(
    name = "Ambient mode",
    description = "Forces Ambient mode on and adds options to bypass power saving restrictions " +
            "for Ambient mode and disable it entirely or in fullscreen.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_ambient_mode_screen",
            preferences = setOf(
                SwitchPreference("morphe_bypass_ambient_mode_restrictions", summary = true),
                SwitchPreference("morphe_disable_ambient_mode"),
                SwitchPreference("morphe_disable_fullscreen_ambient_mode"),
            ),
        ),
    )

    // Force ambient mode, unless it is disabled in the Morphe settings.
    // The flyout menu toggle of the player still turns it on and off.
    // 21.30+ inlines the flag lookups and must patch 4 places for each flag; each flag is on
    // if ambient mode is not disabled.
    insertLiteralOverride(45376186L) { _ -> ExtensionAmbientModePatch.disableAmbientMode(true) }
    insertLiteralOverride(45372814L) { _ -> ExtensionAmbientModePatch.disableAmbientMode(true) }

    // The /next response can have no CinematicContainerRenderer, a static color style
    // (the background does not change with the video, issue #3783) or ambient mode off by
    // default. Override what the server decides.
    runCatching {
        ::resolveCinematicContainerForceAnchors.dexMethod
        installForceAmbientModeRenderer()
    }.onFailure {
        println("NexAlloy: Force ambient mode hook not installed: ${it.message}")
    }

    // Bypass Ambient mode restrictions: while the setting is on, pretend the device is not in
    // power save mode (power save is exactly what gates Ambient mode off by default).
    runCatching {
        DexMethod("Landroid/os/PowerManager;->isPowerSaveMode()Z").hookMethod {
            before { param ->
                if (Settings.BYPASS_AMBIENT_MODE_RESTRICTIONS.get()) {
                    param.result = false
                }
            }
        }
    }

    // Disable the fullscreen dim by restoring an opaque background after the player lays out.
    runCatching {
        SetFullScreenBackgroundColorFingerprint.hookMethod {
            after { param ->
                val view = param.thisObject as? View ?: return@after
                val color = ExtensionAmbientModePatch.getFullScreenBackgroundColor(0)
                if (color != 0) {
                    view.setBackgroundColor(color)
                }
            }
        }
    }
}
