-- Por categoria: soma das movimentações ativas que o relatório NÃO enxerga no
-- regime/período pedido (pendentes no caixa, outras datas). É a diferença esperada
-- entre vw_resumo_financeiro (sem filtro de status nem período) e o relatório.
-- Inclui o que não tem veículo, porque a view também inclui.
-- Parâmetros: :regime, :inicio, :fim
SELECT c.Titulo AS categoria,
       c.Tipo AS tipo,
       COALESCE(SUM(m.Valor), 0) AS fora_do_escopo
  FROM categorias c
  LEFT JOIN movimentacoes m
         ON m.CategoriaId = c.Id
        AND m.Ativo = 1
        AND NOT (
             (:regime = 'caixa' AND m.Status = 'Pago' AND m.DataPagamento BETWEEN :inicio AND :fim)
          OR (:regime = 'competencia' AND m.DataVencimento BETWEEN :inicio AND :fim)
        )
 WHERE c.Ativo = 1
 GROUP BY c.Id, c.Titulo, c.Tipo;
