# M07 — Cartões, faturas e parcelas

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Compra, dívida e pagamento ainda não têm entidades próprias.

## Comportamento e escopo

Cadastrar cartão/ciclo; compra cria parcelas exatas e compromisso. Pagamento integral/parcial liquida dívida sem nova despesa. Juros e tarifa separados; antecipação explícita.

## Contratos e dados

Card, Purchase, Installment, Statement e Settlement; centavos inteiros; regra de fechamento/vencimento e competência documentada.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

R$100/3 soma 10000 centavos; compra+pagamento contabiliza consumo uma vez; testar fechamento, mês curto, antecipação e cancelamento.

## Dependências

M06.

## Limites e privacidade

Não inferir total de compra a partir de uma parcela sem informação. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
