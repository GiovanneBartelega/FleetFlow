-- abastecimentos.ValorTotal diferente do valor da movimentação vinculada
-- (ou movimentação inexistente/inativa). O relatório só usa o valor da movimentação.
SELECT a.Id AS abastecimento_id,
       a.MovimentacaoId AS movimentacao_id,
       a.ValorTotal AS valor_abastecimento,
       m.Valor AS valor_movimentacao,
       a.ValorTotal - m.Valor AS diferenca,
       CASE WHEN m.Id IS NULL THEN 'movimentação inexistente'
            WHEN m.Ativo = 0 THEN 'movimentação inativa'
            ELSE 'valores diferentes' END AS motivo
  FROM abastecimentos a
  LEFT JOIN movimentacoes m ON m.Id = a.MovimentacaoId
 WHERE a.Ativo = 1
   AND (m.Id IS NULL OR m.Ativo = 0 OR a.ValorTotal <> m.Valor)
 ORDER BY a.CriadoEm;
