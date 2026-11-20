package com.rawpos.scanner.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ContrastTest {

    @Test
    fun `light body text meets 4 point 5 to 1`() {
        assertContrast(
            foreground = RawPosTokens.LightTextPrimary,
            background = RawPosTokens.LightSurface,
            minimum = 4.5,
            label = "light textPrimary on surface",
        )
    }

    @Test
    fun `dark body text meets 4 point 5 to 1`() {
        assertContrast(
            foreground = RawPosTokens.DarkTextPrimary,
            background = RawPosTokens.DarkSurface,
            minimum = 4.5,
            label = "dark textPrimary on surface",
        )
    }

    @Test
    fun `light outline meets 3 to 1`() {
        assertContrast(
            foreground = RawPosTokens.LightOutline,
            background = RawPosTokens.LightSurface,
            minimum = 3.0,
            label = "light outline on surface",
        )
    }

    @Test
    fun `dark outline meets 3 to 1`() {
        assertContrast(
            foreground = RawPosTokens.DarkOutline,
            background = RawPosTokens.DarkSurface,
            minimum = 3.0,
            label = "dark outline on surface",
        )
    }

    @Test
    fun `primary button labels meet 4 point 5 to 1 in both themes`() {
        assertContrast(
            foreground = RawPosTokens.LightOnPrimary,
            background = RawPosTokens.LightPrimary,
            minimum = 4.5,
            label = "light onPrimary on primary",
        )
        assertContrast(
            foreground = RawPosTokens.DarkOnPrimary,
            background = RawPosTokens.DarkPrimary,
            minimum = 4.5,
            label = "dark onPrimary on primary",
        )
    }

    private fun assertContrast(
        foreground: Color,
        background: Color,
        minimum: Double,
        label: String,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label expected >= $minimum but was ${"%.2f".format(ratio)}",
            ratio >= minimum,
        )
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val lighter = max(luminance(a), luminance(b))
        val darker = min(luminance(a), luminance(b))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
