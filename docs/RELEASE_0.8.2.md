# Fluxo 0.8.2 — aviso fixo de captura ativa

A captura passa a rodar como serviço em primeiro plano, com um aviso fixo na barra de notificações.

- Enquanto a captura estiver ligada, aparece o aviso "Fluxo está lendo suas notificações" com a hora da última captura. Tocar nele abre o Fluxo. Ao pausar a captura, o aviso some.
- Rodando em primeiro plano, o serviço de captura fica bem menos sujeito a ser fechado pela economia de bateria (Xiaomi/MIUI em especial). Antes, uma compra notificada enquanto o serviço estava fechado só era lida se a notificação ainda estivesse na barra ao abrir o app.
- No Android 13 ou mais novo o aviso precisa da permissão de notificações: em Ajustes → Diagnóstico → "Manter a captura ativa", toque em "Permitir aviso de captura ativa".
- O aviso não mostra valores nem texto de notificações de outros apps.

Atualize sem desinstalar. Não há mudança de banco nesta versão.

Verificação: o workflow Android do GitHub Actions (build, lint e suíte instrumentada em emulador) ficou verde antes da publicação automática. `./tools/check.ps1 all` não rodou localmente. O comportamento em primeiro plano não foi testado em aparelho Xiaomi real.
