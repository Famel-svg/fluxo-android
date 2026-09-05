# Fluxo 0.2 — pesquisa e plano por funcionalidade

Estado: plano aprovado pelo pedido de implementação; pesquisa em 2026-09-05. Escopo implementável mantendo dados locais, sem serviço pago.

## Pesquisa e decisão
Fontes primárias: [Organizze](https://www.organizze.com.br/) apresenta limites por categoria; [Mobills](https://www.mobills.com.br/blog/mobills/como-utilizar-o-mobills/) descreve categorias, planejamento e relatórios; [YNAB](https://www.ynab.com/features) oferece metas e visão dos gastos; [novidades YNAB](https://www.ynab.com/whats-new) incluem busca aprimorada. São evidências de recursos existentes, não prova de benefício para este usuário.

Inferência para Fluxo: os cinco incrementos abaixo têm utilidade direta sobre dados já capturados e dispensam credenciais bancárias. Risco médio: cobertura parcial das notificações pode subestimar orçamento. Relatórios devem excluir revisão e avisar cobertura parcial. Menor validação: categorizar despesas sintéticas, definir limite e conferir comparação/exports, depois repetir com uso real.

## 1. Categorias de lançamentos
Problema: lista não explica onde o dinheiro foi gasto. Entrega: seleção manual de categoria, etiqueta na lista, persistência e migração SQLite v1→v2 sem perda. Categorias iniciais: Sem categoria, Alimentação, Moradia, Transporte, Saúde, Educação, Lazer, Compras, Serviços, Salário, Outros. Captura começa Sem categoria; não adivinhar estabelecimento a partir de mensagens incompletas.
Aceite: atribuir/editar, reiniciar, reentregar notificação e preservar categoria; versão antiga migra com Sem categoria.

## 2. Orçamento por categoria/mês
Problema: falta limite comparável ao gasto observado. Entrega: limite positivo em BRL para categoria e mês escolhido, editar/remover, gasto e restante/excesso com barra e alerta textual. Despesa confirmada inclui compras no crédito por compromisso; pendências e entradas não consomem limite. Não transportar limites automaticamente entre meses. Não recomendar valor ou percentual de renda.
Aceite: R$ 120 de despesas (crédito+débito) em limite R$ 100 mostra excesso R$ 20; outro mês e itens em revisão não afetam resultado. Limpeza integral apaga também limites com aviso explícito.

## 3. Relatório mensal por categoria
Problema: total isolado esconde distribuição. Entrega: tela Planejamento reúne despesas confirmadas por categoria, quantidade, proporção e comparação com mês anterior. Separar crédito do caixa no resumo existente; relatório por categoria mede despesas assumidas, não saldo. Percentual com base zero deve ser explicado, nunca infinito/NaN.
Aceite: soma das categorias coincide com despesas confirmadas no mês, Long agregado sem overflow; incluir Sem categoria para não esconder despesas.

## 4. Busca e filtros combinados
Problema: localizar compra específica fica difícil. Entrega: busca sem distinção de acento/caixa por descrição e nome do banco/categoria, filtros por banco, meio, categoria e direção/revisão; opção Todos os meses e limpeza dos filtros. Mostrar contagem e estado vazio.
Aceite: combinação não mistura OR/AND indevidamente; “itau” encontra Itaú; limpar restaura lista; lançamento de mês antigo encontrado em Todos os meses.

## 5. Exportação CSV
Problema: dados ficam presos no aparelho. Entrega: exportar histórico visível completo (não excluídos), com campos data, banco, descrição, categoria, valor, direção, meio e status, via seletor de destino Android. Diálogo explica conteúdo e ausência de criptografia; usuário escolhe local. Não é backup restaurável, nem inclui texto bruto, chaves técnicas ou tombstones.
Formato: UTF-8 com BOM, separador ponto e vírgula, CRLF, decimais com vírgula, aspas/linhas escapadas, células textuais protegidas contra fórmulas. IO fora da main thread, falha/cancelamento com estado correto. Referências: [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [OWASP CSV Injection](https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/07-Input_Validation_Testing/21-Testing_for_CSV_Injection/).
Aceite: acentos, aspas, ; e novas linhas preservados; descrição iniciada por = não vira fórmula; revisão mantém valor vazio quando ausente; falha de provider não informa sucesso.

## Contratos para agentes
Core: acrescentar `enum class Category(val label: String)` com constantes `UNCATEGORIZED, FOOD, HOUSING, TRANSPORT, HEALTH, EDUCATION, LEISURE, SHOPPING, SERVICES, SALARY, OTHER`. `Transaction.category: Category = Category.UNCATEGORIZED` como último parâmetro. `data class Budget(val month: String, val category: Category, val limitCents: Long)`.
Core analytics: `object SpendingAnalytics { fun byCategory(rows: List<Transaction>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Map<Category, BigInteger> }` retornando centavos confirmados EXPENSE, sem overflow. `object TransactionSearch { fun filter(rows: List<Transaction>, query: String = "", bankPackage: String? = null, method: PaymentMethod? = null, category: Category? = null): List<Transaction> }` com busca pelos nomes conhecidos e descrição. Direção/mês/status filtrados pela UI antes.
Repository: `val budgets: StateFlow<List<Budget>>`; `suspend fun saveBudget(budget: Budget)`; `suspend fun deleteBudget(month: String, category: Category)`; refresh lê ambos; clear apaga ambos e pausa captura. Validação de month YearMonth e valor positivo. SQLite v2 ALTER category + tabela budgets PK(month,category).
UI: manter callbacks atuais; adicionar quarta aba Planejamento antes de Ajustes; ajustes chama composable `ExportCsvCard(transactions: List<Transaction>)` implementado pelo agente principal em arquivo separado. Tela planejamento usa contracts acima.

## Divisão e sequência
1. Agente dados: modelos, busca, analytics, migração, orçamento e testes.
2. Agente interface: categorias no formulário, busca/filtros, Planejamento e testes UI.
3. Agente principal: pesquisa/plano, CSV e seletor, integração, testes completos, commits e release privada 0.2.0.

## Ideias avaliadas para depois
Recorrências, gestão completa de faturas, backup restaurável, biometria e nuvem continuam backlog: exigem respectivamente conciliação para evitar duplicidade, calendário/cartões, recuperação segura e decisões de autenticação/hospedagem. Não integram as cinco entregas prometidas nesta versão. Adaptadores reais dos bancos continuam dependentes de amostras anonimizadas.
