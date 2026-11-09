-- Referência manual: custo por km por veículo (variável e total).
-- Divisão por zero -> NULL (NULLIF). Arredondado em 4 casas.
-- Parâmetros: :regime, :inicio, :fim
SELECT r.veiculo_id,
       r.custo_variavel,
       r.custo_total,
       r.km,
       ROUND(r.custo_variavel / NULLIF(r.km, 0), 4) AS custo_variavel_por_km,
       ROUND(r.custo_total    / NULLIF(r.km, 0), 4) AS custo_total_por_km
  FROM (
        SELECT v.Id AS veiculo_id,
               (SELECT COALESCE(SUM(m.Valor), 0)
                  FROM movimentacoes m
                 WHERE m.Ativo = 1
                   AND m.ViagemId IS NOT NULL
                   AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1 AND c.Tipo = 'Saida')
                   AND COALESCE(m.VeiculoId, (SELECT vi.VeiculoId FROM viagens vi WHERE vi.Id = m.ViagemId)) = v.Id
                   AND (
                        (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
                     OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
                   )) AS custo_variavel,
               (SELECT COALESCE(SUM(m.Valor), 0)
                  FROM movimentacoes m
                 WHERE m.Ativo = 1
                   AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1 AND c.Tipo = 'Saida')
                   AND COALESCE(
                           m.VeiculoId,
                           (SELECT vi.VeiculoId FROM viagens vi WHERE vi.Id = m.ViagemId),
                           (SELECT di.VeiculoId FROM dividas di WHERE di.Id = m.DividaId)
                       ) = v.Id
                   AND (
                        (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
                     OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
                   )) AS custo_total,
               (SELECT COALESCE(SUM(
                           CASE WHEN t.KmRodados IS NOT NULL THEN t.KmRodados
                                WHEN t.HodometroFinal IS NOT NULL THEN t.HodometroFinal - t.HodometroInicial
                                ELSE 0 END), 0)
                  FROM viagens t
                 WHERE t.VeiculoId = v.Id AND t.Status = 'Concluida' AND t.Ativo = 1
                   AND DATE(t.DataFim) BETWEEN :inicio AND :fim) AS km
          FROM veiculos v
       ) r;
