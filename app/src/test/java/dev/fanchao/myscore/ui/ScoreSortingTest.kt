package dev.fanchao.myscore.ui

import dev.fanchao.myscore.data.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreSortingTest {
    private val folder = LibraryEntry("folder", "Beethoven", true, 0, 100)
    private val bach = LibraryEntry("bach", "Bach.pdf", false, 100, 200)
    private val chopin = LibraryEntry("chopin", "chopin.pdf", false, 200, 300)

    @Test
    fun nameSortingIsCaseInsensitiveAndKeepsFoldersFirst() {
        val entries = listOf(chopin, bach, folder)

        assertEquals(
            listOf("Beethoven", "Bach.pdf", "chopin.pdf"),
            sortLibraryEntries(entries, ScoreSortOrder.NameAscending).map { it.name },
        )
        assertEquals(
            listOf("Beethoven", "chopin.pdf", "Bach.pdf"),
            sortLibraryEntries(entries, ScoreSortOrder.NameDescending).map { it.name },
        )
    }

    @Test
    fun modifiedSortingSupportsBothDirectionsAndKeepsFoldersFirst() {
        val entries = listOf(bach, folder, chopin)

        assertEquals(
            listOf("Beethoven", "chopin.pdf", "Bach.pdf"),
            sortLibraryEntries(entries, ScoreSortOrder.NewestFirst).map { it.name },
        )
        assertEquals(
            listOf("Beethoven", "Bach.pdf", "chopin.pdf"),
            sortLibraryEntries(entries, ScoreSortOrder.OldestFirst).map { it.name },
        )
    }
}
