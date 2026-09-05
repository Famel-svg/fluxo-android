# M14 — Metas e reservas

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Limite de despesa não representa dinheiro reservado para objetivo.

## Comportamento e escopo

Criar meta com alvo/prazo e reserva; depósito/resgate não são consumo. Compra continua despesa uma vez. Mostrar progresso e cobertura.

## Contratos e dados

Goal e Allocation vinculados a contas, separados de transferências reais; prazo opcional.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Reservar não aumenta patrimônio nem cria despesa; resgatar reduz reserva; atingir meta não confirma compra.

## Dependências

M06, M12.

## Limites e privacidade

Sem promessas de rentabilidade ou aplicação automática. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
