-- Entrega de 18/11: índices para os relatórios de rentabilidade e custo por km.
--
-- Execute UMA VEZ depois de 006_abastecimentos_alertas_04_11.sql, com o
-- schema fleetflow selecionado. Esta migração foi adaptada ao schema que já
-- existe nesta integração: viagens usa DataFechamento e Status = Encerrada.
-- Não execute o arquivo "banco-de-dados versao final.sql" recebido separadamente:
-- ele recria todas as tabelas com nomes de colunas diferentes.

-- Movimentações no regime de caixa: pagamento dentro do período.
CREATE INDEX ix_mov_caixa ON movimentacoes
  (Status, DataPagamento, Ativo, CategoriaId, ViagemId, DividaId, VeiculoId, Valor);

-- Movimentações no regime de competência: vencimento dentro do período.
CREATE INDEX ix_mov_competencia ON movimentacoes
  (DataVencimento, Ativo, CategoriaId, ViagemId, DividaId, VeiculoId, Valor);

-- Classificação da despesa de manutenção pela movimentação vinculada.
CREATE INDEX ix_os_movimentacao ON ordens_servico (MovimentacaoId, Ativo, Tipo);

-- Viagens encerradas no período. Km é obtido por HodometroFinal - HodometroInicial.
CREATE INDEX ix_viagens_encerradas_relatorio ON viagens
  (Status, Ativo, DataFechamento, VeiculoId, RotaId, MotoristaId, HodometroInicial, HodometroFinal);

-- Litros e valor do combustível no relatório, pela movimentação vinculada.
CREATE INDEX ix_abast_relatorio ON abastecimentos
  (MovimentacaoId, Ativo, VeiculoId, ViagemId, Litros);
