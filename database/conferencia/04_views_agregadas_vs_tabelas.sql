-- Conferência das views da migration 008 contra consultas diretas nas tabelas.
-- Cada consulta compara, por regime, o total do período calculado de duas
-- formas independentes: (a) SQL escrito à mão sobre movimentacoes, viagens e
-- abastecimentos, no estilo de 01 e 02; (b) a soma dos meses da view.
-- Linhas retornadas = divergência (diferença maior que 1 centavo, ou 0,01 km/litro).
-- Resultado vazio nas quatro consultas = as views batem com as tabelas.
--
-- Ajuste o período (use meses cheios: as views têm granularidade mensal).
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @inicio = '2025-11-01';
SET @fim = '2026-10-31';
SET @mes_ini = DATE_FORMAT(@inicio, '%Y-%m');
SET @mes_fim = DATE_FORMAT(@fim, '%Y-%m');

-- A. Rentabilidade por veículo (vw_rentabilidade_mensal_veiculo) ------------
WITH regimes AS (SELECT 'caixa' AS Regime UNION ALL SELECT 'competencia'),
direto AS (
  SELECT r.Regime, COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId) AS VeiculoId,
         SUM(CASE WHEN c.Tipo = 'Entrada' THEN m.Valor ELSE 0 END) AS Receita,
         SUM(CASE WHEN c.Tipo = 'Saida' THEN m.Valor ELSE 0 END) AS Despesa,
         SUM(CASE WHEN c.Tipo = 'Saida' AND m.ViagemId IS NOT NULL THEN m.Valor ELSE 0 END) AS CustoVariavel
    FROM regimes r
    JOIN movimentacoes m ON m.Ativo = 1
    JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
    LEFT JOIN viagens vi ON vi.Id = m.ViagemId
    LEFT JOIN dividas d ON d.Id = m.DividaId
   WHERE (r.Regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN @inicio AND @fim)
      OR (r.Regime = 'competencia' AND m.DataVencimento BETWEEN @inicio AND @fim)
   GROUP BY r.Regime, COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId)
), visao AS (
  SELECT Regime, VeiculoId, SUM(Receita) AS Receita, SUM(Despesa) AS Despesa, SUM(CustoVariavel) AS CustoVariavel
    FROM vw_rentabilidade_mensal_veiculo
   WHERE Mes BETWEEN @mes_ini AND @mes_fim
   GROUP BY Regime, VeiculoId
)
SELECT 'A_rentabilidade_veiculo' AS Verificacao, k.Regime, k.VeiculoId,
       d.Receita AS ReceitaDireta, v.Receita AS ReceitaView,
       d.Despesa AS DespesaDireta, v.Despesa AS DespesaView,
       d.CustoVariavel AS VariavelDireto, v.CustoVariavel AS VariavelView
  FROM (SELECT Regime, VeiculoId FROM direto UNION SELECT Regime, VeiculoId FROM visao) k
  LEFT JOIN direto d ON d.Regime = k.Regime AND d.VeiculoId <=> k.VeiculoId
  LEFT JOIN visao v ON v.Regime = k.Regime AND v.VeiculoId <=> k.VeiculoId
 WHERE ABS(COALESCE(d.Receita, 0) - COALESCE(v.Receita, 0)) > 0.01
    OR ABS(COALESCE(d.Despesa, 0) - COALESCE(v.Despesa, 0)) > 0.01
    OR ABS(COALESCE(d.CustoVariavel, 0) - COALESCE(v.CustoVariavel, 0)) > 0.01;

-- B. Custo por km (vw_custo_km_mensal_veiculo) -----------------------------
WITH regimes AS (SELECT 'caixa' AS Regime UNION ALL SELECT 'competencia'),
custos AS (
  SELECT r.Regime, COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId) AS VeiculoId,
         SUM(CASE WHEN m.ViagemId IS NOT NULL THEN m.Valor ELSE 0 END) AS CustoVariavel,
         SUM(m.Valor) AS CustoTotal
    FROM regimes r
    JOIN movimentacoes m ON m.Ativo = 1
    JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1 AND c.Tipo = 'Saida'
    LEFT JOIN viagens vi ON vi.Id = m.ViagemId
    LEFT JOIN dividas d ON d.Id = m.DividaId
   WHERE (r.Regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN @inicio AND @fim)
      OR (r.Regime = 'competencia' AND m.DataVencimento BETWEEN @inicio AND @fim)
   GROUP BY r.Regime, COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId)
), km AS (
  SELECT VeiculoId, SUM(HodometroFinal - HodometroInicial) AS KmRodados
    FROM viagens
   WHERE Ativo = 1 AND Status = 'Encerrada' AND DATE(DataFechamento) BETWEEN @inicio AND @fim
   GROUP BY VeiculoId
), direto AS (
  SELECT r.Regime, v.Id AS VeiculoId,
         COALESCE(c.CustoVariavel, 0) AS CustoVariavel, COALESCE(c.CustoTotal, 0) AS CustoTotal,
         COALESCE(k.KmRodados, 0) AS KmRodados
    FROM regimes r
    CROSS JOIN veiculos v
    LEFT JOIN custos c ON c.Regime = r.Regime AND c.VeiculoId = v.Id
    LEFT JOIN km k ON k.VeiculoId = v.Id
   WHERE v.Ativo = 1
), visao AS (
  SELECT Regime, VeiculoId, SUM(CustoVariavel) AS CustoVariavel, SUM(CustoTotal) AS CustoTotal, SUM(KmRodados) AS KmRodados
    FROM vw_custo_km_mensal_veiculo
   WHERE Mes BETWEEN @mes_ini AND @mes_fim
   GROUP BY Regime, VeiculoId
)
SELECT 'B_custo_km' AS Verificacao, d.Regime, d.VeiculoId,
       d.CustoVariavel AS VariavelDireto, v.CustoVariavel AS VariavelView,
       d.CustoTotal AS TotalDireto, v.CustoTotal AS TotalView,
       d.KmRodados AS KmDireto, v.KmRodados AS KmView
  FROM direto d
  LEFT JOIN visao v ON v.Regime = d.Regime AND v.VeiculoId = d.VeiculoId
 WHERE ABS(d.CustoVariavel - COALESCE(v.CustoVariavel, 0)) > 0.01
    OR ABS(d.CustoTotal - COALESCE(v.CustoTotal, 0)) > 0.01
    OR ABS(d.KmRodados - COALESCE(v.KmRodados, 0)) > 0.01;

-- C. Combustível (vw_combustivel_mensal_veiculo) ---------------------------
WITH regimes AS (SELECT 'caixa' AS Regime UNION ALL SELECT 'competencia'),
direto AS (
  SELECT r.Regime, a.VeiculoId, SUM(a.Litros) AS Litros, SUM(a.ValorTotal) AS Valor
    FROM regimes r
    JOIN abastecimentos a ON a.Ativo = 1
    JOIN movimentacoes m ON m.Id = a.MovimentacaoId AND m.Ativo = 1
   WHERE (r.Regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN @inicio AND @fim)
      OR (r.Regime = 'competencia' AND m.DataVencimento BETWEEN @inicio AND @fim)
   GROUP BY r.Regime, a.VeiculoId
), visao AS (
  SELECT Regime, VeiculoId, SUM(Litros) AS Litros, SUM(ValorCombustivel) AS Valor
    FROM vw_combustivel_mensal_veiculo
   WHERE Mes BETWEEN @mes_ini AND @mes_fim
   GROUP BY Regime, VeiculoId
)
SELECT 'C_combustivel' AS Verificacao, k.Regime, k.VeiculoId,
       d.Litros AS LitrosDireto, v.Litros AS LitrosView, d.Valor AS ValorDireto, v.Valor AS ValorView
  FROM (SELECT Regime, VeiculoId FROM direto UNION SELECT Regime, VeiculoId FROM visao) k
  LEFT JOIN direto d ON d.Regime = k.Regime AND d.VeiculoId = k.VeiculoId
  LEFT JOIN visao v ON v.Regime = k.Regime AND v.VeiculoId = k.VeiculoId
 WHERE ABS(COALESCE(d.Litros, 0) - COALESCE(v.Litros, 0)) > 0.01
    OR ABS(COALESCE(d.Valor, 0) - COALESCE(v.Valor, 0)) > 0.01;

-- D. Resultado por viagem (vw_viagem_resultado): totais e km por veículo ----
WITH direto AS (
  SELECT v.VeiculoId,
         SUM(CASE WHEN v.Status = 'Encerrada' THEN v.HodometroFinal - v.HodometroInicial ELSE 0 END) AS Km,
         COALESCE(SUM(f.Receita), 0) AS Receita, COALESCE(SUM(f.Despesa), 0) AS Despesa,
         COALESCE(SUM(ab.Litros), 0) AS Litros
    FROM viagens v
    LEFT JOIN (SELECT m.ViagemId,
                      SUM(CASE WHEN c.Tipo = 'Entrada' THEN m.Valor ELSE 0 END) AS Receita,
                      SUM(CASE WHEN c.Tipo = 'Saida' THEN m.Valor ELSE 0 END) AS Despesa
                 FROM movimentacoes m JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
                WHERE m.Ativo = 1 AND m.ViagemId IS NOT NULL GROUP BY m.ViagemId) f ON f.ViagemId = v.Id
    LEFT JOIN (SELECT ViagemId, SUM(Litros) AS Litros FROM abastecimentos
                WHERE Ativo = 1 AND ViagemId IS NOT NULL GROUP BY ViagemId) ab ON ab.ViagemId = v.Id
   WHERE v.Ativo = 1
   GROUP BY v.VeiculoId
), visao AS (
  SELECT VeiculoId, SUM(COALESCE(KmRodados, 0)) AS Km, SUM(Receita) AS Receita, SUM(Despesa) AS Despesa, SUM(Litros) AS Litros
    FROM vw_viagem_resultado GROUP BY VeiculoId
)
SELECT 'D_viagem_resultado' AS Verificacao, d.VeiculoId,
       d.Km AS KmDireto, v.Km AS KmView, d.Receita AS ReceitaDireta, v.Receita AS ReceitaView,
       d.Despesa AS DespesaDireta, v.Despesa AS DespesaView, d.Litros AS LitrosDireto, v.Litros AS LitrosView
  FROM direto d
  LEFT JOIN visao v ON v.VeiculoId = d.VeiculoId
 WHERE ABS(d.Km - COALESCE(v.Km, 0)) > 0.01 OR ABS(d.Receita - COALESCE(v.Receita, 0)) > 0.01
    OR ABS(d.Despesa - COALESCE(v.Despesa, 0)) > 0.01 OR ABS(d.Litros - COALESCE(v.Litros, 0)) > 0.01;
