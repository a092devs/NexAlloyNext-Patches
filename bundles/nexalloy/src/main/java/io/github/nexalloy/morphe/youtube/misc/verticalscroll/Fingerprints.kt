package io.github.nexalloy.morphe.youtube.misc.verticalscroll

import io.github.nexalloy.morphe.findMethodDirect
import org.luckypray.dexkit.query.enums.StringMatchType

val canScrollVerticallyFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass { className(".SwipeRefreshLayout", StringMatchType.EndsWith) }
            paramTypes()
            addInvoke {
                name = "canScrollVertically"
            }
        }
    }.first()
}
