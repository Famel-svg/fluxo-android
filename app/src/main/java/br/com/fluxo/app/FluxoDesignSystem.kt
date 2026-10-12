package br.com.fluxo.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.Category

// ---------------------------------------------------------------------------------------------
// Colour tokens (light petrol palette from p1.html, dark "Petróleo escuro" from p5.html)
// ---------------------------------------------------------------------------------------------

/**
 * Theme-aware colour API. Each property reads the current [FluxoPalette] from [FluxoPaletteHolder] (snapshot state), so
 * composables and draw blocks that use a token follow theme changes; call sites keep the plain `FluxoColors.Ink` form.
 */
internal object FluxoColors {
    private val p: FluxoPalette get() = FluxoPaletteHolder.current

    /** True while the dark palette is (mostly) in effect. */
    val IsDark: Boolean get() = p.isDark
    /** Outer "stage" behind the app surface. */
    val Stage: Color get() = p.stage
    /** Main app surface. */
    val Paper: Color get() = p.paper
    /** Primary text and high-emphasis controls; draw text on an Ink fill with [OnInk]. */
    val Ink: Color get() = p.ink
    val OnInk: Color get() = p.onInk
    /** Secondary text. */
    val Mute: Color get() = p.mute
    /** Hairlines and quiet borders. */
    val Line: Color get() = p.line
    /** Soft tinted fill for tiles. */
    val Soft: Color get() = p.soft
    /** Slightly stronger tint for tickets and split tiles. */
    val Pale: Color get() = p.pale
    /** Main accent petrol: nav pill, bars, links, badges (readable as text on Paper and Soft in both themes). */
    val Petrol: Color get() = p.petrol
    /** Text and icons drawn on a solid [Petrol] fill. */
    val OnPetrol: Color get() = p.onPetrol
    /** Darkest petrol step. */
    val PetrolDark: Color get() = p.petrolDark
    val OnPetrolDark: Color get() = p.onPetrolDark
    /** Mid petrol step used for bars. */
    val PetrolMid: Color get() = p.petrolMid
    /** Light petrol step. */
    val PetrolLight: Color get() = p.petrolLight
    val OnPetrolLight: Color get() = p.onPetrolLight
    /** Palest petrol step used for the 5th chart category. */
    val PetrolSoft: Color get() = p.petrolSoft
    /** Fill of the hero block and of other solid petrol surfaces carrying [OnHero] text. */
    val Hero: Color get() = p.hero
    val OnHero: Color get() = p.onHero
    /** Sparkline stroke and secondary text on the hero. */
    val OnPetrolSoft: Color get() = p.onPetrolSoft
    /** Income / positive. */
    val Income: Color get() = p.income
    val OnIncome: Color get() = p.onIncome
    /** Expense / budget overflow. */
    val Expense: Color get() = p.expense
    val OnExpense: Color get() = p.onExpense
    /** The single warm accent: review, highlight. */
    val Amber: Color get() = p.amber
    val OnAmber: Color get() = p.onAmber
    /** "Capture live" indicator dot. */
    val Live: Color get() = p.live
    /** Neutral grey for "sem categoria". */
    val Gray: Color get() = p.gray
    /** Capture strip fill and text. */
    val Strip: Color get() = p.strip
    val OnStrip: Color get() = p.onStrip
    /** Automatic / bank badge fill and text. */
    val Badge: Color get() = p.badge
    val OnBadge: Color get() = p.onBadge
    /** Outline of unselected chips and secondary buttons. */
    val ChipBorder: Color get() = p.chipBorder
    /** Thumb of a checked switch. */
    val Thumb: Color get() = p.thumb
    val Scrim: Color get() = p.scrim

    /** Ordered chart palette: petrol steps first, amber for the highlighted slice. */
    val ChartSeries: List<Color> get() = p.chartSeries
}

/** Category dot / chart colour following the prototype: petrol steps plus amber for food. */
internal fun fluxoCategoryColor(category: Category): Color = when (category) {
    Category.FOOD -> FluxoColors.Amber
    Category.HOUSING -> FluxoColors.Petrol
    Category.TRANSPORT -> FluxoColors.PetrolMid
    Category.LEISURE -> FluxoColors.PetrolDark
    Category.HEALTH -> FluxoColors.PetrolSoft
    Category.SALARY -> FluxoColors.Income
    Category.UNCATEGORIZED -> FluxoColors.Gray
    Category.OTHER -> FluxoColors.PetrolLight
    Category.EDUCATION -> FluxoPaletteHolder.current.education
    Category.SHOPPING -> FluxoPaletteHolder.current.shopping
    Category.SERVICES -> FluxoPaletteHolder.current.services
}

/**
 * Legacy token names kept for source compatibility, remapped to the nearest new palette role.
 * Mint -> petrol, Blue -> mid petrol, Lime -> light petrol, Amber -> amber.
 */
internal object FluxoDesignTokens {
    val Mint: Color get() = FluxoColors.Petrol
    val Blue: Color get() = FluxoColors.PetrolMid
    val Lime: Color get() = FluxoColors.PetrolLight
    val Amber: Color get() = FluxoColors.Amber
    val ScreenPadding = 16.dp
    val ContentSpacing = 12.dp
    val PanelRadius = 20.dp
    val ItemRadius = 16.dp
}

/** Role-based corner radii and shapes of the prototype. */
internal object FluxoShapes {
    /** Hero block (22dp). */
    val Hero: Shape = RoundedCornerShape(22.dp)
    /** Feature tile, e.g. donut (20dp). */
    val TileFeature: Shape = RoundedCornerShape(20.dp)
    /** Outlined tile, e.g. bar chart (14dp). */
    val TileOutlined: Shape = RoundedCornerShape(14.dp)
    /** Accent tile with a tight bottom-start corner, e.g. split bar. */
    val TileAccent: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 6.dp)
    /** Review ticket, list strips, benefit rows (16dp). */
    val Ticket: Shape = RoundedCornerShape(16.dp)
    /** Capture strip (18dp). */
    val Strip: Shape = RoundedCornerShape(18.dp)
    /** Fully rounded pill for buttons and chips. */
    val Pill: Shape = RoundedCornerShape(percent = 50)
    /** Small badge (8dp). */
    val Badge: Shape = RoundedCornerShape(8.dp)
}

// ---------------------------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------------------------

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFamily(resId: Int, weights: List<Int>) = FontFamily(
    weights.map { w -> Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)

/** Bundled typefaces (SIL OFL): Bricolage Grotesque for titles and numbers, DM Sans for text. */
internal object FluxoType {
    /** Display family: titles and numbers (Bricolage Grotesque, 400-800). */
    val Display: FontFamily = variableFamily(R.font.bricolage_grotesque, listOf(400, 500, 600, 700, 800))
    /** Body family: running text and labels (DM Sans, 400-700). */
    val Body: FontFamily = variableFamily(R.font.dm_sans, listOf(400, 500, 600, 700))

    /** Money / numeric style: display family, tabular numerals. */
    fun number(size: TextUnit = 16.sp, weight: FontWeight = FontWeight.Bold): TextStyle = TextStyle(
        fontFamily = Display, fontWeight = weight, fontSize = size, letterSpacing = (-0.01).em,
        fontFeatureSettings = "tnum"
    )

    /** Large hero number, 42sp ExtraBold, tabular. */
    val HeroNumber: TextStyle = number(42.sp, FontWeight.ExtraBold).copy(letterSpacing = (-0.03).em, lineHeight = 46.sp)
    /** List-row amount, 15sp Bold, tabular. */
    val RowNumber: TextStyle = number(15.sp)
    /** Secondary caption, 11sp Medium in body family. */
    val Caption: TextStyle = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
}

private fun fluxoTypography(): Typography {
    val base = Typography()
    // Letter spacing is explicit (the prototype uses none for text) and line height is relative (1.3em)
    // so call sites that change fontSize keep the prototype's proportions.
    fun TextStyle.display(w: FontWeight, lh: TextUnit = 1.3.em) =
        copy(fontFamily = FluxoType.Display, fontWeight = w, letterSpacing = (-0.01).em, lineHeight = lh)
    fun TextStyle.body(w: FontWeight = FontWeight.Normal) =
        copy(fontFamily = FluxoType.Body, fontWeight = w, letterSpacing = 0.sp, lineHeight = 1.3.em)
    return base.copy(
        displayLarge = base.displayLarge.display(FontWeight.ExtraBold),
        displayMedium = base.displayMedium.display(FontWeight.ExtraBold),
        displaySmall = base.displaySmall.display(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.display(FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.display(FontWeight.ExtraBold, 1.05.em).copy(letterSpacing = (-0.02).em),
        headlineSmall = base.headlineSmall.display(FontWeight.ExtraBold),
        titleLarge = base.titleLarge.display(FontWeight.ExtraBold),
        titleMedium = base.titleMedium.display(FontWeight.ExtraBold),
        titleSmall = base.titleSmall.display(FontWeight.Bold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(FontWeight.Bold),
        labelMedium = base.labelMedium.body(FontWeight.SemiBold),
        labelSmall = base.labelSmall.body(FontWeight.SemiBold),
    )
}

// ---------------------------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------------------------

private fun fluxoColorScheme(p: FluxoPalette): ColorScheme {
    return if (p.isDark) darkColorScheme(
        primary = p.petrol, onPrimary = p.onPetrol,
        primaryContainer = p.pale, onPrimaryContainer = p.ink,
        inversePrimary = p.hero,
        secondary = p.petrolMid, onSecondary = p.onPetrol,
        secondaryContainer = p.soft, onSecondaryContainer = p.ink,
        tertiary = p.amber, onTertiary = p.onAmber,
        tertiaryContainer = p.amberContainer, onTertiaryContainer = p.ink,
        background = p.paper, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink,
        surfaceVariant = p.soft, onSurfaceVariant = p.mute,
        surfaceTint = p.petrol,
        inverseSurface = p.ink, inverseOnSurface = p.onInk,
        error = p.expense, onError = p.onExpense,
        errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
        outline = p.mute, outlineVariant = p.line,
        scrim = p.scrim,
        surfaceBright = p.pale, surfaceDim = p.stage,
        surfaceContainerLowest = p.stage, surfaceContainerLow = p.soft,
        surfaceContainer = p.soft, surfaceContainerHigh = p.pale,
        surfaceContainerHighest = p.pale,
    ) else lightColorScheme(
        primary = p.petrol, onPrimary = p.onPetrol,
        primaryContainer = p.pale, onPrimaryContainer = p.petrolDark,
        inversePrimary = p.petrolLight,
        secondary = p.petrolMid, onSecondary = p.onPetrol,
        secondaryContainer = p.soft, onSecondaryContainer = p.ink,
        tertiary = p.amber, onTertiary = p.onAmber,
        tertiaryContainer = p.amberContainer, onTertiaryContainer = p.ink,
        background = p.paper, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink,
        surfaceVariant = p.soft, onSurfaceVariant = p.mute,
        surfaceTint = p.petrol,
        inverseSurface = p.ink, inverseOnSurface = p.onInk,
        error = p.expense, onError = p.onExpense,
        errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
        outline = p.mute, outlineVariant = p.line,
        scrim = p.scrim,
        surfaceBright = p.paper, surfaceDim = p.stage,
        surfaceContainerLowest = p.paper, surfaceContainerLow = p.soft,
        surfaceContainer = p.soft, surfaceContainerHigh = p.pale,
        surfaceContainerHighest = p.pale,
    )
}

private val FluxoMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Resolves a [ThemeMode] to light or dark; [ThemeMode.SYSTEM] follows the device night mode. */
@Composable
internal fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Shared petrol theme wrapping all Fluxo destinations: Material colour scheme, bundled typography and rounded
 * shapes so stock components (dialogs, fields, switches) match. The resolved palette is published to
 * [FluxoPaletteHolder] (read by [FluxoColors]) and eased over 250 ms when the mode changes. The first composition
 * starts at the target and the system "remove animations" setting makes the switch instant.
 */
@Composable
internal fun FluxoVisualTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode.resolveDark()
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "themeProgress"
    )
    val palette = lerpPalette(FluxoPalette.Light, FluxoPalette.Dark, progress)
    // Published during composition, before any token is read below, so every reader (dialog windows and Canvas
    // draw blocks included) recomposes or redraws on the same frame.
    FluxoPaletteHolder.current = palette
    // Leaving the root theme (activity gone, test finished) puts the neutral light palette back for bare previews/tests.
    DisposableEffect(Unit) { onDispose { FluxoPaletteHolder.current = FluxoPalette.Light } }
    val colorScheme = remember(palette) { fluxoColorScheme(palette) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FluxoTypography,
        shapes = FluxoMaterialShapes
    ) {
        // Plain Text outside a Surface (onboarding, previews) must follow the theme too, not the default black.
        CompositionLocalProvider(LocalContentColor provides palette.ink) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
        }
    }
}

/** Material typography wired to [FluxoType]: display family for display/headline/title, body family elsewhere. */
internal val FluxoTypography: Typography by lazy { fluxoTypography() }

// ---------------------------------------------------------------------------------------------
// Layout containers
// ---------------------------------------------------------------------------------------------

/**
 * Shared panel: full width, 20dp corners, 16dp padding, 12dp spacing, soft fill with a hairline
 * border tinted by [accent]. [gradient] is ignored (kept only so old call sites compile).
 */
@Composable
internal fun FluxoPanel(
    modifier: Modifier = Modifier,
    accent: Color = FluxoDesignTokens.Blue,
    @Suppress("UNUSED_PARAMETER") gradient: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FluxoDesignTokens.PanelRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, accent.copy(alpha = .38f), RoundedCornerShape(FluxoDesignTokens.PanelRadius))
            .padding(FluxoDesignTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FluxoDesignTokens.ContentSpacing),
        content = content
    )
}

/** Visual role of a [FluxoTile], which decides fill, border and corner radius. */
internal enum class FluxoTileRole {
    /** Soft fill, 20dp radius (donut tile). */
    Feature,
    /** Paper fill with 1.5dp line border, 14dp radius (bar chart tile). */
    Outlined,
    /** Pale fill, 16dp radius with a tight bottom-start corner (split / small stat tile). */
    Accent
}

/** Compact tile with role-based fill and radius; content is a 10dp-padded column. */
@Composable
internal fun FluxoTile(
    modifier: Modifier = Modifier,
    role: FluxoTileRole = FluxoTileRole.Feature,
    content: @Composable ColumnScope.() -> Unit
) {
    val (shape, fill) = when (role) {
        FluxoTileRole.Feature -> FluxoShapes.TileFeature to FluxoColors.Soft
        FluxoTileRole.Outlined -> FluxoShapes.TileOutlined to FluxoColors.Paper
        FluxoTileRole.Accent -> FluxoShapes.TileAccent to FluxoColors.Pale
    }
    val outline = if (role == FluxoTileRole.Outlined) Modifier.border(BorderStroke(1.5.dp, FluxoColors.Line), shape) else Modifier
    Column(
        modifier.clip(shape).background(fill).then(outline).padding(10.dp),
        content = content
    )
}

/**
 * Hero block: solid petrol, small [currency] prefix, large tabular [value], optional [equation]
 * line, [note] chip and a [trailing] slot (e.g. a [Sparkline]); extra [content] goes below.
 */
@Composable
internal fun FluxoHero(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    currency: String? = "R$",
    equation: String? = null,
    note: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val overlay = LocalDensity.current.fontScale < 1.3f
    Box(
        modifier
            .fillMaxWidth()
            .clip(FluxoShapes.Hero)
            .background(FluxoColors.Hero)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 12.dp)) {
            Text(label, color = FluxoColors.OnHero, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Crossfade(
                targetState = currency to value, animationSpec = tween(200), label = "heroValue",
                modifier = Modifier.padding(top = 1.dp, bottom = 3.dp)
            ) { (unit, amount) ->
                Text(
                    buildAnnotatedString {
                        if (unit != null) {
                            withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)) { append("$unit ") }
                        }
                        append(amount)
                    },
                    color = FluxoColors.OnHero, style = FluxoType.HeroNumber, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                )
            }
            if (equation != null) Text(
                equation, color = FluxoColors.OnHero,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontFeatureSettings = "tnum")
            )
            if (note != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    note,
                    color = FluxoColors.Ink,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(FluxoColors.Soft).padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            content()
        }
        if (trailing != null && overlay) Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 24.dp)) { trailing() }
    }
}

/**
 * Compact top row used instead of screen titles: previous/next arrows around the month label on the
 * start side and an optional [trailing] action group on the end side (stacked below at large font).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FluxoMonthBar(
    month: java.time.YearMonth,
    previous: () -> Unit,
    next: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val label = month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)).replaceFirstChar { it.titlecase(ptBr) }
    // FlowRow: the month label keeps its natural width; the trailing actions drop to a second row when they do not fit.
    FlowRow(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior", previous)
            Text(label, maxLines = 1, softWrap = false, color = FluxoColors.Ink,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f, fill = false))
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês", next)
        }
        // weight(1f): takes the rest of the line (or the whole second line) and keeps the actions end-aligned.
        if (trailing != null) Row(
            Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End, content = trailing
        )
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 28.dp, height = 44.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = FluxoColors.Petrol, modifier = Modifier.size(22.dp)) }
}

/**
 * Section heading: display-family title with heading semantics, optional decorative 24dp icon
 * and an optional right-aligned [trailing] hint (e.g. "Todos").
 */
@Composable
internal fun FluxoSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = FluxoDesignTokens.Mint,
    trailing: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FluxoDesignTokens.ContentSpacing)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f, fill = trailing != null).semantics { heading() }
        )
        if (trailing != null) Text(trailing, style = FluxoType.Caption.copy(fontSize = 11.5.sp), color = FluxoColors.Mute)
    }
}

/** Readable empty state, no fixed height. */
@Composable
internal fun FluxoEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FluxoDesignTokens.ContentSpacing)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = FluxoColors.Mute)
    }
}

// ---------------------------------------------------------------------------------------------
// Chips, badges, dots, buttons
// ---------------------------------------------------------------------------------------------

/** Chip colour tone: [Default] ink outline, [Review] amber fill for the "Revisar" filter. */
internal enum class FluxoChipTone { Default, Review }

/**
 * Pill filter chip: 32dp visual pill inside a 48dp touch target; selected = ink fill, white text.
 * Exposes single-select (radio) semantics with the selected state.
 */
@Composable
internal fun FluxoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: FluxoChipTone = FluxoChipTone.Default,
    enabled: Boolean = true
) {
    val review = tone == FluxoChipTone.Review
    val fill = when { review -> FluxoColors.Amber; selected -> FluxoColors.Ink; else -> FluxoColors.Paper }
    val border = when { review && !selected -> FluxoColors.Amber; selected -> FluxoColors.Ink; else -> FluxoColors.ChipBorder }
    val text = when { review -> FluxoColors.OnAmber; selected -> FluxoColors.OnInk; else -> FluxoColors.Ink }
    Box(
        modifier.heightIn(min = 44.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(fill)
                .border(2.dp, border, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = text, maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Badge kind: [Automatic] and [Bank] are ink/white, [Manual] is soft/ink. */
internal enum class FluxoBadgeKind { Automatic, Manual, Bank }

/** Small badge such as "automático", "manual" or a bank name (10sp bold, 8dp radius). */
@Composable
internal fun FluxoBadge(text: String, modifier: Modifier = Modifier, kind: FluxoBadgeKind = FluxoBadgeKind.Automatic) {
    val (bg, fg) = if (kind == FluxoBadgeKind.Manual) FluxoColors.Soft to FluxoColors.Ink else FluxoColors.Badge to FluxoColors.OnBadge
    Text(
        text, color = fg, maxLines = 1, softWrap = false,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        modifier = modifier.clip(FluxoShapes.Badge).background(bg).padding(horizontal = 6.dp)
    )
}

/** Filled circle used for category colour dots (default 11dp). */
@Composable
internal fun FluxoDot(color: Color, modifier: Modifier = Modifier, size: Dp = 11.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Pill button style: [Primary] ink fill, [Secondary] ink outline, [Quiet] small grey outline, [Review] amber. */
internal enum class FluxoButtonStyle { Primary, Secondary, Quiet, Review }

/** Pill button, at least 48dp tall, 13sp bold label, optional leading [icon]. */
@Composable
internal fun FluxoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FluxoButtonStyle = FluxoButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val fill: Color; val fg: Color; val borderColor: Color; val borderWidth: Dp
    when (style) {
        FluxoButtonStyle.Primary -> { fill = FluxoColors.Ink; fg = FluxoColors.OnInk; borderColor = FluxoColors.Ink; borderWidth = 2.dp }
        FluxoButtonStyle.Secondary -> { fill = FluxoColors.Paper; fg = FluxoColors.Ink; borderColor = FluxoColors.ChipBorder; borderWidth = 2.dp }
        FluxoButtonStyle.Quiet -> { fill = FluxoColors.Paper; fg = FluxoColors.Mute; borderColor = FluxoColors.Line; borderWidth = 1.5.dp }
        FluxoButtonStyle.Review -> { fill = FluxoColors.Amber; fg = FluxoColors.OnAmber; borderColor = FluxoColors.Amber; borderWidth = 2.dp }
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (enabled) 1f else .38f, androidx.compose.animation.core.tween(180), label = "pillEnabled")
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(FluxoShapes.Pill)
            .background(fill.copy(alpha = fill.alpha * alpha))
            .border(borderWidth, borderColor.copy(alpha = alpha), FluxoShapes.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(16.dp))
        Text(text, color = fg.copy(alpha = alpha), maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

// ---------------------------------------------------------------------------------------------
// Review ticket and capture strip
// ---------------------------------------------------------------------------------------------

/**
 * Review "ticket": pale card, 16dp radius, amber stripe on the start edge. Compose rows with
 * [FluxoTicketHeader] and [FluxoTicketActions].
 */
@Composable
internal fun FluxoTicket(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(FluxoShapes.Ticket)
            .background(FluxoColors.Pale)
            .drawBehind {
                val inset = with(density) { 10.dp.toPx() }
                val w = with(density) { 4.dp.toPx() }
                drawRoundRect(
                    FluxoColors.Amber, topLeft = Offset(-w / 2f, inset),
                    size = Size(w * 1.5f, size.height - 2 * inset), cornerRadius = CornerRadius(w * .75f)
                )
            }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        content = content
    )
}

/** Ticket title line: display-family [title] (14.5sp) with a bank/source [badge] on the end. */
@Composable
internal fun FluxoTicketHeader(title: String, modifier: Modifier = Modifier, badge: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title, modifier = Modifier.weight(1f),
            style = FluxoType.number(14.5.sp, FontWeight.ExtraBold).copy(lineHeight = 18.sp)
        )
        if (badge != null) FluxoBadge(badge, kind = FluxoBadgeKind.Bank)
    }
}

/** Ticket action row below a dashed "tear" line; place two [FluxoPillButton]s with `Modifier.weight(1f)`. */
@Composable
internal fun FluxoTicketActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val density = LocalDensity.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .drawBehind {
                val stroke = with(density) { 2.dp.toPx() }
                val dash = with(density) { 4.dp.toPx() }
                drawLine(
                    FluxoColors.Mute, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                )
            }
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Capture strip: ink bar with a live/idle dot, bold [title], [detail] line and an optional
 * [action] slot (typically a Review-style [FluxoPillButton]).
 */
@Composable
internal fun FluxoCaptureStrip(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    action: (@Composable () -> Unit)? = null
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(FluxoShapes.Strip)
            .background(FluxoColors.Strip)
    ) {
        // Large font or narrow width: the action goes under the text instead of squeezing it.
        val stacked = action != null && (fontScale >= 1.3f || maxWidth < 340.dp)
        val text: @Composable ColumnScope.() -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FluxoDot(if (live) FluxoColors.Live else FluxoColors.Gray, size = 10.dp)
                Text(title, color = FluxoColors.OnStrip, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
            }
            Text(detail, color = FluxoColors.OnStrip, style = MaterialTheme.typography.bodySmall)
        }
        if (stacked) {
            Column(Modifier.padding(start = 14.dp, end = 3.dp, top = 3.dp, bottom = 3.dp)) {
                Column(Modifier.padding(vertical = 5.dp), content = text)
                action?.invoke()
            }
        } else {
            Row(
                Modifier.padding(start = 14.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f).padding(vertical = 5.dp), content = text)
                if (action != null) action()
            }
        }
    }
}
