-- ordens_servico.ValorTotal diferente do valor da movimentação vinculada
-- (ou movimentação inexistente/inativa).
SELECT o.Id AS ordem_servico_id,
       o.MovimentacaoId AS movimentacao_id,
       o.ValorTotal AS valor_os,
       m.Valor AS valor_movimentacao,
       o.ValorTotal - m.Valor AS diferenca,
       CASE WHEN m.Id IS NULL THEN 'movimentação inexistente'
            WHEN m.Ativo = 0 THEN 'movimentação inativa'
            ELSE 'valores diferentes' END AS motivo
  FROM ordens_servico o
  LEFT JOIN movimentacoes m ON m.Id = o.MovimentacaoId
 WHERE o.Ativo = 1
   AND (m.Id IS NULL OR m.Ativo = 0 OR o.ValorTotal <> m.Valor)
 ORDER BY o.CriadoEm;
