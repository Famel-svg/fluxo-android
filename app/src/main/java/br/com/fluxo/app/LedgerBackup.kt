package br.com.fluxo.app

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import br.com.fluxo.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class BackupSummary(val transactions: Int, val budgets: Int, val deleted: Int, val accounts: Int = 0, val benefits: Boolean = false)

/** Versioned logical snapshot. Never executes SQL or table names supplied by a file. */
internal object LedgerBackup {
    private val columns = linkedMapOf(
        "accounts" to listOf("id", "name", "bank_package", "opening_cents", "opening_at", "archived", "type"),
        "transactions" to listOf("id", "source_key", "bank_package", "amount_cents", "direction", "method", "status",
            "occurred_at", "description", "reason", "deleted", "category", "user_modified", "observation_hash", "last_observed_at", "account_id", "transfer_id"),
        "budgets" to listOf("month", "category", "limit_cents"),
        "notification_aliases" to listOf("identity_key", "source_key"),
    )
    data class Validated(val rows: Map<String, List<ContentValues>>, val summary: BackupSummary, val benefits: BenefitSettings? = null)

    fun export(db: SQLiteDatabase, benefits: BenefitSettings): ByteArray {
        val root = JSONObject().put("format", "fluxo-ledger").put("version", 4).put("schema", 4)
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
        root.put("benefits", benefitsJson(benefits))
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
        require(root.get("format") == "fluxo-ledger")
        if (number(root, "version") == 1L && number(root, "schema") == 3L) {
            require(root.keys().asSequence().toSet() == setOf("format", "version", "schema", "transactions", "budgets", "notification_aliases"))
            val rows = root.getJSONArray("transactions")
            require(rows.length() <= 50_000)
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                require(!row.has("account_id") && !row.has("transfer_id"))
                row.put("account_id", JSONObject.NULL).put("transfer_id", JSONObject.NULL)
            }
            root.put("accounts", JSONArray()).put("version", 2).put("schema", 4)
        }
        val version = number(root, "version")
        require((version == 2L || version == 3L || version == 4L) && number(root, "schema") == 4L)
        val withBenefits = version >= 3L
        require(root.keys().asSequence().toSet() ==
            columns.keys + setOf("format", "version", "schema") + (if (withBenefits) setOf("benefits") else emptySet()))
        val benefits = if (withBenefits) parseBenefits(root.get("benefits"), withWeekdays = version == 4L) else null
        val allRows = linkedMapOf<String, List<ContentValues>>()
        val sourceKeys = mutableSetOf<String>()
        val accountIds = mutableSetOf<String>()
        var deleted = 0
        columns.forEach { (table, names) ->
            val rows = root.getJSONArray(table)
            require(rows.length() <= 50_000)
            val identities = mutableSetOf<String>()
            allRows[table] = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                require(row.keys().asSequence().toSet() == names.toSet())
                when (table) {
                    "accounts" -> {
                        require(accountIds.add(string(row, "id", 200).also { require(it.isNotBlank()) }))
                        require(string(row, "name", 80).isNotBlank())
                        val bank = string(row, "bank_package", 200)
                        require(bank == "manual" || supportedBanks.any { it.packageName == bank })
                        number(row, "opening_cents")
                        require(number(row, "opening_at") in -62135596800000L..253402300799999L)
                        flag(row, "archived"); AccountType.valueOf(string(row, "type"))
                    }
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
                        optionalString(row, "account_id", 200)?.let { require(it in accountIds) }
                        optionalString(row, "transfer_id", 200)?.let { require(it.isNotBlank()) }
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
        allRows.getValue("transactions").filter { it.getAsString("transfer_id") != null }.groupBy { it.getAsString("transfer_id") }.values.forEach { pair ->
            require(pair.size == 2)
            require(pair.all { it.getAsLong("deleted") == 0L && it.getAsLong("user_modified") == 1L && it.getAsString("status") == "CONFIRMED" &&
                it.getAsString("account_id") != null && PaymentMethod.valueOf(it.getAsString("method")).affectsCash })
            require(pair.map { it.getAsString("account_id") }.toSet().size == 2)
            require(pair.map { it.getAsString("direction") }.toSet() == setOf("INCOME", "EXPENSE"))
            require(pair[0].getAsLong("amount_cents") == pair[1].getAsLong("amount_cents"))
        }
        return Validated(allRows, BackupSummary(allRows.getValue("transactions").size - deleted,
            allRows.getValue("budgets").size, deleted, accountIds.size, withBenefits), benefits)
    }

    fun restore(db: SQLiteDatabase, validated: Validated) {
        db.beginTransaction()
        try {
            columns.keys.reversed().forEach { db.delete(it, null, null) }
            validated.rows.forEach { (table, rows) -> rows.forEach { db.insertOrThrow(table, null, it) } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun benefitsJson(settings: BenefitSettings): JSONObject {
        val days = JSONArray()
        settings.overrides.toSortedMap().forEach { (month, d) ->
            days.put(JSONObject().put("month", month.toString())
                .put("vr", d.vr ?: JSONObject.NULL).put("vt", d.vt ?: JSONObject.NULL))
        }
        return JSONObject().put("vr_daily_cents", settings.vrDailyCents)
            .put("vt_daily_cents", settings.vtDailyCents).put("days", days)
            .put("vr_weekdays", weekdaysJson(settings.vrWeekdays))
            .put("vt_weekdays", weekdaysJson(settings.vtWeekdays))
    }

    private fun weekdaysJson(days: Set<java.time.DayOfWeek>) =
        JSONArray().also { array -> days.map { it.value }.sorted().forEach { array.put(it) } }

    /** ISO day numbers 1..7, strictly ascending (sorted and unique); anything else is rejected. */
    private fun parseWeekdays(obj: JSONObject, name: String): Set<java.time.DayOfWeek> {
        val array = obj.get(name) as? JSONArray ?: throw IllegalArgumentException("Dias da semana inválidos.")
        require(array.length() <= 7)
        var previous = 0L
        val days = linkedSetOf<java.time.DayOfWeek>()
        for (i in 0 until array.length()) {
            val value = when (val item = array.get(i)) {
                is Int -> item.toLong()
                is Long -> item
                else -> throw IllegalArgumentException("Dia da semana inválido.")
            }
            require(value in 1L..7L && value > previous) { "Dia da semana inválido." }
            previous = value
            days += java.time.DayOfWeek.of(value.toInt())
        }
        return days
    }

    private fun parseBenefits(value: Any, withWeekdays: Boolean): BenefitSettings {
        val obj = value as? JSONObject ?: throw IllegalArgumentException("Benefícios inválidos.")
        require(obj.keys().asSequence().toSet() == setOf("vr_daily_cents", "vt_daily_cents", "days") +
            (if (withWeekdays) setOf("vr_weekdays", "vt_weekdays") else emptySet()))
        val vrRate = number(obj, "vr_daily_cents").also { require(it >= 0) }
        val vtRate = number(obj, "vt_daily_cents").also { require(it >= 0) }
        val days = obj.get("days") as? JSONArray ?: throw IllegalArgumentException("Benefícios inválidos.")
        require(days.length() <= 1200)
        val overrides = linkedMapOf<YearMonth, BenefitDays>()
        for (i in 0 until days.length()) {
            val entry = days.get(i) as? JSONObject ?: throw IllegalArgumentException("Benefícios inválidos.")
            require(entry.keys().asSequence().toSet() == setOf("month", "vr", "vt"))
            val text = string(entry, "month", 20)
            val month = try { YearMonth.parse(text) } catch (_: java.time.DateTimeException) { throw IllegalArgumentException("Mês inválido.") }
            require(month.toString() == text)
            fun count(name: String): Int? = if (entry.isNull(name)) null
                else number(entry, name).also { require(it in 0L..month.lengthOfMonth().toLong()) }.toInt()
            val vr = count("vr")
            val vt = count("vt")
            require(vr != null || vt != null)
            require(overrides.put(month, BenefitDays(vr, vt)) == null)
        }
        val vrWeekdays = if (withWeekdays) parseWeekdays(obj, "vr_weekdays") else BenefitCalculator.WORKDAYS
        val vtWeekdays = if (withWeekdays) parseWeekdays(obj, "vt_weekdays") else BenefitCalculator.WORKDAYS
        return BenefitSettings(vrRate, vtRate, overrides.toMap(), vrWeekdays, vtWeekdays)
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
