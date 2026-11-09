-- =====================================================================
-- Fixture determinística da conferência (carregada DEPOIS do schema, que já
-- traz perfis, categorias, formas de pagamento e usuários de teste).
-- Banco de teste apenas (ex.: fleetflow_test). Os valores esperados estão em
-- fixtures/esperado.json, calculados à mão a partir destas linhas.
--
-- Cenário (período de referência: 2026-10-01 a 2026-10-31):
--   Veículos  : V1 ABC1D23, V2 DEF2E34
--   Rotas     : R1 SP-RJ, R2 SP-BH
--   Motoristas: M1 = Teste Motorista, M2 = Teste Administrador
--   Viagens   : T1 (V1,R1,M1) 430 km | T2 (V1,R2,M2) 590 km
--               T3 (V2,R1,M1) KmRodados NULL, hodômetro 50000->50440 = 440 km
--               T4 (V2,R2,M1) CANCELADA (ignorada)
--               T5 (V2,R2,M1) concluída, sem KmRodados e sem HodometroFinal (viagensSemKm)
-- =====================================================================

SET @m1   = (SELECT Id FROM usuarios WHERE Email = 'teste.motorista@exemplo.com');
SET @m2   = (SELECT Id FROM usuarios WHERE Email = 'teste.admin@exemplo.com');
SET @pix  = (SELECT Id FROM formas_pagamento WHERE Nome = 'Pix');
SET @frete  = (SELECT Id FROM categorias WHERE Titulo = 'Frete');
SET @comb   = (SELECT Id FROM categorias WHERE Titulo = 'Combustível');
SET @pedag  = (SELECT Id FROM categorias WHERE Titulo = 'Pedágio');
SET @manut  = (SELECT Id FROM categorias WHERE Titulo = 'Manutenção');
SET @salar  = (SELECT Id FROM categorias WHERE Titulo = 'Salário');
SET @financ = (SELECT Id FROM categorias WHERE Titulo = 'Financiamento');
SET @seguro = (SELECT Id FROM categorias WHERE Titulo = 'Seguro');

SET @v1 = '10000000-0000-4000-8000-000000000001';
SET @v2 = '10000000-0000-4000-8000-000000000002';
SET @r1 = '20000000-0000-4000-8000-000000000001';
SET @r2 = '20000000-0000-4000-8000-000000000002';
SET @t1 = '30000000-0000-4000-8000-000000000001';
SET @t2 = '30000000-0000-4000-8000-000000000002';
SET @t3 = '30000000-0000-4000-8000-000000000003';
SET @t4 = '30000000-0000-4000-8000-000000000004';
SET @t5 = '30000000-0000-4000-8000-000000000005';
SET @d1 = '50000000-0000-4000-8000-000000000001';

INSERT INTO veiculos (Id, Placa, Marca, Modelo, AnoFabricacao, CapacidadeCargaToneladas, HodometroAtual) VALUES
  (@v1, 'ABC1D23', 'Volvo',  'FH 540', 2020, 30.00, 101020),
  (@v2, 'DEF2E34', 'Scania', 'R 450',  2021, 28.00,  50440);

INSERT INTO rotas (Id, Nome, CidadeOrigem, UfOrigem, CidadeDestino, UfDestino, DistanciaEstimadaKm) VALUES
  (@r1, 'SP-RJ', 'São Paulo', 'SP', 'Rio de Janeiro', 'RJ', 430.00),
  (@r2, 'SP-BH', 'São Paulo', 'SP', 'Belo Horizonte', 'MG', 590.00);

INSERT INTO viagens
  (Id, VeiculoId, RotaId, MotoristaId, HodometroInicial, HodometroFinal, KmRodados, DataInicio, DataFim, Status) VALUES
  (@t1, @v1, @r1, @m1, 100000, 100430, 430,  '2026-10-02 08:00:00', '2026-10-03 18:00:00', 'Concluida'),
  (@t2, @v1, @r2, @m2, 100430, 101020, 590,  '2026-10-09 08:00:00', '2026-10-10 20:00:00', 'Concluida'),
  (@t3, @v2, @r1, @m1,  50000,  50440, NULL, '2026-10-17 07:00:00', '2026-10-18 19:00:00', 'Concluida'),
  (@t4, @v2, @r2, @m1,  50440,   NULL, NULL, '2026-10-26 07:00:00', '2026-10-27 12:00:00', 'Cancelada'),
  (@t5, @v2, @r2, @m1,  50440,   NULL, NULL, '2026-10-24 07:00:00', '2026-10-25 12:00:00', 'Concluida');

INSERT INTO dividas
  (Id, Descricao, CategoriaId, QuantidadeParcelas, ValorParcela, DataVencimentoPrimeira, VeiculoId) VALUES
  (@d1, 'Financiamento Volvo FH', @financ, 3, 1000.00, '2026-10-10', @v1);

-- Movimentações. Colunas: Id, Valor, Descricao, Categoria, Forma, Viagem, Divida, Veiculo, Parcela,
--                         DataVencimento, DataPagamento, Status, Litros, Ativo
INSERT INTO movimentacoes
  (Id, Valor, Descricao, CategoriaId, FormaPagamentoId, ViagemId, DividaId, VeiculoId, NumeroParcela,
   DataVencimento, DataPagamento, Status, LitrosCombustivel, Ativo) VALUES
  -- Receitas (Frete)
  ('40000000-0000-4000-8000-000000000001', 5000.00, 'Frete T1',            @frete, @pix, @t1, NULL, NULL, NULL, '2026-10-05', '2026-10-05', 'Pago',     NULL, 1),
  ('40000000-0000-4000-8000-000000000002', 7000.00, 'Frete T2',            @frete, @pix, @t2, NULL, NULL, NULL, '2026-10-12', '2026-10-12', 'Pago',     NULL, 1),
  ('40000000-0000-4000-8000-000000000003', 4000.00, 'Frete T3',            @frete, @pix, @t3, NULL, NULL, NULL, '2026-10-20', '2026-10-20', 'Pago',     NULL, 1),
  ('40000000-0000-4000-8000-000000000004', 2500.00, 'Frete T2 (saldo)',    @frete, @pix, @t2, NULL, NULL, NULL, '2026-10-28', NULL,         'Pendente', NULL, 1),
  ('40000000-0000-4000-8000-000000000005', 9999.00, 'Frete T1 INATIVO',    @frete, @pix, @t1, NULL, NULL, NULL, '2026-10-06', '2026-10-06', 'Pago',     NULL, 0),
  ('40000000-0000-4000-8000-000000000006', 1000.00, 'Frete T1 (setembro)', @frete, @pix, @t1, NULL, NULL, NULL, '2026-09-30', '2026-09-30', 'Pago',     NULL, 1),
  -- Combustível (com viagem E veículo na própria movimentação)
  ('40000000-0000-4000-8000-000000000007', 1500.00, 'Diesel T1',  @comb, @pix, @t1, NULL, @v1, NULL, '2026-10-04', '2026-10-04', 'Pago', 250.00, 1),
  ('40000000-0000-4000-8000-000000000008', 1800.00, 'Diesel T2',  @comb, @pix, @t2, NULL, @v1, NULL, '2026-10-11', '2026-10-11', 'Pago', 300.00, 1),
  ('40000000-0000-4000-8000-000000000009', 1100.00, 'Diesel T3',  @comb, @pix, @t3, NULL, @v2, NULL, '2026-10-18', '2026-10-18', 'Pago', 200.00, 1),
  -- Pedágio (só com viagem: o veículo vem da viagem)
  ('40000000-0000-4000-8000-000000000010',  200.00, 'Pedágio T1', @pedag, @pix, @t1, NULL, NULL, NULL, '2026-10-04', '2026-10-04', 'Pago', NULL, 1),
  ('40000000-0000-4000-8000-000000000011',  250.00, 'Pedágio T2', @pedag, @pix, @t2, NULL, NULL, NULL, '2026-10-11', '2026-10-11', 'Pago', NULL, 1),
  -- Manutenção (só veículo, sem viagem)
  ('40000000-0000-4000-8000-000000000012',  800.00, 'Embreagem V1',          @manut, @pix, NULL, NULL, @v1, NULL, '2026-10-15', '2026-10-15', 'Pago',     NULL, 1),
  ('40000000-0000-4000-8000-000000000013',  600.00, 'Revisão preventiva V2', @manut, @pix, NULL, NULL, @v2, NULL, '2026-10-30', NULL,         'Pendente', NULL, 1),
  -- Dívida (parcelas; o veículo vem da dívida)
  ('40000000-0000-4000-8000-000000000014', 1000.00, 'Financiamento 1/3', @financ, @pix, NULL, @d1, NULL, 1, '2026-10-10', '2026-10-10', 'Pago',     NULL, 1),
  ('40000000-0000-4000-8000-000000000015', 1000.00, 'Financiamento 2/3', @financ, @pix, NULL, @d1, NULL, 2, '2026-11-10', NULL,         'Pendente', NULL, 1),
  ('40000000-0000-4000-8000-000000000016', 1000.00, 'Financiamento 3/3', @financ, @pix, NULL, @d1, NULL, 3, '2026-12-10', NULL,         'Pendente', NULL, 1),
  -- Sem nenhum veículo (custo não alocado)
  ('40000000-0000-4000-8000-000000000017', 3000.00, 'Salário motoristas', @salar, @pix, NULL, NULL, NULL, NULL, '2026-10-05', '2026-10-05', 'Pago', NULL, 1),
  -- Seguro do V2
  ('40000000-0000-4000-8000-000000000018',  400.00, 'Seguro V2',          @seguro, @pix, NULL, NULL, @v2, NULL, '2026-10-08', '2026-10-08', 'Pago', NULL, 1),
  -- Combustível INATIVO (soft delete)
  ('40000000-0000-4000-8000-000000000019',  777.00, 'Diesel INATIVO',     @comb, @pix, NULL, NULL, @v1, NULL, '2026-10-09', '2026-10-09', 'Pago', 100.00, 0);

INSERT INTO abastecimentos
  (Id, VeiculoId, ViagemId, Litros, ValorTotal, PrecoLitro, Posto, Hodometro, MovimentacaoId) VALUES
  ('60000000-0000-4000-8000-000000000001', @v1, @t1, 250.00, 1500.00, 6.000, 'Posto A', 100200, '40000000-0000-4000-8000-000000000007'),
  ('60000000-0000-4000-8000-000000000002', @v1, @t2, 300.00, 1800.00, 6.000, 'Posto B', 100800, '40000000-0000-4000-8000-000000000008'),
  ('60000000-0000-4000-8000-000000000003', @v2, @t3, 200.00, 1100.00, 5.500, 'Posto C',  50300, '40000000-0000-4000-8000-000000000009');

INSERT INTO ordens_servico
  (Id, VeiculoId, DataManutencao, Descricao, Tipo, ValorTotal, HodometroNaManutencao, DataConclusao, MovimentacaoId) VALUES
  ('70000000-0000-4000-8000-000000000001', @v1, '2026-10-14', 'Troca de embreagem', 'Corretiva',  800.00, 100600, '2026-10-15', '40000000-0000-4000-8000-000000000012'),
  ('70000000-0000-4000-8000-000000000002', @v2, '2026-10-29', 'Revisão preventiva', 'Preventiva', 600.00,  50400, NULL,         '40000000-0000-4000-8000-000000000013');
