# Back end da entrega de 18 11

## Endpoints de indicadores

Todos exigem autenticação. Administrador, Financeiro e Gestor de Frota podem consultar; Operador Motorista recebe 403.

| Endpoint | Finalidade |
| --- | --- |
| `GET /api/indicadores/kpis?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` | Receitas pagas, despesas pagas, resultado e margem do período. |
| `GET /api/indicadores/fluxo-caixa?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` | Fluxo mensal realizado, com saldo acumulado. |
| `GET /api/indicadores/despesas-por-categoria?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` | Despesas pagas agrupadas por categoria e percentual. |
| `GET /api/indicadores/rankings?inicio=AAAA-MM-DD&fim=AAAA-MM-DD&limite=5` | Veículos mais rentáveis e com maiores despesas. |
| `GET /api/indicadores/previsao-caixa?inicio=AAAA-MM-DD&meses=12` | Saldo realizado anterior e previsão mensal de até doze meses. |
| `GET /api/indicadores/alertas-saldo?inicio=AAAA-MM-DD&meses=12` | Meses cuja previsão deixa o saldo negativo. |

## Regras

- Indicadores realizados usam apenas movimentações ativas e pagas, pela data de pagamento.
- Previsão começa com o saldo pago anterior à data inicial e aplica entradas e saídas pendentes pelo vencimento.
- Valores não vinculados a veículo não entram no ranking; isso evita atribuir custo arbitrariamente.
- Datas devem ser reais e no formato `AAAA-MM-DD`; o fim não pode ser anterior ao início.
- A previsão aceita de 1 a 12 meses e o ranking de 1 a 20 posições.

## Testes

`test/indicadores.test.js` cobre os seis endpoints, cálculo de resultado e saldo previsto, validação do período e bloqueio do perfil de motorista.
