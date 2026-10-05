# Fluxo

Aplicativo Android de controle financeiro pessoal, em português e com dados locais. Registra notificações financeiras selecionadas pelo usuário, lançamentos manuais, orçamentos e transferências entre contas próprias.

## Recursos

- Pendências e mensagens com oferta anexada recebem revisão conservadora.
- Atualizações de notificações reabrem revisão sem sobrescrever correções manuais ou exclusões; migração SQLite v3 preserva legado.
- Ajustes mostra diagnóstico do serviço e última observação por banco nesta sessão.
- Backup protegido por senha permite restaurar lançamentos, orçamentos e identificadores contra duplicatas. Permissões e seleção de bancos continuam locais; restauração pausa captura.
- Captura consentida de notificações de C6 Bank, Nubank e Itaú, com revisão de mensagens ambíguas.
- Categorias, orçamentos mensais, busca, filtros e exportação CSV.
- Contas locais e transferências próprias; suporte não equivale a conciliação bancária.
- Backup restaurável protegido por senha.

## Limites e privacidade

Fluxo não conecta a servidor, não sincroniza dados e não precisa de conta, internet ou credenciais bancárias. O histórico depende das notificações recebidas e não representa saldo ou extrato completo. Modelos de notificação precisam de validação em aparelhos e versões Android reais. Captura real de bancos não é comprovada por testes com dados sintéticos.

Notificações brutas não são armazenadas. O SQLite fica no armazenamento privado do Android, sem criptografia adicional. Backup protegido exige senha: guarde senha e arquivo separadamente. CSV é texto legível e não pode restaurar o banco. Desinstalar apaga dados locais.

Veja [evidências e limites de validação](docs/VALIDATION.md), [plano de testes](docs/TEST_PLAN.md), [especificação](docs/SPEC.md), [arquitetura](docs/ARCHITECTURE.md) e [incrementos planejados](docs/specs/README.md).

## Novidades da versão 0.2
- Categorias editáveis em cada lançamento; registros anteriores começam em Sem categoria.
- Planejamento mensal com limite por categoria, gasto, restante/excesso e comparação com mês anterior.
- Relatório por categoria inclui despesas confirmadas no crédito e no débito; itens em revisão ficam fora.
- Busca sem distinção de acentos e filtros combinados por banco, meio e categoria, com opção Todos os meses.
- Exportação CSV de todo o histórico não excluído pelo seletor de arquivos Android. O arquivo não é criptografado e não é backup restaurável; escolha o destino conscientemente.

Pesquisa, critérios de aceite e divisão entre agentes: [plano da versão 0.2](docs/PLAN_0.2.md).

Auditoria posterior: [comparação de 10 apps e plano de 18 melhorias](docs/research/2026-09-05-mercado-e-auditoria.md), com fontes consultadas em 05/09/2026, achados de código e critérios de aceite. As melhorias desse relatório são propostas; não fazem parte da versão 0.2.

Implementação dos próximos incrementos: [lista priorizada e spec de cada item](docs/specs/README.md). O índice distingue propostas, execução e validação.
Atualizar o APK sobre a mesma assinatura migra SQLite v1→v2 preservando lançamentos e identificadores de duplicata. Não desinstale o app para atualizar se quiser manter seus dados.

## Coordenação das próximas edições
Ruflo 3.38.21 inicializado para coordenar tarefas e agentes Codex. Instruções em [AGENTS.md](AGENTS.md), reprodução e limites em [docs/RUFLO.md](docs/RUFLO.md). Configurações, skills e runtime em `.agents/` e `.claude-flow/` permanecem locais e ignorados pelo Git.

## Escopo entregue
- Especificação, arquitetura, critérios de aceite e plano de testes em `docs/`.
- Backend embarcado Kotlin/SQLite: valores inteiros em centavos, captura seletiva, deduplicação, correções e exclusões preservadas.
- Frontend Jetpack Compose/Material 3: resumo mensal, lançamentos, revisão, inclusão/edição/exclusão e configurações.
- CI no GitHub com testes, lint, compilação e artefato APK de desenvolvimento.

Não existe servidor remoto nem sincronização nesta versão. O aplicativo não precisa de conta, internet, senha bancária ou chave de API. Notificações brutas não são armazenadas. O histórico é parcial e não representa saldo bancário. Templates dos bancos exigem validação no aparelho; uma marca pode usar outros pacotes ou textos.

## Compilar
Requisitos: JDK 21, Android SDK Platform 36 e Build Tools 36.0.0. Abra esta pasta no Android Studio e configure o SDK, ou defina `ANDROID_HOME`. Para SDK local, use `local.properties` (ignorado pelo Git) com `sdk.dir=C:/caminho/Android/Sdk`.

Windows:
```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```
Linux/macOS:
```sh
bash ./gradlew :core:test :app:assembleDebug :app:lintDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. Build debug assinado automaticamente para desenvolvimento; distribuição pública exige assinatura de release própria. Nunca versionar keystore ou senha.

Com emulador/aparelho conectado:
```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
bash ./gradlew :app:connectedDebugAndroidTest
```

## Configurar no celular
1. Instale o APK e abra Fluxo.
2. Em Ajustes, leia a explicação de privacidade e escolha C6 Bank, Nubank e/ou Itaú.
3. Abra as configurações Android pelo botão e conceda acesso às notificações ao Fluxo.
4. Volte e habilite a captura. As notificações dos próprios bancos também precisam estar ativas e conter os valores.
5. Confira futuros lançamentos. Corrija itens em Revisar; adicione manualmente o que não foi notificado.

Algumas versões Android restringem acesso especial para aplicativos instalados fora da loja. Se o sistema bloquear, consulte a opção de configurações restritas nas informações do app e autorize somente após verificar a origem do APK.

Pix é meio de pagamento; entrada/saída é direção. Compras no cartão de crédito são compromissos separados das saídas de caixa. Faturas, estornos e notificações com múltiplos valores não são confirmados automaticamente. Sem conciliação automática de faturas: revisar manualmente para evitar dupla contagem.

## Organização
- `core/`: modelos e parser determinístico Kotlin/JVM; testes com dados sintéticos.
- `app/`: serviço Android, persistência privada e interface Compose.
- `docs/SPEC.md`: produto e regras financeiras.
- `docs/ARCHITECTURE.md`: contratos e armazenamento.
- `docs/TEST_PLAN.md`: validação automática e em aparelho.
- `docs/ROADMAP.md`: próximos incrementos e limites do MVP.
- `docs/VALIDATION.md`: evidências efetivamente obtidas nesta entrega.
- `docs/PLAN_0.2.md`: pesquisa, especificação e aceite das cinco novas funções.

## Privacidade
Sem permissão de internet, analytics ou envio de notificações. Acesso Android permite receber notificações, mas o aplicativo descarta fontes não selecionadas antes de interpretar o texto. Backup automático e transferência de dados desabilitados. Proteção usa sandbox Android e segurança do dispositivo; não há criptografia adicional do SQLite. Desinstalar apaga dados locais. Excluir um lançamento conserva identificador técnico para evitar recriação; apagar todo o histórico remove também esses identificadores e pausa captura.
