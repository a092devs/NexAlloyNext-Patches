package io.github.nexalloy.morphe.youtube.layout.buttons.navigation

import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Fingerprint
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexalloy.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.OpcodesFilter
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.fingerprint
import io.github.nexalloy.morphe.literal
import io.github.nexalloy.morphe.methodCall
import io.github.nexalloy.morphe.opcode
import io.github.nexalloy.morphe.string
import io.github.nexalloy.morphe.strings


internal object CreatePivotBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf(
        "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;",
        "Landroid/widget/TextView;",
        "Ljava/lang/CharSequence;",
    ),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_VIRTUAL,
        Opcode.RETURN_VOID,
    ),
)

internal object AutoHideNavigationBarOnFeedScrollingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/support/v7/widget/RecyclerView;", "I", "I"),
    filters = listOf(
        methodCall("Landroid/view/ViewConfiguration;->get(Landroid/content/Context;)Landroid/view/ViewConfiguration;"),
        methodCall(
            smali = "Landroid/view/ViewConfiguration;->getScaledTouchSlop()I",
            location = MatchAfterWithin(5)
        )
    )
)

internal object AutoHideNavigationBarOnDismissMiniplayerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        literal(2),
        methodCall(
            smali = "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/util/Set;->iterator()Ljava/util/Iterator;",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/util/Iterator;->hasNext()Z",
            location = MatchAfterWithin(5)
        ),
        methodCall(
            smali = "Ljava/util/Iterator;->next()Ljava/lang/Object;",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * Draws the blurred background of the translucent navigation bar. If the canvas is hardware
 * accelerated, a copy of the watch layout, with the player, is drawn into a blurred render node,
 * then the bar color is drawn over it.
 */
internal object NavigationBarBlurDrawFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        filters = listOf(
            string("frostedGlassRenderNode"),
        )
    ),
    name = "draw",
    returnType = "V",
    parameters = listOf("Landroid/graphics/Canvas;"),
    filters = listOf(
        methodCall(smali = "Landroid/graphics/Canvas;->isHardwareAccelerated()Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        methodCall(smali = "Landroid/view/View;->draw(Landroid/graphics/Canvas;)V")
    )
)

internal object BrowseResponseTabsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL, AccessFlags.DECLARED_SYNCHRONIZED),
    returnType = "L",
    parameters = listOf(),
    filters = listOf(
        literal(58173949L),
        methodCall(name = "stream"),
        opcode(Opcode.IPUT_OBJECT)
    )
)

internal object PivotBarItemProtoFactoryFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "L", "Z"),
    filters = listOf(
        literal(117501096L)
    )
)

internal object PivotBarRendererFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("L"),
    returnType = "Lj$/util/Optional;",
    filters = listOf(
        literal(117501096L),
        opcode(Opcode.IF_NE),
        opcode(Opcode.CHECK_CAST),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT_RANGE,
            definingClass = "this",
            name = "<init>",
            returnType = "V"
        ),
        opcode(Opcode.RETURN_OBJECT)
    )
)

internal object PivotBarRendererListFingerprint : Fingerprint(
    parameters = listOf("L"),
    returnType = "V",
    filters = listOf(
        opcode(Opcode.IGET_OBJECT),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L"),
            returnType = "L"
        ),
        opcode(Opcode.IPUT_OBJECT),
        literal(45633821L),
    )
)

internal val pivotBarRendererListMethod = findMethodDirect {
    PivotBarRendererListFingerprint.instructionMatches[1].instruction.methodRef!!
}
