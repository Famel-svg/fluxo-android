# M15 — Relatórios de caixa e consumo

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Comparação de mês parcial com completo pode sugerir economia falsa.

## Comportamento e escopo

Escolher visão caixa/consumo e período; comparar mesmo dia opcional; detalhar composição. Separar transferências, liquidações e estornos.

## Contratos e dados

Agregações puras sobre domínio; política de competência única para resumo/orçamento/relatório.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Totais conciliam com detalhamento; dia31/bissexto corretos; coberturas parciais identificadas; nenhum pagamento duplica consumo.

## Dependências

M06, M07, M08.

## Limites e privacidade

Não estimar tendência sem informar base. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
