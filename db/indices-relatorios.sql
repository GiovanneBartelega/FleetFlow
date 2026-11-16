-- =====================================================================
-- Índices dos relatórios de rentabilidade e custo por km
-- (GET /api/relatorios/rentabilidade e /custo-km; consultas em src/queries/relatorios.sql.js)
--
-- Aplicar em cada ambiente:   npm run perf:indices [-- --db=fleetflow]
-- Desfazer:                   npm run perf:indices -- --remover
-- Idempotente: o script pula o que já existe. Somente CREATE INDEX neste arquivo
-- (o aplicador lê o nome e a tabela de cada comando). Não altera dados nem colunas.
--
-- Por que são "cobrindo" (covering): as consultas agregam milhares de linhas lendo só as colunas
-- abaixo. Com todas elas no próprio índice, o MySQL não precisa ir à tabela (clustered index, com
-- UUID de 36 caracteres) buscar cada linha: a leitura vira uma varredura sequencial de um
-- intervalo do índice. Medido a 5x o volume de 12 meses: 1.718 ms -> 190 ms nesse passo.
--
-- As consultas usam o hint /*+ INDEX(...) */ para escolher estes índices. Se um índice não
-- existir (migração ainda não aplicada), o hint só gera um warning e a consulta continua
-- funcionando pelo plano antigo, apenas mais lenta.
--
-- ATENÇÃO ao alterar: se mudar o nome de um índice, atualize o hint em relatorios.sql.js e o
-- mapa INDICES_ESPERADOS em scripts/perf/explain.js (npm run perf:explain acusa a divergência).
-- =====================================================================

-- movimentacoes, regime CAIXA: WHERE Ativo = 1 AND Status = 'Pago' AND DataPagamento no intervalo.
-- Igualdade em Status, intervalo em DataPagamento, Ativo filtrado dentro do índice; o restante são
-- as colunas que a agregação lê (categoria, viagem, dívida, veículo, valor).
CREATE INDEX ix_mov_caixa ON movimentacoes
    (Status, DataPagamento, Ativo, CategoriaId, ViagemId, DividaId, VeiculoId, Valor);

-- movimentacoes, regime COMPETÊNCIA: WHERE Ativo = 1 AND DataVencimento no intervalo (pago ou pendente).
CREATE INDEX ix_mov_competencia ON movimentacoes
    (DataVencimento, Ativo, CategoriaId, ViagemId, DividaId, VeiculoId, Valor);

-- ordens_servico: a consulta classifica a manutenção (preventiva/corretiva) por movimentação
-- (MIN(Tipo) agrupado por MovimentacaoId, só OS ativas). Índice cobrindo: não toca a tabela.
CREATE INDEX ix_os_movimentacao ON ordens_servico (MovimentacaoId, Ativo, Tipo);

-- viagens: km rodados = viagens Concluida e Ativo, no período de DataFim, agrupadas por
-- veículo/rota/motorista/viagem/mês. Cobre todas as colunas lidas.
CREATE INDEX ix_viagens_concluidas ON viagens
    (Status, Ativo, DataFim, VeiculoId, RotaId, MotoristaId, KmRodados, HodometroInicial, HodometroFinal);

-- abastecimentos: litros por veículo/viagem, ligados à movimentação (MovimentacaoId vem primeiro,
-- então serve tanto para varrer abastecimentos quanto para buscar por movimentação).
CREATE INDEX ix_abast_relatorio ON abastecimentos (MovimentacaoId, Ativo, VeiculoId, ViagemId, Litros);
