# Validação real por banco — M04

Estado: roteiro pronto; nenhuma combinação abaixo foi validada com notificação bancária real nesta entrega.

Usar apenas movimentações que aconteceriam normalmente. Não transferir, comprar ou pagar para satisfazer o teste. Não pedir acesso a senha, SMS ou acessibilidade.

| Banco | Pix recebido | Pix enviado | Crédito | Débito |
|---|---|---|---|---|
| C6 Bank | Pendente | Pendente | Pendente | Pendente |
| Nubank | Pendente | Pendente | Pendente | Pendente |
| Itaú | Pendente | Pendente | Pendente | Pendente |

Para cada evento, anotar localmente: versão do Android, versão do banco, versão do Fluxo, permissão/toggle/serviço, modalidade e direção esperadas, classificação obtida, existência de duplicata e necessidade de correção. Valor real e texto bruto não devem entrar em Git ou memória de agentes.

Se necessário criar fixture, redigir texto sintético equivalente sem nomes, CPF, conta, chave Pix ou identificadores. Revisar anonimização antes de versionar. Marcar claramente que a fixture é derivada/anonimizada e não contrato oficial do banco.

Casos adicionais: valor oculto, ausência de notificação, mensagem com múltiplos valores, notificação atualizada, cancelamento posterior, compra com oferta anexada, chave reutilizada, pausa/reativação, reinício do aparelho. Ausência no Fluxo deve ser comparada primeiro à existência da notificação; extrato autorizado é necessário para avaliar cobertura financeira.

Critério: qualquer confirmação indevida impede declarar suporte daquele template; investigar e manter revisão conservadora. Passar nos testes sintéticos não preenche esta matriz automaticamente.
