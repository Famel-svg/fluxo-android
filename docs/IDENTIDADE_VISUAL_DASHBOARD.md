# Identidade visual Fluxo

Status: aplicada no app Android e validada em AVD. Documento descreve a tradução das referências aprovadas para componentes Jetpack Compose; não afirma reprodução pixel a pixel.

## Como a referência foi traduzida

A imagem de referência mostra uma tela financeira em aparelho sobre fundo escuro. Interface usa navy profundo, painéis teal e azul, acentos mint, números grandes, bordas finas, cantos arredondados e navegação inferior fixa. A moldura, reflexos, câmera, barra de status ilustrativa e fundo externo são apresentação da imagem; não fazem parte das telas do app.

Não recebemos prompt, modelo, seed, fonte ou arquivo de projeto usado para gerar a imagem. Portanto, não afirmamos como a arte original foi produzida. A implementação foi reconstruída por inspeção visual com Material 3, Compose, `Surface`, `Canvas`, gradientes `Brush`, ícones Material e dados reais do app. Não usa mockup como fundo nem adiciona dependências.

## Direção de estilo

- **Fundo:** navy quase preto, uniforme, para dar contraste aos painéis.
- **Mint `#00E5B5`:** seleção, ações principais, dinheiro/benefícios e estados ativos.
- **Azul `#51C8FF`:** crédito, gráficos e informação secundária.
- **Texto principal `#F5F7FC`:** títulos e valores; **texto auxiliar `#BCCBE0`:** datas e explicações.
- **Painéis:** navy elevado, contorno colorido discreto, raio de 20 dp e padding de 16 dp.
- **Itens internos:** superfície um pouco mais clara, raio de 16 dp, espaçamento de 12 dp.
- **Gradientes:** poucos e localizados em cartões de destaque; nunca atrás de blocos longos de texto.
- **Tipografia:** Material 3 existente, títulos e valores em SemiBold/Bold, sem fontes remotas e sem truncar conteúdo.
- **Ícones:** Material com 24 dp; logo B usa símbolo de carteira mint/ciano em fundo navy para manter contraste em tamanho pequeno.

Tokens e tema compartilhados estão em `FluxoDesignSystem.kt`. Fundo usa `#001020`; superfícies usam `#071C2E` e `#0B2237`; divisórias usam `#224259`; erros mantêm a cor semântica do tema.

## Aplicação por tela

| Tela | Composição e comportamento |
| --- | --- |
| Início | Resultado observado, dinheiro e crédito separados, gastos, categorias, ações rápidas, estados de registro e acesso à captura. Valores vêm do ledger local. |
| Registros | Busca, filtros, grupos por data e cartões editáveis. Mantém mês selecionado, opção de todos os meses, estados vazios e fluxo de revisão. |
| Planejar | Benefícios VR e VT em cartões próprios; totais confirmados, sete barras diárias reais no fim do mês selecionado, comparação mensal, participação por categoria e limites editáveis. |
| Ajustes | Permissão Android e captura em cartões claros; fontes de notificação, armazenamento local e exclusão com confirmação. Estado de permissão e preferências vêm do sistema/app. |

Planejamento não inventa dados diários: barras mostram os últimos sete dias do mês selecionado e usam despesas positivas confirmadas, sem transferências próprias. Categorias e comparação cobrem o mês inteiro. VR/VT são estimativas separadas; tarifa diária é global, ajuste de dias vale para o mês indicado. Feriados, ausências e escalas não são inferidos. Estimativas não alteram renda, despesas, caixa ou saldo.

Dashboard mantém “Resultado observado” em vez de alegar saldo bancário. Compras no crédito ficam separadas de saídas de caixa. Revisões e transferências próprias não entram nos totais confirmados. Captura de notificações é local e de cobertura parcial; nenhum layout promete captura integral de qualquer app.

## Layout adaptável e acessibilidade

Conteúdo rola verticalmente; navegação inferior permanece fixa. Pares de cartões viram coluna em telas estreitas ou com fonte ampliada. Alturas não são fixas. Ações têm alvo de toque mínimo de 48 dp; cabeçalhos têm semântica de título; gráficos incluem resumo textual dos valores. Cor não é o único modo de comunicar status.

Build, lint, testes Compose selecionados e fluxo E2E de notificações sintéticas foram executados. Capturas do AVD confirmam aparência geral nas quatro rotas. Ainda não foi feita medição formal de contraste, auditoria TalkBack completa nem validação em aparelhos físicos. As capturas sintéticas não comprovam entrega por bancos reais ou cobertura de todos os aplicativos instalados.

## Manutenção

Manter a paleta e o tema centralizados em `FluxoDesignSystem.kt`. Novas telas devem reutilizar `FluxoPanel`, `FluxoSectionHeading` e `FluxoAdaptivePair`, respeitar dados e estados reais, oferecer alternativa textual para gráficos e seguir a escala de fonte do Android. Não adicionar gradientes decorativos indiscriminadamente, métricas financeiras inventadas ou promessas de saldo/fatura que o app não calcula.
