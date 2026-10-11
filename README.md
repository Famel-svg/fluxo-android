# Fluxo

Aplicativo Android de controle financeiro pessoal, em português e com dados somente locais. Registra notificações financeiras escolhidas pelo usuário, lançamentos manuais, orçamentos, contas próprias e transferências entre elas.

## Recursos

- Captura consentida de notificações de C6 Bank, Nubank e Itaú; outros apps apenas com permissão explícita em Ajustes.
- Interpretação conservadora: pendências, recusas, cobranças, ofertas e mensagens com mais de um valor vão para Revisar, nunca para os totais confirmados.
- Atualizações da mesma notificação reabrem a revisão sem sobrescrever correções manuais nem recriar itens excluídos.
- Ao reconectar o serviço, notificações ainda visíveis são reprocessadas sem duplicar lançamentos.
- Lançamentos manuais, categorias, busca sem acentos e filtros combinados por banco, meio e categoria.
- Planejamento mensal: limite por categoria, gasto, restante/excesso e comparação com o mês anterior.
- Contas locais com saldo inicial datado e transferências próprias, que não inflam renda nem consumo.
- Configuração inicial no primeiro uso (também em Ajustes › Rever configuração inicial): nome opcional e, para VR e VT, valor por dia e dias da semana em que cai (por exemplo VR de segunda a sexta e VT de segunda a quinta); o app calcula a estimativa do mês e o nome fica só no aparelho.
- Estimativa de benefícios (VR/VT) pelos dias da semana de cada benefício, com ajuste manual de dias por mês.
- Backup restaurável protegido por senha (lançamentos, orçamentos, contas, benefícios e identificadores contra duplicatas).
- Exportação CSV pelo seletor de arquivos do Android.
- Diagnóstico da captura em Ajustes: permissão, conexão do serviço e última notificação observada na sessão.

## Limites e privacidade

- Sem servidor, sincronização, conta, internet, analytics ou credenciais bancárias. O app não declara permissão de internet.
- O histórico depende das notificações recebidas: é parcial e não representa saldo nem extrato bancário.
- Os testes usam textos sintéticos. Captura real por banco ainda não foi comprovada; modelos de notificação precisam de validação em aparelho ([matriz](docs/CAPTURE_VALIDATION_MATRIX.md)).
- Notificações brutas não são armazenadas nem registradas em log. Fontes não selecionadas são descartadas antes de qualquer interpretação.
- SQLite no armazenamento privado do Android, sem criptografia adicional. Backup automático do Android e transferência entre aparelhos estão desabilitados.
- Backup protegido exige senha de 12 caracteres ou mais; guarde senha e arquivo separados. Permissões e seleção de bancos não são restauradas, e a restauração pausa a captura.
- CSV é texto legível, sem criptografia, e não restaura o banco.
- Excluir um lançamento conserva um identificador técnico para impedir recriação. Apagar todo o histórico remove também esses identificadores e pausa a captura. Desinstalar apaga os dados locais.

Regras financeiras: Pix é meio de pagamento; entrada/saída é direção. Compras no crédito são compromissos separados das saídas de caixa. Faturas, estornos e parcelas não são conciliados automaticamente: revise manualmente para evitar dupla contagem.

## Compilar

Requisitos: JDK 21, Android SDK Platform 36 e Build Tools 36.0.0. Abra a pasta no Android Studio ou defina `ANDROID_HOME`; para SDK local use `local.properties` (ignorado pelo Git) com `sdk.dir=C:/caminho/Android/Sdk`. `JAVA_HOME` precisa apontar para um JDK 21 existente.

Windows:
```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```
Linux/macOS:
```sh
bash ./gradlew :core:test :app:assembleDebug :app:lintDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`, com assinatura de desenvolvimento. Distribuição pública exige assinatura de release própria; nunca versionar keystore ou senha.

Com emulador ou aparelho conectado:
```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
bash ./gradlew :app:connectedDebugAndroidTest
```
Teste ponta a ponta de entrega de notificações com emissor sintético: [docs/NOTIFICATION_E2E.md](docs/NOTIFICATION_E2E.md).

## Atualizar

Instale o novo APK sobre o anterior, com a mesma assinatura. As migrações SQLite são aditivas e preservam lançamentos, correções e identificadores de duplicata. Não desinstale o app se quiser manter os dados; faça um backup protegido antes de atualizar.

## Configurar no celular

1. Instale o APK e abra o Fluxo.
2. Em Ajustes, leia a explicação de privacidade e escolha os bancos.
3. Abra as configurações do Android pelo botão e conceda acesso às notificações ao Fluxo.
4. Volte e habilite a captura. As notificações dos bancos também precisam estar ativas e exibir valores.
5. Confira os lançamentos. Corrija itens em Revisar e adicione manualmente o que não foi notificado.

Algumas versões do Android restringem esse acesso para apps instalados fora da loja. Se o sistema bloquear, use a opção de configurações restritas nas informações do app, somente após verificar a origem do APK.

## Organização

- `core/`: modelos e regras em Kotlin/JVM puro (parser, cifra do backup, CSV, relatórios), com testes JVM.
- `app/`: serviço de notificações, persistência SQLite e interface Compose, com testes instrumentados.
- `notification-fixture/`: emissor sintético de notificações, incluído só com `-PnotificationE2e=true`.
- `docs/`: [especificação](docs/SPEC.md), [arquitetura](docs/ARCHITECTURE.md), [plano de testes](docs/TEST_PLAN.md), [evidências de validação](docs/VALIDATION.md), [roadmap](docs/ROADMAP.md), [notas da versão 0.4.1](docs/RELEASE_0.4.1.md) e [specs dos incrementos](docs/specs/README.md), cujo índice distingue proposto de implementado.

## Coordenação das edições

Ruflo 3.38.21 coordena tarefas e agentes. Instruções em [AGENTS.md](AGENTS.md); reprodução e limites em [docs/RUFLO.md](docs/RUFLO.md). `.agents/` e `.claude-flow/` são locais e ignorados pelo Git.
