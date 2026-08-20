package dev.fanchao.myscore

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
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
            document.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create()).also(document::finishPage)
            document.startPage(PdfDocument.PageInfo.Builder(600, 800, 2).create()).also(document::finishPage)
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
            composeRule.onNodeWithText("2 pages").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page layout: Auto").performClick()
            composeRule.onNodeWithText("Single page").performClick()
            composeRule.onNodeWithContentDescription("Page layout: Single page").assertIsDisplayed()
            scenario.recreate()
            composeRule.onNodeWithContentDescription("Page layout: Single page").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { doubleClick() }
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { swipeLeft() }
            composeRule.onNodeWithContentDescription("Page 1").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { doubleClick() }
            composeRule.onNodeWithContentDescription("Page 1").performTouchInput { swipeLeft() }
            composeRule.onNodeWithContentDescription("Page 2").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Enter full screen").performClick()
            composeRule.onNodeWithContentDescription("Exit full screen").assertIsDisplayed()
            pressBack()
            composeRule.onNodeWithText("External Score").assertIsDisplayed()
            pressBack()
            composeRule.onNodeWithText("MyScore").assertIsDisplayed()
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
