package dev.fanchao.myscore.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerTest {
    @Test
    fun paperModeLeavesDarkNotationUntouched() {
        assertEquals(
            0xFF101010.toInt(),
            paperTonePixel(0xFF101010.toInt(), 0xFFF2E7C9.toInt()),
        )
    }

    @Test
    fun paperModeWarmsNearWhitePaperPixels() {
        assertEquals(
            0xFFF3EAD0.toInt(),
            paperTonePixel(0xFFFCFCFC.toInt(), 0xFFF2E7C9.toInt()),
        )
    }

    @Test
    fun pagerIsDisabledWhenCurrentSinglePageIsZoomed() {
        assertFalse(
            isPagerScrollEnabled(
                currentPane = 2,
                pagesPerPane = 1,
                zoomedPageIndices = setOf(2),
            ),
        )
    }

    @Test
    fun pagerIsDisabledWhenEitherPageInCurrentTwoPagePaneIsZoomed() {
        assertFalse(
            isPagerScrollEnabled(
                currentPane = 1,
                pagesPerPane = 2,
                zoomedPageIndices = setOf(3),
            ),
        )
    }

    @Test
    fun zoomOnPrefetchedPageDoesNotDisableCurrentPane() {
        assertTrue(
            isPagerScrollEnabled(
                currentPane = 1,
                pagesPerPane = 2,
                zoomedPageIndices = setOf(4),
            ),
        )
    }

    @Test
    fun pagerIsEnabledAgainWhenCurrentPaneHasNoZoomedPages() {
        assertTrue(
            isPagerScrollEnabled(
                currentPane = 2,
                pagesPerPane = 1,
                zoomedPageIndices = emptySet(),
            ),
        )
    }

    @Test
    fun zoomOffsetIsResetWhenScaleIsOne() {
        assertEquals(
            0f,
            constrainZoomOffset(offset = 120f, containerSize = 400, scale = 1f),
        )
    }

    @Test
    fun zoomOffsetIsClampedToScaledViewport() {
        assertEquals(
            200f,
            constrainZoomOffset(offset = 350f, containerSize = 400, scale = 2f),
        )
        assertEquals(
            -200f,
            constrainZoomOffset(offset = -350f, containerSize = 400, scale = 2f),
        )
    }
}
