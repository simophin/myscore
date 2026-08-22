package dev.fanchao.myscore

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fanchao.myscore.data.PageLayoutPreference
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfIntentTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun pdfViewIntentOpensReaderEndToEnd() {
        val context = ApplicationProvider.getApplicationContext<MyScoreApplication>()
        val pdf = File(context.cacheDir, "External Score.pdf")
        val document = PdfDocument()
        try {
            repeat(4) { pageIndex ->
                document.startPage(
                    PdfDocument.PageInfo.Builder(600, 800, pageIndex + 1).create(),
                ).also(document::finishPage)
            }
            FileOutputStream(pdf).use(document::writeTo)
        } finally {
            document.close()
        }
        val pdfUri = Uri.fromFile(pdf)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(pdfUri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runBlocking {
            context.settingsRepository.setReaderLayout(pdfUri.toString(), PageLayoutPreference.Auto)
        }

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            composeRule.onNodeWithText("External Score").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page scrubber, page 1 of 4")
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    setProgress(1f)
                }
            composeRule.onNodeWithContentDescription("Page 2").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page scrubber, page 2 of 4")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    setProgress(0f)
                }
            composeRule.onNodeWithContentDescription("Page 1").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Reader options").performClick()
            composeRule.onNodeWithText("4 pages").assertIsDisplayed()
            composeRule.onNodeWithText("Layout: Two pages").performClick()
            composeRule.onNodeWithContentDescription("Page scrubber, pages 1–2 of 4")
                .assertIsDisplayed()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("Pages 1–2")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Pages 1–2").performTouchInput { doubleClick() }
            composeRule.onNodeWithContentDescription("Pages 1–2").performTouchInput { swipeLeft() }
            composeRule.onNodeWithContentDescription("Page scrubber, pages 1–2 of 4")
                .assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Pages 1–2").performTouchInput { doubleClick() }
            composeRule.onNodeWithContentDescription("Pages 1–2").performTouchInput { swipeLeft() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("Page scrubber, pages 3–4 of 4")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Page scrubber, pages 3–4 of 4")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    setProgress(0f)
                }
            composeRule.onNodeWithContentDescription("Reader options").performClick()
            composeRule.onNodeWithText("Layout: Single page").performClick()
            composeRule.onNodeWithContentDescription("Reader options").performClick()
            composeRule.onNodeWithText("Layout: Single page").assertIsDisplayed()
            pressBack()
            scenario.recreate()
            composeRule.onNodeWithContentDescription("Reader options").performClick()
            composeRule.onNodeWithText("Layout: Single page").assertIsDisplayed()
            pressBack()
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { doubleClick() }
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { swipeLeft() }
            composeRule.onNodeWithContentDescription("Page 1").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { doubleClick() }
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { swipeLeft() }
            composeRule.onNodeWithContentDescription("Page 2").assertIsDisplayed()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("Page scrubber, page 2 of 4")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Page 2").performTouchInput { click() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("Reader options")
                    .fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithContentDescription("Reader options").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Page scrubber, page 2 of 4").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Page 2").performTouchInput { click() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("Reader options")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Reader options").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page scrubber, page 2 of 4").assertIsDisplayed()
            pressBack()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("MyScore").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            scenario.close()
            runBlocking {
                context.settingsRepository.setReaderLayout(pdfUri.toString(), PageLayoutPreference.Auto)
            }
        }
    }

    @Test
    fun appIsAdvertisedAsAPdfShareTarget() {
        val context = ApplicationProvider.getApplicationContext<MyScoreApplication>()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
        }

        val matchingPackages = context.packageManager
            .queryIntentActivities(shareIntent, 0)
            .map { it.activityInfo.packageName }

        assertTrue(context.packageName in matchingPackages)
    }
}
