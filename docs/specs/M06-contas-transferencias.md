# M06 — Contas e transferências próprias

Estado: **Implementado**. Evidências em `docs/VALIDATION.md`.

## Problema

Banco não distingue conta; Pix próprio infla consumo.

## Comportamento e escopo

Cadastrar conta com nome/banco/tipo e saldo inicial datado. Transferência vincula duas pernas; tarifa separada. Sugerir correspondência, confirmar vínculo e permitir desfazer.

## Contratos e dados

Novas entidades Account e Transfer; accountId opcional em legado. Nunca inferir saldo inicial do histórico parcial.

SQLite v4 adiciona contas e referências opcionais ao lançamento. Transferência é representada por dois lançamentos com o mesmo `transferId`, gravados atomicamente. Cadastro inclui nome, banco, tipo (corrente, poupança, dinheiro ou outra), saldo inicial datado e arquivamento. Valores agregados usam BigInteger; movimentos futuros, revisão e compras no crédito não alteram saldo observado atual. Saldo inicial futuro aparece como indisponível.

O usuário atribui contas aos registros capturados. Sugestões comparam até 50 entradas e 50 saídas recentes, contas distintas, mesmo valor e diferença máxima de três dias; exibem no máximo dez candidatos. Vínculo depende de confirmação. Desvincular mantém os dois registros; editar ou excluir uma perna exige desvincular primeiro. Transferência manual não presume modalidade Pix. Tarifa continua despesa separada.

Backup payload v2/schema4 inclui contas e vínculos; aceita payload v1/schema3 sem inventar contas. Validação rejeita conta inexistente, par incompleto ou pernas incompatíveis antes de substituir dados. Dashboard e orçamento excluem transferências próprias, enquanto saldos por conta incluem suas pernas.

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
