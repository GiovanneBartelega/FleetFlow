-- Referência manual: custo do período (categorias do tipo Saida).
-- Parâmetros: :regime, :inicio, :fim
-- custo_variavel = custo ligado a uma viagem (ViagemId preenchido).
SELECT
    COALESCE(SUM(CASE WHEN x.veiculo IS NOT NULL THEN x.valor END), 0)                          AS custo_alocado,
    COALESCE(SUM(CASE WHEN x.veiculo IS NULL THEN x.valor END), 0)                              AS custo_nao_alocado,
    COALESCE(SUM(CASE WHEN x.veiculo IS NOT NULL AND x.viagem IS NOT NULL THEN x.valor END), 0) AS custo_variavel_alocado
FROM (
    SELECT m.Valor AS valor,
           m.ViagemId AS viagem,
           COALESCE(
               m.VeiculoId,
               (SELECT vi.VeiculoId FROM viagens vi WHERE vi.Id = m.ViagemId),
               (SELECT di.VeiculoId FROM dividas di WHERE di.Id = m.DividaId)
           ) AS veiculo
      FROM movimentacoes m
     WHERE m.Ativo = 1
       AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1 AND c.Tipo = 'Saida')
       AND (
            (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
         OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
       )
) x;
