# M20 — Google Play e Fluxo Pro

Objetivo: publicar no Google Play e oferecer um "Fluxo Pro" de compra única, mantendo a promessa central:
offline, sem conta, sem servidor, dados só no aparelho.

## Decisões do dono (2026-10-19)

| # | Tema | Decisão |
|---|------|---------|
| 1 | Nome na loja | **Fluxo — Gastos Offline**. O nome no aparelho continua "Fluxo". Pendente do dono: buscar no INPI (classes 9 e 36) e na Play Store antes de publicar. |
| 2 | applicationId | **`io.github.famelsvg.fluxo`** no flavor `play`. O flavor `github` mantém `br.com.fluxo.app` para a atualização continuar por cima. |
| 3 | Preço do Pro | **Por enquanto grátis**: o dono ainda não tem conta de desenvolvedor Google. A meta é ~R$ 20,00, compra única, sem trial. Enquanto isso, a infraestrutura de cobrança fica pronta, mas `Entitlements` dá Pro a todos. |
| 4 | Captura no build da loja | **Só a lista de pacotes financeiros conhecidos** (bancos e VR/VT), centralizada em `core/`. No flavor `play` não existe a opção "outros apps". O `github` mantém a captura de todos os apps. |
| 5 | Política de privacidade | **GitHub Pages** em um repositório público dedicado, publicando só `PRIVACY.md`. |

## Fases
1. Distribuição: flavors `github`/`play`, buildType `release` com R8, workflow `play.yml` gerando o AAB assinado com upload key.
2. Conformidade: sem triagem no `play`, divulgação proeminente com consentimento, lista financeira, política de privacidade.
3. Infraestrutura Pro: `ProFeature`/`Entitlements`/`FeaturePolicy` em `core/`, `BillingRepository` no `play`.
4. Recursos Pro, uma spec por recurso.
5. Ficha da loja, Data Safety, teste fechado e migração de quem usa o APK do GitHub.

## Regras
Free continua útil: captura, revisão, lançamentos manuais, categorias, orçamento, VR/VT, resumo, backup, restauração,
CSV, bloqueio e acessibilidade ficam grátis. Perder o Pro nunca oculta, apaga nem impede exportar dados.
Sem INTERNET, analytics ou crash reporting no `play`, salvo o que a Billing Library exigir (documentar).

## Fase 1 — distribuição (feito)
- Dimensão `distribuicao`: `github` (`br.com.fluxo.app`, comportamento atual) e `play` (`io.github.famelsvg.fluxo`).
- Variantes: `githubDebug` (APK da release do GitHub, mesmo certificado), `githubRelease` (smoke do R8), `playRelease` (AAB da loja).
- `release`: R8 + shrink de recursos; `app/proguard-rules.pro` mantém o nome de `BankNotificationListener`.
- `tools/check.ps1 quick` também gera `assemblePlayRelease`; `release-smoke` instala o `githubRelease` assinado com a chave debug
  local e confere que abre sem crash.
- `.github/workflows/play.yml` (manual): gera `bundlePlayRelease` assinado e publica AAB + `mapping.txt` como artefato. Não envia ao Play.

### Pendência do dono: upload key
Gerar fora do repositório e guardar cópia segura (perder a upload key exige pedido de reset ao Google):

    keytool -genkeypair -v -keystore fluxo-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000

Segredos no GitHub: `PLAY_UPLOAD_KEYSTORE_BASE64` (base64 do .jks), `PLAY_UPLOAD_STORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`,
`PLAY_UPLOAD_KEY_PASSWORD`. Usar Play App Signing no Console. Nunca versionar o .jks.
