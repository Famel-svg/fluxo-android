# Fluxo 0.4.1 — configuração inicial e benefícios por dia da semana

- Na primeira abertura, uma tela pede nome (opcional) e configura VR e VT: valor por dia e em quais dias da semana cada um é depositado. A estimativa do mês aparece na hora, por exemplo "22 dias × R$ 39,00 = R$ 858,00". Dá para pular e rever depois em Ajustes.
- VR e VT passam a ter dias da semana próprios; antes os dois contavam sempre de segunda a sexta. O ajuste manual de dias por mês continua valendo por cima.
- Planejar: editor de benefícios com os mesmos dias da semana. Início mostra uma saudação discreta quando há nome.
- Backup protegido passa ao payload v4, com os dias da semana de cada benefício. Backups v1, v2 e v3 continuam restauráveis. O nome fica só no aparelho: não vai para backup nem CSV.
- Novo ícone nas cores do aplicativo.
- Correções após a revisão da 0.4.0: rótulo do mês em telas de 320dp, textos com fonte ampliada, série de 7 dias no início do mês, mês por extenso em Planejar.

Atualize sem desinstalar; quem já usa o app vê a configuração inicial uma vez, preenchida com os valores atuais. APK de desenvolvimento, com a mesma assinatura das versões anteriores. A estimativa de benefícios não considera feriados nem ausências. Nenhuma notificação bancária real foi validada. Evidências em [VALIDATION](VALIDATION.md).
