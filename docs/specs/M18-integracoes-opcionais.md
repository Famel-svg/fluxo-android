# M18 — Nuvem, compartilhamento, Open Finance e IA

Estado: **Proposto; decisões externas pendentes**. Spec final; implementação só considerada concluída com evidência registrada.

## Problema

Funcionalidades externas acrescentam custo, credenciais e manutenção.

## Comportamento e escopo

Somente após necessidade validada: escolher provedor/custo e consentimento por função. Contas individuais, revogação, exportação e exclusão; uso local segue disponível.

## Contratos e dados

ADR de hospedagem/autenticação/isolamento/retencao; threat model, contratos, segredos protegidos; nunca senha bancária em memória de agentes.

## Fluxo de implementação

1. Fixar contratos e cenários de regressão antes de alterar dados compartilhados.
2. Implementar regras/persistência antes da interface dependente.
3. Validar erro, cancelamento e preservação do histórico.
4. Revisar diff, registrar evidências e fazer commit por incremento usando Ruflo para coordenação.

## Aceite e testes

Sem consentimento não há envio; isolamento entre usuários testado; desconectar preserva histórico manual; custo e limitações visíveis.

## Dependências

M05–M17 relevantes; escolha de provedor pelo proprietário.

## Limites e privacidade

Não contratar serviço, conectar bancos ou enviar dados reais automaticamente. Dados financeiros reais, notificações brutas e credenciais não entram em Git, logs ou memória Ruflo. Migração deve preservar legado; mudanças destrutivas no app exigem confirmação contextual.

## Evidência

Estado de execução mantido no [índice](README.md). Base e justificativa: [auditoria competitiva](../research/2026-09-05-mercado-e-auditoria.md).

