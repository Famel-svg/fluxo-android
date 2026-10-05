# Plano de testes

Automação: parser com Pix, crédito em conta, cartões, acentos, reais/centavos, múltiplos valores, saldo, mensagens promocionais, falha/agendamento, overflow; teste de persistência e deduplicação em instrumentação quando disponível. Build debug e lint Android.

No aparelho: instalar APK; abrir Ajustes; escolher bancos; ativar captura e acesso especial. Testar Pix recebido/enviado, compras no crédito e débito em C6/Nubank/Itaú. Confirmar que o valor capturado é o da transação. Testar notificação expandida e resumo de grupo; reentrega; nova compra de mesmo valor; app fechado; revogação e restauração de acesso; pausa; reinício do app e telefone; edição e exclusão; mensagem ambígua em revisão; mês anterior; fonte ampliada e tema escuro. Não fazer transações financeiras só para validar sem decisão do proprietário.

Limite: emulador e fixtures sintéticas não certificam templates reais dos bancos. Não enviar notificações pessoais ao repositório. Resultados efetivamente executados ficam em `docs/VALIDATION.md`.

## Casos da versão 0.2
- Migração v1→v2 com registro editado e tombstone; categoria padrão e persistência de orçamento após reabrir repository.
- Orçamento por categoria/mês: inserir, atualizar, excluir, rejeitar valor inválido; clear integral remove limites e pausa captura.
- Analytics inclui crédito+débito confirmados, exclui revisão/entrada/mês diferente, respeita fuso e não transborda Long na soma.
- Busca ignora acentos, combina filtros por AND e permite meses anteriores.
- Planejamento exibe excesso corretamente; falha ao salvar/remover mantém diálogo e campos; edição preserva mês original.
- CSV: precisão, acentos, aspas, quebras de linha, proteção de fórmulas, campos privados ausentes; congelamento de snapshot, cancelamento e falha de destino.
- Manual no aparelho: atualizar sem desinstalar, editar categoria, definir orçamento, filtrar compras e salvar CSV no destino escolhido; conferir planilha antes de compartilhar.

## Casos versão 0.3 - Privacidade e Captura Multiapp
- Sanitização de Conteúdo: Fixtures automáticas em `NotificationPrivacyDeduplicationTest.kt` validam que dados sensíveis (nomes completos, CPFs) não são armazenados na descrição final da transação persistida nem em formato bruto (`raw`).
- Deduplicação Estrita: Transações recebidas múltiplas vezes (mesmo package e id reconstruído) com mesmo conteúdo financeiro resolvidas como duplicatas. O banco ignora redeliveries redundantes, atualizando apenas `last_observed_at`.
- Multiapp (Opt-in x Allowlist): Fontes originais como carteiras digitais, compras/entregas, sistema, e e-mail passam por ingestão quando expressamente autorizadas; categorias desmarcadas não sofrem extração nem parser.
- Descarte de Promocionais: Exclusão rígida na etapa de parsing de eventos sem caráter financeiro, garantindo banco SQLite limpo de spam. Casos de múltiplas origens (bancos tradicionais até apps de e-mail e chat) validados em `NotificationParserTest`.
