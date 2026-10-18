package io.github.nexalloy.nexfusion.gboard

import android.text.TextPaint
import android.widget.TextView

/**
 * Runtime helper for candidate strip emoji rendering.
 *
 * Ensures emoji glyphs in suggestion candidates render at full opacity (alpha 255)
 * instead of adopting secondary candidate translucent text styles that make color
 * emoji bitmap glyphs appear dull and washed out.
 *
 * Ported from Akshayykadam/PixelBoard (GPL-3.0).
 */
object GboardCandidateEmojiRuntime {

    /**
     * Checks if the character sequence contains one or more emoji characters.
     */
    fun containsEmoji(cs: CharSequence?): Boolean {
        if (cs.isNullOrEmpty()) return false
        val len = cs.length
        for (i in 0 until len) {
            val c = cs[i]
            // High/low surrogate pairs (SMP Plane 1: modern emojis U+1F000 - U+1FAFF)
            if (Character.isSurrogate(c)) return true
            // BMP emoji and symbol blocks (Dingbats, Misc Symbols, Arrows, Enclosed)
            if (c.code in 0x203C..0x3299) return true
            // Variation Selector-16 (emoji presentation selector)
            if (c.code == 0xFE0F) return true
        }
        return false
    }

    /**
     * Adjusts the candidate TextView TextPaint to full opacity if the candidate text contains emojis.
     */
    fun adjustCandidatePaint(textView: TextView?, paint: TextPaint? = textView?.paint) {
        if (textView == null || paint == null) return
        val text = textView.text ?: return
        if (containsEmoji(text)) {
            paint.color = paint.color or 0xFF000000.toInt()
            paint.alpha = 255
        }
    }
}

