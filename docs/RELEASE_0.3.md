# Fluxo 0.3.0 — captura confiável e recuperação

- Parser trata pendências/negações e mensagens mistas conservadoramente.
- Atualizações de notificações reabrem revisão, preservando correções e exclusões.
- Diagnóstico diferencia permissão, captura e conexão do serviço.
- Backup criptografado por senha com prévia, validação e restauração transacional; captura fica pausada.
- SQLite v3 preserva legado; 32 testes JVM e 42 Android aprovados localmente, lint sem erros.
- Lista e 18 specs em docs/specs. Contas/cartões completos, recorrências e demais módulos ainda estão planejados.

Atualize sem desinstalar. APK debug tem o mesmo certificado da release 0.2; não misture com APK reconstruído no CI, que pode usar outro certificado. Nenhuma notificação bancária real foi validada nesta entrega. Backup exige senha: sem ela, arquivo não pode ser recuperado. CSV continua sem criptografia e não é restaurável.

SHA-256 do APK: `ce70f5d556395c3c28a04ce6b2a5ddeeac8e582dfc053b1c10d793247e64e445`.
