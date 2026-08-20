package dev.fanchao.myscore.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerTest {
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
}
