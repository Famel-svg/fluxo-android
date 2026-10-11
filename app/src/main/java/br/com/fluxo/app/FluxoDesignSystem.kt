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
import androidx.compose.runtime.Composable
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
// Colour tokens (light petrol palette from the approved prototype p1.html)
// ---------------------------------------------------------------------------------------------

/** Light petrol palette; every value mirrors a CSS variable of the approved prototype. */
internal object FluxoColors {
    /** Outer "stage" behind the app surface (#E9EFF1). */
    val Stage = Color(0xFFE9EFF1)
    /** Main app surface (#FBFCFC). */
    val Paper = Color(0xFFFBFCFC)
    /** Primary text and dark controls (#14232B). */
    val Ink = Color(0xFF14232B)
    /** Secondary text (#56676F). */
    val Mute = Color(0xFF56676F)
    /** Hairlines and quiet borders (#DCE5E8). */
    val Line = Color(0xFFDCE5E8)
    /** Soft tinted fill for tiles (#E3EFF3). */
    val Soft = Color(0xFFE3EFF3)
    /** Slightly stronger tint for tickets and split tiles (#D2E6EC). */
    val Pale = Color(0xFFD2E6EC)
    /** Main brand petrol (#0B5C73). */
    val Petrol = Color(0xFF0B5C73)
    /** Darkest petrol step (#084457). */
    val PetrolDark = Color(0xFF084457)
    /** Mid petrol step used for bars (#1F6F84). */
    val PetrolMid = Color(0xFF1F6F84)
    /** Light petrol step (#7FB5C2). */
    val PetrolLight = Color(0xFF7FB5C2)
    /** Palest petrol step used for the 5th chart category (#B5D3DA). */
    val PetrolSoft = Color(0xFFB5D3DA)
    /** Income / positive (#17794A). */
    val Income = Color(0xFF17794A)
    /** Expense / budget overflow (#BF3F17). */
    val Expense = Color(0xFFBF3F17)
    /** The single warm accent: review, highlight (#D9962B). */
    val Amber = Color(0xFFD9962B)
    /** Sparkline stroke and text on petrol (#CFE6EC). */
    val OnPetrolSoft = Color(0xFFCFE6EC)
    /** Text and icons drawn on a solid petrol block. */
    val OnPetrol = Color.White
    /** "Capture live" indicator dot (#5FD39A). */
    val Live = Color(0xFF5FD39A)
    /** Neutral grey for "sem categoria" (#A3A8B0). */
    val Gray = Color(0xFFA3A8B0)

    /** Ordered chart palette: petrol steps first, amber for the highlighted slice. */
    val ChartSeries: List<Color> = listOf(PetrolLight, Petrol, Amber, PetrolMid, PetrolDark, PetrolSoft)
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
    Category.EDUCATION -> Color(0xFF3F8EA3)
    Category.SHOPPING -> Color(0xFF9CC4CE)
    Category.SERVICES -> Color(0xFF4B7380)
}

/**
 * Legacy token names kept for source compatibility, remapped to the nearest new palette role.
 * Mint -> petrol, Blue -> mid petrol, Lime -> light petrol, Amber -> amber.
 */
internal object FluxoDesignTokens {
    val Mint = FluxoColors.Petrol
    val Blue = FluxoColors.PetrolMid
    val Lime = FluxoColors.PetrolLight
    val Amber = FluxoColors.Amber
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

private val FluxoLightColorScheme = lightColorScheme(
    primary = FluxoColors.Petrol, onPrimary = Color.White,
    primaryContainer = FluxoColors.Pale, onPrimaryContainer = FluxoColors.PetrolDark,
    inversePrimary = FluxoColors.PetrolLight,
    secondary = FluxoColors.PetrolMid, onSecondary = Color.White,
    secondaryContainer = FluxoColors.Soft, onSecondaryContainer = FluxoColors.Ink,
    tertiary = FluxoColors.Amber, onTertiary = FluxoColors.Ink,
    tertiaryContainer = Color(0xFFF6E3BD), onTertiaryContainer = FluxoColors.Ink,
    background = FluxoColors.Paper, onBackground = FluxoColors.Ink,
    surface = FluxoColors.Paper, onSurface = FluxoColors.Ink,
    surfaceVariant = FluxoColors.Soft, onSurfaceVariant = FluxoColors.Mute,
    surfaceTint = FluxoColors.Petrol,
    inverseSurface = FluxoColors.Ink, inverseOnSurface = Color.White,
    error = FluxoColors.Expense, onError = Color.White,
    errorContainer = Color(0xFFF6DCD2), onErrorContainer = Color(0xFF5E1A05),
    outline = FluxoColors.Mute, outlineVariant = FluxoColors.Line,
    scrim = Color.Black,
    surfaceBright = FluxoColors.Paper, surfaceDim = FluxoColors.Stage,
    surfaceContainerLowest = FluxoColors.Paper, surfaceContainerLow = FluxoColors.Soft,
    surfaceContainer = FluxoColors.Soft, surfaceContainerHigh = FluxoColors.Pale,
    surfaceContainerHighest = FluxoColors.Pale,
)

private val FluxoMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Shared light petrol theme wrapping all Fluxo destinations: Material colour scheme, bundled
 * typography and rounded shapes so stock components (dialogs, fields, switches) match.
 */
@Composable
internal fun FluxoVisualTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FluxoLightColorScheme,
        typography = FluxoTypography,
        shapes = FluxoMaterialShapes
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
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
            .background(FluxoColors.Petrol)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 12.dp)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Text(
                buildAnnotatedString {
                    if (currency != null) {
                        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)) { append("$currency ") }
                    }
                    append(value)
                },
                color = Color.White, style = FluxoType.HeroNumber, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 1.dp, bottom = 3.dp)
            )
            if (equation != null) Text(
                equation, color = Color.White,
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
    val border = when { review && !selected -> FluxoColors.Amber; else -> FluxoColors.Ink }
    val text = when { review -> FluxoColors.Ink; selected -> Color.White; else -> FluxoColors.Ink }
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
    val (bg, fg) = if (kind == FluxoBadgeKind.Manual) FluxoColors.Soft to FluxoColors.Ink else FluxoColors.Ink to Color.White
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
        FluxoButtonStyle.Primary -> { fill = FluxoColors.Ink; fg = Color.White; borderColor = FluxoColors.Ink; borderWidth = 2.dp }
        FluxoButtonStyle.Secondary -> { fill = FluxoColors.Paper; fg = FluxoColors.Ink; borderColor = FluxoColors.Ink; borderWidth = 2.dp }
        FluxoButtonStyle.Quiet -> { fill = FluxoColors.Paper; fg = FluxoColors.Mute; borderColor = FluxoColors.Line; borderWidth = 1.5.dp }
        FluxoButtonStyle.Review -> { fill = FluxoColors.Amber; fg = FluxoColors.Ink; borderColor = FluxoColors.Amber; borderWidth = 2.dp }
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
            .background(FluxoColors.Ink)
    ) {
        // Large font or narrow width: the action goes under the text instead of squeezing it.
        val stacked = action != null && (fontScale >= 1.3f || maxWidth < 340.dp)
        val text: @Composable ColumnScope.() -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FluxoDot(if (live) FluxoColors.Live else FluxoColors.Gray, size = 10.dp)
                Text(title, color = Color.White, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
            }
            Text(detail, color = Color.White, style = MaterialTheme.typography.bodySmall)
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
