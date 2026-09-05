# Fluxo

Controle financeiro Android pessoal, offline, com leitura consentida de notificações de C6 Bank, Nubank e Itaú. Interface em português. Pix recebido/enviado, compras no crédito/débito, lançamentos manuais e revisão de mensagens ambíguas.

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

## Privacidade
Sem permissão de internet, analytics ou envio de notificações. Acesso Android permite receber notificações, mas o aplicativo descarta fontes não selecionadas antes de interpretar o texto. Backup automático e transferência de dados desabilitados. Proteção usa sandbox Android e segurança do dispositivo; não há criptografia adicional do SQLite. Desinstalar apaga dados locais. Excluir um lançamento conserva identificador técnico para evitar recriação; apagar todo o histórico remove também esses identificadores e pausa captura.
