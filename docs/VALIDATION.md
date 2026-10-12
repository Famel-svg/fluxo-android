# Validação das entregas

## Versão 0.5.0 — 2026-10-12

Modo escuro, transições, saldo do VR no Início, meios de pagamento vale-refeição e vale-transporte, configuração inicial em três etapas com salário, ajustes para telas maiores, rotina `tools/check.ps1` e release automática por tag.

- `./tools/check.ps1 all` na árvore da versão: 84 testes JVM, 0 falhas; lint 0 erros e 25 avisos; 117 testes Android, 0 falhas, 16 ignorados (15 ponta a ponta de notificações, executados na etapa seguinte, e o carregador opcional de dados de demonstração); entrega de notificações 8 de 8 mais fases de reinício do processo. AVD descartável API 37, 320x640dp.
- Contraste das duas paletas conferido por `ContrastTest` (texto ≥ 4,5:1; marcas de gráfico do tema escuro ≥ 3:1). No tema claro, algumas marcas de gráfico já ficavam abaixo de 3:1 e não são verificadas.
- Telas grandes: capturas em 444dp e 411dp de largura com altura limitada pelo emulador a cerca de 700dp; `LargeScreenUiTest` força 444x986dp e 320x640dp. Trocar o tamanho com `wm size` derrubou o system_server três vezes nessas imagens; a rotina passou a usar o tamanho nativo e a reiniciar o emulador quando ele deixa de responder.
- A suíte instrumentada passou de cerca de 3 para 11 minutos no mesmo dia. A mesma classe mediu igual no commit anterior ao modo escuro, portanto a lentidão é do emulador ou da máquina, não das animações.
- CI: duas execuções falharam em `OnboardingUiTest` por `Espresso.pressBack()` sem foco de janela; o teste passou a acionar o voltar pelo dispatcher da atividade e o workflow voltou a passar.
- Release: publicada por `.github/workflows/release.yml` a partir da tag, com o certificado fixado `0fc1435e…8e933` (keystore debug do dono, senha pública). Quatro revisores automáticos divergiram entre manter essa chave e trocar por chave própria, e convergiram em manter até que o backup tenha sido restaurado no aparelho real.
- Não verificado: notificação bancária real e aparelho físico; restauração de backup no aparelho real; tema escuro com fonte ampliada e em telas largas; sistema com animações desligadas; AVD de 1080x2424; abertura a frio com tema Escuro e sistema em claro mostra a janela inicial clara por um instante; fechamento do editor não é animado.
- Coordenação: Ruflo executou status e route; implementação por subagentes em worktrees, integração e verificação pelo coordenador.

## Versão 0.4.0, redesign e parser — 2026-10-11

Redesign completo a partir do protótipo aprovado, ampliação do parser e correção do CI.

- JVM: 66 testes, 0 falhas. Lint: 0 erros, 16 avisos (APIs KTX e versões de dependências).
- Android com `disposableEmulator=true`: 77 testes, 0 falhas, 15 ignorados (ponta a ponta de notificações, executados pelo script abaixo), AVD descartável API 37. Até esta data os testes de interface com esse parâmetro eram ignorados em silêncio nas execuções comuns e no CI; agora o CI passa o parâmetro.
- `tools/notification-e2e/run.ps1`: 8 testes de entrega pelo Android aprovados, mais fases de reinício do processo; execução com reboot aprovada antes da ampliação do parser.
- Parser: 22 mensagens em formato realista, redigidas à mão, serviram de sonda; 6 eram descartadas e 2 perdiam o valor. Casos cobertos por testes novos. Não são capturas reais de bancos.
- Revisão independente somente leitura antes da release: sem bloqueadores. Achados corrigidos: rótulo do mês cortado em 320dp, quebra de palavra e cortes com fonte 1,5, bloco de 7 dias com dias futuros em Planejar, série de 7 dias ignorando o mês anterior, mês em formato ISO, rótulo da rosca cortado e código morto.
- CI: `android-actions/setup-android@v3` falhava no aceite de licenças desde 25/09. Workflow passou a usar o SDK da imagem com aceite explícito; primeira execução completa aprovada nos dois jobs em 11/10/2026.
- Comparação visual com o protótipo feita por capturas em 340x720 com dados equivalentes. Diferenças conhecidas: Registros mantém busca, seletor de mês e linha de filtros; faixa de captura e bloco Estado só foram vistos na variante sem permissão concedida; Planejar e Ajustes mantêm seções existentes abaixo do conteúdo do protótipo.
- Não verificado: notificação bancária real; aparelho físico; fonte em escala 2 visualmente; Planejar, Ajustes e o editor com fonte ampliada; botão "Confirmar" corta em escala 1,5; modo escuro (o tema é somente claro); build de release com R8.
- Incidente: uma captura de tela travou e encerrou o emulador descartável; foi reaberto e os resultados acima vêm da nova instância. Causa não identificada.
- Coordenação: Ruflo executou status e route; implementação por subagentes em worktrees isoladas, integração e publicação pelo coordenador.

## Versão 0.4.0 — 2026-10-10

Correção da migração SQLite (v5), reprocessamento de notificações ativas ao reconectar, benefícios no backup (payload v3), divisão de `MainActivity.kt` por tela e Compose BOM 2026.06.01.

- Comando: `./gradlew.bat :core:test :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest`, JDK 21.0.12.
- JVM: 54 testes aprovados. Lint sem erros; APK debug compilado.
- Android: 76 testes, 0 falhas, 19 ignorados por pré-condição (ponta a ponta de notificações e telas que exigem ambiente isolado), Pixel 9 Pro AVD API 37. Portanto 57 executados.
- Novos cenários: schema idêntico entre instalação nova, atualização da v1 e banco v4 com estruturas de fatura residuais; roundtrip de benefícios; payload v2 sem alterar benefícios; 14 payloads v3 malformados rejeitados sem modificar dados.
- Execuções anteriores da mesma suíte tiveram uma falha intermitente em `CaptureHealthUiTest` ("No compose hierarchies found"), com testes diferentes a cada vez e também com o BOM antigo. Outro aplicativo estava em primeiro plano no mesmo emulador. A execução registrada acima passou integralmente; a causa não foi isolada além dessa observação.
- Não verificado: reprocessamento ao reconectar não tem teste automatizado próprio (depende do ciclo de vida do serviço no sistema); a nova frase da confirmação de restauração não foi conferida visualmente; nenhum teste em aparelho físico nem com notificação bancária real.
- Em SQLite anterior a 3.35 a coluna residual `invoice_id` não pode ser removida e permanece sempre nula; migrações futuras devem verificar sua existência antes de criá-la.
- Se a gravação das preferências falhar depois da restauração SQLite, o histórico já estará substituído e os benefícios permanecerão os anteriores; o erro é propagado.
- Coordenação: Ruflo executou init check, swarm status e hooks route; roteamento não equivale a execução. Dois subagentes em worktrees isoladas fizeram a divisão da interface e os benefícios no backup; integração e suíte completa pelo coordenador.

## Incremento M06 — 2026-09-06

Contas locais com tipo, saldo inicial datado, arquivamento e atribuição a lançamentos. Transferências próprias podem ser registradas em par ou vinculadas a registros existentes, sem inflar renda/consumo. SQLite v4 aditivo; backup payload v2 aceita também payload v1/schema3.

- Comando completo: `./gradlew.bat :core:test :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest`.
- 36 testes JVM e 47 Android aprovados, sem falhas/ignorados, Pixel 9 Pro API37. Lint sem erros; APK debug compilado.
- Novos cenários: conservação dos saldos, exclusão do orçamento, datas futuras, soma sem overflow, vínculo sem duplicar, proteção de decisão humana, roundtrip com tipos de conta, rejeição de referências órfãs, compatibilidade de backup antigo, saldo negativo e data impossível na UI.
- Revisão independente somente leitura por agente Codex. Corrigidos saldo incluindo movimentos futuros e modalidade Pix presumida em transferência manual. Ruflo executou init check/status/route; roteamento não equivale a execução por agentes.
- Uma edição textual intermediária corrompeu identificadores da interface e falhou na compilação. Arquivo recuperado preservando mudanças M06, diff revisado e suíte completa reexecutada com sucesso antes do commit.
- Não houve captura bancária real ou validação em aparelho físico neste incremento. Sugestões de transferência exigem confirmação; cobertura do saldo continua parcial.

Skills aplicadas no desenvolvimento deste lote: `swarm-orchestration`, `sparc-methodology`, `mobile-android-design`; referência operacional `rafael-core/references/core.md`. Nesta retomada: `swarm-orchestration` e núcleo operacional.

## Versão 0.3.0 — 2026-09-05

Implementação: M01, M02, M03 e M05; M04 tem roteiro e M17 ganhou execução instrumentada no CI. Demais specs continuam propostas. Ruflo coordenou; três agentes trabalharam em worktrees até atingir limite de uso, depois coordenador revisou e concluiu integração localmente.

- JVM: 32 testes aprovados (15 parser, 5 planejamento, 5 CSV, 7 cifra).
- Android: 42 testes aprovados em execução integral, 0 falhas/ignorados, Pixel 9 Pro API37. Incluem migração, notificações atualizadas, aliases, correções humanas, tombstones, backup e diagnóstico UI.
- Lint: sem erros. APK debug compilado e instalado.
- Execução inicial encontrou texto esperado corrompido no novo teste; codificação corrigida, 17 testes de repository passaram e depois suíte integral de 42 passou. Não foi ocultada falha.
- Fluxo manual no emulador: criar backup via seletor Android, informar/repetir senha sintética, salvar, abrir arquivo, verificar prévia, confirmar restauração e observar captura pausada. Usou base vazia; roundtrip com registros/categorias/tombstones/orçamentos foi instrumentado.
- Certificado local v0.3 comparado ao APK da release v0.2: mesmo SHA-256 `0fc1435e14e34a078ed4db17d777888aa87d04c2a02cd7e29904f322dcd8e933`.
- `.agents/` e `.claude-flow/` ausentes do índice Git e cobertas pelo `.gitignore`; cópias locais mantidas.

Backup usa envelope versionado AES-GCM e PBKDF2-HMAC-SHA256, executados fora da UI. JSON validado antes da transação, com limites de tamanho, profundidade, tipos e referências. O formato cobre SQLite v3; novas entidades futuras precisam evoluir formato/migração. Isso não constitui auditoria criptográfica externa. Referências: [OWASP armazenamento criptográfico](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html), [parâmetros PBKDF2](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Limites: não houve teste bancário real, aparelho físico ou ensaio assistivo completo. Alias sem `when` depende do ciclo de notificação; callback de remoção perdido ou chave/horário alterados continuam ambíguos. Mudança detectada exige revisão, não reconciliação contábil automática. Permissões e bancos selecionados não vêm do backup, e captura fica pausada após restaurar.

## Versão 0.2.0 — 2026-09-05
Estado: cinco melhorias implementadas, APK compilado e validação local concluída.

- 22 testes JVM aprovados: parser (12), analytics/busca (5), CSV (5).
- 29 testes Android distintos aprovados: repository/migração (11), extração (8), UI original (2), CSV/estado de exportação (4), Planejamento (3), busca/categoria integrada (1).
- A suíte de 29 executou inicialmente com 28 aprovados e uma ambiguidade no seletor de menu do teste; seletor corrigido, teste afetado reexecutado e aprovado. Nenhum teste foi removido ou ignorado para obter sucesso.
- Build APK e lint concluídos; lint com 0 erros e 10 avisos de dependências/extensões Kotlin.
- Migração aditiva preservou lançamento antigo editado, valor e tombstones; reabertura confirmou categoria e orçamento persistidos.
- Teste integrado encontrou Itaú pela busca `itau`, localizou mês anterior, categorizou lançamento e combinou filtros.
- CSV testado com centavos exatos, UTF-8, fórmulas neutralizadas, snapshot, cancelamento, falha e perda do snapshot após reinício.
- Ambiente: JDK 21, Gradle 8.13, compileSdk 36, Pixel 9 Pro Android 17/API 37 com páginas de 16 KB.

APK 0.2.0 SHA-256: `fc96081f05b6c5fb3dc91f6ee89d8e823e625433410528ca3d9f0124f1668e03`.
Pesquisa e critérios de aceite: `PLAN_0.2.md`. Relatórios representam despesas notificadas/registradas, não extrato completo. Templates reais dos três bancos e seletor de cada provedor de arquivos continuam dependentes do aparelho. CSV não é backup criptografado nem restauração.

## Histórico da versão 0.1.0

Data: 2026-09-05. Estado: MVP de desenvolvimento implementado e validado localmente; validação com bancos reais pendente.

## Evidências executadas
Ambiente: Windows, JDK 21, Gradle 8.13, Android SDK 36. Emulador Pixel 9 Pro, Android 17/API 37, x86_64 com páginas de 16 KB.

| Verificação | Resultado |
| --- | --- |
| `:core:test` | 12 testes JVM aprovados |
| `:app:connectedDebugAndroidTest` | 18 testes aprovados, 0 falhas, 0 ignorados |
| `:app:assembleDebug` | APK gerado |
| `:app:lintDebug` | 0 erros, 9 avisos |
| Instalação e abertura no emulador | Sucesso; Resumo e Ajustes inspecionados visualmente |
| Visibilidade GitHub | PRIVATE confirmada por `gh repo view` |

Os 18 testes Android cobrem 8 cenários de persistência, 8 de extração de notificações e 2 fluxos de UI. Teste UI executou inclusão, edição e exclusão de lançamento; outro verificou R$ 20,00 de débito separado de R$ 80,00 de crédito e resultado observado de -R$ 20,00. Fixtures são sintéticas. Testes de UI com limpeza são limitados a emulador; testes de repository usam banco isolado.

Avisos do lint são sugestões de atualização de dependências e uso de extensões Kotlin. Dependências fixadas; nenhuma supressão ampla adicionada para ocultar erros. Espresso 3.7.0 foi necessário para compatibilidade da automação com APIs modernas de input: https://developer.android.com/jetpack/androidx/releases/test

CI privado executa testes JVM, build do aplicativo, compilação dos testes instrumentados e lint; execução instrumentada descrita acima ocorreu no emulador local. Histórico e artefatos: https://github.com/Famel-svg/fluxo-android/actions

## APK
Arquivo local: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `9a72b9937b81673caee7af240ee3256889b8b4d0f03111fbade108cd28b3983b`.
Assinatura debug, própria para testes. O APK local anexado à release pode ter certificado diferente do APK reconstruído no CI; não intercambiar origens de assinatura para atualizações sobre dados importantes.

## Limites ainda não validados
- Nenhuma notificação real de banco capturada nesta sessão. Pacotes conferidos nas páginas oficiais da Play Store; textos bancários não são contratos estáveis.
- Acesso especial e entrega real pelo sistema ao listener dependem da autorização no aparelho; extração e persistência foram testadas independentemente dessa autorização.
- Sem testes físicos de bateria, reinício, fabricante, acessibilidade com TalkBack ou matriz de versões Android 8–16.
- Sem saldo bancário completo, retroatividade, conciliação de faturas ou sincronização em nuvem.
- Deduplicação depende de chave e horário original preservados. Se um banco mudar ambos numa atualização, pode haver duplicata; se reutilizar ambos para outro evento, pode haver omissão.

Próximo gate: instalar a versão no celular e verificar eventos do uso normal em cada banco seguindo `TEST_PLAN.md`; corrigir regras com exemplos anonimizados quando necessário.
