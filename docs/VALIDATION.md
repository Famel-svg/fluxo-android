# Validação da entrega 0.1.0

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
