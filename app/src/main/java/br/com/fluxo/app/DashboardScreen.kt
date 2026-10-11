package br.com.fluxo.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Dashboard accents match the shared navy theme used by all destinations and editors.
internal val NeonMint = Color(0xFF00E5B5)
internal val NeonBlue = Color(0xFF51C8FF)
internal val NeonLime = Color(0xFFD5F568)
internal val NeonAmber = Color(0xFFFFD166)
@Composable
internal fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color = NeonBlue,
    background: List<Color> = listOf(Color(0xFF061D2E), Color(0xFF001020)),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = Color.Transparent, border = BorderStroke(1.dp, accent.copy(alpha = .38f))
    ) {
        Column(
            Modifier.background(Brush.linearGradient(background)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

@Composable
internal fun NeonHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color = NeonMint) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun NeonPair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { first(); second() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
internal fun Dashboard(
    rows: List<Transaction>, month: YearMonth, active: Boolean,
    previous: () -> Unit, next: () -> Unit, settings: () -> Unit,
    showAll: () -> Unit, review: () -> Unit, add: () -> Unit,
    planning: () -> Unit, edit: (Transaction) -> Unit
) {
    // Preserve baseline accounting: only confirmed, non-transfer records enter totals.
    val confirmed = rows.filter { it.status == ReviewStatus.CONFIRMED && it.transferId == null }
    fun total(predicate: (Transaction) -> Boolean): BigDecimal = confirmed.filter(predicate)
        .fold(BigDecimal.ZERO) { sum, row -> sum + BigDecimal.valueOf(row.amountCents ?: 0, 2) }
    val income = total { it.direction == Direction.INCOME }
    val cash = total { it.direction == Direction.EXPENSE && it.method != PaymentMethod.CREDIT_CARD }
    val credit = total { it.direction == Direction.EXPENSE && it.method == PaymentMethod.CREDIT_CARD }
    val pending = rows.count { it.status == ReviewStatus.REVIEW }
    val pendingCredit = rows.count { it.status == ReviewStatus.REVIEW && it.method == PaymentMethod.CREDIT_CARD }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var chartCredit by rememberSaveable { mutableStateOf(false) }
    var showDailyValues by rememberSaveable { mutableStateOf(false) }
    fun amount(value: BigDecimal) = if (hidden) "••••" else money(value)
    val expenseRows = confirmed.filter {
        it.direction == Direction.EXPENSE && (it.method == PaymentMethod.CREDIT_CARD) == chartCredit
    }
    val expenseTotal = if (chartCredit) credit else cash
    val today = LocalDate.now()
    val weekEnd = if (YearMonth.from(today) == month) today else month.atEndOfMonth()
    val weekStart = maxOf(month.atDay(1), weekEnd.minusDays(6))
    val dates = (0L..java.time.temporal.ChronoUnit.DAYS.between(weekStart, weekEnd)).map { weekStart.plusDays(it) }
    val zone = ZoneId.systemDefault()
    val daily = dates.map { date -> expenseRows.filter {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() == date
    }.fold(BigDecimal.ZERO) { sum, row -> sum + BigDecimal.valueOf(row.amountCents ?: 0, 2) } }
    val largest = daily.maxOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
    val categories = expenseRows.groupBy { it.category }.map { (category, items) ->
        category.label to items.fold(BigDecimal.ZERO) { sum, row -> sum + BigDecimal.valueOf(row.amountCents ?: 0, 2) }
    }.sortedByDescending { it.second }
    val accents = listOf(NeonMint, NeonBlue, Color(0xFF43D8EB), NeonLime)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard.neon"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                    Image(painterResource(R.drawable.ic_fluxo_mark), contentDescription = null, modifier = Modifier.padding(8.dp).size(32.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Olá!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Suas finanças, no seu aparelho", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = showAll) { Icon(Icons.Default.Search, "Buscar lançamentos") }
                IconButton(onClick = settings) { Icon(Icons.Default.Notifications, "Configurar captura de notificações", tint = if (active) NeonMint else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Text("Visão geral", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            MonthPicker(month, previous, next)
        }
        item {
            NeonPanel(accent = NeonMint, background = listOf(Color(0xFF00584E), Color(0xFF003347), Color(0xFF00162D))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resultado observado", Modifier.weight(1f), color = Color(0xFF9CF6E1), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { hidden = !hidden }, modifier = Modifier.testTag("dashboard.visibility")) {
                        Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Mostrar valores do resumo" else "Ocultar valores do resumo")
                    }
                }
                Text(amount(income - cash), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Entradas registradas: ${amount(income)}", style = MaterialTheme.typography.bodyMedium)
                Text("Entradas menos saídas de caixa. Cobertura parcial: este valor não é seu saldo bancário.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            NeonPair(first = {
                NeonPanel(accent = NeonMint, background = listOf(Color(0xFF004439), Color(0xFF002031))) {
                    NeonHeading("Dinheiro", Icons.Default.AccountBalanceWallet)
                    Text(amount(cash), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Saídas de caixa no mês", style = MaterialTheme.typography.bodySmall)
                    Text("Crédito separado", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }, second = {
                NeonPanel(accent = NeonBlue, background = listOf(Color(0xFF123D75), Color(0xFF08163D))) {
                    NeonHeading("Crédito", Icons.Default.CreditCard, NeonBlue)
                    Text(amount(credit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Compras confirmadas no mês", style = MaterialTheme.typography.bodySmall)
                    Text("Limite não informado", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            })
        }
        item {
            NeonPanel {
                NeonHeading("Seus gastos", Icons.Default.BarChart)
                Text("${amount(expenseTotal)} no mês · ${if (chartCredit) "crédito" else "caixa"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !chartCredit, onClick = { chartCredit = false }, label = { Text("Caixa") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = chartCredit, onClick = { chartCredit = true }, label = { Text("Crédito") }, modifier = Modifier.heightIn(min = 48.dp))
                }
                Text("Semana: ${weekStart.format(DateTimeFormatter.ofPattern("dd/MM"))} a ${weekEnd.format(DateTimeFormatter.ofPattern("dd/MM"))}", style = MaterialTheme.typography.labelLarge)
                if (hidden) {
                    Text("Gráfico e categorias ocultos", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        dates.forEachIndexed { index, date ->
                            val ratio = daily[index].divide(largest, 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                            Column(
                                Modifier.widthIn(min = 42.dp).padding(horizontal = 4.dp).clearAndSetSemantics {
                                    contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", ptBr))}: ${money(daily[index])}, ${if (chartCredit) "crédito" else "saídas de caixa"}"
                                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.height(104.dp).width(24.dp), contentAlignment = Alignment.BottomCenter) {
                                    if (ratio > 0f) Box(Modifier.fillMaxWidth().height((100 * ratio).coerceAtLeast(2f).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Brush.verticalGradient(listOf(accents[index % accents.size], accents[index % accents.size].copy(alpha = .55f)))))
                                }
                                Text(date.format(DateTimeFormatter.ofPattern("EE", ptBr)), style = MaterialTheme.typography.labelSmall)
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Altura relativa ao maior gasto diário. Valores por dia abaixo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showDailyValues = !showDailyValues }) { Text(if (showDailyValues) "Ocultar valores por dia" else "Ver valores por dia") }
                    if (showDailyValues) dates.forEachIndexed { index, date ->
                        Text("${date.format(DateTimeFormatter.ofPattern("EE dd/MM", ptBr))}: ${money(daily[index])}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Categorias do mês", style = MaterialTheme.typography.titleSmall)
                    if (categories.isEmpty()) Text("Sem gastos confirmados neste recorte.", style = MaterialTheme.typography.bodyMedium)
                    categories.chunked(2).forEachIndexed { group, pair ->
                        NeonPair(first = { NeonCategory(pair[0], expenseTotal, accents[(group * 2) % accents.size]) }, second = {
                            if (pair.size > 1) NeonCategory(pair[1], expenseTotal, accents[(group * 2 + 1) % accents.size])
                        })
                    }
                }
                Text("Revisões e transferências próprias não entram nos totais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            NeonPanel {
                NeonHeading("Ações rápidas", Icons.Default.Bolt)
                NeonPair(first = { NeonAction("Adicionar", Icons.Default.Add, add, NeonMint) }, second = { NeonAction("Lançamentos", Icons.AutoMirrored.Filled.ReceiptLong, showAll, NeonBlue) })
                NeonPair(first = { NeonAction("Planejar", Icons.Default.PieChart, planning, NeonBlue) }, second = { NeonAction("Mais opções", Icons.Default.Settings, settings, NeonLime) })
            }
        }
        item {
            NeonPanel(accent = NeonMint, background = listOf(Color(0xFF08473C), Color(0xFF00202D))) {
                NeonHeading("Saúde dos registros", Icons.Default.Spa, NeonLime)
                Text(if (pending > 0) "$pending lançamento(s) precisam de revisão." else "Nenhuma revisão pendente neste mês.", fontWeight = FontWeight.SemiBold)
                Text("Cobertura parcial. Os registros disponíveis não permitem avaliar sua saúde financeira.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = if (pending > 0) review else showAll, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (pending > 0) "Revisar lançamentos" else "Ver registros") }
            }
        }
        item {
            NeonPanel(accent = NeonAmber, background = listOf(Color(0xFF34301A), Color(0xFF071C2B))) {
                NeonHeading("Crédito para revisar", Icons.AutoMirrored.Filled.ReceiptLong, NeonAmber)
                Text(if (pendingCredit > 0) "$pendingCredit lançamento(s) de crédito pendente(s)" else "Nenhuma pendência de crédito neste mês", fontWeight = FontWeight.SemiBold)
                Text("Fatura, limite e vencimento não estão disponíveis. Confira no emissor do cartão.", style = MaterialTheme.typography.bodySmall)
                if (pending > 0) TextButton(onClick = review, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abrir todas as revisões") }
            }
        }
        item {
            OutlinedCard(onClick = settings, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeonHeading(if (active) "Captura habilitada" else "Configure a captura", Icons.Default.Notifications)
                    Text(if (active) "Notificações futuras das origens autorizadas. Isso não garante entrega de cada notificação." else "Escolha bancos ou permita outros apps, conceda acesso e ative a captura.", style = MaterialTheme.typography.bodySmall)
                    Text("Processamento local · texto bruto não armazenado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Últimos lançamentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            TextButton(onClick = showAll) { Text("Ver todos") }
        }
        if (rows.isEmpty()) item { EmptyState("Seu mês começa aqui", "Adicione um lançamento ou configure a captura. Nenhum histórico anterior será importado.") }
        else if (hidden) item { Text("Lançamentos ocultos neste resumo.") }
        else items(rows.take(5), key = { it.id }) { TransactionRow(it, edit) }
    }
}

@Composable
private fun NeonCategory(entry: Pair<String, BigDecimal>, total: BigDecimal, accent: Color) {
    val fraction = if (total > BigDecimal.ZERO) entry.second.divide(total, 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) else 0f
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.first, style = MaterialTheme.typography.labelLarge)
            Text(money(entry.second), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics { }) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent))
            }
            Text("${NumberFormat.getPercentInstance(ptBr).format(fraction.toDouble())} do recorte mensal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeonAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, accent: Color) {
    OutlinedCard(
        onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, accent.copy(alpha = .5f)),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = accent)
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable
private fun Metric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TransactionRow(row: Transaction, edit: (Transaction) -> Unit) {
    OutlinedCard(onClick = { edit(row) }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(bankName(row.bankPackage) + " · " + row.direction.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(money(row.amountCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(row.description.ifBlank { row.method.label() }, style = MaterialTheme.typography.bodyMedium)
            Text(row.category.label, style = MaterialTheme.typography.labelMedium)
            Text(row.method.label() + " · " + Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ptBr)), style = MaterialTheme.typography.labelMedium)
            if (row.status == ReviewStatus.REVIEW) Text("Revisar · ${row.reason.ifBlank { "Confirme valor e classificação" }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
