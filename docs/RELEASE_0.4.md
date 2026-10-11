# Fluxo 0.4.0 — contas, benefícios e consistência

- Contas locais e transferências próprias (M06); estimativa de VR/VT por dias úteis; captura opcional de outros apps com consentimento explícito.
- Backup protegido passa ao payload v3 e inclui tarifas e dias de VR/VT. Backups v1 e v2 continuam restauráveis e não alteram os benefícios do aparelho.
- SQLite v5 corrige divergência de schema: uma versão intermediária criava estruturas de fatura, nunca usadas, só para quem atualizava da v1. Elas são removidas; lançamentos, correções e exclusões são preservados.
- Ao reconectar, o serviço reprocessa notificações ainda visíveis, sem duplicar lançamentos. Não recupera notificações já dispensadas.
- Lançamentos vindos de outros apps aparecem como "Outro app", não mais como "Manual".
- Interface dividida em um arquivo por tela, sem mudança de comportamento. Compose BOM 2026.06.01.

Atualize sem desinstalar; faça um backup protegido antes. Faturas, parcelas e estornos (M07/M08) continuam não implementados. Nenhuma notificação bancária real foi validada nesta entrega. Evidências em [VALIDATION](VALIDATION.md).
