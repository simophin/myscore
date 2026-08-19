package dev.fanchao.myscore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.fanchao.myscore.data.PageLayoutPreference
import org.junit.Rule
import org.junit.Test

class AdaptivePageLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun autoUsesTheComposeWidgetsMeasuredWidth() {
        composeRule.setContent {
            Column {
                AdaptivePageLayout(
                    preference = PageLayoutPreference.Auto,
                    modifier = Modifier.requiredWidth(839.dp).height(40.dp),
                ) { Text("below breakpoint: $it") }
                AdaptivePageLayout(
                    preference = PageLayoutPreference.Auto,
                    modifier = Modifier.requiredWidth(840.dp).height(40.dp),
                ) { Text("at breakpoint: $it") }
            }
        }

        composeRule.onNodeWithText("below breakpoint: 1").fetchSemanticsNode()
        composeRule.onNodeWithText("at breakpoint: 2").fetchSemanticsNode()
    }

    @Test
    fun explicitOverridesIgnoreTheComposeWidgetsMeasuredWidth() {
        composeRule.setContent {
            Column {
                AdaptivePageLayout(
                    preference = PageLayoutPreference.Single,
                    modifier = Modifier.requiredWidth(1_200.dp).height(40.dp),
                ) { Text("forced single: $it") }
                AdaptivePageLayout(
                    preference = PageLayoutPreference.Two,
                    modifier = Modifier.requiredWidth(320.dp).height(40.dp),
                ) { Text("forced two: $it") }
            }
        }

        composeRule.onNodeWithText("forced single: 1").fetchSemanticsNode()
        composeRule.onNodeWithText("forced two: 2").fetchSemanticsNode()
    }
}
