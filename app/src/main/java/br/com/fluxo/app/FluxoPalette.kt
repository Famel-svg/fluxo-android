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
 * it available everywhere; [Light] and [Dark] are the "Índigo Pix" palette (indigo primary, honey review accent); role names keep the old petrol wording.
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
            stage = Color(0xFFEFEEF8), paper = Color(0xFFF8F7FC),
            ink = Color(0xFF15162B), onInk = Color.White,
            mute = Color(0xFF55577A), line = Color(0xFFD9D8EA),
            soft = Color(0xFFEFEEF8), pale = Color(0xFFE1E0F2),
            petrol = Color(0xFF3A3FC0), onPetrol = Color.White,
            petrolDark = Color(0xFF262A8A), onPetrolDark = Color.White,
            petrolMid = Color(0xFF5157CF), petrolLight = Color(0xFFA9ACFF), onPetrolLight = Color(0xFF15162B), petrolSoft = Color(0xFFC9CBF5),
            hero = Color(0xFF2E3AAE), onHero = Color.White, onPetrolSoft = Color(0xFFD9DBFF),
            income = Color(0xFF0F7A5A), onIncome = Color.White,
            expense = Color(0xFFBE3E0B), onExpense = Color.White,
            amber = Color(0xFFF2C14E), onAmber = Color(0xFF15162B),
            live = Color(0xFF4ADE9A), gray = Color(0xFFA3A5B8),
            strip = Color(0xFF15162B), onStrip = Color.White,
            badge = Color(0xFF15162B), onBadge = Color.White,
            chipBorder = Color(0xFF15162B), thumb = Color.White, scrim = Color.Black,
            education = Color(0xFF6C8BE0), shopping = Color(0xFFB7B9F0), services = Color(0xFF5A5F9E),
            amberContainer = Color(0xFFFBEBC4),
            errorContainer = Color(0xFFF6DCD2), onErrorContainer = Color(0xFF5E1A05),
        )

        val Dark = FluxoPalette(
            isDark = true,
            stage = Color(0xFF090A16), paper = Color(0xFF0E0F1E),
            ink = Color(0xFFEDEEFA), onInk = Color(0xFF0E0F1E),
            mute = Color(0xFFA3A5C8), line = Color(0xFF2C2E52),
            soft = Color(0xFF171830), pale = Color(0xFF212345),
            petrol = Color(0xFFA9ACFF), onPetrol = Color(0xFF12134A),
            petrolDark = Color(0xFF5C61E0), onPetrolDark = Color.White,
            petrolMid = Color(0xFF7F84F0), petrolLight = Color(0xFFA9ACFF), onPetrolLight = Color(0xFF0E0F1E), petrolSoft = Color(0xFFC9CBF5),
            hero = Color(0xFF3A3FC0), onHero = Color.White, onPetrolSoft = Color(0xFFD9DBFF),
            income = Color(0xFF4ADE9A), onIncome = Color(0xFF0E0F1E),
            expense = Color(0xFFFF9A6B), onExpense = Color(0xFF0E0F1E),
            amber = Color(0xFFF2C14E), onAmber = Color(0xFF0E0F1E),
            live = Color(0xFF4ADE9A), gray = Color(0xFF8A8CA8),
            strip = Color(0xFF212345), onStrip = Color(0xFFEDEEFA),
            badge = Color(0xFF2C2E52), onBadge = Color(0xFFEDEEFA),
            chipBorder = Color(0xFF7F84C8), thumb = Color(0xFF0E0F1E), scrim = Color.Black,
            education = Color(0xFF8FA8F0), shopping = Color(0xFFB7B9F0), services = Color(0xFF7A7FC0),
            amberContainer = Color(0xFF4A3A12),
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
