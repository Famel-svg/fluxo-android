# M09 — Importação CSV e OFX

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Notificações não recuperam histórico nem permitem conciliar extrato.

## Comportamento e escopo

Selecionar arquivo, mapear campos/moeda/data, visualizar erros e possíveis duplicatas antes de importar. Cancelar não grava; reimportação idempotente.

## Contratos e dados

ImportBatch e identificador externo/origem; parser com limites; validar todo lote antes de transação; sem execução de fórmulas.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Duplicação evitada; duas compras iguais legítimas preservadas; decimal/data inválidos têm erro por linha; falha faz rollback.

## Dependências

M02, M05, M06.

## Limites e privacidade

Sem login bancário automatizado nem importação silenciosa. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

