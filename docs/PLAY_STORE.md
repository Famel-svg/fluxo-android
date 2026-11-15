# Google Play — ficha, formulários e pendências

Status: rascunho para a variante `play` (applicationId `io.github.famelsvg.fluxo`). Nada aqui foi publicado.
Fontes: `docs/PRIVACY.md`, `docs/SPEC.md`, `README.md`, `docs/specs/M20-play-pro.md`, código da captura e das telas.
Itens marcados com **[confirmar]** dependem de decisão do dono ou de verificação no Play Console.

## 1. Ficha da loja

**Título (24/30):** Fluxo — Gastos Offline

**Descrição curta (79/80):** Gastos pelas notificações do banco, revisão rápida e dados só no aparelho.

**Descrição longa:**

Fluxo — Gastos Offline é um controle financeiro pessoal que funciona sem conexão, sem conta e sem servidor. Os seus dados ficam guardados somente no seu aparelho.

Lançamento automático
Com o acesso a notificações concedido, o Fluxo lê as notificações de bancos, carteiras digitais e apps de benefícios conhecidos e transforma compras, Pix recebidos e enviados e outras movimentações em lançamentos. Notificações de outros apps são ignoradas. Com a captura pausada, nada é registrado.

Interpretação cuidadosa
Mensagens ambíguas, como recusas, agendamentos, pendências, faturas ou textos com mais de um valor, não entram nos totais. Elas ficam na aba Registros, em Revisar, com o motivo, para você confirmar ou corrigir.

Revisão rápida
Na lista de registros, deslize um lançamento para confirmá-lo ou para editá-lo. Você corrige valor, data, categoria e forma de pagamento sem sair da tela. [confirmar: campos exatos do editor]

Início e planejamento
- Entradas e saídas do mês. Compras no crédito aparecem separadas das saídas de caixa.
- Limite mensal por categoria, com quanto resta ou quanto passou do limite.
- Contas locais com saldo inicial e transferências próprias, que não inflam renda nem consumo.
- Renda prevista do mês (salário, vale-refeição e vale-transporte), como estimativa.

Privacidade no aparelho
- Sem conta, sem login, sem analytics e sem relatório de falhas.
- O app não pede permissão de internet.
- A captura é opcional. Sem o acesso a notificações, você lança gastos à mão.
- Você pode pausar a captura a qualquer momento, apagar um lançamento ou apagar todo o histórico.
- Backup protegido por senha que você escolhe. A exportação em planilha é texto legível: guarde esse arquivo com cuidado.

Limites
O Fluxo não substitui extrato nem saldo bancário. O histórico depende das notificações recebidas, por isso é parcial. Faturas, estornos e parcelas precisam de revisão manual para não serem contados duas vezes. Bancos podem mudar o texto das notificações ou deixar de enviá-las; nesses casos, faça o lançamento à mão.

**Regras de redação aplicadas:** sem emoji, sem palavras em caixa alta, sem promessa de ranking, prêmio ou resultado financeiro, sem nome de banco (evita uso indevido de marca na ficha). Não citar preço do Pro enquanto a compra não existir.

**Categoria sugerida:** Finanças. **Público:** adultos (18+). [confirmar]

## 2. Roteiro de screenshots (6)

Gerar com `./tools/check.ps1 shots -Themes light` usando o AVD grande (`-Avd ApkPreview_Phone_API37 -Serial emulator-5558`, 1080x2424), com o seed de demonstração (`./tools/check.ps1 seed`). Nunca usar dados reais na ficha. Textos da interface em português.

| # | Tela | O que mostra | Legenda |
|---|------|--------------|---------|
| 1 | Início | Destaque do mês com entradas x saídas (“Entrou x saiu no mês”) e gráfico | Entradas e saídas do mês em um só lugar |
| 2 | Registros, aba Revisar | Lançamentos capturados aguardando confirmação, com o motivo visível | Confirme ou corrija o que o banco notificou |
| 3 | Registros, deslizando | Um item em deslize mostrando a ação de confirmar | Deslize para confirmar ou editar |
| 4 | Planejamento | Limite por categoria com barra de progresso e renda prevista | Limites por categoria e renda prevista do mês |
| 5 | Ajustes | Aviso de privacidade, estado da captura e diagnóstico do acesso | Captura opcional, pausável a qualquer momento |
| 6 | Ajustes, Dados | Backup protegido por senha e opção Apagar histórico | Backup criptografado e apagar tudo com um toque |

Obs.: a tela 3 pode ser capturada com o item parcialmente deslocado; se a captura não ficar clara, use a tela de edição do lançamento no lugar. [confirmar]

## 3. Data safety (formulário)

Princípio: nada sai do aparelho pelo app. Pelo código, o app não declara permissão de internet no flavor `play`, e os dados são processados só no dispositivo. Pela regra do Google, dado que não é transmitido para fora do aparelho pelo desenvolvedor não é “coletado”.

| Pergunta | Resposta sugerida |
|----------|-------------------|
| Coleta dados de usuário? | Não. Nenhum dado é transmitido ao desenvolvedor ou a terceiros. |
| Compartilha dados com terceiros? | Não. |
| Dados em trânsito são criptografados? | Não se aplica: o app não transmite dados pela rede. |
| Usuário pode pedir exclusão? | Sim, no próprio app: apagar lançamento individual, Ajustes › Dados › Apagar histórico, ou desinstalar. |
| Dados categorizados (que o app acessa no aparelho) | Informações financeiras (valores, datas, estabelecimentos, categorias) e mensagens de notificação de apps financeiros: processados só no aparelho. |
| Backup | Backup automático do Android desativado (`allowBackup=false`). Backup manual é um arquivo criptografado (AES-256-GCM, PBKDF2-HMAC-SHA256 com 600.000 iterações) salvo onde o usuário escolher. |
| Criptografia em repouso | Banco SQLite privado do app, sem camada extra de criptografia. Backup manual é criptografado. Exportação em planilha não é criptografada. |
| Compras / pagamentos | Hoje o Pro é grátis para todos (`PRO_FOR_ALL=true`), então não há compra. Se o Pro for cobrado, declarar histórico de compras tratado pelo Google Play e revisar esta seção. [confirmar] |
| SDKs de terceiros | Só a Play Billing Library, presente no flavor `play`. Ela conversa com o app da Play Store, não com servidor do Fluxo. Confirmar se o formulário pede declaração para ela. [confirmar] |

## 4. Declaração de acesso a notificações (Play Console)

O Console exige justificativa e vídeo para o uso de `NotificationListenerService` e para o serviço em primeiro plano `specialUse` (`BankNotificationListener`). Use o texto abaixo; se o formulário estiver em inglês, traduza.

**Justificativa (núcleo da função):**
O Fluxo é um controle de gastos pessoal. O acesso a notificações é o recurso que registra automaticamente compras e movimentações de bancos e carteiras que o usuário já usa. Sem esse acesso, o app continua funcionando, mas o lançamento passa a ser manual. O app lê somente notificações de apps financeiros conhecidos, extrai valor, data, estabelecimento, categoria e forma de pagamento, e não guarda o texto original da notificação no build da loja. A captura pode ser pausada a qualquer momento e o acesso pode ser retirado nas configurações do Android. Nenhum dado sai do aparelho.

**Justificativa do serviço em primeiro plano (`specialUse`):**
O serviço mantém a captura ativa enquanto o usuário a deixou ligada. Ele mostra uma notificação fixa de status para o usuário saber que a captura está ativa. Não executa tarefas em segundo plano sem essa finalidade.

**Divulgação destacada (prominent disclosure):** o app já mostra aviso antes de abrir as configurações do Android (`NotificationDisclosure.kt`). O vídeo deve mostrar esse aviso.

**Roteiro do vídeo (≈80 s, sem dados pessoais, em aparelho de teste):**

1. 0:00–0:10: abrir o Fluxo na aba Início. Mostrar que não há tela de login.
2. 0:10–0:25: Ajustes › Captura. Mostrar o aviso de privacidade e o botão que abre as configurações do Android. Deixar claro que a captura é opcional.
3. 0:25–0:40: nas configurações do Android, mostrar o Fluxo na lista de acesso a notificações e a concessão.
4. 0:40–0:55: disparar uma notificação sintética de app financeiro (emissor `notification-fixture`, `-PnotificationE2e=true`). Mostrar o lançamento em Registros › Revisar, com o motivo.
5. 0:55–1:10: pausar a captura no app, disparar outra notificação e mostrar que nada é registrado.
6. 1:10–1:20: retirar o acesso nas configurações do Android e mostrar o estado “permissão revogada” no app. Mostrar a notificação fixa de status ser encerrada.

Não mostrar texto de notificação real nem valores reais. Gravar com o seed de demonstração.

## 5. Pendências antes de publicar

- [ ] **Target SDK 36:** já está em `app/build.gradle.kts` (`targetSdk = 36`). Confirmar a exigência vigente da Play na data do envio. [confirmar]
- [ ] **Declaração de serviços financeiros** no Console: o app é ferramenta de controle pessoal; não oferece empréstimo, conta, investimento, cartão nem movimenta dinheiro. Responder conforme a categoria atual do formulário. [confirmar]
- [ ] **Política de privacidade pública com URL:** `https://famel-svg.github.io/fluxo-privacidade/` (já em `PRIVACY_URL`). Confirmar que a página está no ar e idêntica a `docs/PRIVACY.md`. Incluir o link também na ficha.
- [ ] **Chave de release própria:** o AGENTS.md exige migrar do keystore debug antes de distribuir a terceiros. Gerar upload key, ativar Play App Signing, guardar a chave fora do Git, e enviar AAB (`playRelease`), não APK. Exige desinstalar uma vez, então só depois de backup protegido restaurado no aparelho real.
- [ ] **Divergências de texto a corrigir antes de publicar** (o código manda):
  - PRIVACY §7 diz “Apagar definitivamente”; o app mostra “Apagar histórico”. Escolher um nome e alinhar os dois.
  - PRIVACY §2 diz “bancos e benefícios”; a lista do build `play` (`KnownFinancialApps`) também inclui carteiras digitais (PicPay, Mercado Pago, PagBank) e recibos da Carteira do Google. Atualizar a política.
  - PRIVACY §1 diz que não há SDK de analytics ou falhas; o flavor `play` traz a Billing Library. Esclarecer na política.
  - A ficha diz “conhecidos”, porque no build `play` não há escolha de apps, só a lista fixa.
- [ ] **Captura real validada:** a matriz `docs/CAPTURE_VALIDATION_MATRIX.md` precisa estar preenchida para os bancos citados. Não prometer captura garantida.
- [ ] **Declaração de notificações e vídeo** enviados (seção 4).
- [ ] **Declaração do serviço em primeiro plano `specialUse`** com justificativa (seção 4).
- [ ] **Busca de nome:** INPI (classes 9 e 36) e Play Store, conforme M20. [confirmar]
- [ ] **Classificação de conteúdo** (questionário IARC) e público-alvo 18+.
- [ ] **Teste fechado** antes da produção, conforme M20 fase 5.
- [ ] **Verificação de build:** `./tools/check.ps1 all` antes de qualquer release. Só o `play` gera o AAB da loja.
- [ ] **Migração de quem usa o APK do GitHub:** o applicationId da loja (`io.github.famelsvg.fluxo`) é diferente de `br.com.fluxo.app`. Quem instalou pelo GitHub não recebe atualização pela loja e precisa de backup e reinstalação. Informar na página de instalação.
- [ ] **Pro:** `PRO_FOR_ALL=true`. A ficha não promete recursos Pro nem preço. Se o Pro for cobrado, revisar ficha, Data safety e PRIVACY §8 antes.
