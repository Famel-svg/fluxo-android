# Arquitetura e contratos

Módulo `core`: Kotlin/JVM puro, parser e modelos testáveis sem Android.
Módulo `app`: Compose → LedgerViewModel → LedgerRepository → SQLite; NotificationListenerService → parser → repository. Um banco por processo, IO serializado via SQLite; StateFlow sinaliza atualização das telas.

## Contrato Kotlin compartilhado
Pacote `br.com.fluxo.core`:
`enum class Direction { INCOME, EXPENSE, UNKNOWN }`
`enum class PaymentMethod { PIX, CREDIT_CARD, DEBIT_CARD, UNKNOWN }`
`enum class ReviewStatus { CONFIRMED, REVIEW }`
`data class ParsedNotification(val amountCents: Long?, val direction: Direction, val method: PaymentMethod, val status: ReviewStatus, val reason: String)`
`object NotificationParser { fun parse(text: String): ParsedNotification? }`
`data class Transaction(val id: String, val sourceKey: String?, val bankPackage: String, val amountCents: Long?, val direction: Direction, val method: PaymentMethod, val status: ReviewStatus, val occurredAt: Long, val description: String, val reason: String = "")`

Pacote `br.com.fluxo.app`:
`data class Bank(val packageName: String, val name: String)`; `val supportedBanks: List<Bank>`.
`class LedgerRepository(context: Context)` com `val transactions: StateFlow<List<Transaction>>`, `val captureEnabled: StateFlow<Boolean>`, `val enabledBanks: StateFlow<Set<String>>`; `suspend fun refresh()`, `suspend fun save(transaction: Transaction)`, `suspend fun delete(id: String)`, `suspend fun clear()`, `fun setCaptureEnabled(value: Boolean)`, `fun setBankEnabled(packageName: String, enabled: Boolean)`.
`class FluxoApplication : Application` com `val repository: LedgerRepository`.
UI usa repository em ViewModel e chama escrita em `viewModelScope.launch`; erros exibidos, nunca engolidos. Serviço recebe repository via Application; entrada `suspend fun ingest(sourceKey: String, bankPackage: String, occurredAt: Long, text: String)` disponível no repository.

SQLite v1; migration destrutiva proibida. `source_key` UNIQUE, INSERT OR IGNORE para captura; edição por id. Exclusão lógica mantém fingerprint; limpeza total explicitamente remove histórico e fingerprints. Nenhum texto bancário bruto guardado. Dados protegidos pelo sandbox/criptografia do dispositivo, sem alegação de criptografia própria de banco.

## Evolução
Adaptadores por banco quando houver amostras anonimizadas; exportação e backup sob consentimento; sincronização exige contrato HTTP, autenticação e política de retenção em decisão futura. Backend embarcado atende MVP offline; não existe servidor implantado nesta entrega.
