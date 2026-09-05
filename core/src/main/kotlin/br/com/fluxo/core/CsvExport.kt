package br.com.fluxo.core

import java.io.Writer
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** User-requested portability export, not a restorable database backup. */
object CsvExport {
    fun write(rows: List<Transaction>, writer: Writer, zone: ZoneId = ZoneId.systemDefault()) {
        writer.write("\uFEFF")
        record(writer, listOf("Data", "Banco", "Descrição", "Categoria", "Valor (BRL)", "Direção", "Meio", "Status", "Motivo da revisão"))
        val dateFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ssXXX")
        rows.forEach { row ->
            record(writer, listOf(
                Instant.ofEpochMilli(row.occurredAt).atZone(zone).format(dateFormat),
                when (row.bankPackage) {
                    "com.c6bank.app" -> "C6 Bank"
                    "com.nu.production" -> "Nubank"
                    "com.itau" -> "Itaú"
                    "manual" -> "Manual"
                    else -> row.bankPackage
                },
                row.description,
                row.category.label,
                row.amountCents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') }.orEmpty(),
                when (row.direction) { Direction.INCOME -> "Entrada"; Direction.EXPENSE -> "Saída"; Direction.UNKNOWN -> "Não definida" },
                when (row.method) { PaymentMethod.PIX -> "Pix"; PaymentMethod.CREDIT_CARD -> "Cartão de crédito"; PaymentMethod.DEBIT_CARD -> "Cartão de débito"; PaymentMethod.UNKNOWN -> "Outro / não informado" },
                if (row.status == ReviewStatus.CONFIRMED) "Confirmado" else "Revisar",
                row.reason,
            ))
        }
    }

    private fun record(writer: Writer, cells: List<String>) {
        writer.write(cells.joinToString(";") { quote(it) })
        writer.write("\r\n")
    }

    private fun quote(value: String): String {
        // Quotes alone do not prevent spreadsheet formula execution.
        val startsWithControl = value.firstOrNull()?.let { it == '\t' || it == '\r' || it == '\n' } == true
        val risky = value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || startsWithControl
        val safe = if (risky) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
