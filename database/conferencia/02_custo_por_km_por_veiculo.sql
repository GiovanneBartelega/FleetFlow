-- Conferência manual de custo por km. Usa os mesmos campos da integração 04/11.
SET @regime = 'caixa'; -- caixa ou competencia
SET @inicio = '2026-11-01';
SET @fim = '2026-11-11';

WITH custos AS (
  SELECT COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId) AS VeiculoId,
         SUM(CASE WHEN m.ViagemId IS NOT NULL THEN m.Valor ELSE 0 END) AS CustoVariavel,
         SUM(m.Valor) AS CustoTotal
    FROM movimentacoes m
    JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1 AND c.Tipo = 'Saida'
    LEFT JOIN viagens vi ON vi.Id = m.ViagemId
    LEFT JOIN dividas d ON d.Id = m.DividaId
   WHERE m.Ativo = 1
     AND ((@regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN @inicio AND @fim)
       OR (@regime = 'competencia' AND m.DataVencimento BETWEEN @inicio AND @fim))
   GROUP BY COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId)
), km AS (
  SELECT VeiculoId, SUM(HodometroFinal - HodometroInicial) AS KmRodados
    FROM viagens
   WHERE Ativo = 1 AND Status = 'Encerrada'
     AND DATE(DataFechamento) BETWEEN @inicio AND @fim
   GROUP BY VeiculoId
)
SELECT v.Id AS VeiculoId, v.Placa, COALESCE(c.CustoVariavel, 0) AS CustoVariavel,
       COALESCE(c.CustoTotal, 0) AS CustoTotal, COALESCE(k.KmRodados, 0) AS KmRodados,
       ROUND(c.CustoVariavel / NULLIF(k.KmRodados, 0), 4) AS CustoVariavelPorKm,
       ROUND(c.CustoTotal / NULLIF(k.KmRodados, 0), 4) AS CustoTotalPorKm
  FROM veiculos v
  LEFT JOIN custos c ON c.VeiculoId = v.Id
  LEFT JOIN km k ON k.VeiculoId = v.Id
 WHERE v.Ativo = 1;
