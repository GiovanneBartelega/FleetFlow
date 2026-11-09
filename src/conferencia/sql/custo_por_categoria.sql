-- Referência manual: custo alocado a veículo, por categoria (Saida).
-- Parâmetros: :regime, :inicio, :fim
SELECT c.Titulo AS categoria,
       SUM(m.Valor) AS valor
  FROM movimentacoes m
  JOIN categorias c ON c.Id = m.CategoriaId
 WHERE m.Ativo = 1
   AND c.Ativo = 1
   AND c.Tipo = 'Saida'
   AND COALESCE(
           m.VeiculoId,
           (SELECT vi.VeiculoId FROM viagens vi WHERE vi.Id = m.ViagemId),
           (SELECT di.VeiculoId FROM dividas di WHERE di.Id = m.DividaId)
       ) IS NOT NULL
   AND (
        (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
     OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
   )
 GROUP BY c.Titulo;
