package br.com.fluxo.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast of the text and mark pairs the UI draws, for both palettes. Pure maths: needs no emulator state
 * and no composition, so a palette edit that makes a pair unreadable fails here.
 */
class ContrastTest {
    @Test fun helperMatchesKnownValues() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 1e-9)
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 1e-9)
        assertEquals(1.0, contrastRatio(Color(0xFF3FA7BF), Color(0xFF3FA7BF)), 1e-9)
        // #767676 on white is the classic AA threshold grey (4.54:1).
        assertEquals(4.54, contrastRatio(Color(0xFF767676), Color.White), 0.01)
        assertEquals(contrastRatio(Color(0xFF14232B), Color(0xFFFBFCFC)), contrastRatio(Color(0xFFFBFCFC), Color(0xFF14232B)), 1e-12)
    }

    private fun palettes() = listOf("light" to FluxoPalette.Light, "dark" to FluxoPalette.Dark)

    private fun atLeast(min: Double, name: String, fg: Color, bg: Color, palette: String) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$palette: $name is %.2f:1, needs $min:1".format(ratio), ratio >= min)
    }

    @Test fun textPairsReachAaInBothPalettes() {
        for ((name, p) in palettes()) {
            fun text(label: String, fg: Color, bg: Color) = atLeast(4.5, label, fg, bg, name)
            text("ink on paper", p.ink, p.paper)
            text("ink on soft tile", p.ink, p.soft)
            text("mute on paper", p.mute, p.paper)
            text("mute on soft tile", p.mute, p.soft)
            text("on-hero on hero", p.onHero, p.hero)
            text("hero secondary text on hero", p.onPetrolSoft, p.hero)
            text("ticket title on ticket", p.ink, p.pale)
            text("ticket detail on ticket", p.mute, p.pale)
            text("capture strip text", p.onStrip, p.strip)
            text("automatic / bank badge", p.onBadge, p.badge)
            text("manual badge", p.ink, p.soft)
            text("selected chip", p.onInk, p.ink)
            text("unselected chip", p.ink, p.paper)
            text("review chip", p.onAmber, p.amber)
            text("amber button text", p.onAmber, p.amber)
            text("primary button", p.onInk, p.ink)
            text("income on paper", p.income, p.paper)
            text("income on tile", p.income, p.soft)
            text("expense on paper", p.expense, p.paper)
            text("expense on tile", p.expense, p.soft)
            text("accent text on paper", p.petrol, p.paper)
            text("nav label on pill", p.onPetrol, p.petrol)
            text("dark petrol badge", p.onPetrolDark, p.petrolDark)
            text("light petrol badge", p.onPetrolLight, p.petrolLight)
            text("Estado block", p.onIncome, p.income)
            text("budget overflow pill", p.onExpense, p.expense)
        }
    }

    @Test fun darkMarksReachThreeToOneOnTheirSurfaces() {
        val p = FluxoPalette.Dark
        for (surface in listOf(p.paper, p.soft)) {
            for ((i, color) in p.chartSeries.withIndex()) atLeast(3.0, "chart series #$i", color, surface, "dark")
            atLeast(3.0, "amber mark", p.amber, surface, "dark")
            atLeast(3.0, "income mark", p.income, surface, "dark")
            atLeast(3.0, "expense mark", p.expense, surface, "dark")
            atLeast(3.0, "neutral grey", p.gray, surface, "dark")
            atLeast(3.0, "education", p.education, surface, "dark")
            atLeast(3.0, "shopping", p.shopping, surface, "dark")
            atLeast(3.0, "services", p.services, surface, "dark")
        }
        atLeast(3.0, "chip outline", p.chipBorder, p.paper, "dark")
        atLeast(3.0, "ticket stripe", p.amber, p.pale, "dark")
        atLeast(3.0, "live dot on strip", p.live, p.strip, "dark")
    }

    @Test fun paletteInterpolationEndsAreExact() {
        assertEquals(FluxoPalette.Light, lerpPalette(FluxoPalette.Light, FluxoPalette.Dark, 0f))
        assertEquals(FluxoPalette.Dark, lerpPalette(FluxoPalette.Light, FluxoPalette.Dark, 1f))
        val mid = lerpPalette(FluxoPalette.Light, FluxoPalette.Dark, .5f)
        assertTrue(mid.paper != FluxoPalette.Light.paper && mid.paper != FluxoPalette.Dark.paper)
    }
}
