package io.github.nexalloy.nexfusion.gboard

import android.util.Log
import java.lang.reflect.Field

/**
 * Runtime interceptor for Rambler Agentic Dictation voice text results.
 * Ported from Akshay Kadam's PixelBoard (GPL-3.0).
 *
 * When Rambler processes speech that resembles an instruction or question,
 * the backend server may classify the user's intent as an agentic command
 * (e.g. INTENT_COMPOSE = 4, INTENT_REWRITE = 3, INTENT_RAMBLE = 5) and generate
 * an AI answer into the committed text span while placing the user's verbatim
 * transcript into `action.d` (full_transcript).
 *
 * This runtime helper restores the user's verbatim transcript into the committed text span
 * and resets the intent to dictation (0), ensuring speech-to-text always types what was spoken
 * rather than an AI chatbot answer.
 */
object GboardRamblerDictationRuntime {

    private const val TAG = "NexAlloy"

    /**
     * Inspects incoming voice text spans and ensures the committed text is the verbatim
     * spoken transcript rather than an AI-generated prompt answer.
     *
     * @param voiceTextProto the voice text proto instance (Laajc in 18.4.1, Laaru in 18.5.3/18.5.4)
     */
    fun filterVoiceText(voiceTextProto: Any?) {
        if (voiceTextProto == null) return
        try {
            val spansField = findField(voiceTextProto.javaClass, "b") ?: return
            spansField.isAccessible = true
            val spansObj = spansField.get(voiceTextProto)
            if (spansObj !is List<*>) return

            for (span in spansObj) {
                if (span == null) continue

                // span has action field 'j'
                val actionField = findField(span.javaClass, "j") ?: continue
                actionField.isAccessible = true
                val action = actionField.get(span) ?: continue

                // action has transcript field 'd' (full_transcript)
                val transcriptField = findField(action.javaClass, "d") ?: continue
                transcriptField.isAccessible = true
                val transcriptObj = transcriptField.get(action)
                if (transcriptObj !is String || transcriptObj.isEmpty()) continue

                // action has intent field 'c' (int)
                val intentField = findField(action.javaClass, "c") ?: continue
                intentField.isAccessible = true
                val intent = intentField.getInt(action)

                // Intent 0 = DEFAULT_INTENT_DICTATION.
                // Intent 3 = INTENT_REWRITE, 4 = INTENT_COMPOSE, 5 = INTENT_RAMBLE.
                // When intent is non-zero, Gboard received an AI instruction result.
                // Overwrite the AI generated text with the user's verbatim transcript.
                if (intent != 0) {
                    val textSpanField = findField(span.javaClass, "c")
                    if (textSpanField != null) {
                        textSpanField.isAccessible = true
                        textSpanField.set(span, transcriptObj)
                        intentField.setInt(action, 0)
                        runCatching {
                            Log.i(TAG, "NexFusion: Restored verbatim dictation transcript and reset agentic intent ($intent -> 0): \"$transcriptObj\"")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NexFusion: Failed to filter voice text transcript", t)
        }
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
