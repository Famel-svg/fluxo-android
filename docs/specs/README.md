# Lista de melhorias e especificações

Specs de cada incremento derivadas da auditoria de 05/09/2026. Estados abaixo distinguem proposta de implementação. Não há promessa de entrega simultânea; dependências de domínio são obrigatórias.

| ID | Melhoria/spec | Estado |
|---|---|---|
| M01 | [Parser conservador](M01-captura-conservadora.md) | Implementado; 15 testes do parser |
| M02 | [Atualizações e duplicatas](M02-atualizacoes-idempotentes.md) | Implementado; migração e regressões validadas |
| M03 | [Diagnóstico da captura](M03-diagnostico-captura.md) | Implementado; 3 testes de interface |
| M04 | [Corpus e matriz de validação](M04-corpus-bancos.md) | Proposto; validação real depende de amostras consentidas |
| M05 | [Backup criptografado restaurável](M05-backup-restauracao.md) | Implementado; payload v3 inclui benefícios e importa payloads v1 e v2 |
| M06 | [Contas e transferências próprias](M06-contas-transferencias.md) | Implementado; persistência, backup e UI validados |
| M07 | [Cartões, faturas e parcelas](M07-cartoes-faturas-parcelas.md) | Proposto |
| M08 | [Estornos e reembolsos](M08-estornos-vinculados.md) | Proposto |
| M09 | [Importação CSV e OFX](M09-importacao-arquivos.md) | Proposto |
| M10 | [Regras locais de categorias](M10-regras-categorias.md) | Proposto |
| M11 | [Recorrências e lembretes](M11-recorrencias-lembretes.md) | Proposto |
| M12 | [Projeção 30/60/90 dias](M12-projecao-caixa.md) | Proposto |
| M13 | [Categorias próprias, tags e lotes](M13-categorias-tags-lotes.md) | Proposto |
| M14 | [Metas e reservas](M14-metas-reservas.md) | Proposto |
| M15 | [Relatórios de caixa e consumo](M15-relatorios-competencia.md) | Proposto |
| M16 | [Bloqueio, modo discreto e acessibilidade](M16-privacidade-acessibilidade.md) | Proposto |
| M17 | [CI, migrações e distribuição](M17-qualidade-distribuicao.md) | Parcial: CI instrumentado configurado |
| M18 | [Nuvem, compartilhamento, Open Finance e IA](M18-integracoes-opcionais.md) | Proposto; decisões externas pendentes |
| M19 | [Triagem provisória de notificações](M19-triagem-provisoria.md) | Implementado; aguarda JSON real |

## Ordem de execução

1. M01–M04: confiabilidade e evidência da captura.
2. M05–M08: recuperação, contas, cartões e estornos.
3. M09, M13, M10, M11: reduzir trabalho manual, sem dependências circulares.
4. M12, M14, M15: planejamento sobre domínio consistente.
5. M16/M17 acompanham incrementos. M18 depende de necessidade, provedor e custo definidos.

## Coordenação e controle

Ruflo obrigatório em cada incremento; executores em worktrees isoladas, máximo quatro agentes incluindo coordenador. Configurações `.agents/` e `.claude-flow/` somente locais. Root integra, testa e envia commits ao GitHub privado. Teste real bancário depende de amostras consentidas; nenhum resultado será inventado.

Primeiro lote: 32 testes JVM e 42 Android aprovados, lint sem erros. M04 possui [roteiro](../CAPTURE_VALIDATION_MATRIX.md), mas teste bancário real continua pendente. M17 executa instrumentação no CI; assinatura release própria ainda não entregue. M06–M16 não marcados como implementados continuam backlog. Os agentes atingiram limite de uso durante execução; coordenador revisou e concluiu localmente os arquivos de captura e backup.

M06 concluído em 06/09/2026: 36 testes JVM e 47 Android aprovados, lint sem erros. Contas e transferências incluídas no backup. Evidências em [VALIDATION](../VALIDATION.md). Cartões/faturas/parcelas (M07) continuam próximos no plano; não estão implementados por este incremento.
