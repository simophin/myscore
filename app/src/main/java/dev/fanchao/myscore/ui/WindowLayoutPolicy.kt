package dev.fanchao.myscore.ui

internal fun shouldUseNavigationRail(widthPx: Int, heightPx: Int): Boolean = widthPx > heightPx

internal fun pagesPerPane(windowWidthDp: Float): Int = if (windowWidthDp >= 840f) 2 else 1
