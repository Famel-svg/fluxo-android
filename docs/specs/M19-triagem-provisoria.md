# M19 — Triagem provisória de notificações

## Problema
A captura lê notificações de todos os apps, mas o leitor (`NotificationParser`) descarta mensagens que não reconhece.
Notificações reais de bancos e benefícios ficaram de fora, e não sabemos o id de pacote nem o formato do texto.

## Solução provisória
Configurações → Dados → **Triagem de notificações** (desligada por padrão, pede confirmação para ligar).

- Com a triagem ativa, cada notificação observada (exceto contínuas: player, download) é guardada na tabela
  `notification_triage` (banco v6) com pacote, nome do app, título, texto (até 4.000 caracteres), horário e o
  veredito do leitor (`ACCEPTED` ou o `IgnoreReason`).
- O que o leitor já reconheceu continua virando lançamento automaticamente e aparece como **reconhecida**.
- O restante fica **pendente**. Em "Revisar notificações" o dono escolhe:
  - **Guardar**: cria lançamento em revisão, com o valor/direção que o leitor conseguir extrair e nada inventado.
  - **Descartar**: só marca a decisão.
- Uma notificação decidida não reabre quando o app a atualiza. Mantém-se no máximo 300 linhas (as mais novas).
- **Exportar JSON** (`fluxo-triage` v1): resumo por `packageName` (total, decisões, vereditos) e a lista completa com
  texto e decisão. Serve para escolher pacotes e escrever regras por app.
- **Apagar triagem** remove todas as linhas; "Apagar histórico" também.

## Privacidade
Exceção explícita e temporária à regra "texto bruto não é guardado": só com a triagem ligada, só no armazenamento
privado do app. Não entra em log, CSV nem backup. O JSON exportado contém texto bruto: não versionar, não colar em
issue ou memória Ruflo; anonimizar antes de virar fixture.

## Próximo passo
Com o JSON real: listar pacotes que trazem eventos financeiros, criar fixtures anonimizadas no `NotificationParserTest`
e ajustar o leitor/regras por pacote. Depois, remover a triagem ou reduzi-la a diagnóstico sem texto.
