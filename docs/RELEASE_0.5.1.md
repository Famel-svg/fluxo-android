# Fluxo 0.5.1 — conexão do serviço de captura

Correção para o caso em que a permissão de notificações está concedida, mas o Android não liga o serviço e nenhuma movimentação é capturada.

- "Reconectar serviço" agora força o Android a religar o serviço e sempre mostra o resultado. Antes o pedido podia ser ignorado em silêncio.
- Ao abrir o app, se a permissão está dada e o serviço não conectou em alguns segundos, o Fluxo tenta reconectar sozinho uma vez.
- Estado da captura mais claro: sem permissão, aguardando conexão, tentando conectar, conectado, ou "O Android não está iniciando o serviço".
- Quando o serviço não inicia mesmo após a tentativa, aparece um cartão com atalhos para o acesso a notificações, as informações do app e a otimização de bateria, com a orientação para Xiaomi, Redmi e POCO ("Início automático" e bateria "Sem restrições").
- Diagnóstico por banco: quantas notificações foram observadas, quantas viraram lançamento e quantas foram ignoradas, com o motivo. Só contagens ficam em memória; nenhum texto de notificação é guardado.

Atualize sem desinstalar. A correção foi verificada em emulador, onde o Android sempre religa o serviço; o caso real relatado em aparelho físico não pôde ser reproduzido, então a confirmação depende do teste no aparelho. As orientações para outras marcas além de Xiaomi são genéricas. Evidências em [VALIDATION](VALIDATION.md).
