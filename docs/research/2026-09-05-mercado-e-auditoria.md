# Fluxo: pesquisa competitiva e auditoria do projeto

Data de consulta: **5 de setembro de 2026**. Estado: **relatório final de análise; melhorias propostas, ainda não implementadas**. Base auditada: commit `06ea927718846e220944bc0ff6735f495553d78c`, versão funcional 0.2.

## 1. Conclusão e decisão recomendada

Fluxo já tem uma base adequada para controle pessoal local: captura consentida, valores em centavos, revisão de ambiguidades, categorias, orçamento e CSV. Seu diferencial potencial é registrar movimentações com pouco esforço, explicar dúvidas e manter dados no aparelho.

O maior ganho agora está em **confiabilidade e modelo financeiro**, antes de novos gráficos. Há casos sintéticos em que uma pendência entra como confirmada, atualizações de notificações são ignoradas e conteúdo promocional misturado elimina uma compra. Além disso, transferências próprias, faturas, parcelas e estornos ainda não têm representação suficiente. Um relatório visualmente correto pode, por isso, apresentar consumo incorreto.

Recomendação: próxima entrega deve corrigir captura e tornar seu estado visível; em seguida, oferecer backup restaurável e evoluir contas/cartões. Categorias automáticas, recorrências, metas e projeções vêm sobre essa base. Nuvem, IA e Open Finance permanecem opcionais, com decisões próprias de custo e privacidade.

## 2. Método, cobertura e limites

Foram examinados **10 produtos**: Organizze, Mobills, Minhas Economias, YNAB, Wallet, Spendee, Money Manager, Cashew, Actual Budget e Ivy Wallet. Os três brasileiros são comparadores diretos; os demais servem também como referências de planejamento, portabilidade e arquitetura. Ivy é referência histórica, não produto com manutenção ativa.

Pesquisa documental em páginas oficiais, centrais de ajuda, repositórios e Google Play. Não houve compra de assinatura, login bancário, instalação dos concorrentes, teste de Open Finance ou medição de satisfação. Banco listado significa suporte declarado, não conexão testada. Preços são fotografia da consulta; loja, campanha, impostos e região podem alterar contratação.

Evidências do relatório usam quatro classes: **código** (comportamento verificável no projeto), **documental** (declaração do fornecedor), **anedótica** (avaliação individual) e **proposta** (inferência de produto). Ausência de confirmação não significa ausência do recurso. Não foram atribuídas notas arbitrárias nem usado ranking de estrelas para concluir qualidade.

Ruflo foi usado para verificar coordenação e rotear a tarefa. Três agentes executores Codex trabalharam em paralelo, somente leitura: mercado brasileiro, mercado internacional e auditoria financeira. Coordenador examinou interface, privacidade, Android e referências locais, e consolidou este documento. Memória técnica local do Ruflo foi inicializada e recebeu resumo sem dados pessoais. Roteamento não equivale a execução automática por agentes Ruflo.

## 3. Comparação rápida

| Produto | Principal força documentada | Limite relevante | Referência útil para Fluxo |
|---|---|---|---|
| Organizze | Contas/cartões, parcelas, conexão dos três bancos | Conciliação de transferências/faturas exige tratamento; exportação geral web | Semântica consistente de caixa e competência |
| Mobills | Amplitude de planejamento, importação e cartões | Integração diária com restrições de edição e divergências documentadas | Correções locais devem sobreviver à automação |
| Minhas Economias | Bancos brasileiros, regras, membros e objetivos | Nova experiência sem CSV/PDF nativo; transição de versões | Portabilidade e migração sem perda |
| YNAB | Dinheiro reservado, metas e tratamento de dívida | Importação direta declarada não cobre Brasil | Separar compra, dívida e pagamento |
| Wallet | Regras, filtros, contas e planejamento | Exportação não recompõe configurações; limites de recorrência | Backup completo e calendário explícito |
| Spendee | Carteiras e compartilhamento | Dependência de banco/MFA; recorrência em carteira manual | Vínculos reversíveis e origem do registro |
| Money Manager | Parcelas, ciclos de cartão e backup SQLite | Trabalho manual; sem sync simultâneo declarado | Profundidade financeira com dados portáveis |
| Cashew | Personalização, orçamento flexível e automação | Compartilhamento depende de conta Google comum; rateio não nativo | Captura configurável e interface gradual |
| Actual Budget | Dados locais, importação e sync opcional | Servidor para bank sync; Pluggy experimental | Conciliação e controle sobre hospedagem |
| Ivy Wallet | Android Kotlin/Compose, simplicidade | Sem manutenção dos autores desde 2024 | Referência visual/técnica histórica |

As fichas abaixo sustentam a matriz com fontes. Comparação é por capacidades, não recomendação de assinatura.

## 4. Preços e cobertura dos bancos

| Produto/plano | Preço observado | C6 / Nubank / Itaú via conexão bancária |
|---|---|---|
| Organizze Manual | R$35/mês; R$199,90/ano à vista ou 12×R$19,90 = R$238,80 | Manual não inclui conexão |
| Organizze Conectado | R$45/mês; R$399,90/ano à vista ou 12×R$39,90 = R$478,80 | Os três listados; 3 conexões PF |
| Organizze Plus | R$69/mês; R$599,90/ano à vista ou 12×R$59,90 = R$718,80 | Os três listados; 10 conexões PF/PJ |
| Mobills Premium | R$42,90/mês no app; R$199,90/ano à vista ou 12×R$19,90 = R$238,80 | Nubank/Itaú listados; C6 não confirmado |
| Mobills Pro | R$299,90/ano à vista ou 12×R$29,99 = R$359,88 | Não presumir cobertura adicional |
| Minhas Economias Meu Clube | R$24,90/mês ou R$199,90/ano; manual gratuito | Os três listados |
| YNAB | US$14,99/mês ou US$109/ano; teste direto 34 dias | Brasil fora da cobertura direta declarada |
| Wallet | Gratuito; Premium mensal/anual/vitalício, preço dentro do app | Três bancos não confirmados |
| Spendee | Basic gratuito; Plus US$1,99/mês ou US$14,99/ano; Premium US$5,99/mês ou US$35,99/ano | Brasil aparece no seletor; três bancos não confirmados |
| Money Manager | Gratuito com anúncios; página internacional paga exibiu US$5,99 | Integração com os três não localizada |
| Cashew | Gratuito com compras; assinatura e vitalício documentados, preço BR não verificado | FAQ descreve automação por notificações; não confirma os três |
| Actual Budget | Software aberto; custo total depende de hospedagem/provedor escolhido | Pluggy para bancos brasileiros, experimental; não testado com os três |
| Ivy Wallet | Projeto gratuito/aberto, sem manutenção | Não estabelecida nesta pesquisa |

Parcelamento anual **não é mensalidade cancelável**. Valores internacionais não foram convertidos nem apresentados como preço da Play brasileira. Organizze conta conta+cartão do mesmo banco/CPF como uma conexão. Fontes de preços: [Organizze](https://www.organizze.com.br/planos/), [condições Organizze](https://ajuda.organizze.com.br/hc/pt-br/articles/5661263190035-Conhe%C3%A7a-nossos-pre%C3%A7os-e-assinaturas), [Mobills Premium](https://lp2.mobills.com.br/gerenciador-financeiro), [Mobills Pro](https://lp2.mobills.com.br/mobills-pro), [Meu Clube](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35615401810715-Qual-o-valor-e-forma-de-pagamento-da-assinatura-Meu-Clube), [YNAB](https://www.ynab.com/pricing), [Wallet](https://support.budgetbakers.com/hc/en-us/articles/7151349344018-Everything-about-Premium), [Spendee](https://www.spendee.com/pricing), [Realbyte pago](https://play.google.com/store/apps/details?hl=en&id=com.realbyteapps.moneya), [Cashew FAQ](https://cashewapp.web.app/faq.html).

## 5. Fichas dos concorrentes

### Organizze

Forte em organização de contas e cartões, categorias/subcategorias, limites, lançamentos parcelados/fixos, tags e anexos. Android, iOS e web. Lista oficial inclui C6, Nubank e Itaú; planos conectados trabalham com atualização diária e cotas extras. É comparação especialmente relevante para o conjunto de bancos do proprietário. [Produto](https://www.organizze.com.br/), [bancos, atualizado em 03/08/2026](https://ajuda.organizze.com.br/hc/pt-br/articles/34237078723091-Quais-bancos-est%C3%A3o-dispon%C3%ADveis-para-conectar-sincronizar-no-Organizze), [lançamentos](https://ajuda.organizze.com.br/hc/pt-br/articles/5662695438739-Web-Lan%C3%A7amentos-Como-lan%C3%A7ar-uma-despesa).

Limitação documentada: transferências e pagamentos importados precisam de marcadores especiais para evitar distorções em determinados totais. Exportação geral é web, até 24 meses por operação. Relatórios podem usar data de compra ou de fatura, exigindo entender competência. [Marcadores](https://ajuda.organizze.com.br/hc/pt-br/articles/42157927350547-Web-Conex%C3%A3o-Banc%C3%A1ria-Ignorar-transa%C3%A7%C3%A3o-lan%C3%A7amento-com-marcador-especial), [exportação](https://ajuda.organizze.com.br/hc/pt-br/articles/5994285582611-Web-Lan%C3%A7amentos-Exportar-lan%C3%A7amentos-backup?sort_by=votes), [relatórios Android](https://ajuda.organizze.com.br/hc/pt-br/articles/9967594029587-Android-Relat%C3%B3rios-Relat%C3%B3rio-de-categorias).

Oferece conector para assistentes IA com leitura padrão, escrita opcional e revogação. Isso serve de referência para consentimento futuro, não justifica introduzir IA já. [Conector](https://ajuda.organizze.com.br/hc/pt-br/articles/53424370501523-Como-conectar-a-Organizze-com-assistentes-de-IA).

**Lição:** evitar que o usuário precise conhecer marcadores internos para impedir dupla contagem; mostrar claramente por que um movimento altera caixa, consumo ou dívida.

### Mobills

Amplo conjunto manual: orçamento, objetivos, contas/cartões, parcelas, recorrências, tags e importações. Pro acrescenta assistente WhatsApp. Android documenta leitura de notificações/SMS em beta, com aviso para inserir despesa. Essa documentação é de 2024: não prova comportamento atual nem equivalente ao processamento automático do Fluxo. [Funcionalidades](https://mobills.zendesk.com/hc/pt-br/categories/360004156193-Todas-as-funcionalidades), [notificações](https://mobills.zendesk.com/hc/pt-br/articles/360051695074-Como-importar-minhas-despesas-atrav%C3%A9s-de-notifica%C3%A7%C3%B5es-ou-SMS-para-o-Mobills).

Integração oficial informa atualização diária, histórico a partir do mês de adesão e restrições à edição/exclusão/vínculo manual em contas automáticas; categorização automática dessas contas não é oferecida na página consultada. Nubank e Itaú constam; C6 não consta nas listas examinadas. Documentação de cartões reconhece divergências e condições de elegibilidade. Não há medição da frequência desses problemas nesta pesquisa. [Contas automáticas](https://mobills.zendesk.com/hc/pt-br/articles/17287679665563-Como-integrar-sua-conta-banc%C3%A1ria-no-Mobills-via-Open-Finance-e-d%C3%BAvidas-frequentes), [cartões automáticos](https://mobills.zendesk.com/hc/pt-br/articles/17288982976539-Como-integrar-seu-cart%C3%A3o-de-cr%C3%A9dito-no-Mobills-via-Open-Finance-e-d%C3%BAvidas-frequentes).

Exportação web PDF/XLS inclui pendentes e efetuadas; mobile tem exclusões, incluindo transferências e pendentes. [Exportar](https://mobills.zendesk.com/hc/pt-br/articles/360051811854-Como-exportar-minhas-transa%C3%A7%C3%B5es-do-Mobills).

**Lição:** manual e automático precisam coexistir. Não bloquear correção do usuário, não apagar categoria por nova importação e não esconder restrições entre plataformas.

### Minhas Economias

Nova experiência tem controle manual gratuito e Open Finance pago, com C6/Nubank/Itaú listados. Cadastro diferencia crédito/débito, parcelas/recorrência e fatura; transferências não representam consumo. Oferece objetivos e filtros por membros/tags. [Bancos](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35592369795995-Quais-bancos-est%C3%A3o-disponiveis-para-conex%C3%A3o-autom%C3%A1tica), [cadastro](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35592699159707-Como-registrar-um-gasto-manualmente), [transferências](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35611866010523-Como-registrar-uma-transfer%C3%AAncia-entre-contas), [filtros](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35612043640475-Como-aplicar-filtros-no-extrato).

Ponto fraco concreto: nova experiência não oferece exportação CSV/PDF nativa; FAQ direciona a MCP/atendimento. Versão antiga web possui exportação, condicionada aos dados anteriormente sincronizados. O aplicativo antigo será descontinuado, segundo FAQ. Não misturar funcionalidades entre gerações. [Exportação atual](https://faq.minhaseconomias.com.br/hc/pt-br/articles/54680203879963-Consigo-exportar-o-extrato-da-minha-conta), [exportação antiga](https://faq.minhaseconomias.com.br/hc/pt-br/articles/48360429520283-Como-exportar-meus-dados-da-antiga-experi%C3%AAncia), [transição](https://faq.minhaseconomias.com.br/hc/pt-br/articles/48463892293659-A-vers%C3%A3o-antiga-continuar%C3%A1-dispon%C3%ADvel).

Contas manuais não se vinculam às conectadas; recorrência vinculada a objetivos não repete automaticamente esse vínculo. [Contas](https://faq.minhaseconomias.com.br/hc/pt-br/articles/42746043272987-Posso-transferir-o-saldo-da-minha-conta-manual-para-a-conta-conectada-no-Open-Finance), [objetivos](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35615605707163-O-que-%C3%A9-a-ferramenta-de-Objetivos).

**Lição:** manter portabilidade independente de IA, migração verificável e continuidade entre entradas manuais e conectadas.

### YNAB

Referência de planejamento do dinheiro disponível, metas e dívida. Pagamento de cartão tem tratamento próprio, com dinheiro reservado para quitar compromisso; não vira novamente compra. Compartilhamento até seis pessoas e uma moeda por plano. Importação direta cobre instituições selecionadas de EUA, Canadá, Reino Unido e União Europeia, não Brasil. [Cartões](https://support.ynab.com/en_us/credit-card-payments-a-guide-r1_506Q1j), [preço/cobertura](https://www.ynab.com/pricing).

Limitações: exportação somente web; CSV/TSV não leva metas e notas de categorias. Offline abre planos previamente baixados. Parcelamento brasileiro e fechamento de fatura local não foram confirmados. [Exportação](https://support.ynab.com/en_us/how-to-export-plan-data-Sy_CouWA9?mobile-help=true), [offline](https://support.ynab.com/en_us/navigating-multiple-plans-r1qeOvbC5).

**Lição:** distinguir dinheiro existente, dinheiro reservado e compromisso futuro. Exigir aprendizado e assinatura internacional pode reduzir adequação ao uso simples pretendido; isso é inferência de adequação, não defeito comprovado.

### Wallet — BudgetBakers

Regras, categorias, etiquetas, orçamentos, importação e compartilhamento oferecem amplitude. Cartão manual permite limite, dívida e vencimento; a conexão bancária pode não fornecer todos os tipos de conta/cartão. Recursos variam entre Android, iOS e web. [Recursos](https://support.budgetbakers.com/hc/en-us/sections/6961343873426-Features), [cartão](https://support.budgetbakers.com/hc/en-us/articles/6950259945362-Adding-a-Credit-Card), [Premium](https://support.budgetbakers.com/hc/en-us/articles/7151349344018-Everything-about-Premium).

Limites documentados: atualização bancária normalmente a cada 24 horas; algumas instituições exigem ação manual. Exportação de transações não inclui orçamentos, metas, recorrências e configurações; exclusão pessoal não é restaurada pelo backup do fornecedor. Recorrência no dia 31 pode ser pulada em mês curto; uso offline em vários dispositivos pode duplicar eventos. [Atualização](https://support.budgetbakers.com/hc/en-us/articles/7152012249618-How-often-does-my-bank-account-data-update), [backup](https://support.budgetbakers.com/hc/en-us/articles/10105401036690-Backup-Measures-and-Data-Loss), [recorrências](https://support.budgetbakers.com/hc/en-us/articles/7149523920786-Setup-Planned-Payments).

**Lição:** definir calendário, restauração e idempotência antes de anunciar conveniência. Quantidade mundial de bancos não comprova C6/Nubank/Itaú.

### Spendee

Carteiras e compartilhamento são diferenciais. Basic limita carteira manual/orçamento; planos pagos ampliam organização e Premium oferece conexão/categorização. Transferência fica fora dos relatórios de receita/despesa. Recorrências são documentadas apenas para carteiras manuais. [Planos](https://www.spendee.com/pricing), [transferências](https://help.spendee.com/article/234-transfers), [agendamentos](https://help.spendee.com/article/229-scheduled-transactions).

Sync diário depende do banco e MFA; podem existir atrasos de até três dias. Desconectar/reconectar pode perder categorias, notas e parte do histórico, recuperando geralmente 60–90 dias. Brasil consta do seletor, sem confirmação individual dos três bancos. [Sync](https://help.spendee.com/article/140-sync-my-bank-account-with-spendee), [troubleshooting](https://help.spendee.com/article/219-how-to-troubleshoot-connected-bank-account-issue), [bancos](https://www.spendee.com/bank-connect).

**Lição:** separar origem externa do enriquecimento local; reconectar uma fonte não deve destruir trabalho manual. Compartilhamento precisa de identidade e permissões próprias.

### Money Manager — Realbyte

Boa referência de controle manual: contas, cartões, subcategorias, transferências e parcelas. Documentação Android representa quantidade de parcelas, saldo restante, fechamento e vencimento. Backup local/Google Drive restaura SQLite; importação de planilha exige TSV e campos compatíveis. Planilha exportada não equivale a backup completo. [Parcelas](https://help.realbyteapps.com/hc/en-us/articles/360046668993-How-to-set-up-a-repeat-schedule-installment), [backup](https://help.realbyteapps.com/hc/en-us/articles/360042747934-How-to-backup-and-restore-data), [importação](https://help.realbyteapps.com/hc/en-us/articles/360043223253-How-to-import-bulk-data-by-Excel-file).

Gratuito contém anúncios e limite declarado de 15 ativos; pago amplia recursos. Resposta do suporte de junho/2026 informa ausência de sincronização entre dispositivos. Ficha Play declara possível coleta de dados pessoais/financeiros e compartilhamento de atividade/identificadores; armazenamento local não deve ser confundido com ausência de coleta. [Play gratuito](https://play.google.com/store/apps/details?hl=en-US&id=com.realbyteapps.moneymanagerfree).

**Lição:** profundidade de cartão e restauração são úteis mesmo sem integração bancária. Documentos de parcelas/backup são de 2022/2023, portanto interfaces atuais precisam de teste antes de comparação operacional definitiva.

### Cashew

Referência próxima por automação e personalização. Repositório documenta orçamentos com períodos flexíveis, metas, categorias, edição múltipla, biometria e importação. Play anuncia recorrências e backup Google Drive. [Repositório](https://github.com/jameskokoska/Cashew), [Play](https://play.google.com/store/apps/details?hl=en-US&id=com.budget.tracker_app).

FAQ descreve leitura de notificações/SMS, app links e outras automações. Crédito é organizado por conta, período e orçamento; pagamento usa transferência. Não há rateio nativo entre categorias. Compartilhamento entre pessoas depende de conta Google comum; compras Pro são separadas entre Android/iOS. Sem backup local ou Drive, suporte não recupera dados. Valores brasileiros não verificados. [FAQ oficial](https://cashewapp.web.app/faq.html).

Código público é referência, mas esta pesquisa não demonstrou equivalência entre commit público e binário atual da loja. Não concluir que inspecionar repositório audita a versão instalada.

**Lição:** simplificar configuração da captura e oferecer boa experiência sem obrigar personalização extensa. Para colaboração futura, preferir contas individuais com permissão revogável.

### Actual Budget

Referência de controle sobre dados: cliente e servidor separados; orçamento e importação/exportação de arquivos podem funcionar sem servidor. Sincronização entre dispositivos e bancária precisam de servidor. Aceita CSV, QIF, OFX, QFX e CAMT. [Instalação](https://actualbudget.org/docs/install/), [importação](https://actualbudget.org/docs/transactions/importing/).

Integração Pluggy para Brasil é experimental e exige configuração de credenciais/item IDs. Sincronização bancária não ocorre automaticamente no fluxo documentado: usuário solicita atualização. Criptografia ponta a ponta opcional protege dados do orçamento, mas não tokens bancários no servidor; dados locais também não recebem essa proteção adicional. [Bank sync](https://actualbudget.org/docs/advanced/bank-sync/), [Pluggy](https://actualbudget.org/docs/advanced/bank-sync/pluggyai/), [criptografia/sync](https://actualbudget.org/docs/getting-started/sync/).

**Lição:** nuvem deve ter fronteiras claras. Hospedagem própria oferece controle, mas traz manutenção; não cabe afirmar custo zero de operação. Bom modelo para importação e conciliação, sem obrigar Fluxo a adotar servidor agora.

### Ivy Wallet

Projeto Android gratuito, Kotlin/Compose, controle manual e personalização. Código e organização de módulos servem como referência próxima da stack do Fluxo. Autores declaram encerramento de manutenção em **05/11/2024**; GitHub mostra arquivamento em **11/04/2026**. São eventos diferentes. [Repositório oficial](https://github.com/Ivy-Apps/ivy-wallet).

**Lição:** avaliar manutenção, não apenas aparência e quantidade de funções. Não adotar base abandonada como atalho sem assumir atualização e suporte. Nesta pesquisa não houve reutilização de código de concorrentes.

## 6. Voz dos usuários: sinais, não estatística

| Fonte | Relato e data | Interpretação limitada |
|---|---|---|
| [Organizze Play](https://play.google.com/store/apps/details?hl=pt-BR&id=com.organizze.android) | Iran Marcius, 06/05/2026: elogia funcionamento, pede melhores filtros/relatórios e consistência entre plataformas | Descoberta e paridade merecem teste de uso |
| [Mobills Play](https://play.google.com/store/apps/details?hl=pt-BR&id=br.com.gerenciadorfinanceiro.controller) | Jonas Teixeira, 06/12/2024: gosta da web, critica logins e publicidade mesmo pago | Relato histórico; não comprova versão atual |
| [Minhas Economias Play](https://play.google.com/store/apps/details?hl=pt_BR&id=com.minhaseconomias) | Paulo Antonio, 29/04/2026: reclama de interface/migração | Testar atualização como jornada, não apenas SQL |
| [Money Manager Play](https://play.google.com/store/apps/details?hl=en-US&id=com.realbyteapps.moneymanagerfree) | Kristen, 23/06/2026: elogia manual e pede sync; suporte responde dia seguinte | Demanda por continuidade entre aparelhos |
| [Cashew Play](https://play.google.com/store/apps/details?hl=en-US&id=com.budget.tracker_app) | J Sev, 18/08/2026: dificuldade com saldo acumulado/layout; desenvolvedor, 22/08, aponta configurações existentes | Problema pode ser descoberta do recurso, não ausência dele |

Não houve amostragem representativa, análise longitudinal ou cálculo de frequência. Relatos antigos não foram convertidos em bugs atuais. Respostas do desenvolvedor foram consideradas quando visíveis.

## 7. Auditoria do Fluxo: pontos fortes preserváveis

Evidência primária: código do commit auditado e [validação já registrada](../VALIDATION.md).

- Direção e modalidade separadas; compra no crédito não vira saída imediata de caixa no resumo.
- Valores individuais em `Long` centavos, parsing sem ponto flutuante e agregações amplas sem overflow simples.
- Itens em revisão fora dos totais; múltiplos valores e faturas recebem tratamento conservador inicial.
- Allowlist, consentimento e pausa verificados no listener e na persistência; texto bruto não persistido.
- SQL parametrizado, operações IO com exclusão mútua, migração preservando histórico, tombstones e correções manuais.
- CSV com escape, proteção contra fórmulas e seletor Android; documentação explicita que não é backup.
- Interface informa “resultado observado” e cobertura parcial; não promete saldo bancário.
- Sem permissão INTERNET, analytics ou servidor. SQLite protegido pelo sandbox/dispositivo, sem criptografia adicional própria.

Esses pontos são base técnica, não prova de cobertura de todos os templates bancários. A validação anterior registra 51 testes distintos aprovados; não foi repetida integralmente nesta auditoria documental.

## 8. Achados de código e lacunas financeiras

Prioridades de defeito: **P1** = pode distorcer registro/totais em cenário plausível; **P2** = perda de captura ou comportamento condicional relevante. Não foi demonstrada exploração de segurança nem perda generalizada em uso real.

### A1 — P1: mudança de estado da mesma notificação é ignorada

`app/src/main/java/br/com/fluxo/app/LedgerRepository.kt:76` usa `CONFLICT_IGNORE`; `BankNotificationListener.kt:55` busca identidade estável. Se a mesma chave chega como Pix pendente e depois enviado, primeiro registro permanece. Se confirmado recebe depois cancelamento, continua confirmado. Evidência: fluxo/SQL inspecionado; cenário ainda não executado em instrumentação nesta auditoria.

**Proposta:** separar observação recebida de lançamento financeiro; guardar versão estruturada, origem e mudança de estado. Atualização deve reabrir revisão quando necessário, preservando correções humanas e exclusões. `REPLACE` indiscriminado apagaria essas garantias.

### A2 — P1: qualificadores de pendência escapam ao parser

Em `core/src/main/kotlin/br/com/fluxo/core/NotificationParser.kt:12`, `:14` e `:51`, conjunto negativo não cobre todas as expressões. Agente executou contra classes Kotlin locais via JShell:

| Entrada sintética | Resultado atual | Resultado seguro proposto |
|---|---|---|
| `Pix enviado R$ 50,00 em analise` | `EXPENSE / PIX / CONFIRMED` | Revisão; não incluir no caixa confirmado |
| `Pix recebido R$ 50,00 nao confirmado` | `INCOME / PIX / CONFIRMED` | Revisão; negação prevalece |

**Proposta:** testes adversariais de negação e estado, adaptadores por banco com fixtures versionadas e evidência positiva contextual. Não basta acrescentar palavras indefinidamente a uma regex global.

### A3 — P2: veto de marketing elimina compra válida misturada

`NotificationParser.kt:11` e `:28` retornam `null` para qualquer ocorrência de termos como cashback/oferta. Reprodução sintética executada: `Compra aprovada no credito de R$ 80,00. Cashback liberado` retorna `null`.

**Proposta:** distinguir trecho promocional de transacional. Mensagem mista com evidência de compra deve ao menos entrar em revisão; promoção isolada continua descartada. Não foi demonstrado que os bancos do usuário emitem exatamente esse texto.

### A4 — P2: identidade pode duplicar redelivery sem horário original

`BankNotificationListener.kt:56` usa `postTime` quando `notification.when` não é positivo. Mesma chave entregue com novo `postTime` produz novo hash; mudanças do próprio `when` também alteram identidade. Evidência por inspeção; ocorrência nos três bancos não medida.

**Proposta:** distinguir atualização da mesma notificação de novo evento, conciliando com cautela. Não deduplicar só por valor e intervalo: duas compras legítimas iguais devem permanecer distintas.

### Limitações de domínio: escopo incompleto, não regressões

| Lacuna | Efeito concreto | Contrato necessário |
|---|---|---|
| Transferência entre contas próprias | Pix Nubank→C6 aparece como receita e despesa; caixa agregado pode compensar, orçamento infla | Conta origem/destino e vínculo; não consumir orçamento |
| Pagamento de fatura | Entra em revisão; confirmar como despesa pode contar compra e pagamento duas vezes | Liquidação de dívida separada de consumo |
| Estorno/reembolso | Entrada corrige caixa, mas gasto da categoria original não diminui | Vínculo ao original, parcial/integral e política de competência |
| Parcelas | Sem compromisso futuro, fechamento ou vencimento | Compra total, parcelas e faturas distintas |
| Revisão com descrição genérica | Usuário pode precisar consultar banco para descobrir valor/contexto | Motivo claro e metadados mínimos, sem texto bruto |
| Categoria manual | Capturas ficam Sem categoria | Regras locais explicáveis, prévia e desfazer |

Base: `Models.kt:3`, `SpendingAnalytics.kt:18`, `LedgerRepository.kt:74` e contratos já reconhecidos em [SPEC](../SPEC.md) e [ROADMAP](../ROADMAP.md).

## 9. Interface, privacidade e operação

**Captura habilitada não significa listener conectado.** `MainActivity.kt:191` calcula estado com toggle, permissão e bancos. Listener não publica estado de conexão nem último processamento. Proposta: mostrar permissão, ligação do serviço, última observação e erro separadamente. Ausência de notificação recente não comprova falha: usuário pode não ter movimentado dinheiro.

**Comparação mensal precisa de contexto.** Planejamento compara período selecionado ao mês anterior inteiro. No começo do mês, menor gasto não comprova economia. Oferecer “até o mesmo dia” ou avisar que mês atual está incompleto. Não afirmar tendência com cobertura parcial.

**Backup é lacuna importante.** Manifest desabilita backup automático; CSV contém categoria por lançamento, mas app não possui restauração e arquivo não inclui toda a configuração da base. Desinstalação/perda do aparelho pode eliminar histórico. Priorizar arquivo completo criptografado, restaurado primeiro em área temporária, com validação e troca atômica.

**Proteção visual opcional.** Não há biometria própria nem ocultação de valores/tela recente. Propor bloqueio após inatividade e modo discreto; captura pode continuar em segundo plano. Biometria não substitui criptografia nem estratégia de recuperação.

**Escala ainda sem medição.** Repository recarrega histórico completo a cada alteração; busca/analytics percorrem listas. Medir 1 mil, 10 mil e 50 mil registros antes de introduzir paginação/agregação incremental. Não classificar lentidão como observada sem benchmark.

**CI compila testes Android, mas não os executa.** `.github/workflows/android.yml` roda JVM, APK e lint, além de montar APK de instrumentação. Adicionar execução em emulador e teste de migração/restauração. Distribuição atual é debug; assinatura release e continuidade de atualização merecem plano antes de distribuição mais ampla.

**Acessibilidade ainda exige ensaio dedicado.** Verificar TalkBack, fonte ampliada, contraste, campos monetários, mensagens de erro, paisagem e tela pequena. Não foi feito teste assistivo completo nesta auditoria; não declarar conformidade ou defeitos visuais não reproduzidos.

### Limites externos da captura

Android 15 restringe conteúdo não ocultado de notificações em que detecta OTP para listeners não confiáveis. Isso **não significa bloqueio de todas as notificações bancárias**. Fluxo deve explicar valor indisponível e permitir lançamento manual, sem tentar contornar proteção. [Android 15](https://developer.android.com/about/versions/15/behavior-changes-all).

API também documenta restrições em aparelhos low-RAM com Android 10 ou inferior e perfis de trabalho. Serviço precisa considerar conexão/desconexão e reconexão permitida pela plataforma. [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

Notificações são observações parciais: não constituem extrato integral, histórico retroativo ou confirmação de saldo. App bancário pode ocultar valores, mudar texto ou não notificar determinado evento. O produto deve manter esse limite visível mesmo depois de ganhar gráficos e projeções.

## 10. Plano de melhorias priorizado

Tudo abaixo é **proposta**, não funcionalidade entregue. Esforço relativo: P = mudança localizada; M = vários fluxos/testes; G = alteração de domínio/migração. Não representa prazo contratado. Ordem combina impacto no usuário, integridade e dependências.

| ID | Prioridade / esforço | Melhoria e entrega | Dependências | Critério principal de aceite |
|---|---|---|---|---|
| M01 | Primeiro / M | Corrigir negações e mensagens mistas | A2/A3 | Fixtures adversariais nunca confirmam pendência; compra mista não some |
| M02 | Primeiro / G | Atualizações idempotentes e reconciliação de identidade | A1/A4; contrato de origem | Redelivery não duplica; mudança de estado aparece; edição/exclusão humana preservada |
| M03 | Primeiro / M | Diagnóstico e onboarding por banco | Estado do listener | Permissão, toggle, serviço e última captura diferenciados; orientação recuperável |
| M04 | Primeiro / M | Corpus por banco e matriz de aparelhos | Consentimento e anonimização | Cenários por C6/Nubank/Itaú; resultados separados entre sintético e real |
| M05 | Alta / G | Backup criptografado e restauração | Inventário SQLite e formato versionado | Restaurar arquivo reproduz entidades/totais; falha não altera base atual |
| M06 | Alta / G | Contas, saldo inicial e transferências próprias | Migração, M02 | Transferir entre próprias contas não muda renda/consumo consolidado |
| M07 | Alta / G | Cartões, parcelas, fechamento e fatura | M06, política de competência | Compra + pagamento não duplicam gasto; parcelas somam total exato |
| M08 | Alta / M | Estornos/reembolsos vinculados | M06/M07 | Estorno parcial/integral ajusta conta/dívida e categoria segundo política |
| M09 | Alta / M | Importação CSV/OFX com prévia | M02/M06 | Importar novamente não duplica; erros por linha; rollback integral |
| M10 | Seguinte / M | Regras locais de categorias | M02; metadados mínimos opcionais | Explicar regra, simular alcance, preservar alteração manual, desfazer |
| M11 | Seguinte / M | Recorrências e lembretes | M06/M07 | Mês curto/ano bissexto definidos; previsão não confirma pagamento |
| M12 | Seguinte / M | Projeção 30/60/90 dias | M06/M07/M11 | Separar observado, agendado e estimado; fórmula e cobertura visíveis |
| M13 | Seguinte / M | Categorias próprias, tags e edição em lote | Migração de categorias | Arquivar sem perder histórico; prévia e desfazer no lote |
| M14 | Seguinte / M | Metas e reserva por finalidade | M06/M12 | Reserva não vira despesa; depósitos/resgates coerentes |
| M15 | Seguinte / M | Relatórios de caixa/consumo e comparação justa | M06/M07/M08 | Totais conciliam com detalhe; comparação até mesmo dia opcional |
| M16 | Alta / M | Biometria, modo discreto e acessibilidade | Política de desbloqueio | Fluxo manual acessível; retorno bloqueia quando devido; sem dados no preview opcional |
| M17 | Alta / M | CI instrumentado, migrações e distribuição release | Ambiente CI, compatibilidade de assinatura; M05 se nova chave | Checks executam emulador; atualização compatível preserva dados ou migração por backup é validada |
| M18 | Condicional / G | Nuvem/compartilhamento/Open Finance/IA | Necessidade validada e arquitetura própria | Consentimento granular, custo definido, exclusão/exportação e isolamento testados |

### Contratos detalhados dos incrementos principais

**Captura confiável (M01–M04).** Separar `observedAt` de data financeira, origem de conteúdo interpretado e decisão automática de decisão humana. Adotar resultado de parser tipado com motivo e versão, sem guardar notificação bruta. Atualizações podem confirmar ou invalidar hipótese anterior; cancelamento nunca deve ser silencioso. Definir precedência para callbacks fora de ordem e notificações reutilizadas. Cada mudança exige teste unitário e caso integrado listener→repository. Não prometer precisão percentual antes de corpus representativo.

**Recuperação (M05).** Inventário inclui lançamentos, categorias, orçamentos, contas futuras, vínculos, regras e identificadores necessários para evitar recriação. Formato versionado e autenticado; senha errada, truncamento, versão incompatível e cancelamento devem falhar sem sobrescrever dados. Restaurar com captura pausada, conferir contagens e somas e pedir reativação explícita. Chave de backup deve permitir recuperar em outro aparelho; não depender exclusivamente de chave não exportável do dispositivo antigo. Interface precisa distinguir CSV legível de backup protegido.

**Contas e transferências (M06).** Banco não é conta: pessoa pode ter múltiplas contas/cartões no mesmo banco. Saldo inicial informado deve ter data e origem. Transferência tem duas pernas vinculadas; tarifa, se existir, é despesa separada. Sugerir pareamento por sinais, sem confirmar apenas por valor/horário. Permitir desvincular e corrigir. Migração deve preservar registros sem conta identificada, sem inventar saldo histórico.

**Cartão e parcelas (M07).** Modelar compra, cronograma e liquidação. R$100 em três parcelas deve somar exatamente 10.000 centavos, por exemplo 33,34 + 33,33 + 33,33, com regra documentada. Vencimento/fechamento configuráveis; notificação da parcela não cria novamente a compra total. Pagamento parcial reduz dívida; juros/tarifas são fatos separados. Não inferir parcelamento inteiro a partir de valor único sem informação. Testar compra no fechamento, antecipação, cancelamento e mudança de ciclo.

**Estorno (M08).** Associar ao original quando confirmado pelo usuário, aceitar parcial e impedir reembolso acima do valor elegível sem justificativa explícita. Estorno de cartão pode reduzir dívida sem entrada bancária. Relatórios devem definir se ajustam competência original ou período do estorno; ambos são visões possíveis, nunca mistura silenciosa.

**Importação e regras (M09/M10/M13).** Arquivo não confiável: limitar tamanho, validar moeda/data/centavos, mostrar prévia e permitir abandonar. Preservar ID externo/origem e correções; sugestão de duplicata deve ser reversível. Regras locais inicialmente simples, ordenadas e explicáveis; estabelecimento só deve ser usado se houver metadado estruturado consentido. Uma descrição genérica não sustenta classificação por comerciante. Categoria deixa de depender apenas de enum fixo quando usuário puder criar/arquivar.

**Planejamento (M11/M12/M14/M15).** Previsão não vira dinheiro real. Cobrança recorrente cria expectativa; chegada de registro real concilia expectativa. Para dia 31, usuário escolhe último dia válido ou outra regra explícita. Cancelar assinatura interrompe futuras expectativas sem apagar passado. Projeção deve indicar contas cobertas, saldo inicial informado e itens ausentes. Meta é reserva, não nova despesa; compra usando reserva continua consumo uma única vez.

**Qualidade e privacidade (M16/M17).** Testes devem cobrir perda de processo, armazenamento indisponível, cancelamento de exportação/restauração, atualização de app e banco grande. Biometria deve ter recuperação por credencial do aparelho quando apropriado. Release usa chave preservada e segredos fora do Git. Conferir certificado contra APK já distribuído: nova chave de release não permite presumir atualização sobre debug. Se incompatível, validar migração por backup (M05) antes de qualquer desinstalação. Nenhuma telemetria financeira necessária para esses testes: fixtures sintéticas e medições locais bastam.

### Sequência de entrega proposta

1. **0.3 — confiança na captura:** M01–M04, checks de regressão e diagnóstico mínimo.
2. **0.4 — recuperação e base de contas:** M05–M06, com migração e teste de restauração antes de ampliar domínio.
3. **0.5 — cartão completo:** M07–M08 e conciliação de compra/pagamento/estorno.
4. **0.6 — menos trabalho manual:** M09–M11 e M13.
5. **0.7 — planejamento:** M12, M14–M15. M16–M17 acompanham as entregas relevantes, não ficam adiados até o fim.

Versões são agrupamentos propostos, ajustáveis após detalhamento. M18 não entra automaticamente nessa sequência.

## 11. Organização dos próximos agentes

| Responsável | Caminhos / contrato | Entrega |
|---|---|---|
| Coordenador | Specs, modelos compartilhados, integração e Git | Fechar semântica e ordem de migração; revisar/push |
| Dados | `core/`, persistência e migrações em worktree própria | Regras, consistência e testes de domínio |
| Android/UI | Compose, listener e fluxos em worktree própria | Experiência, diagnóstico, acessibilidade e testes de fluxo |
| Validação | Revisão independente e matriz de cenários | Reproduzir defeitos, testar limites e conferir evidências |

Contratos compartilhados devem ser definidos pelo coordenador antes de escrita paralela. Limite de quatro agentes simultâneos. Ruflo registra roteamento/decisões; agentes Codex executam. Nenhum agente deve gravar notificações reais em logs, memória ou Git.

## 12. Critérios para decidir o que realmente melhorou

- **Confiabilidade:** falsos confirmados, eventos perdidos e duplicatas em corpus rotulado; publicar numerador/denominador e separar cada banco/modalidade. Corpus de notificações mede extração/classificação; movimentação que banco não notificou exige comparação autorizada com extrato para medir cobertura financeira, sem copiar dados reais para Git ou logs.
- **Trabalho manual:** tempo e quantidade de ações para revisar, corrigir, transferir e pagar fatura, medidos em roteiro sintético.
- **Integridade:** somas antes/depois de migração e restauração; transferência conserva patrimônio; pagamento não duplica consumo; parcelas conservam total.
- **Clareza:** usuário distingue saldo informado, resultado observado, dívida e previsão em teste de tarefas.
- **Desempenho:** latência e memória com histórico crescente, aparelho/API informados.
- **Recuperação:** restaurar em instalação nova com senha correta e manter base anterior intacta em falha.

Não transformar esses indicadores em envio automático de dados. Podem ser coletados localmente durante validação consentida.

## 13. Incertezas que continuam abertas

Não há corpus real dos três bancos, teste de todos os modelos de aparelho, ensaio assistivo completo ou benchmark. Limites comerciais podem mudar. Cobertura internacional não prova suporte brasileiro. Política pública de Minhas Economias consultada é de 2023, enquanto FAQ Open Finance é de 2026: não concluir tratamento atual de credenciais a partir apenas do documento antigo. [Política](https://minhaseconomias.com.br/termos-de-uso-e-politica-de-privacidade), [FAQ atual](https://faq.minhaseconomias.com.br/hc/pt-br/articles/35613748858651-Open-Finance-o-que-%C3%A9-e-como-funciona).

Pesquisa ampla não é inventário exaustivo de todos os apps. Foram escolhidos produtos com relação direta à necessidade e fontes verificáveis. Nenhuma funcionalidade foi alterada por esta auditoria.

## 14. Skills e registro de execução

Aplicadas nesta análise: `competitive-landscape` (comparação), `code-review-excellence` (auditoria), `swarm-orchestration` (divisão de responsabilidades) e `memory-management` (registro técnico local). Núcleo operacional `rafael-core/references/core.md` aplicado. Skill `deep-research` foi examinada, mas não aplicada: suas instruções reservam o fluxo ao modo/pedido explícito correspondente.

Ruflo: `init check`, `swarm status`, `hooks route`; memória local inicialmente ausente, depois inicializada com backend SQLite e verificação 6/6 do próprio CLI. Esses testes verificam a memória Ruflo, não o app. Não houve alteração de código funcional nem nova execução integral da suíte Android. Reproduções sintéticas de A2/A3 ocorreram sobre classes locais; A1/A4 são achados por inspeção e precisam de testes integrados no incremento corretivo.
