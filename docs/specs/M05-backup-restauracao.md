# M05 — Backup criptografado restaurável

Estado: **Implementado para SQLite v4, compatível com backups v3**. Evidências em `docs/VALIDATION.md`.

## Problema

CSV não permite recuperar base completa após perda do aparelho.

## Comportamento e escopo

Arquivo protegido por senha, seletor Android, prévia e confirmação de substituição. Autenticar e validar antes de escrever; falha mantém histórico. Captura fica pausada após restaurar.

## Contratos e dados

Envelope AES-256-GCM, salt16/nonce12 aleatórios, PBKDF2-SHA256600000, header versionado autenticado. Payload inclui registros/tombstones/categorias/orçamentos/metadados de captura; limite16MiB.

Formato atual: payload JSON versão 3, schema SQLite 4, que acrescenta a chave `benefits` (tarifas diárias de VR/VT em centavos e sobreposições mensais de dias, validadas estritamente); restaurar substitui os benefícios do aparelho, enquanto payloads versão 2 e 1 os deixam intactos. Demais tabelas: até 50 mil registros por tabela. Inclui contas, tipos e transferências, além de aliases, hashes estruturados e proteção de edições. Importa também payload versão 1/schema3, com contas vazias e referências nulas. Seleção de bancos e permissão pertencem ao aparelho e não são importadas. Captura fica pausada após restauração; falha na gravação também pode pausá-la. Novas entidades futuras exigem evolução do formato antes de serem consideradas cobertas pelo backup.

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
