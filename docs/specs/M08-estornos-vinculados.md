# M08 — Estornos e reembolsos

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Entrada de estorno não reduz categoria original corretamente.

## Comportamento e escopo

Associar devolução ao original, parcial/integral. Cartão pode reduzir dívida sem entrada bancária. Mostrar competência escolhida explicitamente.

## Contratos e dados

Reversal referencia lançamento/compra; valores elegíveis e política de competência; referência preservada em backup.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Parcial e integral corrigem conta/dívida e consumo; impedir excesso sem tratamento explícito; desfazer vínculo não duplica histórico.

## Dependências

M06, M07.

## Limites e privacidade

Não transformar qualquer Pix recebido em estorno. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
