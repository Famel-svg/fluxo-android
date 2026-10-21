# Fluxo 0.8.1 — captura que volta sozinha, categoria automática e Completar

Correções para notificações perdidas, botão de confirmar e classificação manual.

- A captura volta sozinha: depois de reiniciar o celular, depois de atualizar o app e a cada 15 minutos, o Fluxo pede ao Android para religar o serviço de notificações se ele tiver sido fechado. O app não precisa ficar aberto e não há notificação fixa.
- Em Ajustes → Diagnóstico, novo bloco "Manter a captura ativa" com atalhos para a otimização de bateria e, em Xiaomi/Redmi/POCO, para o "Início automático". Deixe o Fluxo sem restrição de bateria e com início automático ligado; sem isso o sistema ainda pode fechar o serviço entre as checagens.
- O nome do estabelecimento vira a descrição do lançamento ("MERCADO EXEMPLO" em vez de "Movimentação detectada"), tanto na notificação do banco quanto na da Carteira do Google.
- Categoria automática pelo nome do estabelecimento: mercado e restaurante → Alimentação, posto e Uber → Transporte, drogaria → Saúde, streaming → Lazer, lojas online → Compras, entre outras. Ao trocar a categoria de um lançamento capturado, as próximas compras no mesmo estabelecimento já chegam com ela.
- Revisar: quando falta valor ou direção, o botão vira "Completar" e abre o editor, que diz o que falta para salvar. O valor aceita "1.234,56" e "R$".

Atualize sem desinstalar. O banco passa para a versão 7 só com uma tabela nova (categorias aprendidas); nada é apagado. As categorias aprendidas não entram no backup e são apagadas com "Apagar histórico".

Verificação: testes JVM do core passaram e o workflow Android do GitHub Actions (build, lint e suíte instrumentada em emulador) ficou verde antes da publicação automática. `./tools/check.ps1 all` não rodou localmente para esta versão. A volta automática da captura não foi testada em aparelho Xiaomi real.
