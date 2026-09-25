# Validação de captura — 2026-09-24

## Correções aplicadas

- Parser valida token monetário completo. Sufixos como `10,00e3`, `10,00abc` e `10,00/20,00` não viram valores truncados confirmados.
- Confirmação futura e contextos condicionais cobertos pelas regras seguem para revisão.
- Regressões executadas antes da correção: 39 testes JVM, 2 falhas. Depois: 39 testes, zero falhas (18 do parser).
- Regressão final `:core:test :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest`: build aprovado; XML Android registra 54 casos, 48 aprovados e 6 E2E ignorados pelo opt-in, zero falhas/erros. Os seis E2E passaram separadamente pelo script abaixo. Lint sem erros, com 13 avisos preexistentes.

## Entrega real pelo Android, com conteúdo sintético

Em `ApkPreview_Phone_API37`, API 37, seis testes ponta a ponta passaram em 20,354 segundos. Evidência local: `.artifacts/fluxo-preview/notification-e2e/20260924-141151.txt`.

O emissor chama `NotificationManager.notify`; o Android entrega ao `BankNotificationListener` de produção, que extrai, interpreta e grava no SQLite. Testes não chamam diretamente `onNotificationPosted` nem `ingest`. O indicador de recebimento exige avanço do diagnóstico do callback, além das asserções de conteúdo persistido.

Cobertura:

- Pacotes permitidos C6/Nubank/Itaú, representados por APKs sintéticos; Pix recebido, compra no crédito e débito.
- BigText prevalece sobre texto curto; entradas malformadas e confirmação futura ficam em revisão.
- Redelivery não duplica; mudança de valor exige revisão; edição manual e exclusão prevalecem sobre automação.
- Ausência de `when`, atualização, remoção e reutilização da chave Android.
- Captura pausada, banco desmarcado, pacote não permitido, resumo de grupo e texto vazio não geram lançamento; controle positivo posterior comprova serviço funcional.
- Revogação desconecta; nova concessão recebe evento futuro.
- Reabertura do repositório lê os registros persistidos. Isso não equivale a teste de reinício do processo ou reboot.

## Executar

Requisitos: JDK 21, SDK Android e **emulador descartável** cujo AVD começa por `ApkPreview_`. Recomendado 4 GB de RAM para esta imagem API 37. Nunca executar em celular pessoal ou em AVD com dados financeiros a preservar: o teste limpa o livro-caixa do Fluxo.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'
./tools/notification-e2e/run.ps1 -Serial emulator-5554 -DisposableEmulator
```

O script:

1. Confirma serial de emulador, QEMU e nome do AVD.
2. Recusa qualquer pacote de fixture já instalado; nunca usa `install -r` para substituir bancos.
3. Inclui módulo `notification-fixture` somente com `-PnotificationE2e=true`. Sem dependência do aplicativo de produção; variantes release do emissor desabilitadas.
4. Compila, instala, concede publicação de notificações às fixtures e executa a classe opt-in.
5. Salva resultado textual e exige `OK (6 tests)`.
6. Restaura acesso original do listener e desinstala somente fixtures instaladas nesta execução, inclusive em falha.

O receiver tem permissão de assinatura/shell `android.permission.DUMP`, exige hardware de emulador, não possui Internet nem SDK bancário. Identidades de pacote compatíveis servem apenas para testar a lista de permissão existente, sem modificá-la.

Na suíte Android comum, os seis testes E2E são **ignorados por intenção** sem argumento `notificationE2e=true`. Para validar captura, executar este script; não interpretar skips como aprovação.

## Problemas de infraestrutura diagnosticados

- Primeiro comando PowerShell confundiu `-w` com parâmetro comum; corrigido passando lista explícita de argumentos.
- AVD de 2 GB registrou ANR durante inicialização de emissores. Reiniciado sem apagar dados, com 4 GB. Ocorrência não foi tratada como falha do parser nem sucesso de teste.
- Fixture inicialmente usava data de 2023, rejeitada pelo Android com `Ignored enqueue for old`. Agora usa timestamp atual estável por teste.
- Texto é transportado em Base64 para não depender da interpretação de aspas/espacos de comandos de instrumentação. Conteúdo decodificado continua exclusivamente sintético.
- Resultados de falhas anteriores foram preservados no diretório local de evidências.

## Limites

Não comprova templates, políticas de privacidade ou publicação de notificações dos apps bancários reais. A matriz `CAPTURE_VALIDATION_MATRIX.md` permanece pendente. API 26, outras versões/OEMs, reinício completo do processo/aparelho e stress prolongado ainda precisam de validação específica. Regras linguísticas são heurísticas conservadoras, não compreensão completa de português.

Ruflo coordenou roteamento; agente isolado implementou parser e revisou infraestrutura. OmniRoute autenticado não estava disponível; nenhuma revisão por esse provedor é alegada.
