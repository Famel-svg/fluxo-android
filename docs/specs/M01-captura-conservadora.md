# M01 — Parser conservador

Estado: **Em validação**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Pendências e mensagens mistas podem confirmar ou desaparecer.

## Comportamento e escopo

Negação e estado pendente prevalecem sobre palavras positivas. Oferta isolada é ignorada; movimentação com oferta entra em revisão.

## Contratos e dados

Sem mudança de schema. Preservar ParsedNotification e valores Long em centavos.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Pix enviado R$50 em análise e Pix recebido não confirmado ficam REVIEW; compra com cashback não retorna null; fixtures anteriores passam.

## Dependências

Nenhuma.

## Limites e privacidade

Não interpretar linguagem bancária universalmente nem afirmar cobertura real. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

