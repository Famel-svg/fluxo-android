# Fluxo — especificação do MVP

Estado: especificação inicial, implementação autorizada em 2026-09-05.

## Objetivo e público
Aplicativo Android pessoal para Rafael acompanhar movimentações notificadas por C6 Bank, Nubank e Itaú. Interface em português, moeda BRL. Detectar Pix recebido/enviado e distinguir direção financeira de modalidade de cartão. Sem movimentar dinheiro, credenciais bancárias ou integração via scraping.

## Decisões iniciais
- Android nativo, Kotlin, Jetpack Compose e Material 3; Android 8/API 26 ou superior.
- Backend embarcado: parser determinístico, regras financeiras e SQLite privado. Funciona offline, sem login nem servidor necessário. Sincronização em nuvem fica fora desta primeira versão, pois não foi solicitada na resposta de esclarecimento.
- Bancos habilitados individualmente pelo usuário; captura inicialmente desligada. Pacotes iniciais: com.nu.production, com.c6bank.app, com.itau. Pacotes precisam ser conferidos no aparelho; uma marca pode ter outros aplicativos.
- Capturar somente notificações futuras dos aplicativos selecionados. Acesso especial via NotificationListenerService; autorização concedida nas configurações Android.
- Nenhuma notificação bruta enviada à rede, registrada em logs ou incluída em fixtures reais. Sem permissão INTERNET. Backup automático desabilitado.
- GitHub privado na conta autenticada, commits por incremento revisável.

## Semântica financeira
Direção: INCOME (entrada), EXPENSE (saída), UNKNOWN (revisão). Meio: PIX, CREDIT_CARD, DEBIT_CARD, UNKNOWN. “Crédito em conta” não significa compra no cartão de crédito. Pix recebido é entrada, Pix enviado é saída. Compra no crédito é despesa comprometida e não saída imediata da conta. Dashboard apresenta entradas registradas, saídas de caixa, compras no crédito e resultado observado; nunca chama resultado parcial de saldo bancário.

Pagamento de fatura é transferência/liquidação de dívida, não nova compra. Texto de fatura, estorno, devolução, agendamento, transação recusada ou mensagem com múltiplos valores vai para revisão ou é ignorado quando não representa movimentação. Não inventar valor, banco, modalidade ou direção. Eventos pendentes ficam fora dos totais. Usuário pode corrigir valor, direção e meio ou excluir.

## Fluxos e telas
1. Início: explicação de privacidade; seleção dos bancos; botão para habilitar acesso; estado de captura e pausa.
2. Resumo: mês atual, navegação por mês, entradas, saídas de caixa, crédito e resultado observado, indicação de cobertura parcial.
3. Lançamentos: lista ordenada, filtro por direção/revisão, origem, valor, meio e data; inclusão manual e edição/exclusão.
4. Revisão: mensagens ambíguas com motivo, confirmação/correção manual. Não confirmar sem valor e direção válidos.
5. Ajustes: seleção dos bancos, pausa, acesso Android, política local de privacidade, limpeza dos dados com confirmação.

## Contrato de captura
Usar EXTRA_TITLE e EXTRA_BIG_TEXT ou EXTRA_TEXT (não concatenar versões duplicadas). Ignorar resumos de grupo. Persistir apenas banco, metadados do lançamento e motivo de revisão; não persistir texto completo. Chave estável por pacote, chave da notificação e horário original; impedir duplicatas exatas de reentrega. Não deduplicar só por valor/hora, pois transações legítimas podem coincidir. Reutilização da mesma notificação por um banco pode exigir adaptação após teste real. Não substituir correções manuais por reentregas. Exclusão mantém tombstone para impedir recriação do mesmo evento.

## Dados
Transaction: id UUID, sourceKey opcional único, bankPackage, amountCents Long anulável, direction, method, status CONFIRMED/REVIEW, occurredAt epoch millis, description genérica, reason, deleted. Valores positivos em centavos; direção separada; zero/negativo/overflow rejeitados. Datas agrupadas pelo fuso local. Settings: captureEnabled e conjunto de pacotes permitidos.

## Critérios de aceite
- Exemplos sintéticos inequívocos de Pix entram como entrada/saída; crédito e débito são classificados separadamente.
- Mensagens ambíguas não contaminam totais, valores monetários não usam ponto flutuante.
- Reentrega não duplica; exclusão/correção sobrevive à reentrega.
- Banco não autorizado, resumo agrupado e captura pausada não persistem eventos.
- Usuário inclui, corrige e exclui lançamentos; listas sobrevivem ao reinício.
- Permissão revogada aparece na interface; processamento pesado fora da thread principal.
- Build debug, testes JVM e lint executados; roteiro físico cobre comportamento dos três bancos.

## Riscos e validação
Risco principal alto: bancos podem ocultar valores, mudar texto ou não emitir notificação. Menor validação útil: receber uma notificação real de cada modalidade por banco em aparelho autorizado, sem compartilhar dados pessoais. Não prometer 100% de captura nem retroatividade. Sem evidência comercial, MVP é ferramenta pessoal; monetização, distribuição pública, Open Finance, faturas completas e nuvem adiados.

## Referências
- https://developer.android.com/reference/android/service/notification/NotificationListenerService
- https://developer.android.com/develop/ui/compose
- https://developer.android.com/build/releases/about-agp
- https://play.google.com/store/apps/details?id=com.c6bank.app
- https://play.google.com/store/apps/details?id=com.nu.production
- https://play.google.com/store/apps/details?id=com.itau
