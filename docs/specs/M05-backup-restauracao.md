# M05 — Backup criptografado restaurável

Estado: **Em implementação**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

CSV não permite recuperar base completa após perda do aparelho.

## Comportamento e escopo

Arquivo protegido por senha, seletor Android, prévia e confirmação de substituição. Autenticar e validar antes de escrever; falha mantém histórico. Captura fica pausada após restaurar.

## Contratos e dados

Envelope AES-256-GCM, salt16/nonce12 aleatórios, PBKDF2-SHA256600000, header versionado autenticado. Payload inclui registros/tombstones/categorias/orçamentos/metadados de captura; limite16MiB.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Roundtrip preserva valores e proteção de duplicatas; senha errada, corrupção, truncamento, versão inválida e cancelamento não alteram base; restauração em aparelho novo.

## Dependências

M02.

## Limites e privacidade

Sem nuvem obrigatória; senha perdida não é recuperável pelo aplicativo. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

