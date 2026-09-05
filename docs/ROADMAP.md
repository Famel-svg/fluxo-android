# Plano de implementação e evolução

## Incrementos do MVP
1. Especificação e contratos: produto, regras, privacidade e teste.
2. Motor e captura: parser, SQLite, allowlist, pausa, revisão e deduplicação.
3. Interface: resumo mensal, CRUD, revisão e configurações.
4. Validação: testes JVM, instrumentação, lint, APK debug e CI privado.

## Próximo gate: teste real por banco
Sem comprar ou transferir dinheiro automaticamente. No uso normal do proprietário, conferir Pix recebido/enviado, cartão crédito e débito para cada banco. Registrar apenas exemplos anonimizados, pacote e classificação esperada. Não copiar número de conta, nome de terceiros, CPF ou texto bruto para GitHub.

## Backlog após evidência
- Adaptadores específicos por versão/template dos bancos; tratar notificações atualizadas que alteram chave/horário.
- Categorias, filtros por banco/meio, pesquisa e exportação CSV por consentimento.
- Contas e cartão/fatura como entidades próprias; pagamento de fatura como liquidação para conciliar caixa e competência.
- Bloqueio biométrico e criptografia adicional do banco, com estratégia de recuperação de chaves.
- Backup exportável e restauração explícita. Sincronização opcional exige decisão de hospedagem/custo, autenticação, isolamento de usuários e retenção antes de implementação.
- Release assinado e distribuição privada de APK; Google Play exige revisão de permissões, política de privacidade e ficha de dados.

## Fora do escopo inicial
Movimentar dinheiro, automatizar login bancário, capturar SMS, pedir acessibilidade, prometer saldo em tempo real ou completude do extrato. Open Finance e integração bancária oficial são decisões separadas.
