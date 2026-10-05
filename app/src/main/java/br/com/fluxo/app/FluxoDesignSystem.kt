package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object FluxoDesignTokens {
    val Mint = Color(0xFF00E5B5)
    val Blue = Color(0xFF51C8FF)
    val Lime = Color(0xFFD5F568)
    val Amber = Color(0xFFFFD166)
    val ScreenPadding = 16.dp
    val ContentSpacing = 12.dp
    val PanelRadius = 20.dp
    val ItemRadius = 16.dp
}

/**
 * Shared navy dark theme wrapping all Fluxo destinations.
 * Uses the exact dashboardColors darkColorScheme values from the original Dashboard identity.
 * Typography is inherited unchanged from MaterialTheme defaults.
 */
@Composable
internal fun FluxoVisualTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = FluxoDesignTokens.Mint, onPrimary = Color(0xFF002D26),
        primaryContainer = Color(0xFF00463E), onPrimaryContainer = Color(0xFFE6FFF7),
        secondary = FluxoDesignTokens.Blue, onSecondary = Color(0xFF002A40),
        secondaryContainer = Color(0xFF113550), onSecondaryContainer = Color(0xFFDCF4FF),
        background = Color(0xFF001020), onBackground = Color(0xFFF5F7FC),
        surface = Color(0xFF001020), onSurface = Color(0xFFF5F7FC),
        surfaceVariant = Color(0xFF10283D), onSurfaceVariant = Color(0xFFBCCBE0),
        surfaceContainerLow = Color(0xFF071C2E), surfaceContainer = Color(0xFF0B2237),
        outline = Color(0xFF55758C), outlineVariant = Color(0xFF224259),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Shared panel: full width, 20dp rounded corners, 16dp padding, 12dp vertical spacing,
 * 1dp border at accent alpha .38, surfaceContainerLow background unless explicit gradient.
 */
@Composable
internal fun FluxoPanel(
    modifier: Modifier = Modifier,
    accent: Color = FluxoDesignTokens.Blue,
    gradient: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FluxoDesignTokens.PanelRadius),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = .38f))
    ) {
        val bg = if (gradient != null) {
            Modifier.background(Brush.linearGradient(gradient))
        } else {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
        }
        Column(
            bg.padding(FluxoDesignTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(FluxoDesignTokens.ContentSpacing),
            content = content
        )
    }
}

/**
 * Section heading: titleMedium Bold with heading semantics, optional decorative 24dp icon.
 */
@Composable
internal fun FluxoSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = FluxoDesignTokens.Mint
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
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * Readable empty state, no fixed height.
 */
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
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Adaptive pair: column below 340dp or fontScale >= 1.3, otherwise equal columns gap 12dp.
 */
@Composable
internal fun FluxoAdaptivePair(
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(FluxoDesignTokens.ContentSpacing)) {
                first(); second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(FluxoDesignTokens.ContentSpacing)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}
