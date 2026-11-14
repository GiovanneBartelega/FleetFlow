-- Conferência manual da rentabilidade por veículo (entrega 11/11).
-- Ajuste as três variáveis antes de executar no MySQL Workbench.
SET @regime = 'caixa'; -- caixa ou competencia
SET @inicio = '2026-11-01';
SET @fim = '2026-11-11';

WITH financeiro AS (
  SELECT COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId) AS VeiculoId,
         c.Tipo, m.Valor
    FROM movimentacoes m
    JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
    LEFT JOIN viagens vi ON vi.Id = m.ViagemId
    LEFT JOIN dividas d ON d.Id = m.DividaId
   WHERE m.Ativo = 1
     AND ((@regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN @inicio AND @fim)
       OR (@regime = 'competencia' AND m.DataVencimento BETWEEN @inicio AND @fim))
)
SELECT v.Id AS VeiculoId, v.Placa,
       COALESCE(SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor END), 0) AS Receita,
       COALESCE(SUM(CASE WHEN f.Tipo = 'Saida' THEN f.Valor END), 0) AS Despesa,
       COALESCE(SUM(CASE WHEN f.Tipo = 'Entrada' THEN f.Valor ELSE -f.Valor END), 0) AS Lucro
  FROM veiculos v
  LEFT JOIN financeiro f ON f.VeiculoId = v.Id
 WHERE v.Ativo = 1
 GROUP BY v.Id, v.Placa;
