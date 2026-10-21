package br.com.fluxo.core

import java.text.Normalizer
import java.util.Locale

/**
 * Merchant name and category suggestion for a captured purchase. Works only on the notification text on the device;
 * a category the owner chose for a merchant before always wins over the keyword table.
 */
object MerchantCategorizer {
    private const val MAX_LENGTH = 60

    // "... em SUPER DA SE MERCADO    SAO PAULO     BRA, foi aprovada" / "APROVADA em MERCADO QUEIROZ para o cartão".
    // The name ends at a run of spaces (city padding), punctuation or the next clause.
    private val atMerchant = Regex(
        """\bem\s+([A-Za-zÀ-ÿ0-9&*'.\-][A-Za-zÀ-ÿ0-9&*'.\- ]*?)(?=\s{2,}|\s*[,;\n]|\s+(?i:para|foi|no|na|com|em|às|as|dia|valor|aprovad[oa]|autorizad[oa]|realizad[oa]|efetuad[oa]|confirmad[oa])\b|\.\s|\.?$)""",
        RegexOption.IGNORE_CASE,
    )
    // Words after "em" that are a state or a place in the message, not a merchant.
    private val notMerchant = Regex(
        "^(?:sua|seu|suas|seus|analise|processamento|aberto|andamento|breve|ate|instantes|nome|conta|cartao|" +
            "credito|debito|[0-9/:]+)\\b",
    )

    /** Merchant shown as the description, or null when the text names none with confidence. */
    fun merchant(packageName: String, text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val raw = if (WalletReceipt.isWallet(packageName)) {
            // Wallet receipts put the merchant in the title, above the "R$ X com cartão" line.
            lines.firstOrNull()?.takeIf { WalletReceipt.parse(text) != null }
        } else {
            val parsed = NotificationParser.parse(text)
            // Only card purchases name a merchant after "em"; Pix and transfers name people or accounts.
            if (parsed == null || parsed.direction != Direction.EXPENSE || parsed.method == PaymentMethod.PIX) null
            else lines.drop(1).ifEmpty { lines }.firstNotNullOfOrNull { line -> atMerchant.find(line)?.groupValues?.get(1) }
        }
        val name = raw?.replace(Regex("\\s+"), " ")?.trim(' ', '.', '-')?.take(MAX_LENGTH) ?: return null
        if (name.length < 2 || !name.any { it.isLetter() } || notMerchant.containsMatchIn(key(name))) return null
        return name
    }

    /** Lookup key for learned categories: no accents, case or repeated spaces. */
    fun key(merchant: String): String = Normalizer.normalize(merchant, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    // First match wins: specific stores come before generic words they contain ("mercado livre" before "mercado").
    private val keywords: List<Pair<Regex, Category>> = listOf(
        "mercado ?livre|mercadolivre|shopee|amazon|magalu|magazine luiza|americanas|shein|aliexpress|renner|riachuelo|c&a|zara|centauro|decathlon|kalunga" to Category.SHOPPING,
        "uber|99 ?app|99 ?pop|99 ?taxi|cabify|posto|auto ?posto|combustive|shell|ipiranga|petrobras|br mania|estacionamento|estapar|metro|cptm|sptrans|bilhete unico|pedagio|sem parar|veloe|conectcar|onibus|rodoviaria" to Category.TRANSPORT,
        "farmacia|drogaria|droga ?raia|drogasil|pague menos|panvel|hospital|clinica|laboratorio|odonto|otica|dentista" to Category.HEALTH,
        "supermercado|mercado|mercadinho|atacad|assai|carrefour|extra\\b|pao de acucar|dia supermercado|hortifruti|sacolao|acougue|padaria|panificadora|confeitaria|restaurante|lanchonete|lanches|pizzaria|hamburgueria|burger|mcdonald|mc donalds|bk\\b|subway|habibs|ifood|rappi|cafe|cafeteria|starbucks|sorveteria|doceria|bar\\b|food|refeicao|alimentacao" to Category.FOOD,
        "netflix|spotify|disney|hbo|prime video|globoplay|youtube|deezer|steam|playstation|xbox|nintendo|cinema|cinemark|ingresso|sympla|teatro|parque" to Category.LEISURE,
        "aluguel|condominio|enel|light\\b|sabesp|comgas|cemig|copel|celesc|energia|saneamento|imobiliaria" to Category.HOUSING,
        "vivo|claro|tim\\b|oi fibra|internet|telecom|academia|smart ?fit|bluefit|lavanderia|barbearia|salao|cabeleireiro|pet ?shop|petz|cobasi|seguro" to Category.SERVICES,
        "escola|colegio|faculdade|universidade|curso|udemy|alura|livraria|papelaria" to Category.EDUCATION,
    ).map { (words, category) -> Regex("(?:^|[^a-z])(?:$words)") to category }

    /** Learned choice for the merchant first, then the keyword table, otherwise no category. */
    fun suggest(merchant: String?, learned: (String) -> Category?): Category {
        if (merchant == null) return Category.UNCATEGORIZED
        val key = key(merchant)
        learned(key)?.let { return it }
        return keywords.firstOrNull { (pattern, _) -> pattern.containsMatchIn(key) }?.second ?: Category.UNCATEGORIZED
    }
}
