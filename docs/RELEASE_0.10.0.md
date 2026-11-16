# Fluxo 0.10.0 — ritmo do mês, recorrentes e alertas inteligentes

Vinte melhorias de visualização pedidas por usuários de apps de finanças, e avisos que só aparecem quando importam.

- Início: "Ritmo do mês" com gasto, esperado até hoje e projeção do fim do mês (contas fixas já pagas não são repetidas na projeção). Categorias a partir de 80% do orçamento aparecem como chips; o toque abre Registros filtrado.
- Início: cartão de crédito com a fatura aberta do ciclo, dia de fechamento e de vencimento. Gráficos com valores, rosca clicável e bloco recolhível. Os blocos podem ser reordenados e ocultados em "Personalizar início".
- Planejar: projeção de 30 dias, fluxo de caixa de 6 meses, comparação por categoria com o mês anterior, mini tendência em cada orçamento, contas recorrentes com calendário e mapa de calor de gastos por dia.
- Registros: confirmar em lote como Crédito ou VR quando há duas ou mais pendências em que só falta o meio (Pix agendado, fatura e recibos de carteira ficam fora). Aplicar a categoria a todos os lançamentos do mesmo estabelecimento. Etiquetas com filtro e selo de origem em cada lançamento.
- Widget de tela inicial com o saldo do mês e as pendências de revisão.
- Notificações inteligentes: um resumo diário de revisão (nunca um aviso por lançamento), orçamento em 80% e estourado, conta recorrente vencendo amanhã, gasto fora do padrão, captura parada e resumo semanal. Horário silencioso (22h às 8h por padrão), no máximo 3 avisos por dia, cada tipo pode ser desligado em Ajustes e a tela de bloqueio não mostra valores.

Atualize sem desinstalar. O banco passa para a versão 9 (nova tabela de etiquetas) sem mexer no histórico. Backups novos ficam no formato 6 e incluem as etiquetas; backups antigos continuam restaurando.

Verificação: `./tools/check.ps1 all` com 154 testes JVM, 150 instrumentados sem falhas e e2e de entrega de notificações (8 testes) ok. Widget e notificações inteligentes não foram vistos em aparelho real.
