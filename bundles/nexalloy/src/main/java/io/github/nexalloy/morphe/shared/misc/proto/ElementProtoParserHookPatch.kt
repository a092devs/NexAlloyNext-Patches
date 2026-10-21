package io.github.nexalloy.morphe.shared.misc.proto

import io.github.nexalloy.PatchExecutor
import io.github.nexalloy.patch


/**
 * Shared factory for the element proto parser hook used by both YouTube and YT Music.
 *
 * Hooks the generic proto parser method so that patched extensions can inspect (and modify)
 * the raw byte buffer of every parsed proto element.
 *
 * @param readsResourcesWithParser Whether the inline resources of the responses, such as the
 *                                 templates and the JavaScript modules, can be read with the
 *                                 element proto parser (YouTube 21.40+). Evaluated lazily inside
 *                                 the patch body so version flags are already set.
 * @param block Callback to add app-specific dependencies, such as versionCheckPatch.
 */
internal fun createElementProtoParserHookPatch(
    readsResourcesWithParser: () -> Boolean = { false },
    block: PatchExecutor.() -> Unit = {},
) = patch(
    description = "Hook to modify the proto message class, which can only be accessed through reflection.",
) {
    block()

    NewElementProtoParserFingerprint.hookMethod {
        after {
            var result = it.result as ByteArray?
            hooks.forEach {
                result = it(result)
            }
            it.result = result
        }
    }

    if (readsResourcesWithParser()) {
        // On YouTube 21.40+, inline resources (templates, JS modules) are read through the
        // element proto parser via a helper method that bypasses element hooks. Those bytes
        // are not filtered by AdsFilter unless the helper is also hooked. Bytecode-level
        // instruction replacement for that helper requires the bytecodePatch DSL; this
        // placeholder keeps the API ready for that migration.
    }
}

private val hooks = mutableListOf<(ByteArray?) -> ByteArray?>()
fun hookElement(hook: (ByteArray?) -> ByteArray?) {
    hooks.add(hook)
}