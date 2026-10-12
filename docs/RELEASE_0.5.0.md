# Fluxo 0.5.0 — modo escuro, saldo do VR e configuração em etapas

- Modo escuro: Ajustes → Aparência, com Sistema, Claro ou Escuro. Paleta em petróleo profundo com contraste conferido por teste.
- Início mostra "VR disponível": estimativa do mês menos o que foi pago com vale-refeição, com barra de uso; linha equivalente para o VT.
- Novos meios de pagamento Vale-refeição e Vale-transporte. Nos cartões de Revisar, um toque em Crédito, Débito ou VR classifica o gasto. Gasto com vale fica fora das saídas de caixa e dos saldos de conta, mas entra nas categorias e gráficos.
- Configuração inicial em três páginas (nome, salário mensal, VR e VT), com transição animada; cada página libera a próxima quando está preenchida, e dá para pular.
- Salário mensal: Planejar mostra a renda prevista do mês (salário + VR + VT) e quanto do salário já foi gasto. É apoio de planejamento; não altera entradas, saídas nem o resultado observado.
- Transições ao trocar de aba, abrir o editor, confirmar itens em Revisar e trocar de mês. Cada aba mantém rolagem e busca ao voltar.
- Layout ajustado para telas maiores: configuração inicial centralizada, gráficos usando a largura extra, barra inferior e editor com largura máxima.
- Backup protegido passa ao payload v5, com o salário. Backups v1 a v4 continuam restauráveis. O nome fica só no aparelho.

Atualize sem desinstalar; o APK tem a mesma assinatura das versões anteriores. O saldo do VR é estimativa do mês: não acumula sobra de meses anteriores e depende de você marcar os gastos como VR. Feriados e ausências não entram no cálculo dos benefícios. Nenhuma notificação bancária real foi validada. A partir desta versão a release é publicada pelo GitHub Actions a partir da tag. Evidências em [VALIDATION](VALIDATION.md).
