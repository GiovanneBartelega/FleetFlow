-- Entrega de 18/11 (banco): consultas agregadas dos relatórios, como views.
--
-- Execute UMA VEZ depois de 007_indices_relatorios_18_11.sql, com o banco
-- fleetflow selecionado. Só cria views (CREATE OR REPLACE): não altera tabelas,
-- colunas nem dados, e pode ser reexecutada.
--
-- Adaptada ao schema integrado (migrations 001 a 007):
--   viagens        -> Status = 'Encerrada', DataFechamento, HodometroInicial/Final
--                     (km = HodometroFinal - HodometroInicial; não existe KmRodados)
--   rotas          -> Nome, DistanciaEstimadaKm
--   abastecimentos -> Litros, ValorTotal, MovimentacaoId (1 para 1 com a despesa)
-- O schema "banco-de-dados versao final.sql" NÃO deve ser executado.
--
-- Regras iguais às de src/services/relatorios.js:
--   * só movimentações ativas, com categoria ativa;
--   * regime 'caixa'       = Status 'Pago', mês de DataPagamento;
--     regime 'competencia' = qualquer status, mês de DataVencimento;
--   * veículo da movimentação = COALESCE(m.VeiculoId, viagem.VeiculoId, divida.VeiculoId);
--   * custo variável = despesa ligada a uma viagem (ViagemId não nulo);
--   * Mes é texto 'AAAA-MM', como a dimensão "mes" da API.
--
-- Forma das consultas (duas etapas): a tabela grande (movimentacoes) é agregada
-- sozinha, por chaves cruas, na etapa 1; só depois viagens e dividas entram no
-- JOIN, sobre o resultado já agregado (etapa 2). Nenhuma view junta
-- movimentacoes com viagens/abastecimentos para somar depois (evita duplicar).
-- Granularidade: mês. Para período em dias, a API continua consultando as
-- tabelas (índices da 007).

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
-- As colunas Regime e Mes nascem de literais e de DATE_FORMAT, que herdam a
-- collation de quem cria a view. Por isso elas levam COLLATE explícito: sem
-- isso, um cliente com outra collation de conexão (o driver Node, por exemplo)
-- recebe "Illegal mix of collations" ao filtrar Regime = 'caixa' ou por Mes.

-- ---------------------------------------------------------------------------
-- Etapa 1: movimentações agregadas por regime, mês, tipo e chaves cruas.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_fin_mensal_chaves AS
SELECT 'caixa' COLLATE utf8mb4_unicode_ci AS Regime,
       (DATE_FORMAT(m.DataPagamento, '%Y-%m') COLLATE utf8mb4_unicode_ci) AS Mes,
       c.Tipo AS Tipo,
       m.VeiculoId AS VeiculoId,
       m.ViagemId AS ViagemId,
       m.DividaId AS DividaId,
       SUM(m.Valor) AS Valor,
       COUNT(*) AS Movimentacoes
  FROM movimentacoes m
  JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
 WHERE m.Ativo = 1 AND m.Status = 'Pago' AND m.DataPagamento IS NOT NULL
 GROUP BY (DATE_FORMAT(m.DataPagamento, '%Y-%m') COLLATE utf8mb4_unicode_ci), c.Tipo, m.VeiculoId, m.ViagemId, m.DividaId
UNION ALL
SELECT 'competencia' COLLATE utf8mb4_unicode_ci,
       (DATE_FORMAT(m.DataVencimento, '%Y-%m') COLLATE utf8mb4_unicode_ci),
       c.Tipo,
       m.VeiculoId,
       m.ViagemId,
       m.DividaId,
       SUM(m.Valor),
       COUNT(*)
  FROM movimentacoes m
  JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
 WHERE m.Ativo = 1 AND m.DataVencimento IS NOT NULL
 GROUP BY (DATE_FORMAT(m.DataVencimento, '%Y-%m') COLLATE utf8mb4_unicode_ci), c.Tipo, m.VeiculoId, m.ViagemId, m.DividaId;

-- ---------------------------------------------------------------------------
-- Etapa 2: rentabilidade mensal por veículo, rota e motorista.
-- VeiculoId/RotaId/MotoristaId NULL = custo ou receita não alocados (por
-- exemplo salários e despesas avulsas, sem veículo nem viagem).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_rentabilidade_mensal_veiculo AS
SELECT f.Regime,
       f.Mes,
       COALESCE(f.VeiculoId, vi.VeiculoId, d.VeiculoId) AS VeiculoId,
       SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE 0 END) AS Receita,
       SUM(CASE WHEN f.Tipo = 'Saida' THEN f.Valor ELSE 0 END) AS Despesa,
       SUM(CASE WHEN f.Tipo = 'Saida' AND f.ViagemId IS NOT NULL THEN f.Valor ELSE 0 END) AS CustoVariavel,
       SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE -f.Valor END) AS Lucro
  FROM vw_fin_mensal_chaves f
  LEFT JOIN viagens vi ON vi.Id = f.ViagemId
  LEFT JOIN dividas d ON d.Id = f.DividaId
 GROUP BY f.Regime, f.Mes, COALESCE(f.VeiculoId, vi.VeiculoId, d.VeiculoId);

CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_rentabilidade_mensal_rota AS
SELECT f.Regime,
       f.Mes,
       vi.RotaId AS RotaId,
       SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE 0 END) AS Receita,
       SUM(CASE WHEN f.Tipo = 'Saida' THEN f.Valor ELSE 0 END) AS Despesa,
       SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE -f.Valor END) AS Lucro
  FROM vw_fin_mensal_chaves f
  LEFT JOIN viagens vi ON vi.Id = f.ViagemId
 GROUP BY f.Regime, f.Mes, vi.RotaId;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_rentabilidade_mensal_motorista AS
SELECT f.Regime,
       f.Mes,
       vi.MotoristaId AS MotoristaId,
       SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE 0 END) AS Receita,
       SUM(CASE WHEN f.Tipo = 'Saida' THEN f.Valor ELSE 0 END) AS Despesa,
       SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE -f.Valor END) AS Lucro
  FROM vw_fin_mensal_chaves f
  LEFT JOIN viagens vi ON vi.Id = f.ViagemId
 GROUP BY f.Regime, f.Mes, vi.MotoristaId;

-- ---------------------------------------------------------------------------
-- Quilometragem: viagens encerradas, no mês de DataFechamento.
-- ViagensSemKm = encerradas sem HodometroFinal (não entram na soma de km).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_km_mensal_veiculo AS
SELECT (DATE_FORMAT(v.DataFechamento, '%Y-%m') COLLATE utf8mb4_unicode_ci) AS Mes,
       v.VeiculoId AS VeiculoId,
       COUNT(*) AS Viagens,
       SUM(CASE WHEN v.HodometroFinal IS NULL THEN 1 ELSE 0 END) AS ViagensSemKm,
       COALESCE(SUM(v.HodometroFinal - v.HodometroInicial), 0) AS KmRodados
  FROM viagens v
 WHERE v.Ativo = 1 AND v.Status = 'Encerrada' AND v.DataFechamento IS NOT NULL
 GROUP BY (DATE_FORMAT(v.DataFechamento, '%Y-%m') COLLATE utf8mb4_unicode_ci), v.VeiculoId;

-- ---------------------------------------------------------------------------
-- Combustível: litros e valor dos abastecimentos, pelo regime da despesa
-- vinculada (a.MovimentacaoId). Um abastecimento tem exatamente uma despesa.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_combustivel_mensal_veiculo AS
SELECT 'caixa' COLLATE utf8mb4_unicode_ci AS Regime,
       (DATE_FORMAT(m.DataPagamento, '%Y-%m') COLLATE utf8mb4_unicode_ci) AS Mes,
       a.VeiculoId AS VeiculoId,
       COUNT(*) AS Abastecimentos,
       SUM(a.Litros) AS Litros,
       SUM(a.ValorTotal) AS ValorCombustivel,
       ROUND(SUM(a.ValorTotal) / NULLIF(SUM(a.Litros), 0), 3) AS PrecoMedioLitro
  FROM abastecimentos a
  JOIN movimentacoes m ON m.Id = a.MovimentacaoId AND m.Ativo = 1
 WHERE a.Ativo = 1 AND m.Status = 'Pago' AND m.DataPagamento IS NOT NULL
 GROUP BY (DATE_FORMAT(m.DataPagamento, '%Y-%m') COLLATE utf8mb4_unicode_ci), a.VeiculoId
UNION ALL
SELECT 'competencia' COLLATE utf8mb4_unicode_ci,
       (DATE_FORMAT(m.DataVencimento, '%Y-%m') COLLATE utf8mb4_unicode_ci),
       a.VeiculoId,
       COUNT(*),
       SUM(a.Litros),
       SUM(a.ValorTotal),
       ROUND(SUM(a.ValorTotal) / NULLIF(SUM(a.Litros), 0), 3)
  FROM abastecimentos a
  JOIN movimentacoes m ON m.Id = a.MovimentacaoId AND m.Ativo = 1
 WHERE a.Ativo = 1 AND m.DataVencimento IS NOT NULL
 GROUP BY (DATE_FORMAT(m.DataVencimento, '%Y-%m') COLLATE utf8mb4_unicode_ci), a.VeiculoId;

-- ---------------------------------------------------------------------------
-- Custo por km por veículo e mês. Une o lado financeiro e o de km (UNION ALL +
-- GROUP BY, o equivalente a um FULL JOIN) para não perder mês que só tem um dos
-- dois, e referencia cada view uma única vez.
-- Linhas sem veículo (custos não alocados) ficam de fora: não têm km.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_custo_km_mensal_veiculo AS
SELECT x.Regime,
       x.Mes,
       x.VeiculoId,
       SUM(x.CustoVariavel) AS CustoVariavel,
       SUM(x.CustoTotal) AS CustoTotal,
       SUM(x.KmRodados) AS KmRodados,
       ROUND(SUM(x.CustoVariavel) / NULLIF(SUM(x.KmRodados), 0), 4) AS CustoVariavelPorKm,
       ROUND(SUM(x.CustoTotal) / NULLIF(SUM(x.KmRodados), 0), 4) AS CustoTotalPorKm
  FROM (
        SELECT r.Regime, r.Mes, r.VeiculoId, r.CustoVariavel, r.Despesa AS CustoTotal, 0 AS KmRodados
          FROM vw_rentabilidade_mensal_veiculo r
         WHERE r.VeiculoId IS NOT NULL
        UNION ALL
        SELECT rg.Regime, k.Mes, k.VeiculoId, 0, 0, k.KmRodados
          FROM vw_km_mensal_veiculo k
          CROSS JOIN (SELECT 'caixa' COLLATE utf8mb4_unicode_ci AS Regime
                      UNION ALL SELECT 'competencia' COLLATE utf8mb4_unicode_ci) rg
       ) x
 GROUP BY x.Regime, x.Mes, x.VeiculoId;

-- ---------------------------------------------------------------------------
-- Resultado por viagem (todas as viagens ativas, abertas ou encerradas).
-- Cada fonte é agregada por ViagemId antes do JOIN (sem duplicar valores).
--   Receita / Despesa              = tudo que está lançado na viagem (competência)
--   ReceitaRecebida / DespesaPaga  = só o que já foi pago (caixa)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE SQL SECURITY INVOKER VIEW vw_viagem_resultado AS
SELECT v.Id AS ViagemId,
       v.VeiculoId,
       ve.Placa,
       v.RotaId,
       ro.Nome AS Rota,
       ro.DistanciaEstimadaKm,
       v.MotoristaId,
       us.Nome AS Motorista,
       v.Status,
       v.DataAbertura,
       v.DataFechamento,
       v.HodometroInicial,
       v.HodometroFinal,
       v.HodometroFinal - v.HodometroInicial AS KmRodados,
       COALESCE(f.Receita, 0) AS Receita,
       COALESCE(f.Despesa, 0) AS Despesa,
       COALESCE(f.Receita, 0) - COALESCE(f.Despesa, 0) AS Lucro,
       COALESCE(f.ReceitaRecebida, 0) AS ReceitaRecebida,
       COALESCE(f.DespesaPaga, 0) AS DespesaPaga,
       COALESCE(f.ReceitaRecebida, 0) - COALESCE(f.DespesaPaga, 0) AS LucroRealizado,
       COALESCE(ab.Abastecimentos, 0) AS Abastecimentos,
       COALESCE(ab.Litros, 0) AS Litros,
       COALESCE(ab.ValorCombustivel, 0) AS ValorCombustivel,
       ROUND((v.HodometroFinal - v.HodometroInicial) / NULLIF(ab.Litros, 0), 2) AS ConsumoKmL,
       ROUND(COALESCE(f.Despesa, 0) / NULLIF(v.HodometroFinal - v.HodometroInicial, 0), 4) AS CustoPorKm
  FROM viagens v
  JOIN veiculos ve ON ve.Id = v.VeiculoId
  JOIN rotas ro ON ro.Id = v.RotaId
  JOIN usuarios us ON us.Id = v.MotoristaId
  LEFT JOIN (
        SELECT m.ViagemId,
               SUM(CASE WHEN c.Tipo = 'Entrada' THEN m.Valor ELSE 0 END) AS Receita,
               SUM(CASE WHEN c.Tipo = 'Saida' THEN m.Valor ELSE 0 END) AS Despesa,
               SUM(CASE WHEN c.Tipo = 'Entrada' AND m.Status = 'Pago' THEN m.Valor ELSE 0 END) AS ReceitaRecebida,
               SUM(CASE WHEN c.Tipo = 'Saida' AND m.Status = 'Pago' THEN m.Valor ELSE 0 END) AS DespesaPaga
          FROM movimentacoes m
          JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
         WHERE m.Ativo = 1 AND m.ViagemId IS NOT NULL
         GROUP BY m.ViagemId
       ) f ON f.ViagemId = v.Id
  LEFT JOIN (
        SELECT a.ViagemId,
               COUNT(*) AS Abastecimentos,
               SUM(a.Litros) AS Litros,
               SUM(a.ValorTotal) AS ValorCombustivel
          FROM abastecimentos a
         WHERE a.Ativo = 1 AND a.ViagemId IS NOT NULL
         GROUP BY a.ViagemId
       ) ab ON ab.ViagemId = v.Id
 WHERE v.Ativo = 1;
