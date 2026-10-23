package io.github.nexalloy.patches

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

class SettingsSortRegressionTest {

    @Test
    fun `test sortPreferenceGroups handles null safely via reflection`() {
        val clazz = Class.forName("app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment")
        val sortMethod: Method = clazz.getDeclaredMethod("sortPreferenceGroups", android.preference.PreferenceGroup::class.java)
        sortMethod.isAccessible = true

        // Calling with null group must return gracefully without throwing NullPointerException
        assertDoesNotThrow {
            sortMethod.invoke(null, null)
        }
    }

    @Test
    fun `test sortPreferenceGroups method exists and is accessible`() {
        val clazz = Class.forName("app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment")
        val method = clazz.declaredMethods.firstOrNull { it.name == "sortPreferenceGroups" }
        assertNotNull(method, "sortPreferenceGroups must exist on AbstractPreferenceFragment")
    }
}

