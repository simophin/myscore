package dev.fanchao.myscore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PageLayoutPreferenceTest {
    @Test fun `stored values round trip`() {
        PageLayoutPreference.entries.forEach { preference ->
            assertEquals(preference, PageLayoutPreference.fromStoredValue(preference.storedValue))
        }
    }

    @Test fun `missing or unknown stored values safely use auto`() {
        assertEquals(PageLayoutPreference.Auto, PageLayoutPreference.fromStoredValue(null))
        assertEquals(PageLayoutPreference.Auto, PageLayoutPreference.fromStoredValue("future-value"))
    }
}
