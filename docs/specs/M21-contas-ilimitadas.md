# M21 — Contas ilimitadas (Fluxo Pro)

Fase 4, item 1 do [M20](M20-play-pro.md).

## Regra
`FeaturePolicy.canCreateAccount(existentes, isPro)`: grátis até `MAX_CONTAS_FREE = 2`; Pro sem limite.

## O que conta
Contas **ativas** (não arquivadas). Não existe conta embutida/padrão: todas são criadas pelo usuário (o banco nasce sem contas),
então nada é excluído da contagem além das arquivadas. Arquivar libera vaga. Desarquivar conta como criar: no grátis, só abaixo do limite. Editar nunca é bloqueado.

## Comportamento
- "Adicionar conta" fica sempre visível. No limite sem Pro, abre explicação curta com botão "Ver o Fluxo Pro" (paywall existente).
- Defesa: `LedgerViewModel.saveAccount(conta, isPro, ...)` recusa conta **nova** acima do limite (mensagem em `editError`).
  `isPro` vem da UI (`entitlements`); `LedgerRepository` não conhece cobrança.
- Perder o Pro: quem tem mais de 2 contas mantém todas, edita, arquiva, exporta e usa em transferências; só não cria novas.

## Testes
- Core: `FeaturePolicyTest` (limite, acima do limite após perder Pro, Pro sem limite).
- Instrumentado: `AccountsUiTest.addAccountAtFreeLimitExplainsProAndProCreatesThird` (`DebugEntitlements.forced`, restaurado ao fim).
