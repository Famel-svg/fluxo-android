# M17 — CI, migrações e distribuição

Estado: **Parcial: CI instrumentado configurado**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Compilar instrumentação não executa testes; assinatura debug não resolve distribuição futura.

## Comportamento e escopo

CI executa JVM, lint e emulador; publica relatórios. Validar migração/restauração; planejar chave release contra APK existente.

## Contratos e dados

Workflow GitHub com permissões mínimas, timeout e artifacts; chaves/senhas fora Git; logs sem dados financeiros.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

CI verde com instrumentação real; atualização compatível preserva base; chave incompatível exige migração por backup antes de desinstalar.

## Dependências

M05 para troca de assinatura.

## Limites e privacidade

Não publicar Play nem substituir assinatura sem plano de recuperação. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

## Publicação no Google Play (M20)

- Flavors na dimensão `distribuicao`: `github` (`br.com.fluxo.app`, APK atual) e `play` (`io.github.famelsvg.fluxo`).
- O AAB da loja sai de `.github/workflows/play.yml` (manual) com `bundlePlayRelease`, assinado pela upload key; o workflow não envia nada ao Play.
- Assinatura final pela Play App Signing: a chave de upload só autentica o envio.
- Política de privacidade publicada em GitHub Pages: https://famel-svg.github.io/fluxo-privacidade/ (repositório público a ser criado pelo dono, com apenas `PRIVACY.md`; fonte em `docs/PRIVACY.md`).
- Migração de quem usa o APK do GitHub: a assinatura e o applicationId da loja são diferentes; por isso o app da Play não atualiza o APK por cima.
  1. No APK atual, gerar backup criptografado e guardar a senha fora do aparelho.
  2. Instalar o app da Play.
  3. Restaurar o backup no app da Play.
  4. Conceder de novo o acesso a notificações, pois é um app novo para o Android.
- Os dois apps podem coexistir no mesmo aparelho, pois os IDs são diferentes.
