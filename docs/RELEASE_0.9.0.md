# Fluxo 0.9.0 — visual Índigo Pix e revisão por deslize

O app ganhou identidade nova e a revisão do que foi capturado ficou mais rápida.

- Nova paleta Índigo Pix (índigo com mel para o que falta revisar), no app e no ícone, em claro e escuro.
- Início: saldo do mês num destaque em gradiente, com valor que anima ao mudar e barras de entradas e saídas com os valores. Cabeçalho numa linha com botão "+" e faixa de captura logo abaixo do destaque.
- Registros: deslize o cartão para a direita para confirmar e para a esquerda para editar, com vibração ao passar do ponto. O valor abre o cartão e aparece "Tudo revisado" quando a fila acaba.
- Quando só falta o meio de pagamento, a revisão pergunta apenas VR ou Crédito. Estabelecimentos já conhecidos entram com a categoria preenchida.
- O vale-transporte (VT) saiu da interface. Lançamentos VT antigos continuam guardados, visíveis e fora do caixa.
- Ajustes reorganizado em seções, com o estado da captura em destaque no topo.
- Onboarding com demonstração da captura e passo a passo para liberar o acesso às notificações.
- Fluxo Pro: preço e botão de compra sempre visíveis no rodapé.
- Navegação com indicador que desliza entre as abas; gráficos e blocos entram com animação curta, desligada quando o sistema remove animações.
- Triagem: o texto de uma notificação é apagado assim que ela é guardada ou descartada.

Atualize sem desinstalar. O banco passa para a versão 8, que só limpa o texto de itens de triagem já decididos; lançamentos e histórico ficam intactos.

Verificação: `./tools/check.ps1 all` com 125 testes JVM e 140 instrumentados; uma falha de ambiente em `OnboardingUiTest` (activity não abriu a tempo) passou ao repetir a classe. e2e de entrega de notificações: 8 testes ok.
