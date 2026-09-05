package br.com.fluxo.app

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import br.com.fluxo.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class BackupSummary(val transactions: Int, val budgets: Int, val deleted: Int)

/** Versioned logical snapshot. Never executes SQL or table names supplied by a file. */
internal object LedgerBackup {
    private val columns = linkedMapOf(
        "transactions" to listOf("id", "source_key", "bank_package", "amount_cents", "direction", "method", "status",
            "occurred_at", "description", "reason", "deleted", "category", "user_modified", "observation_hash", "last_observed_at"),
        "budgets" to listOf("month", "category", "limit_cents"),
        "notification_aliases" to listOf("identity_key", "source_key"),
    )
    data class Validated(val rows: Map<String, List<ContentValues>>, val summary: BackupSummary)

    fun export(db: SQLiteDatabase): ByteArray {
        val root = JSONObject().put("format", "fluxo-ledger").put("version", 1).put("schema", 3)
        columns.forEach { (table, names) ->
            val rows = JSONArray()
            db.query(table, names.toTypedArray(), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    require(rows.length() < 50_000) { "Limite de registros do backup excedido." }
                    val row = JSONObject()
                    names.forEachIndexed { i, name -> row.put(name, when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        else -> error("Tipo de dado não suportado.")
                    }) }
                    rows.put(row)
                }
            }
            root.put(table, rows)
        }
        return root.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupCipher.MAX_PLAINTEXT_BYTES) { "Backup acima do limite de 16 MiB." }
            validate(it)
        }
    }

    fun validate(payload: ByteArray): Validated {
        require(payload.size <= BackupCipher.MAX_PLAINTEXT_BYTES)
        val json = payload.toString(Charsets.UTF_8)
        // Bound parser recursion even for a deliberately crafted, authenticated file.
        var depth = 0
        var quoted = false
        var escaped = false
        json.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 8) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
        val root = JSONObject(json)
        require(root.get("format") == "fluxo-ledger" && number(root, "version") == 1L && number(root, "schema") == 3L)
        require(root.keys().asSequence().toSet() == columns.keys + setOf("format", "version", "schema"))
        val allRows = linkedMapOf<String, List<ContentValues>>()
        val sourceKeys = mutableSetOf<String>()
        var deleted = 0
        columns.forEach { (table, names) ->
            val rows = root.getJSONArray(table)
            require(rows.length() <= 50_000)
            val identities = mutableSetOf<String>()
            allRows[table] = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                require(row.keys().asSequence().toSet() == names.toSet())
                when (table) {
                    "transactions" -> {
                        require(identities.add(string(row, "id", 200).also { require(it.isNotBlank()) }))
                        optionalString(row, "source_key", 256)?.let { require(sourceKeys.add(it)) }
                        string(row, "bank_package", 200)
                        val amount = if (row.isNull("amount_cents")) null else number(row, "amount_cents").also { require(it > 0) }
                        val direction = Direction.valueOf(string(row, "direction"))
                        PaymentMethod.valueOf(string(row, "method"))
                        val status = ReviewStatus.valueOf(string(row, "status"))
                        Category.valueOf(string(row, "category"))
                        val removed = flag(row, "deleted")
                        flag(row, "user_modified")
                        if (removed == 1L) deleted++
                        if (removed == 0L && status == ReviewStatus.CONFIRMED) require(amount != null && direction != Direction.UNKNOWN)
                        require(number(row, "occurred_at") in -62135596800000L..253402300799999L)
                        require(number(row, "last_observed_at") >= 0)
                        string(row, "description", 200); string(row, "reason", 1000)
                        optionalString(row, "observation_hash", 64)?.let { require(it.matches(Regex("[a-f0-9]{64}"))) }
                    }
                    "budgets" -> {
                        val month = string(row, "month", 20)
                        require(YearMonth.parse(month).toString() == month)
                        val category = string(row, "category")
                        Category.valueOf(category)
                        require(number(row, "limit_cents") > 0)
                        require(identities.add("$month|$category"))
                    }
                    "notification_aliases" -> {
                        require(identities.add(string(row, "identity_key", 256)))
                        require(string(row, "source_key", 256) in sourceKeys)
                    }
                }
                ContentValues().apply { names.forEach { name -> when (val value = row.get(name)) {
                    JSONObject.NULL -> putNull(name)
                    is String -> put(name, value)
                    is Int -> put(name, value.toLong())
                    is Long -> put(name, value)
                    else -> error("Tipo inválido no backup.")
                } } }
            }
        }
        return Validated(allRows, BackupSummary(allRows.getValue("transactions").size - deleted,
            allRows.getValue("budgets").size, deleted))
    }

    fun restore(db: SQLiteDatabase, validated: Validated) {
        db.beginTransaction()
        try {
            columns.keys.reversed().forEach { db.delete(it, null, null) }
            validated.rows.forEach { (table, rows) -> rows.forEach { db.insertOrThrow(table, null, it) } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun number(row: JSONObject, name: String): Long = when (val value = row.get(name)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("Inteiro inválido.")
    }
    private fun flag(row: JSONObject, name: String) = number(row, name).also { require(it in 0L..1L) }
    private fun string(row: JSONObject, name: String, max: Int = 64): String =
        (row.get(name) as? String ?: throw IllegalArgumentException("Texto inválido.")).also { require(it.length <= max) }
    private fun optionalString(row: JSONObject, name: String, max: Int): String? =
        if (row.isNull(name)) null else string(row, name, max)
}
