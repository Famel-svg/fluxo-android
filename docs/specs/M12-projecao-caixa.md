# M12 — Projeção 30/60/90 dias

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Resultado observado não mostra compromissos futuros.

## Comportamento e escopo

Separar caixa informado, observado, agendado e estimado. Exibir fórmula, cobertura e hipóteses; simulação não altera registros.

## Contratos e dados

Serviço puro de projeção com data de referência, contas e expectativas; BigInteger nos totais.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Mesmo input produz mesma projeção; cartão não sai duas vezes; remover hipótese atualiza estimativa sem mudar caixa real.

## Dependências

M06, M07, M11.

## Limites e privacidade

Não chamar projeção de saldo bancário nem recomendar investimento. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
