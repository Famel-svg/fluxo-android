# M13 — Categorias próprias, tags e lotes

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Enum fixo limita organização pessoal.

## Comportamento e escopo

Criar/renomear/arquivar categoria; tags opcionais; selecionar vários lançamentos com prévia de alteração e desfazer.

## Contratos e dados

CategoryEntity com ID estável; migrar enum preservando IDs/labels; BatchChange reversível; orçamento referencia ID.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Renomear preserva totais/histórico; arquivar não apaga registros; lote falho reverte tudo; desfazer não sobrescreve edição posterior silenciosamente.

## Dependências

M05.

## Limites e privacidade

Não apagar categoria com histórico nem transformar tags em contas. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

