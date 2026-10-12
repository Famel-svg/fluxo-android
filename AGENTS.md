# Fluxo Android — instruções para próximas edições

## Projeto
Android nativo, Kotlin/JVM, Jetpack Compose Material 3 e SQLite privado.
Módulos: core/ (regras e testes JVM), app/ (Android/UI/persistência), docs/ (specs).
JDK 21, compileSdk 36, Gradle Wrapper 8.13. Não executar npm build/test: este não é um projeto Node.
Manter português na interface; comunicação curta. Código, commits e avisos claros.

## Ruflo obrigatório como coordenador
Pedido explícito do proprietário: usar Ruflo nas próximas edições.
Versão configurada: 3.38.21. Ruflo coordena; Codex executa código e testes.
No início da edição:
1. Ler docs relevantes e a skill .agents/skills/swarm-orchestration/SKILL.md.
2. Executar ruflo init check e ruflo swarm status na raiz.
3. Se não houver swarm: ruflo swarm init --topology hierarchical --max-agents 4 --auto-scale=false --strategy specialized.
4. Executar ruflo hooks route --task "descrição concreta da edição".
5. Definir responsáveis, caminhos e critérios de aceite antes de delegar.
Não confundir saída de roteamento com trabalho executado nem probabilidades estimadas com medições.

## Divisão de trabalho
Para funções novas ou alterações em múltiplos módulos, usar agentes executores:
- Coordenador/integrador: requisitos, contratos, integração, Gradle, commits e release.
- Dados: core/, parser, regras, SQLite e migrações.
- Interface: Compose, navegação, acessibilidade e fluxos.
- Validação/revisão: testes e revisão independente.
Limite: quatro agentes simultâneos, incluindo coordenador; adaptar à disponibilidade real.
Agentes escritores devem usar worktrees isoladas; atribuir caminhos sem sobreposição.
Pesquisa somente leitura pode compartilhar checkout. Apenas coordenador altera contratos compartilhados.
Alteração pequena ainda usa status/route; não criar agentes ociosos só para cumprir ritual.
Se Ruflo falhar, informar falha específica e não declarar que foi usado com sucesso.
Skill local sparc-methodology está disponível para desenvolvimento estruturado;
memory-management e security-audit apenas quando relevantes, após leitura integral.

## Validação e publicação
Usar sempre `tools/check.ps1`; não remontar comandos Gradle/adb à mão. O script acha o JDK 21, sobe ou
reinicia o emulador descartável, restaura tamanho/densidade/fonte/modo noturno e termina com um resumo curto.
- `./tools/check.ps1 quick` — testes JVM, build debug e lint. Sem aparelho. Rodar a cada alteração.
- `./tools/check.ps1 ui` — quick + suíte instrumentada com os testes de interface ligados
  (`disposableEmulator=true`; sem esse argumento eles são ignorados em silêncio). `-Class <nome>` roda uma classe.
- `./tools/check.ps1 e2e` — entrega real de notificações com emissores sintéticos.
- `./tools/check.ps1 seed` — instala o debug e carrega dados de demonstração pelo repositório (`SeedDemoDataTest`).
- `./tools/check.ps1 shots -Sizes 320x640@160,1080x1920@420 -Themes light,dark` — seed + capturas das quatro abas
  em `.artifacts/shots/`. Olhar as imagens; captura salva não é verificação.
- `./tools/check.ps1 all` — ui + e2e. Exigido antes de release.
Ler só o bloco `== check summary ==`; em falha o script imprime as linhas relevantes e o caminho do log completo.
Proporção: mudança só em core → quick; mudança de tela → ui da classe afetada + shots; antes de publicar → all.
Subagentes em worktree: o emulador é único; apenas um agente por vez usa modos com aparelho.
O emulador limita a altura em 1920 px: telas mais altas são cobertas por `LargeScreenUiTest`, não por captura.
Executar verificações proporcionais à mudança; não alegar captura bancária real por fixtures sintéticas.
Migrar banco sem apagar histórico; valores financeiros em centavos, agregações sem overflow.
Preservar crédito separado de caixa; revisão não entra nos totais confirmados.
Usuário autorizou commits e pushes incrementais no repositório privado Famel-svg/fluxo-android.
Manter commits por incremento, conferir diff e evidências antes de publicar APK.
Não publicar em loja ou repositório público sem autorização específica.

## Privacidade e limites
Não guardar notificações brutas, CPF, credenciais ou informações bancárias reais em memória Ruflo.
Guardar somente decisões técnicas e resumos sem dados pessoais.
Runtime, logs, sessões, bancos de memória, caches e worktrees ficam fora do Git.
Não mudar modelo, permissões globais, sandbox ou provedor como efeito colateral.
MCP pode exigir recarregar a sessão Codex; CLI Ruflo funciona como alternativa explícita.
Configuração local MCP é .codex/config.toml; .agents/ e .claude-flow/ são locais e ignoradas pelo Git.
Em checkout novo, inicializar Ruflo conforme docs/RUFLO.md antes de procurar skills locais.
Guia de instalação/reprodução: docs/RUFLO.md.
