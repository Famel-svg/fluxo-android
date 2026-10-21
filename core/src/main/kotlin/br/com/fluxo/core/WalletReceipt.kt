package br.com.fluxo.core

import java.text.Normalizer
import java.util.Locale

/**
 * Tap-to-pay receipts from wallet apps, e.g. "MERCADO\nR$ 7,98 com Cartão C6 Mastercard ••1234".
 * They carry no financial verb and usually repeat the card issuer's own purchase notification, so they are
 * always stored for review and folded into the issuer's notification when both arrive.
 */
object WalletReceipt {
    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    val packages = setOf(GOOGLE_WALLET)
    /** Issuer and wallet notifications of one purchase arrive seconds apart; minutes cover delayed delivery. */
    const val MATCH_WINDOW_MS = 15 * 60_000L
    const val REASON = "Pagamento por aproximação na carteira digital; confira se o banco não notificou a mesma compra."

    // The whole receipt line: one amount, "com", a card name and its masked final digits.
    private val receipt = Regex("""(?<![a-z0-9])r\$ ?((?:[0-9]+|[1-9][0-9]{0,2}(?:\.[0-9]{3})+),[0-9]{2}) com [^\n]{1,80}?(?:\u2022+|\*+|final) ?[0-9]{4}\b""")

    // Refunds and failed payments share the receipt layout but are not a purchase.
    private val notAPurchase = Regex("\\b(estorn|reembols|devolu|recusad|negad|cancelad|falh|nao aprovad)")

    fun isWallet(packageName: String) = packageName in packages

    /** Parsed receipt, or null when the wallet text is not a payment receipt. */
    fun parse(text: String): ParsedNotification? {
        if (text.isBlank() || text.length > 1_000) return null
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('\u00a0', ' ')
            .replace(Regex("[ \\t]+"), " ").trim()
        // A second amount means it is not a single receipt line.
        if (Regex("r\\$").findAll(normalized).count() != 1 || notAPurchase.containsMatchIn(normalized)) return null
        val match = receipt.find(normalized) ?: return null
        val cents = match.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()?.takeIf { it > 0 } ?: return null
        val method = when {
            Regex("\\bcredito\\b").containsMatchIn(normalized) -> PaymentMethod.CREDIT_CARD
            Regex("\\bdebito\\b").containsMatchIn(normalized) -> PaymentMethod.DEBIT_CARD
            else -> PaymentMethod.UNKNOWN
        }
        return ParsedNotification(cents, Direction.EXPENSE, method, ReviewStatus.REVIEW, REASON)
    }

    /** Same gate ingestion applies for [packageName]: wallet receipts have their own reader. */
    fun screen(packageName: String, text: String): NotificationParser.Screening =
        if (isWallet(packageName)) parse(text)?.let { NotificationParser.Screening.Accepted(it) }
            ?: NotificationParser.screen(text)
        else NotificationParser.screen(text)
}
