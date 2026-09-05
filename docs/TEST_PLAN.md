# Plano de testes

Automação: parser com Pix, crédito em conta, cartões, acentos, reais/centavos, múltiplos valores, saldo, mensagens promocionais, falha/agendamento, overflow; teste de persistência e deduplicação em instrumentação quando disponível. Build debug e lint Android.

No aparelho: instalar APK; abrir Ajustes; escolher bancos; ativar captura e acesso especial. Testar Pix recebido/enviado, compras no crédito e débito em C6/Nubank/Itaú. Confirmar que o valor capturado é o da transação. Testar notificação expandida e resumo de grupo; reentrega; nova compra de mesmo valor; app fechado; revogação e restauração de acesso; pausa; reinício do app e telefone; edição e exclusão; mensagem ambígua em revisão; mês anterior; fonte ampliada e tema escuro. Não fazer transações financeiras só para validar sem decisão do proprietário.

Limite: emulador e fixtures sintéticas não certificam templates reais dos bancos. Não enviar notificações pessoais ao repositório. Resultados efetivamente executados ficam em `docs/VALIDATION.md`.
