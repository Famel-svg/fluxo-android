package br.com.fluxo.app

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import br.com.fluxo.core.Invoice

@Composable
fun InvoicesPanel(invoices: List<Invoice>) {
    LazyColumn {
        items(invoices) { invoice ->
            Card {
                Text("Mês: ${invoice.month}")
                Text("Valor: R$ ${invoice.amountCents / 100.0}")
                Text("Status: ${if (invoice.isPaid) "Pago" else "Pendente"}")
            }
        }
    }
}
