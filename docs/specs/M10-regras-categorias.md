# M10 — Regras locais de categorias

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Toda captura exige classificação manual.

## Comportamento e escopo

Criar regra por metadado disponível, ordenar prioridade, simular alcance, aplicar e desfazer. Mostrar razão da sugestão; decisão humana prevalece.

## Contratos e dados

CategoryRule com campos permitidos, prioridade e revisão; log técnico de lote sem bruto; não usar estabelecimento ausente.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Regra conflitante tem precedência explícita; categoria editada não sobrescrita; lote reversível; dados não saem do dispositivo.

## Dependências

M02, M13.

## Limites e privacidade

Sem IA remota ou inferência de comerciante a partir de descrição genérica. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
