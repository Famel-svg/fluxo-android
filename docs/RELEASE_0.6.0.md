# Fluxo 0.6.0 — captura de todos os apps e reconhecimento de VR e cartão

Duas mudanças para pagamentos que não eram reconhecidos, principalmente de VR e cartão.

- Captura de todos os apps: com a captura ligada, o Fluxo lê notificações de qualquer app, exceto o próprio. Não é mais preciso escolher bancos nem ligar "Outros apps". Só o interruptor global e a pausa controlam a captura.
- Diagnóstico separado em "Bancos" e "Outros apps", com nome do app, contadores e o motivo de cada ignorado. Notificações barradas pela pausa aparecem como contagem, sem o nome do app.
- Reconhecimento de compras de VR e cartão: "Compra aprovada", "Você gastou…", "Transação aprovada/realizada" e "Compra autorizada" viram despesa. "Vale alimentação" e "refeição" viram VR.
- Mensagens recusadas, agendadas, pendentes, faturas, promoções e perguntas continuam fora dos lançamentos confirmados.
- Extração de texto também lê `EXTRA_TEXT_LINES` e `EXTRA_SUMMARY_TEXT`.

Atualize sem desinstalar. Não há mudança de banco: lançamentos antigos continuam como estavam.

Verificado em emulador: testes JVM, instrumentados e de entrega de notificação passaram. Também chegaram notificações reais do Google Mensagens (SMS simulado) e foram gravadas como despesa de crédito, débito e VR. Ainda não foi testado com notificações de um app de VR ou banco real em aparelho físico.
