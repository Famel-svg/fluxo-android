package br.com.fluxo.app

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fluxo.core.FeaturePolicy

private val FreeIncludes = listOf(
    "Captura por notificação dos bancos que você escolher",
    "Lançamentos, categorias e triagem do que precisa de revisão",
    "Orçamentos e planejamento com VR",
    "Backup criptografado e exportação CSV",
    "Até ${FeaturePolicy.MAX_CONTAS_FREE} contas ativas",
)
private val ProBenefits = listOf(
    "Contas ativas ilimitadas",
    "Pagamento único, sem assinatura",
    "Funciona offline, sem conta e sem nuvem",
    "Seus dados nunca são ocultados nem apagados",
)
private const val ProIncludes = "Tudo do grátis, com contas ativas ilimitadas"

@Composable
private fun PlanBox(title: String, lines: List<String>, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = FluxoColors.Ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        lines.forEach { Text("•  $it", color = FluxoColors.Ink, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(FluxoColors.Petrol), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = FluxoColors.OnPetrol, modifier = Modifier.size(16.dp))
        }
        Text(text, color = FluxoColors.Ink, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Tela "Fluxo Pro". [price] vem do Google Play; null enquanto carrega ou indisponível (então [onRetry] tenta de novo). */
@Composable
internal fun ProPaywallScreen(isPro: Boolean, price: String?, proForAll: Boolean, onBuy: () -> Unit, onClose: () -> Unit, onRetry: () -> Unit = {}) {
    val hero = FluxoColors.Hero
    val blue = Color(0xFF1F7AC4)
    val gradient = Brush.linearGradient(listOf(hero, if (hero.luminance() > .2f) blue else lerp(hero, blue, .5f)))
    Surface(Modifier.fillMaxSize().testTag("paywall"), color = FluxoColors.Paper) {
        // Preço e compra ficam fixos no rodapé: visíveis sem rolar mesmo em 320x640 e fonte grande.
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.fillMaxWidth().background(gradient).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Fluxo Pro", color = FluxoColors.OnHero, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                    Text("O Fluxo funciona por inteiro sem Pro. O Pro é uma compra única, sem assinatura.", color = FluxoColors.OnHero.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FluxoColors.PetrolSoft).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Notifications, null, tint = FluxoColors.Petrol, modifier = Modifier.size(28.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Captura automática pelas notificações", color = FluxoColors.Ink, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Tudo no aparelho: nada sai do seu celular.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { ProBenefits.forEach { BenefitRow(it) } }
                    PlanBox("Grátis", FreeIncludes, "paywall.free")
                    PlanBox("Pro", listOf(ProIncludes), "paywall.pro")
                    Text("Seus dados nunca são ocultados nem apagados, e a exportação segue liberada, com ou sem Pro. Tudo continua offline e só no seu aparelho.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = FluxoColors.Line)
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (proForAll) "Grátis por enquanto" else price ?: "Preço indisponível",
                    modifier = Modifier.testTag("paywall.price"), color = FluxoColors.Ink,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (!proForAll && !isPro) Text("Cobrança única pelo Google Play. Nada é cobrado ao abrir esta tela.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
                if (isPro) Text("Você já tem o Fluxo Pro.", modifier = Modifier.testTag("paywall.owned"), color = FluxoColors.Income, style = MaterialTheme.typography.bodyLarge)
                else if (!proForAll) {
                    val enabled = price != null
                    Box(
                        Modifier.testTag("paywall.buy").fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
                            .background(FluxoColors.Petrol.copy(alpha = if (enabled) 1f else .38f))
                            .clickable(enabled = enabled, role = Role.Button, onClick = onBuy).padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Comprar o Fluxo Pro", color = FluxoColors.OnPetrol, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (price == null) FluxoWrapButton("Tentar de novo", onRetry, Modifier.testTag("paywall.retry"), quiet = true)
                }
                FluxoWrapButton("Fechar", onClose, Modifier.testTag("paywall.close"), quiet = true)
            }
        }
    }
}

/** Cartão da aba Configurações: abre o paywall, restaura compras (play) e, em debug, força Pro. */
@Composable
internal fun ProSettingsCard() {
    val entitlements = (LocalContext.current.applicationContext as FluxoApplication).entitlements
    val isPro by entitlements.isPro.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    FluxoSettingsCard {
        FluxoSectionHeading("Fluxo Pro")
        FluxoDataRow("Ver o Fluxo Pro", modifier = Modifier.testTag("settings.pro"), caption = if (isPro) "Ativo" else null, onClick = { open = true })
        if (BuildConfig.STORE_BUILD && !BuildConfig.PRO_FOR_ALL) {
            FluxoDataRow("Restaurar compras", modifier = Modifier.testTag("settings.pro.restore"), onClick = { entitlements.restore() })
        }
        (entitlements as? DebugEntitlements)?.takeIf { it.switchVisible }?.let { DebugProRow(it) }
    }
    if (open) ProPaywallDialog { open = false }
}

@Composable
internal fun ProPaywallDialog(onClose: () -> Unit) {
    val entitlements = (LocalContext.current.applicationContext as FluxoApplication).entitlements
    val activity = LocalActivity.current
    val isPro by entitlements.isPro.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var price by remember { mutableStateOf<String?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        LaunchedEffect(attempt) { if (!BuildConfig.PRO_FOR_ALL) price = entitlements.queryPrice() }
        ProPaywallScreen(isPro, price, BuildConfig.PRO_FOR_ALL, onBuy = { activity?.let(entitlements::launchPurchase) }, onClose = onClose, onRetry = { attempt++ })
    }
}

/** Só debug: liga/desliga o Pro à força, para testar a interface sem Play. */
@Composable
internal fun DebugProRow(entitlements: DebugEntitlements) {
    val forced by entitlements.forced.collectAsStateWithLifecycle()
    val isPro by entitlements.isPro.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Depuração: Pro ${if (forced == null) "(automático)" else "forçado"}", Modifier.weight(1f), color = FluxoColors.Ink, style = MaterialTheme.typography.bodyMedium)
        Switch(isPro, { entitlements.forced.value = it }, Modifier.testTag("settings.debugPro"))
    }
}
