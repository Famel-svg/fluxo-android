package br.com.fluxo.core

import java.io.Writer
import java.time.Instant

/**
 * Provisional triage mode: while it is on, every observed notification is kept on the device so the owner can
 * decide what becomes a transaction and export the samples to tune capture per app.
 * Names are persisted as text: never rename or reorder; append only.
 */
enum class TriageDecision { PENDING, KEPT, DISCARDED, AUTO_STORED }

data class TriageItem(
    val id: Long,
    val packageName: String,
    val appLabel: String?,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** Parser verdict at capture time: "ACCEPTED" or an [IgnoreReason] name. */
    val verdict: String,
    val decision: TriageDecision,
    val decidedAt: Long? = null,
)

object NotificationTriage {
    /** Oldest rows beyond this are dropped; triage is a sample, not an archive. */
    const val MAX_ITEMS = 300
    /** Raw text above this length is truncated before it is stored. */
    const val MAX_TEXT = 4_000
    const val APPROVED_REASON = "Autorizada manualmente na triagem; confira valor, direção e meio."

    /** Verdict label for the parser outcome, never content. */
    fun verdict(text: String): String = when (val screening = NotificationParser.screen(text)) {
        is NotificationParser.Screening.Accepted -> "ACCEPTED"
        is NotificationParser.Screening.Ignored -> screening.reason.name
    }

    /**
     * Fields for a notification the owner authorized by hand. Whatever the parser recognized is kept, but the
     * result always needs review: a manual approval is not evidence of amount or direction.
     */
    fun approved(text: String): ParsedNotification {
        val parsed = NotificationParser.parse(text)
        val reason = listOfNotNull(APPROVED_REASON, parsed?.reason?.takeIf { it.isNotBlank() }).joinToString(" ")
        return ParsedNotification(
            amountCents = parsed?.amountCents,
            direction = parsed?.direction ?: Direction.UNKNOWN,
            method = parsed?.method ?: PaymentMethod.UNKNOWN,
            status = ReviewStatus.REVIEW,
            reason = reason,
        )
    }

    /**
     * Owner-requested JSON export. It contains raw notification text: the caller must warn before writing it.
     * The per-app summary shows which package ids carry financial events.
     */
    fun writeJson(items: List<TriageItem>, writer: Writer, exportedAt: Long) {
        val byPackage = items.groupBy { it.packageName }.toSortedMap()
        writer.write("{\n")
        writer.write("  \"format\": \"fluxo-triage\",\n")
        writer.write("  \"version\": 1,\n")
        writer.write("  \"exportedAt\": ${string(Instant.ofEpochMilli(exportedAt).toString())},\n")
        writer.write("  \"apps\": [")
        byPackage.entries.forEachIndexed { index, (pkg, rows) ->
            writer.write(if (index == 0) "\n" else ",\n")
            val counts = TriageDecision.entries.joinToString(", ") { decision ->
                "${string(decision.name)}: ${rows.count { it.decision == decision }}"
            }
            val verdicts = rows.groupingBy { it.verdict }.eachCount().toSortedMap().entries
                .joinToString(", ") { "${string(it.key)}: ${it.value}" }
            writer.write("    {\"packageName\": ${string(pkg)}, \"appLabel\": ${nullable(rows.firstNotNullOfOrNull { it.appLabel })}, " +
                "\"total\": ${rows.size}, \"decisions\": {$counts}, \"verdicts\": {$verdicts}}")
        }
        writer.write(if (byPackage.isEmpty()) "],\n" else "\n  ],\n")
        writer.write("  \"notifications\": [")
        items.sortedBy { it.postedAt }.forEachIndexed { index, item ->
            writer.write(if (index == 0) "\n" else ",\n")
            writer.write("    {\"packageName\": ${string(item.packageName)}, \"appLabel\": ${nullable(item.appLabel)}, " +
                "\"postedAt\": ${string(Instant.ofEpochMilli(item.postedAt).toString())}, " +
                "\"title\": ${string(item.title)}, \"text\": ${string(item.text)}, " +
                "\"verdict\": ${string(item.verdict)}, \"decision\": ${string(item.decision.name)}, " +
                "\"decidedAt\": ${nullable(item.decidedAt?.let { Instant.ofEpochMilli(it).toString() })}}")
        }
        writer.write(if (items.isEmpty()) "]\n" else "\n  ]\n")
        writer.write("}\n")
    }

    private fun nullable(value: String?) = value?.let(::string) ?: "null"

    internal fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                // Control characters and line/paragraph separators break naive JSON readers.
                c < ' ' || c == ' ' || c == ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
