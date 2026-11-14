# Back end da entrega de 11 de novembro

Este pacote contém somente o back end Node.js/Express e seus testes. Ele deve ser aplicado sobre a integração de 04/11, que já contém as tabelas de viagens, movimentações e abastecimentos.

## Endpoints incluídos

- `GET /api/viagens/:id/detalhe`: viagem, movimentações vinculadas e indicadores de receita, despesa, lucro, quilômetros, custo por km, consumo e preço médio de combustível.
- `GET /api/relatorios/rentabilidade?inicio=AAAA-MM-DD&fim=AAAA-MM-DD`: rentabilidade agregada.
- `GET /api/relatorios/custo-km?inicio=AAAA-MM-DD&fim=AAAA-MM-DD`: custo por km agregado.

Os dois relatórios aceitam `agrupar=veiculo|rota|motorista|viagem|mes`, `regime=caixa|competencia` e, opcionalmente, `veiculoId`, `rotaId` e `motoristaId`. Administrador, Financeiro e Gestor de Frota podem consultar; Motorista não tem acesso.

## Regras

- Movimentações são a única fonte de valores financeiros; abastecimentos complementam litros e preço médio.
- Cada fonte é agregada separadamente para não multiplicar receitas/despesas ao relacionar uma viagem a vários abastecimentos.
- Divisões por zero retornam `null`, nunca `Infinity` ou `NaN`.
- As fórmulas estão isoladas em `src/domain/indicadoresViagem.js` e possuem testes unitários.

## Contratos pendentes de banco

O app de 11/11 solicita suporte persistente a `Idempotency-Key` e uma marca de viagem em conflito para revisão do gestor. Isso exige uma migration da frente de Dados e, por isso, não foi improvisado neste pacote somente de back end.
