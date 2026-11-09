-- Referência manual: receita e custo por veículo (o lucro é receita - custo).
-- Parâmetros: :regime, :inicio, :fim
SELECT x.veiculo AS veiculo_id,
       COALESCE(SUM(CASE WHEN x.tipo = 'Entrada' THEN x.valor END), 0) AS receita,
       COALESCE(SUM(CASE WHEN x.tipo = 'Saida'   THEN x.valor END), 0) AS custo
  FROM (
        SELECT m.Valor AS valor,
               (SELECT c.Tipo FROM categorias c WHERE c.Id = m.CategoriaId AND c.Ativo = 1) AS tipo,
               COALESCE(
                   m.VeiculoId,
                   (SELECT vi.VeiculoId FROM viagens vi WHERE vi.Id = m.ViagemId),
                   (SELECT di.VeiculoId FROM dividas di WHERE di.Id = m.DividaId)
               ) AS veiculo
          FROM movimentacoes m
         WHERE m.Ativo = 1
           AND (
                (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
             OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
           )
       ) x
 WHERE x.veiculo IS NOT NULL
   AND x.tipo IS NOT NULL
 GROUP BY x.veiculo;
