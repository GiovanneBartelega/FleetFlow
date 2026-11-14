# Back end da entrega de 04 de novembro

Este pacote contém somente código Node.js/Express e testes. Não contém migrations, arquivos SQL, nem código Android.

## Recursos incluídos

- `kmRodados` retornado na viagem encerrada, calculado por `hodometroFinal - hodometroInicial`.
- Conclusão de manutenção preventiva atualiza `HodometroAtual` e `HodometroUltimaManutencao` do veículo na mesma transação.
- `GET /api/alertas?diasAntecedencia=30` para manutenção preventiva por km ou tempo, licenciamento e seguro.
- CRUD de `abastecimentos`: criação, consulta, edição, exclusão lógica, filtros, totais de litros/valores e concorrência por `syncVersion`.
- O POST cria a despesa paga na categoria de sistema `Combustível`, vincula veículo/viagem e atualiza o hodômetro em uma transação.
- Preço por litro calculado automaticamente ou validado contra litros e valor total, com tolerância de R$ 0,10 para arredondamento de bomba.

## Contrato que o banco deverá atender

O endpoint de abastecimentos pressupõe uma tabela chamada `abastecimentos` com os campos `Id`, `CriadoEm`, `AtualizadoEm`, `Ativo`, `SyncVersion`, `VeiculoId`, `ViagemId`, `Litros`, `ValorTotal`, `PrecoLitro`, `Posto`, `Hodometro` e `MovimentacaoId`. A tabela deve relacionar veículo, viagem opcional e movimentação gerada. Este pacote não cria essa tabela, pois o ZIP recebido não contém migration ou script SQL.

## Regras principais

- Nenhum hodômetro pode diminuir.
- Abastecimento só aceita viagem aberta do mesmo veículo.
- A manutenção preventiva concluída só atualiza a referência se possuir hodômetro válido.
- Alertas críticos representam vencimento; alertas de atenção representam proximidade do prazo configurado.
