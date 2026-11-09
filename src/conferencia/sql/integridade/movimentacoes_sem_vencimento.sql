-- Movimentações ativas sem DataVencimento: o regime de competência filtra por
-- DataVencimento, então elas não aparecem nesse regime (mas aparecem no caixa
-- se estiverem pagas). Informativo.
SELECT m.Id AS movimentacao_id,
       m.Descricao AS descricao,
       c.Titulo AS categoria,
       m.Valor AS valor,
       m.Status AS status,
       m.DataPagamento AS data_pagamento
  FROM movimentacoes m
  JOIN categorias c ON c.Id = m.CategoriaId
 WHERE m.Ativo = 1
   AND m.DataVencimento IS NULL
 ORDER BY m.CriadoEm;
