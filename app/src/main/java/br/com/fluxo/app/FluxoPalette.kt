package br.com.fluxo.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/** User choice for the app appearance; [SYSTEM] follows the device night mode. Stored locally, never exported. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Complete set of colour roles. [FluxoColors] reads the current instance, so adding a role here is enough to make
 * it available everywhere; [Light] mirrors the approved prototype p1 and [Dark] the "Petróleo escuro" direction (p5).
 * Every text/background pair used by the UI is checked by `ContrastTest` for both instances.
 */
internal data class FluxoPalette(
    val isDark: Boolean,
    /** Outer ground behind the app surface. */
    val stage: Color,
    /** Main app surface. */
    val paper: Color,
    /** Primary text; also the fill of high-emphasis controls (drawn with [onInk]). */
    val ink: Color,
    /** Text and icons on an [ink] fill. */
    val onInk: Color,
    val mute: Color,
    val line: Color,
    val soft: Color,
    val pale: Color,
    /** Accent petrol: nav pill, bars, links, badges. Always readable as text on [paper] and [soft]. */
    val petrol: Color,
    val onPetrol: Color,
    val petrolDark: Color,
    val onPetrolDark: Color,
    val petrolMid: Color,
    val petrolLight: Color,
    val onPetrolLight: Color,
    val petrolSoft: Color,
    /** Fill of the hero block, which stays the strongest petrol surface of the screen. */
    val hero: Color,
    val onHero: Color,
    /** Secondary text and sparkline stroke on [hero]. */
    val onPetrolSoft: Color,
    val income: Color,
    val onIncome: Color,
    val expense: Color,
    val onExpense: Color,
    val amber: Color,
    val onAmber: Color,
    val live: Color,
    val gray: Color,
    /** Capture strip fill and its text. */
    val strip: Color,
    val onStrip: Color,
    /** Automatic / bank badge fill and text. */
    val badge: Color,
    val onBadge: Color,
    /** Outline of unselected chips and secondary buttons. */
    val chipBorder: Color,
    /** Thumb of a checked switch, drawn on [income]. */
    val thumb: Color,
    val scrim: Color,
    val education: Color,
    val shopping: Color,
    val services: Color,
    val amberContainer: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
) {
    /** Ordered chart palette: petrol steps first, amber for the highlighted slice. */
    val chartSeries: List<Color> get() = listOf(petrolLight, petrol, amber, petrolMid, petrolDark, petrolSoft)

    companion object {
        val Light = FluxoPalette(
            isDark = false,
            stage = Color(0xFFE9EFF1), paper = Color(0xFFFBFCFC),
            ink = Color(0xFF14232B), onInk = Color.White,
            mute = Color(0xFF56676F), line = Color(0xFFDCE5E8),
            soft = Color(0xFFE3EFF3), pale = Color(0xFFD2E6EC),
            petrol = Color(0xFF0B5C73), onPetrol = Color.White,
            petrolDark = Color(0xFF084457), onPetrolDark = Color.White,
            petrolMid = Color(0xFF1F6F84), petrolLight = Color(0xFF7FB5C2), onPetrolLight = Color(0xFF14232B), petrolSoft = Color(0xFFB5D3DA),
            hero = Color(0xFF0B5C73), onHero = Color.White, onPetrolSoft = Color(0xFFCFE6EC),
            income = Color(0xFF17794A), onIncome = Color.White,
            expense = Color(0xFFBF3F17), onExpense = Color.White,
            amber = Color(0xFFD9962B), onAmber = Color(0xFF14232B),
            live = Color(0xFF5FD39A), gray = Color(0xFFA3A8B0),
            strip = Color(0xFF14232B), onStrip = Color.White,
            badge = Color(0xFF14232B), onBadge = Color.White,
            chipBorder = Color(0xFF14232B), thumb = Color.White, scrim = Color.Black,
            education = Color(0xFF3F8EA3), shopping = Color(0xFF9CC4CE), services = Color(0xFF4B7380),
            amberContainer = Color(0xFFF6E3BD),
            errorContainer = Color(0xFFF6DCD2), onErrorContainer = Color(0xFF5E1A05),
        )

        val Dark = FluxoPalette(
            isDark = true,
            stage = Color(0xFF041920), paper = Color(0xFF062028),
            ink = Color(0xFFEAF2F4), onInk = Color(0xFF062028),
            mute = Color(0xFF9DB4BC), line = Color(0xFF1D4753),
            soft = Color(0xFF0B2E39), pale = Color(0xFF123C49),
            petrol = Color(0xFF3FA7BF), onPetrol = Color(0xFF062028),
            petrolDark = Color(0xFF2E7F95), onPetrolDark = Color.White,
            petrolMid = Color(0xFF3A8FA5), petrolLight = Color(0xFF7FB5C2), onPetrolLight = Color(0xFF062028), petrolSoft = Color(0xFFBFDCE3),
            hero = Color(0xFF0F4C5F), onHero = Color.White, onPetrolSoft = Color(0xFFCFE6EC),
            income = Color(0xFF4CC38A), onIncome = Color(0xFF062028),
            expense = Color(0xFFF0825A), onExpense = Color(0xFF062028),
            amber = Color(0xFFE3A53A), onAmber = Color(0xFF062028),
            live = Color(0xFF5FD39A), gray = Color(0xFF8A99A0),
            strip = Color(0xFF123C49), onStrip = Color(0xFFEAF2F4),
            badge = Color(0xFF1D4753), onBadge = Color(0xFFEAF2F4),
            chipBorder = Color(0xFF6C98A5), thumb = Color(0xFF062028), scrim = Color.Black,
            education = Color(0xFF8CC0CD), shopping = Color(0xFFA6CBD4), services = Color(0xFF5A8D9B),
            amberContainer = Color(0xFF4A3813),
            errorContainer = Color(0xFF4D2316), onErrorContainer = Color(0xFFFFD9CC),
        )
    }
}

/** Linear blend of two palettes, used to animate a theme switch; [FluxoPalette.isDark] snaps at the midpoint. */
internal fun lerpPalette(a: FluxoPalette, b: FluxoPalette, t: Float): FluxoPalette = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> FluxoPalette(
        isDark = if (t < .5f) a.isDark else b.isDark,
        stage = lerp(a.stage, b.stage, t), paper = lerp(a.paper, b.paper, t),
        ink = lerp(a.ink, b.ink, t), onInk = lerp(a.onInk, b.onInk, t),
        mute = lerp(a.mute, b.mute, t), line = lerp(a.line, b.line, t),
        soft = lerp(a.soft, b.soft, t), pale = lerp(a.pale, b.pale, t),
        petrol = lerp(a.petrol, b.petrol, t), onPetrol = lerp(a.onPetrol, b.onPetrol, t),
        petrolDark = lerp(a.petrolDark, b.petrolDark, t), onPetrolDark = lerp(a.onPetrolDark, b.onPetrolDark, t),
        petrolMid = lerp(a.petrolMid, b.petrolMid, t), petrolLight = lerp(a.petrolLight, b.petrolLight, t), onPetrolLight = lerp(a.onPetrolLight, b.onPetrolLight, t),
        petrolSoft = lerp(a.petrolSoft, b.petrolSoft, t),
        hero = lerp(a.hero, b.hero, t), onHero = lerp(a.onHero, b.onHero, t),
        onPetrolSoft = lerp(a.onPetrolSoft, b.onPetrolSoft, t),
        income = lerp(a.income, b.income, t), onIncome = lerp(a.onIncome, b.onIncome, t),
        expense = lerp(a.expense, b.expense, t), onExpense = lerp(a.onExpense, b.onExpense, t),
        amber = lerp(a.amber, b.amber, t), onAmber = lerp(a.onAmber, b.onAmber, t),
        live = lerp(a.live, b.live, t), gray = lerp(a.gray, b.gray, t),
        strip = lerp(a.strip, b.strip, t), onStrip = lerp(a.onStrip, b.onStrip, t),
        badge = lerp(a.badge, b.badge, t), onBadge = lerp(a.onBadge, b.onBadge, t),
        chipBorder = lerp(a.chipBorder, b.chipBorder, t), thumb = lerp(a.thumb, b.thumb, t),
        scrim = lerp(a.scrim, b.scrim, t),
        education = lerp(a.education, b.education, t), shopping = lerp(a.shopping, b.shopping, t),
        services = lerp(a.services, b.services, t),
        amberContainer = lerp(a.amberContainer, b.amberContainer, t),
        errorContainer = lerp(a.errorContainer, b.errorContainer, t),
        onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, t),
    )
}

/**
 * Snapshot-state holder behind [FluxoColors]. [FluxoVisualTheme] writes the (possibly mid-animation) palette here
 * before its content composes; composables and draw blocks that read a token are re-run when it changes.
 */
internal object FluxoPaletteHolder {
    var current: FluxoPalette by mutableStateOf(FluxoPalette.Light)
}

/** WCAG 2.x relative luminance of an sRGB colour (alpha ignored). */
internal fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG 2.x contrast ratio between two opaque colours, from 1.0 (identical) to 21.0 (black on white). */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
