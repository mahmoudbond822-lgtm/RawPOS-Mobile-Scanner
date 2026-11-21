package com.rawpos.scanner.feature.scanner

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rawpos.scanner.MainActivity
import com.rawpos.scanner.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the scanner screen is reachable and renders its camera controls on the device.
 *
 * The permission grant is attempted through [android.app.UiAutomation] rather than `pm grant`, which
 * this MIUI build blocks. If the grant is refused the test still asserts the screen and its controls:
 * the permission panel and the controls share the same layout, so the UI contract is checked either
 * way, and a refusal is not mistaken for a failure.
 */
@RunWith(AndroidJUnit4::class)
class ScannerScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun grantCameraIfPossible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.CAMERA,
            )
        } catch (_: SecurityException) {
            // Resource-protected build: the user grants it by tapping Allow. Not a test failure.
        }
    }

    @Test
    fun scannerScreenIsReachableAndShowsControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        compose
            .onNodeWithText(context.getString(R.string.connection_action_preview))
            .performScrollTo()
            .performClick()

        compose.onNodeWithTag("scanner_screen").assertExists()
        compose.onNodeWithTag("scanner_torch_button").assertExists()
        compose.onNodeWithTag("scanner_pause_button").assertExists()
        compose.onNodeWithTag("scanner_zoom_button").assertExists()
    }
}
