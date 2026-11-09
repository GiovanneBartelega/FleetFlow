-- Referência manual: contas a pagar pendentes (Saida, Pendente, ativas), em qualquer data.
-- Escrita direto sobre as tabelas; o app consulta a view vw_contas_pagar.
-- Sem parâmetros.
SELECT m.Id AS id,
       m.Descricao AS descricao,
       m.Valor AS valor,
       m.DataVencimento AS data_vencimento
  FROM movimentacoes m
 WHERE m.Ativo = 1
   AND m.Status = 'Pendente'
   AND m.CategoriaId IN (SELECT c.Id FROM categorias c WHERE c.Ativo = 1 AND c.Tipo = 'Saida');
