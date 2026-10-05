package br.com.fluxo.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.fluxo.core.Category
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import br.com.fluxo.core.TransactionSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation only. The caller supplies the month slice and the existing edit action. */
@Composable
internal fun RecordsScreen(
    rows: List<Transaction>,
    filter: Int,
    setFilter: (Int) -> Unit,
    allMonths: Boolean,
    setAllMonths: (Boolean) -> Unit,
    edit: (Transaction) -> Unit,
    bankOptions: List<String?>,
    bankLabel: (String) -> String,
    moneyLabel: (Long?) -> String,
    directionLabel: (Direction) -> String,
    methodLabel: (PaymentMethod) -> String
) {
    var query by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf<String?>(null) }
    var method by rememberSaveable { mutableStateOf<PaymentMethod?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(rows, filter, query, bank, method, category) {
        TransactionSearch.filter(rows.filter { when (filter) { 1 -> it.direction == Direction.INCOME; 2 -> it.direction == Direction.EXPENSE; 3 -> it.status == ReviewStatus.REVIEW; else -> true } }, query, bank, method, category).sortedByDescending { it.occurredAt }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val groups = remember(filtered, zone) {
        filtered.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Registros", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                }
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Buscar descrição, banco ou categoria") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(20.dp),
                        // Allow the label and input to grow at accessibility font sizes.
                        singleLine = false
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Todos", "Entradas", "Saídas", "Revisar").forEachIndexed { index, label ->
                            RecordFilterChip(filter == index, { setFilter(index) }, label)
                        }
                    }
                }
                item { RecordFilterChip(allMonths, { setAllMonths(!allMonths) }, "Todos os meses") }
                item {
                    TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (advanced) "Ocultar filtros" else "Banco, meio e categoria")
                    }
                }
                if (advanced) {
                    item { Choice("Banco", bank, bankOptions, { it?.let(bankLabel) ?: "Todos" }) { bank = it } }
                    item { Choice("Meio", method, listOf<PaymentMethod?>(null) + PaymentMethod.entries, { it?.let(methodLabel) ?: "Todos" }) { method = it } }
                    item { Choice("Categoria", category, listOf<Category?>(null) + Category.entries, { it?.label ?: "Todas" }) { category = it } }
                }
                item {
                    // Vertical controls keep the count and action readable at large font sizes.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${filtered.size} lançamento(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { query = ""; bank = null; method = null; category = null; setFilter(0); setAllMonths(false) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Limpar filtros") }
                    }
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Nenhum lançamento", style = MaterialTheme.typography.titleMedium)
                        Text("Nenhum registro corresponde aos filtros. Altere a busca ou escolha Todos os meses.")
                    }
                }
                groups.forEach { (date, transactions) ->
                    item {
                        val title = when (date) {
                            today -> "Hoje"
                            today.minusDays(1) -> "Ontem"
                            else -> date.format(dateFormat)
                        }
                        Column(Modifier.padding(top = 8.dp).semantics { heading() }) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (date == today || date == today.minusDays(1)) {
                                Text(date.format(dateFormat), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(transactions, key = { it.id }) { transaction ->
                        RecordCard(transaction, edit, bankLabel, moneyLabel, directionLabel, methodLabel, zone, timeFormat)
                    }
                }
            }
    }
}

@Composable
private fun RecordFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun RecordCard(
    row: Transaction,
    edit: (Transaction) -> Unit,
    bankLabel: (String) -> String,
    moneyLabel: (Long?) -> String,
    directionLabel: (Direction) -> String,
    methodLabel: (PaymentMethod) -> String,
    zone: ZoneId,
    timeFormat: DateTimeFormatter
) {
    val fontScale = LocalDensity.current.fontScale
    val reviewing = row.status == ReviewStatus.REVIEW
    OutlinedCard(
        onClick = { edit(row) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (reviewing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(bankLabel(row.bankPackage) + " · " + directionLabel(row.direction),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val description: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.description.ifBlank { methodLabel(row.method) },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(row.category.label, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val amount: @Composable () -> Unit = {
                    Text(moneyLabel(row.amountCents), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.direction == Direction.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                if (maxWidth < 360.dp || fontScale >= 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { description(); amount() }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { description() }
                        Box(Modifier.weight(1f)) { amount() }
                    }
                }
            }
            Text(methodLabel(row.method) + " · " + Instant.ofEpochMilli(row.occurredAt).atZone(zone).format(timeFormat),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reviewing) {
                Text("Revisar · ${row.reason.ifBlank { "Confirme valor e classificação" }}",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Confirmada", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
