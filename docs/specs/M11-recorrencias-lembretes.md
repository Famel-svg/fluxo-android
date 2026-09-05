# M11 — Recorrências e lembretes

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Compromissos fixos exigem novos lançamentos todo mês.

## Comportamento e escopo

Criar expectativa futura, nunca pagamento confirmado; definir dia31 como último dia ou pular explicitamente. Chegada de transação real permite conciliar; cancelamento só afeta futuro.

## Contratos e dados

RecurringRule e occurrenceId único por período; agendamento Android apropriado, permissão de notificação opcional.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Ano bissexto/mês curto passam; reexecução não duplica; recusa de notificações não impede uso; pagamento precisa confirmação.

## Dependências

M06, M07.

## Limites e privacidade

Sem alarmes exatos obrigatórios nem movimentação de dinheiro. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
