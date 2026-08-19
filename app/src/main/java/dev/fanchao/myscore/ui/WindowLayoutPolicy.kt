package dev.fanchao.myscore.ui

import dev.fanchao.myscore.data.PageLayoutPreference

internal fun shouldUseNavigationRail(widthPx: Int, heightPx: Int): Boolean = widthPx > heightPx

internal fun pagesPerPane(availableWidthDp: Float, preference: PageLayoutPreference): Int = when (preference) {
    PageLayoutPreference.Auto -> if (availableWidthDp >= 840f) 2 else 1
    PageLayoutPreference.Single -> 1
    PageLayoutPreference.Two -> 2
}
