# Fluxo 0.7.0 — triagem provisória de notificações

Para descobrir por que notificações de bancos e benefícios não viram lançamentos.

- Nova opção em Ajustes → Dados: "Triagem de notificações". Vem desligada e pede confirmação para ligar.
- Com a triagem ligada, o Fluxo guarda no aparelho o texto de cada notificação recebida (até 300, as mais novas), com o id do app e o motivo pelo qual o leitor aceitou ou ignorou.
- Em "Revisar notificações" você escolhe Guardar (vira lançamento em revisão, com o valor que o leitor conseguir extrair) ou Descartar.
- "Exportar JSON" gera um arquivo com o resumo por app (`packageName`) e a lista completa, para ajustar a captura por app.
- "Apagar triagem" remove todo o texto guardado; "Apagar histórico" também.

Privacidade: com a triagem ligada, o texto completo das notificações (inclusive mensagens pessoais) fica no armazenamento privado do app até você apagar. Não vai para log, CSV, backup nem internet. O JSON exportado não tem criptografia.

Atualize sem desinstalar. O banco passa para a versão 6 só com uma tabela nova; lançamentos antigos ficam como estavam.

Verificação: testes JVM do core passaram localmente. Build, lint e testes instrumentados rodaram no CI do GitHub (emulador), não em aparelho físico. Ainda não houve teste com notificações reais de banco ou VR.
