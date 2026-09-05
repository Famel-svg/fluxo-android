# M03 — Diagnóstico da captura

Estado: **Em validação**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Toggle ativo não prova ligação do serviço ao Android.

## Comportamento e escopo

Exibir permissão, toggle, estado conectado/desconectado/desconhecido e última observação por banco selecionado. Oferecer requestRebind quando apropriado.

## Contratos e dados

StateFlow volátil apenas com estado e horário; nenhuma notificação bruta; reinício zera diagnóstico.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Permissão sem callback não mostra conectado; pausa esconde reconexão; banco desmarcado não aparece; ausência recente não vira erro.

## Dependências

M02.

## Limites e privacidade

Não prometer histórico integral ou detectar transação nunca notificada. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

