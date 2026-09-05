# Ruflo no projeto Fluxo

Estado: configurado e inicializado em 2026-09-05. Versão instalada: 3.38.21.
As versões 0.1/0.2 usaram agentes nativos Codex, sem Ruflo. A partir desta configuração, próximas edições usam Ruflo para coordenação e agentes Codex como executores.

## Arquivos
- `AGENTS.md`: instruções persistentes específicas de Kotlin/Android e divisão por área.
- `.agents/skills/`: quatro skills geradas pelo Ruflo: swarm-orchestration, memory-management, sparc-methodology e security-audit. Ler somente as relevantes antes de aplicar.
- `.agents/config.toml`: template MCP local, ignorado pelo Git, sem substituir modelo ou permissões do usuário.
- `.codex/config.toml`: cópia local do template, ignorada pelo Git; pode exigir reabrir/recarregar sessão Codex. Em outro computador, mesclar o bloco MCP com configuração existente, preservando valores locais.
- `.claude-flow/config.yaml`: runtime hierárquico, máximo quatro agentes, sem autoescala.

## Comandos usados e verificados
```powershell
ruflo --version
ruflo init --codex --no-global --no-signup --no-skills-sh --no-codex-detect
ruflo init --skip-claude --no-global --no-signup --no-skills-sh --no-codex-detect
ruflo swarm init --topology hierarchical --max-agents 4 --auto-scale=false --strategy specialized
ruflo init check
ruflo swarm status
ruflo hooks route --task "Fluxo Android: configurar coordenacao de proximas edicoes"
ruflo daemon status
```

Resultado: runtime detectado; swarm hierárquico criado com quatro agentes no máximo e autoescala desativada; roteador respondeu. O daemon foi iniciado automaticamente pelo CLI, com workers locais e AI workers desativados. CLI informa TTL de 12 horas; não é serviço permanente do Windows. Para encerrar o daemon deste projeto: `ruflo daemon stop`.

Nenhum agente executor foi iniciado nesta configuração: não havia nova funcionalidade para delegar. Swarm criado não significa agentes trabalhando, e estimativas de roteamento não são medições. Nas próximas alterações, executar status/route e depois delegar implementação concreta, conforme AGENTS.md.

## Reproduzir em outro computador
Instalar Node.js compatível com o Ruflo e `npm install -g ruflo@3.38.21`; manter a versão fixada até atualização deliberada. As pastas `.agents/` e `.claude-flow/` não acompanham o clone. Em checkout novo, executar os comandos `init` acima para gerar configuração e skills locais, preservar as instruções Android de `AGENTS.md`, mesclar MCP na configuração local e inicializar swarm pela raiz. Não repetir init com --force sobre configurações personalizadas. A remoção do versionamento mantém essas pastas no computador; não reescreve commits históricos.

## Limitações verificadas
O inicializador não encontrou o executável Codex CLI no PATH desta máquina. O aplicativo Codex está em uso; a configuração MCP foi escrita no projeto, não registrada globalmente por um comando Codex. As ferramentas Ruflo MCP não estavam expostas na sessão durante a configuração; CLI é o caminho disponível e validado. Não foi alterado modelo nem instalado provedor pago.

## Privacidade
Logs, memória, sessões, métricas, arquivos do daemon e worktrees são locais e ignorados no Git. Não registrar texto de notificações, transações reais, credenciais ou outros dados bancários em memória de agentes. Apenas decisões técnicas sem dados pessoais.

Fonte primária e documentação: https://github.com/ruvnet/ruflo. Opções efetivamente usadas conferidas também em `ruflo init --help` e `ruflo swarm init --help` da versão instalada.
