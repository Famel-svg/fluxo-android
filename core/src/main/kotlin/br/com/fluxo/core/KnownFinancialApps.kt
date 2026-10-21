package br.com.fluxo.core

/**
 * Packages the Play build may read notifications from. OWNER: review this list before publishing.
 * Only package names known to be real are listed; VR/VT/benefit apps (Alelo, Sodexo/Pluxee, Ticket, Flash, Ben, VR)
 * are still missing because their package names must be confirmed on the Play Store first.
 */
object KnownFinancialApps {
    val packages: Set<String> = setOf(
        "com.nu.production", // Nubank
        "com.itau", // Itaú
        "com.c6bank.app", // C6 Bank
        "br.com.intermedium", // Inter
        "br.com.bb.android", // Banco do Brasil
        "com.bradesco", // Bradesco
        "com.santander.app", // Santander
        "br.com.gabba.Caixa", // Caixa
        "br.com.original.bank", // Original
        "br.com.uol.ps.myaccount", // PagBank
        "com.picpay", // PicPay
        "com.mercadopago.wallet", // Mercado Pago
    ) + WalletReceipt.packages // Carteira do Google: recibos de aproximação

    fun contains(packageName: String): Boolean = packageName in packages
}
