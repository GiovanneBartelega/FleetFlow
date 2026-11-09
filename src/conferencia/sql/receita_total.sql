-- Referência manual: receita do período (categorias do tipo Entrada).
-- Escrita de forma independente do service: subconsultas escalares e BETWEEN,
-- sem CTE e sem reaproveitar nenhuma query de src/queries.
-- Parâmetros: :regime, :inicio, :fim
-- "Alocada" = movimentação com veículo (próprio, da viagem ou da dívida).
SELECT
    COALESCE(SUM(CASE WHEN x.veiculo IS NOT NULL THEN x.valor END), 0) AS receita_alocada,
    COALESCE(SUM(CASE WHEN x.veiculo IS NULL THEN x.valor END), 0)     AS receita_nao_alocada
FROM (
    SELECT m.Valor AS valor,
           COALESCE(
               m.VeiculoId,
               (SELECT vi.VeiculoId FROM viagens vi WHERE vi.Id = m.ViagemId),
               (SELECT di.VeiculoId FROM dividas di WHERE di.Id = m.DividaId)
           ) AS veiculo
      FROM movimentacoes m
     WHERE m.Ativo = 1
       AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1 AND c.Tipo = 'Entrada')
       AND (
            (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
         OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
       )
) x;
