# Fluxo

Controle financeiro pessoal para Android, em português, offline e sem conta. O Fluxo transforma notificações de bancos e benefícios em lançamentos, e soma a isso lançamentos manuais, orçamentos, contas próprias e estimativas de VR/VT. Todos os dados ficam no aparelho.

> O histórico depende das notificações recebidas: é parcial e **não** substitui saldo nem extrato bancário.

## Instalar

1. Baixe `fluxo-X.Y.Z-debug.apk` na [última release](https://github.com/Famel-svg/fluxo-android/releases/latest) e confira o SHA-256 publicado junto.
2. Instale e abra o Fluxo. O assistente inicial (nome, salário, VR e VT) pode ser pulado e revisto depois em Ajustes › Rever configuração inicial.
3. Em **Ajustes**, leia o aviso de privacidade, abra as configurações do Android pelo botão e conceda acesso às notificações.
4. Volte e ligue a captura. As notificações dos bancos precisam estar ativas e mostrar valores.
5. Confira **Registros**: itens em **Revisar** esperam sua confirmação; o que não foi notificado pode ser lançado à mão.

Se o Android bloquear o acesso para apps de fora da loja, use "configurações restritas" nas informações do app, só depois de conferir a origem do APK.

**Atualizar:** instale o APK novo por cima do anterior, sem desinstalar. A assinatura é a mesma entre versões e as migrações do banco preservam lançamentos, correções e identificadores de duplicata. Faça um backup protegido antes, por garantia. Novidades de cada versão: [`docs/RELEASE_*.md`](docs/).

## O que o app faz

**Captura**
- Lê notificações de qualquer app (exceto o próprio) enquanto a captura estiver ligada; a pausa interrompe tudo.
- Interpretação conservadora: recusas, agendamentos, pendências, faturas, ofertas, perguntas e mensagens com mais de um valor vão para Revisar, nunca para os totais confirmados.
- Compras de cartão e VR ("Compra aprovada", "Você gastou…", "vale-refeição"…) viram despesa no meio certo.
- Atualizações da mesma notificação reabrem a revisão sem sobrescrever correções nem recriar itens excluídos; reconectar o serviço reprocessa as notificações visíveis sem duplicar.
- Diagnóstico em Ajustes: permissão, conexão do serviço e contadores por app (bancos e outros apps) com o motivo de cada ignorado.
- **Triagem provisória** (Ajustes › Dados, desligada por padrão): guarda o texto das notificações para você decidir o que vira lançamento e exportar amostras em JSON, usadas para ajustar a leitura por app. Veja [M19](docs/specs/M19-triagem-provisoria.md).

**Organização e planejamento**
- Lançamentos manuais, categorias, busca sem acentos e filtros por banco, meio e categoria.
- Limite mensal por categoria, com gasto, restante/excesso e comparação com o mês anterior.
- Contas locais com saldo inicial datado e transferências próprias, que não inflam renda nem consumo.
- Renda prevista do mês (salário + VR + VT) e quanto do salário já foi gasto, como estimativa.
- VR e VT por valor diário e dias da semana, com ajuste manual de dias; gastos no benefício abatem o saldo estimado no Início e ficam fora das saídas de caixa.

**Dados**
- Backup restaurável criptografado com senha (12+ caracteres): lançamentos, orçamentos, contas, benefícios, salário e identificadores contra duplicatas.
- Exportação CSV pelo seletor de arquivos do Android.
- Tema Sistema, Claro ou Escuro, com animações curtas que respeitam "remover animações" do Android.

### Regras financeiras

- Pix é **meio** de pagamento; entrada/saída é **direção**.
- Compras no crédito são compromissos, separados das saídas de caixa.
- Itens em revisão não entram nos totais confirmados.
- Faturas, estornos e parcelas não são conciliados automaticamente: revise à mão para evitar dupla contagem.

## Privacidade e limites

- Sem servidor, sincronização, conta, analytics ou credenciais bancárias. O app não declara permissão de internet.
- Texto de notificação não é guardado nem vai para log; só os dados extraídos do lançamento. **Exceção:** com a triagem ligada, o texto completo (inclusive mensagens pessoais de outros apps) fica no armazenamento privado até você apagar a triagem. Ele nunca entra em log, CSV nem backup; o JSON exportado não é criptografado.
- SQLite no armazenamento privado do Android, sem criptografia adicional. Backup automático do Android e transferência entre aparelhos estão desligados.
- Guarde a senha e o arquivo de backup em lugares separados. Permissões não são restauradas e a restauração pausa a captura.
- CSV é texto legível, sem criptografia, e não restaura o banco.
- Excluir um lançamento conserva um identificador técnico para impedir recriação; "Apagar histórico" remove tudo, inclusive esses identificadores e a triagem, e pausa a captura. Desinstalar apaga os dados.
- Os testes usam textos sintéticos. A captura com bancos reais ainda precisa de validação em aparelho ([matriz](docs/CAPTURE_VALIDATION_MATRIX.md)).
- O APK usa assinatura de desenvolvimento, adequada para uso pessoal. Distribuição a terceiros exige chave de release própria.

## Desenvolvimento

Requisitos: JDK 21, Android SDK Platform 36 e Build Tools 36.0.0 (Android Studio ou `ANDROID_HOME`; SDK local em `local.properties`, ignorado pelo Git).

Use [`tools/check.ps1`](tools/check.ps1), que acha o JDK, gerencia o emulador descartável e imprime um resumo:

| Comando | O que roda | Quando |
| --- | --- | --- |
| `./tools/check.ps1 quick` | testes JVM, build debug e lint | toda alteração |
| `./tools/check.ps1 ui [-Class Nome]` | quick + testes instrumentados e de interface | mudança de tela ou banco |
| `./tools/check.ps1 e2e` | entrega real de notificações com emissor sintético | mudança na captura |
| `./tools/check.ps1 shots -Themes light,dark` | capturas das quatro abas com dados de demonstração | revisão visual |
| `./tools/check.ps1 all` | ui + e2e | antes de release |

Sem PowerShell, o equivalente do CI é `bash ./gradlew :core:test :app:assembleDebug :app:lintDebug`. O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

**Release:** suba `versionCode`/`versionName` em `app/build.gradle.kts`, escreva `docs/RELEASE_X.Y.Z.md`, rode `check.ps1 all`, envie o `main`, espere o workflow Android ficar verde e envie a tag `vX.Y.Z`. O workflow [Release](.github/workflows/release.yml) testa, confere o certificado e publica o APK com SHA-256. Detalhes em [AGENTS.md](AGENTS.md).

### Estrutura

- `core/`: modelos e regras em Kotlin/JVM puro (parser, triagem, cifra do backup, CSV, relatórios), com testes JVM.
- `app/`: serviço de notificações, SQLite e interface Compose, com testes instrumentados.
- `notification-fixture/`: emissor sintético de notificações, incluído só com `-PnotificationE2e=true` ([guia](docs/NOTIFICATION_E2E.md)).
- `docs/`: [especificação](docs/SPEC.md), [arquitetura](docs/ARCHITECTURE.md), [plano de testes](docs/TEST_PLAN.md), [validação](docs/VALIDATION.md), [roadmap](docs/ROADMAP.md) e [specs dos incrementos](docs/specs/README.md), com status proposto ou implementado.

Edições são coordenadas com Ruflo 3.38.21: instruções em [AGENTS.md](AGENTS.md), instalação em [docs/RUFLO.md](docs/RUFLO.md).
