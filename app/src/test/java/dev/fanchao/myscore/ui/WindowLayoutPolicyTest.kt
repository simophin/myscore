package dev.fanchao.myscore.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutPolicyTest {
    @Test fun `landscape windows use a navigation rail`() {
        assertTrue(shouldUseNavigationRail(widthPx = 1600, heightPx = 900))
    }

    @Test fun `portrait and square windows keep bottom navigation`() {
        assertFalse(shouldUseNavigationRail(widthPx = 800, heightPx = 1200))
        assertFalse(shouldUseNavigationRail(widthPx = 800, heightPx = 800))
    }

    @Test fun `expanded reader windows show paired pages`() {
        assertEquals(1, pagesPerPane(839.9f))
        assertEquals(2, pagesPerPane(840f))
    }
}
