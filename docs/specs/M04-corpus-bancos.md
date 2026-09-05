# M04 — Corpus e matriz de validação

Estado: **Proposto; validação real depende de amostras consentidas**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Fixtures sintéticas não comprovam textos reais dos bancos.

## Comportamento e escopo

Roteiro C6/Nubank/Itaú × Pix entrada/saída × crédito/débito; coletar no uso normal, sem realizar pagamentos para teste. Registrar esperado/obtido/versão e origem sintética ou real anonimizada.

## Contratos e dados

Fixtures versionadas sem CPF, conta, nomes, identificadores reais ou bruto. Evidências reais permanecem locais até anonimização conferida.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

12 combinações documentadas; casos de atualização, cancelamento, valores ocultos e múltiplos valores; precisão e cobertura medidas separadamente.

## Dependências

M01, M02, M03.

## Limites e privacidade

Não inventar resultado real nem percentual de precisão. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).
