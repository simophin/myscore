package dev.fanchao.myscore.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerTest {
    @Test
    fun paperModeBackgroundUsesPaperColorWhenEnabled() {
        assertEquals(
            Color(0xFFF2E7C9),
            paperModeBackgroundColor(enabled = true),
        )
    }

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
    fun pagerIsDisabledWhenCurrentPaneIsZoomed() {
        assertFalse(
            isPagerScrollEnabled(
                currentPane = 2,
                zoomedPaneIndices = setOf(2),
            ),
        )
    }

    @Test
    fun zoomOnPrefetchedPaneDoesNotDisableCurrentPane() {
        assertTrue(
            isPagerScrollEnabled(
                currentPane = 1,
                zoomedPaneIndices = setOf(2),
            ),
        )
    }

    @Test
    fun pagerIsEnabledAgainWhenCurrentPaneHasNoZoomedPages() {
        assertTrue(
            isPagerScrollEnabled(
                currentPane = 2,
                zoomedPaneIndices = emptySet(),
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

    @Test
    fun zoomGestureDeltaMatchesFingerMovementAtHigherScale() {
        assertEquals(
            30f,
            zoomGestureDeltaToOffsetDelta(delta = 10f, scale = 3f),
        )
    }

    @Test
    fun zoomGestureDeltaIsUnchangedAtBaseScale() {
        assertEquals(
            10f,
            zoomGestureDeltaToOffsetDelta(delta = 10f, scale = 1f),
        )
    }

    @Test
    fun scrubberPositionMapsAcrossTheWholeDocument() {
        assertEquals(0, pageForScrubberPosition(position = 0f, width = 400f, pageCount = 101))
        assertEquals(50, pageForScrubberPosition(position = 200f, width = 400f, pageCount = 101))
        assertEquals(100, pageForScrubberPosition(position = 400f, width = 400f, pageCount = 101))
    }

    @Test
    fun scrubberPositionIsClampedToTheDocument() {
        assertEquals(0, pageForScrubberPosition(position = -40f, width = 400f, pageCount = 12))
        assertEquals(11, pageForScrubberPosition(position = 600f, width = 400f, pageCount = 12))
        assertEquals(0, pageForScrubberPosition(position = 200f, width = 0f, pageCount = 12))
    }

    @Test
    fun scrubberPositionUsesTheVisibleTrackInsets() {
        assertEquals(
            0,
            pageForScrubberPosition(
                position = 12f,
                width = 424f,
                pageCount = 101,
                horizontalInset = 12f,
            ),
        )
        assertEquals(
            100,
            pageForScrubberPosition(
                position = 412f,
                width = 424f,
                pageCount = 101,
                horizontalInset = 12f,
            ),
        )
    }

    @Test
    fun shortDocumentsShowOneMarkerPerPage() {
        assertEquals(
            8,
            pageScrubberMarkerCount(
                pageCount = 8,
                availableWidth = 400f,
                minimumSpacing = 10f,
            ),
        )
    }

    @Test
    fun longDocumentsRespectMinimumMarkerSpacing() {
        assertEquals(
            41,
            pageScrubberMarkerCount(
                pageCount = 500,
                availableWidth = 400f,
                minimumSpacing = 10f,
            ),
        )
    }

    @Test
    fun twoPageScrubberMapsBothPagesToTheirContainingPane() {
        assertEquals(
            0,
            paneForScrubberPosition(
                position = 80f,
                width = 500f,
                pageCount = 6,
                pagesPerPane = 2,
            ),
        )
        assertEquals(
            1,
            paneForScrubberPosition(
                position = 300f,
                width = 500f,
                pageCount = 6,
                pagesPerPane = 2,
            ),
        )
    }

    @Test
    fun twoPageScrubberReportsTheVisibleSpread() {
        assertEquals(0..1, scrubberPageRange(paneIndex = 0, pagesPerPane = 2, pageCount = 6))
        assertEquals(2..3, scrubberPageRange(paneIndex = 1, pagesPerPane = 2, pageCount = 6))
    }

    @Test
    fun finalOddPageFormsASinglePagePane() {
        assertEquals(4..4, scrubberPageRange(paneIndex = 2, pagesPerPane = 2, pageCount = 5))
    }

    @Test
    fun scrubberTapDoesNotCrossTheDragThreshold() {
        assertFalse(
            isScrubberDrag(
                startPosition = 100f,
                currentPosition = 107f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun scrubberPreviewStartsAtTheDragThreshold() {
        assertTrue(
            isScrubberDrag(
                startPosition = 100f,
                currentPosition = 108f,
                touchSlop = 8f,
            ),
        )
    }
}
