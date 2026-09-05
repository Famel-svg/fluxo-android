# M02 — Atualizações e duplicatas

Estado: **Implementado e validado, com limites de identidade documentados**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Uma notificação muda sem criar outra transação, mas estado anterior permanecia.

## Comportamento e escopo

Reentrega idêntica não duplica. Mudança estruturada exige revisão, preservando id e decisões manuais. Alias de ciclo trata ausência de when; remoção encerra ciclo.

## Contratos e dados

SQLite v3: user_modified, observation_hash, last_observed_at e notification_aliases; migrados protegidos por padrão.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Pendente→enviado e confirmado→cancelado reabrem revisão; edição e tombstone vencem automação; callbacks antigos não sobrescrevem; duas chaves distintas permanecem.

## Dependências

M01.

## Limites e privacidade

Não fundir eventos por valor/horário. Remoção perdida/chave reutilizada continua ambígua. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
