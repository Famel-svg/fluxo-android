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

Na suíte Android comum, os seis testes de entrega e o teste de reinício são **ignorados por intenção** sem argumentos opt-in. Para validar captura, executar este script; não interpretar skips como aprovação.

## Reinício real do processo — incremento posterior

`NotificationRestartE2eTest` passa em três invocações separadas de instrumentação: `seed`, `verify` e `cleanup`. O script executa essas fases depois dos seis testes de entrega.

- `seed` recebe um Pix sintético pelo Android, confirma valor/direção/modalidade/status e grava marcador com PID, ID e chave do lançamento.
- Host executa `am force-stop` e aguarda até cinco segundos pelo término do PID original. Eventual novo processo criado pelo Android não é confundido com o original.
- `verify` exige PID diferente daquele que gravou o evento, recupera consentimento, bancos selecionados e registro SQLite sem recriar dados.
- Após reconexão explícita via API `requestRebind`, atualização da mesma notificação sem `when` preserva ID e entra em revisão; outro ID recebe novo lançamento confirmado.
- `cleanup` remove marcadores e dados sintéticos. O host restaura permissão anterior e remove somente fixtures desta execução. Qualquer falha de limpeza termina com erro, depois de tentar as demais etapas.

Execução final aprovada: seis testes de entrega em 20,793 s; fases seed/verify/cleanup aprovadas em 0,713/0,966/0,229 s. Build, testes JVM e lint aprovados. Resultados e PIDs ficam em `.artifacts/fluxo-preview/notification-e2e/*-restart-*.txt`.

Isso comprova morte/recriação do processo com reconexão explícita, não garantia de reinício automático em background para qualquer fabricante.

## Reboot completo do emulador

Executar `./tools/notification-e2e/run.ps1 -DisposableEmulator -Reboot` acrescenta outro seed, reboot real e fase `verifyBoot`.

- Host exige mudança do identificador de boot do kernel e `sys.boot_completed=1`, com prazo de 180 segundos.
- Teste exige aumento de `Settings.Global.BOOT_COUNT`, preservação da permissão do listener, consentimento, bancos selecionados e lançamento SQLite.
- Sem nova concessão de acesso ou chamada explícita a `requestRebind`, aguarda conexão do listener e recebe outro Pix sintético pelo Android.
- Execução aprovada em 24/09/2026: seis testes de entrega em 20,44 s; `verifyBoot` em 4,963 s; fases de processo e limpeza também aprovadas. Evidências locais: `20260924-221753-boot-ids.txt` e `20260924-221807-restart-verifyBoot.txt`, no diretório de resultados acima.

Limite importante: a instrumentação inicia o processo do aplicativo antes do novo envio. Isso comprova captura após reboot com processo iniciado pelo teste, não captura autônoma em background antes de qualquer inicialização do aplicativo.

## Problemas de infraestrutura diagnosticados

- Primeiro comando PowerShell confundiu `-w` com parâmetro comum; corrigido passando lista explícita de argumentos.
- AVD de 2 GB registrou ANR durante inicialização de emissores. Reiniciado sem apagar dados, com 4 GB. Ocorrência não foi tratada como falha do parser nem sucesso de teste.
- Fixture inicialmente usava data de 2023, rejeitada pelo Android com `Ignored enqueue for old`. Agora usa timestamp atual estável por teste.
- Texto é transportado em Base64 para não depender da interpretação de aspas/espacos de comandos de instrumentação. Conteúdo decodificado continua exclusivamente sintético.
- Resultados de falhas anteriores foram preservados no diretório local de evidências.

## Limites

Não comprova templates, políticas de privacidade ou publicação de notificações dos apps bancários reais. A matriz `CAPTURE_VALIDATION_MATRIX.md` permanece pendente. API 26, outras versões/OEMs, captura autônoma após boot, reconexão automática em background e stress prolongado ainda precisam de validação específica. Regras linguísticas são heurísticas conservadoras, não compreensão completa de português.

Ruflo coordenou roteamento; agente isolado implementou parser e revisou infraestrutura. OmniRoute autenticado não estava disponível; nenhuma revisão por esse provedor é alegada.
