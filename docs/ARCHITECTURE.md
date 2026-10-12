# Arquitetura e contratos

## Atualização 0.4
SQLite v5. Migrações são aditivas, exceto a v5, que remove a tabela `invoices` e a coluna `transactions.invoice_id`, criadas por engano apenas no caminho v1→v4 e nunca gravadas. Toda instalação converge no schema de `onCreate`; um teste instrumentado compara os caminhos. `BankNotificationListener.onListenerConnected` reenvia à fila as notificações ativas de fontes permitidas; a ingestão é idempotente por `source_key`. Interface: `MainActivity.kt` (navegação), `LedgerViewModel.kt`, `DashboardScreen.kt`, `SettingsScreen.kt`, `TransactionEditor.kt`, `Formatting.kt`.

Configuração inicial e benefícios por dia da semana: `BenefitSettings` ganha `vrWeekdays` e `vtWeekdays` (padrão segunda a sexta); `BenefitCalculator.scheduledDays` conta os dias do mês nesses dias e o ajuste manual do mês continua prevalecendo. Os conjuntos ficam em SharedPreferences `benefits` como máscara de 7 bits (segunda = bit 0; ausente ou inválida = segunda a sexta). Perfil local (`profile`: nome com até 40 caracteres e flag de onboarding) fica fora do SQLite, do backup e do CSV e sobrevive a "apagar histórico". O backup passa ao payload v4 (v3 mais `vr_weekdays` e `vt_weekdays`, listas ISO 1–7 estritamente crescentes dentro de `benefits`); v1, v2 e v3 continuam aceitos, e v3 restaura com segunda a sexta. `OnboardingScreen.kt` aparece uma vez antes das abas (também para quem já tinha dados) e pode ser reaberta em Ajustes; `WeekdayChips.kt` é compartilhado com o editor de benefícios em Planejar.

Saldo de vale: `PaymentMethod` ganha `MEAL_VOUCHER` e `TRANSPORT_VOUCHER` (acrescentados ao fim; os nomes são persistidos). Como o crédito, gasto confirmado com vale conta como consumo (`SpendingAnalytics`) mas fica fora das saídas de caixa, do resultado observado e dos saldos de conta (`PaymentMethod.affectsCash`), e não entra em transferências próprias nem no par de transferência do backup. `BenefitBalance.remaining` subtrai do depósito estimado do mês (`BenefitCalculator.estimate`) os gastos confirmados com o vale no mês, sem repasse de meses anteriores; o restante pode ser negativo e Início mostra "acima da estimativa". O parser de notificações não infere pagamento com vale: o usuário reclassifica em Registros (botão "VR" na revisão) ou no editor.
Salário mensal e assistente: a configuração inicial virou um assistente de três páginas (nome, salário mensal, VR e VT juntos), cada uma liberada só quando válida e com "pular esta etapa" que não grava nada daquela etapa; o estado (etapa e campos) é salvo com `rememberSaveable`. O salário líquido (`salary_monthly_cents`, 0 = não informado) fica em SharedPreferences `profile`, sobrevive a "apagar histórico", nunca vai ao CSV nem aos logs e entra no backup como payload v5 (v4 mais `profile` com apenas `salary_monthly_cents` >= 0; o nome continua fora). v1 a v4 restauram como antes e não alteram o salário. `MoneyParser` e `SalaryPlanner` (core) validam o valor e calculam o percentual gasto do salário; Planejar mostra "Renda prevista do mês" (salário + VR + VT) e "Gastos do mês: N% do salário" apenas como estimativa de planejamento, sem alterar renda, despesas, caixa nem o resultado observado.

Aparência e movimento: `FluxoColors` continua sendo a API de cores, mas cada propriedade lê a `FluxoPalette` atual (instâncias `Light` e `Dark`, "Petróleo escuro") de um `FluxoPaletteHolder` baseado em estado do Compose; `FluxoVisualTheme(mode: ThemeMode = SYSTEM)` resolve Sistema/Claro/Escuro (`isSystemInDarkTheme()` para Sistema), publica a paleta (interpolada em 250 ms na troca) e monta `lightColorScheme` ou `darkColorScheme` a partir dela, então diálogos, campos, switches e menus acompanham. Novos papéis de cor devem entrar na paleta (por exemplo `OnHero`, `OnInk`, `OnAmber`), nunca como `Color(0x…)` ou `Color.White` na UI; os pares de texto e fundo de ambas as paletas são verificados por `ContrastTest` (WCAG 4,5:1 para texto). A escolha fica em `LedgerRepository.themeMode` (`StateFlow<ThemeMode>`, `suspend setThemeMode`), no arquivo de preferências do perfil; sobrevive a `clear()` e não entra no backup nem no CSV. `MainActivity` lê a escolha de forma síncrona para o fundo da janela e os ícones das barras do sistema, e `values-night/styles.xml` cobre o Sistema escuro. Transições: troca de aba com `AnimatedContent` (fade com deslocamento de 1/12 da largura, 200 ms) e `SaveableStateProvider` por aba, que mantém busca e rolagem; pill da navegação, barras de orçamento e de VR, valores do herói e do VR (`Crossfade`), mudança de mês e abertura do editor animam por 180 a 300 ms; `animateItem` em Registros. Sem animações infinitas; a configuração "remover animações" do Android zera a duração.

## Atualização 0.2
Backup criptografado usa payload JSON v3 (schema SQLite 4): além das tabelas, inclui `benefits` (tarifas VR/VT em centavos e dias por mês, de SharedPreferences); v1/v2 continuam aceitos e não alteram benefícios (a versão 0.4 acrescenta o payload v4, descrito acima).

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
