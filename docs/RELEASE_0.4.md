# Fluxo 0.4.0 — novo visual, captura mais ampla e consistência

- Interface refeita: tema claro em azul petróleo, sem barra de título, com gráficos (rosca por categoria, gastos dos últimos 7 dias, automático vs manual, despesas dos últimos 6 meses, orçamentos com marca de 100%).
- Registros: itens em revisão viram cartões resolvidos com um toque (Crédito/Débito ou Confirmar); nunca confirmam lançamento sem valor positivo e direção conhecida.
- Parser reconhece mais redações comuns: valor seguido de vírgula, "compra … aprovada", "Pix … realizado", boleto pago, débito automático, salário e pagamento de fatura deixam de ser descartados. Compras em moeda estrangeira, recusas, agendamentos e negações continuam em revisão.
- Ao reconectar, o serviço reprocessa notificações ainda visíveis, sem duplicar. Não recupera notificações já dispensadas.
- Contas locais e transferências próprias; estimativa de VR/VT; captura opcional de outros apps com consentimento explícito.
- Backup protegido passa ao payload v3 e inclui tarifas e dias de VR/VT. Backups v1 e v2 continuam restauráveis e não alteram os benefícios do aparelho.
- SQLite v5 corrige divergência de schema entre instalação nova e atualização antiga; lançamentos, correções e exclusões são preservados.

Atualize sem desinstalar; faça um backup protegido antes. APK de desenvolvimento, com a assinatura debug local. Faturas, parcelas e estornos (M07/M08) continuam não implementados. Nenhuma notificação bancária real foi validada: os testes usam emissores e textos sintéticos. Evidências e limites em [VALIDATION](VALIDATION.md).
