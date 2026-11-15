package br.com.fluxo.app

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
private const val ProIncludes = "Tudo do grátis, com contas ativas ilimitadas"

@Composable
private fun PlanBox(title: String, lines: List<String>, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = FluxoColors.Ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        lines.forEach { Text("•  $it", color = FluxoColors.Ink, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Tela "Fluxo Pro". [price] vem do Google Play; null enquanto carrega ou indisponível (então [onRetry] tenta de novo). */
@Composable
internal fun ProPaywallScreen(isPro: Boolean, price: String?, proForAll: Boolean, onBuy: () -> Unit, onClose: () -> Unit, onRetry: () -> Unit = {}) {
    Surface(Modifier.fillMaxSize().testTag("paywall"), color = FluxoColors.Paper) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Fluxo Pro", color = FluxoColors.Ink, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text("O Fluxo funciona por inteiro sem Pro. O Pro é uma compra única, sem assinatura.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodyMedium)
            PlanBox("Grátis", FreeIncludes, "paywall.free")
            PlanBox("Pro", listOf(ProIncludes), "paywall.pro")
            Text("Seus dados nunca são ocultados nem apagados, e a exportação segue liberada, com ou sem Pro. Tudo continua offline e só no seu aparelho.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
            Text(
                if (proForAll) "Grátis por enquanto" else price ?: "Preço indisponível",
                modifier = Modifier.testTag("paywall.price"), color = FluxoColors.Ink,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (!proForAll && !isPro) Text("Cobrança única pelo Google Play. Nada é cobrado ao abrir esta tela.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
            if (isPro) Text("Você já tem o Fluxo Pro.", modifier = Modifier.testTag("paywall.owned"), color = FluxoColors.Income, style = MaterialTheme.typography.bodyLarge)
            else if (!proForAll) {
                FluxoWrapButton("Comprar o Fluxo Pro", onBuy, Modifier.testTag("paywall.buy"), enabled = price != null)
                if (price == null) FluxoWrapButton("Tentar de novo", onRetry, Modifier.testTag("paywall.retry"), quiet = true)
            }
            FluxoWrapButton("Fechar", onClose, Modifier.testTag("paywall.close"), quiet = true)
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
