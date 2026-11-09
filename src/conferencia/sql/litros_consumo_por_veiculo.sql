-- Referência manual: litros, km/l e preço médio por veículo.
-- Litros vêm de abastecimentos cuja movimentação entra no mesmo regime/período.
-- Preço médio = valor da movimentação / litros. km/l = km / litros.
-- Parâmetros: :regime, :inicio, :fim
SELECT r.veiculo_id,
       r.litros,
       r.km,
       ROUND(r.km / NULLIF(r.litros, 0), 4)    AS consumo_km_l,
       ROUND(r.valor / NULLIF(r.litros, 0), 4) AS preco_medio_litro
  FROM (
        SELECT v.Id AS veiculo_id,
               (SELECT COALESCE(SUM(a.Litros), 0)
                  FROM abastecimentos a
                  JOIN movimentacoes m ON m.Id = a.MovimentacaoId
                 WHERE a.Ativo = 1 AND m.Ativo = 1 AND a.VeiculoId = v.Id
                   AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1)
                   AND (
                        (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
                     OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
                   )) AS litros,
               (SELECT COALESCE(SUM(m.Valor), 0)
                  FROM abastecimentos a
                  JOIN movimentacoes m ON m.Id = a.MovimentacaoId
                 WHERE a.Ativo = 1 AND m.Ativo = 1 AND a.VeiculoId = v.Id
                   AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1)
                   AND (
                        (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
                     OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
                   )) AS valor,
               (SELECT COALESCE(SUM(
                           CASE WHEN t.KmRodados IS NOT NULL THEN t.KmRodados
                                WHEN t.HodometroFinal IS NOT NULL THEN t.HodometroFinal - t.HodometroInicial
                                ELSE 0 END), 0)
                  FROM viagens t
                 WHERE t.VeiculoId = v.Id AND t.Status = 'Concluida' AND t.Ativo = 1
                   AND DATE(t.DataFim) BETWEEN :inicio AND :fim) AS km
          FROM veiculos v
       ) r;
