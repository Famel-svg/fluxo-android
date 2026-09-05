# M06 — Contas e transferências próprias

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Banco não distingue conta; Pix próprio infla consumo.

## Comportamento e escopo

Cadastrar conta com nome/banco/tipo e saldo inicial datado. Transferência vincula duas pernas; tarifa separada. Sugerir correspondência, confirmar vínculo e permitir desfazer.

## Contratos e dados

Novas entidades Account e Transfer; accountId opcional em legado. Nunca inferir saldo inicial do histórico parcial.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Nubank→C6 R$500 conserva patrimônio e não consome orçamento; contas distintas mesmo banco coexistem; migração preserva sem conta.

## Dependências

M02, M05.

## Limites e privacidade

Sem consulta automática de saldo bancário. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
