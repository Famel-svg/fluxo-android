# Fluxo 0.8.0 — Carteira do Google, consentimento e Fluxo Pro

Lançamentos que faltavam, aviso claro sobre notificações e o plano Pro.

- Compra paga por aproximação com a Carteira do Google ("R$ X com Cartão ••1234") vira lançamento em revisão e não duplica junto com a notificação do banco: mesmo valor em até 15 minutos conta uma vez só. Estorno, recusa e Pix ficam fora desse pareamento.
- Aviso claro antes de abrir o acesso às notificações: em Ajustes, o Fluxo explica o que lê, o que guarda e que nada sai do aparelho, e só então abre a tela do Android. Quem já usava a captura continua como estava — a captura não é pausada nem precisa ser religada; o aviso aparece só na hora de abrir a tela de acesso novamente.
- Link da política de privacidade nos Ajustes.
- Fluxo Pro chega com paywall em Ajustes → Ver o Fluxo Pro. Nesta versão do GitHub o Pro vem liberado para todos ("Pro grátis por enquanto"), sem limite de contas. A versão do Google Play passará a ter limite de 2 contas ativas no plano grátis — perder o Pro só bloqueia criar itens novos: nada é apagado, oculto ou impedido de exportar.
- Lista de apps financeiros conhecidos: no Google Play a captura passa a aceitar só bancos e apps de benefícios reconhecidos (inclui a Carteira do Google). No GitHub continua lendo notificações de todos os apps e guardando apenas eventos financeiros reconhecidos.
- A triagem provisória de notificações continua disponível só na versão do GitHub, desligada por padrão.

Atualize sem desinstalar: mesma assinatura, atualização instala por cima. O banco continua na versão 6, sem migração nova — lançamentos, contas, revisões e histórico ficam exatamente como estavam.

Verificação: `./tools/check.ps1 all` rodou localmente (testes JVM do core, build debug, lint e suíte instrumentada em emulador descartável) e o workflow Android do GitHub Actions ficou verde no commit desta release. Não foi testado em aparelho físico real, nem com notificação real de banco ou da Carteira do Google em dispositivo físico. A compra no Play (Billing) não faz parte desta release: o APK publicado é o da variante github.
