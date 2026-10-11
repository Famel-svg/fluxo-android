# Arquitetura e contratos

## Atualização 0.4
SQLite v5. Migrações são aditivas, exceto a v5, que remove a tabela `invoices` e a coluna `transactions.invoice_id`, criadas por engano apenas no caminho v1→v4 e nunca gravadas. Toda instalação converge no schema de `onCreate`; um teste instrumentado compara os caminhos. `BankNotificationListener.onListenerConnected` reenvia à fila as notificações ativas de fontes permitidas; a ingestão é idempotente por `source_key`. Interface: `MainActivity.kt` (navegação), `LedgerViewModel.kt`, `DashboardScreen.kt`, `SettingsScreen.kt`, `TransactionEditor.kt`, `Formatting.kt`.

## Atualização 0.2
Backup criptografado usa payload JSON v3 (schema SQLite 4): além das tabelas, inclui `benefits` (tarifas VR/VT em centavos e dias por mês, de SharedPreferences); v1/v2 continuam aceitos e não alteram benefícios.

Modelo `Transaction` recebe `category: Category = UNCATEGORIZED`. `Category` contém rótulos em português; `Budget(month, category, limitCents)` identifica limite por mês/categoria. SQLite v2 adiciona coluna com valor padrão e tabela `budgets` via migração aditiva; limpeza integral usa transação para remover ambos os conjuntos.

`SpendingAnalytics.byCategory` agrega centavos em `BigInteger`, usa mês/fuso explícitos e somente despesas confirmadas. `TransactionSearch.filter` combina condições por AND e normaliza acentos/caixa. `CsvExport.write` produz UTF-8/BOM, CSV separado por ponto e vírgula com proteção de fórmulas; nenhum identificador interno ou texto bruto exportado.

`PlanningScreen` usa flows de lançamentos/limites do repository; valor de mês fica congelado no diálogo durante edição. `CsvExportViewModel` mantém snapshot em memória entre escolha do destino e gravação IO; sem snapshot após morte do processo exige reiniciar exportação. Arquivo selecionado pelo sistema, sem permissão ampla de armazenamento. Contratos completos em `PLAN_0.2.md`.

## Base da versão 0.1

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
