-- Dívidas cujas movimentações-parcela não batem com QuantidadeParcelas:
-- quantidade diferente, NumeroParcela repetido, fora de 1..N, nulo, ou parcela faltando.
SELECT d.Id AS divida_id,
       d.Descricao AS descricao,
       d.QuantidadeParcelas AS parcelas_previstas,
       COUNT(m.Id) AS movimentacoes,
       COUNT(DISTINCT m.NumeroParcela) AS numeros_distintos,
       COUNT(m.Id) - COUNT(DISTINCT m.NumeroParcela) AS duplicadas_ou_sem_numero,
       d.QuantidadeParcelas
         - COUNT(DISTINCT CASE WHEN m.NumeroParcela BETWEEN 1 AND d.QuantidadeParcelas
                               THEN m.NumeroParcela END) AS faltando
  FROM dividas d
  LEFT JOIN movimentacoes m ON m.DividaId = d.Id AND m.Ativo = 1
 WHERE d.Ativo = 1
 GROUP BY d.Id, d.Descricao, d.QuantidadeParcelas
HAVING COUNT(m.Id) <> d.QuantidadeParcelas
    OR COUNT(m.Id) <> COUNT(DISTINCT m.NumeroParcela)
    OR d.QuantidadeParcelas
         - COUNT(DISTINCT CASE WHEN m.NumeroParcela BETWEEN 1 AND d.QuantidadeParcelas
                               THEN m.NumeroParcela END) <> 0
 ORDER BY d.Descricao;
