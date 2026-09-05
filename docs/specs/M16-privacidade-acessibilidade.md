# M16 — Bloqueio, modo discreto e acessibilidade

Estado: **Proposto**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Conteúdo financeiro pode aparecer sem proteção visual; ensaio assistivo falta.

## Comportamento e escopo

Bloqueio opcional após inatividade com biometria/credencial suportada, ocultação de valores e preview; captura continua consentida. Interface TalkBack e fonte ampliada.

## Contratos e dados

Preferências locais; estado bloqueado não persistido como desbloqueado; fallback seguro quando biometria indisponível.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Retorno após timeout bloqueia; cancelamento não revela dados; leitores anunciam campos/erros; fonte grande e paisagem sem controles inacessíveis.

## Dependências

M05.

## Limites e privacidade

Biometria não é criptografia do banco; não coletar biometria. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

