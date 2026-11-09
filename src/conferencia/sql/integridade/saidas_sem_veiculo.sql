-- Movimentações de Saída sem veículo atribuível: VeiculoId nulo, sem viagem
-- (viagem sempre traz veículo) e sem dívida com veículo.
-- São exatamente as que entram em "custosNaoAlocados" do relatório. Informativo.
SELECT m.Id AS movimentacao_id,
       m.Descricao AS descricao,
       c.Titulo AS categoria,
       m.Valor AS valor,
       m.Status AS status,
       m.DataVencimento AS data_vencimento,
       m.DataPagamento AS data_pagamento
  FROM movimentacoes m
  JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
  LEFT JOIN dividas d ON d.Id = m.DividaId
 WHERE m.Ativo = 1
   AND c.Tipo = 'Saida'
   AND m.VeiculoId IS NULL
   AND m.ViagemId IS NULL
   AND d.VeiculoId IS NULL
 ORDER BY m.DataVencimento, m.Descricao;
