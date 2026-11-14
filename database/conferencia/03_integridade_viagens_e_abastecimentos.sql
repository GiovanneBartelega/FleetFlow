-- Linhas retornadas por estas consultas precisam de correção antes da conferência.

-- Viagens encerradas com hodômetro inválido.
SELECT Id, VeiculoId, HodometroInicial, HodometroFinal, DataFechamento
  FROM viagens
 WHERE Ativo = 1 AND Status = 'Encerrada'
   AND (HodometroFinal IS NULL OR HodometroFinal < HodometroInicial);

-- Abastecimentos cujo valor não bate com a despesa vinculada.
SELECT a.Id AS AbastecimentoId, a.ValorTotal, m.Valor AS ValorMovimentacao
  FROM abastecimentos a
  JOIN movimentacoes m ON m.Id = a.MovimentacaoId
 WHERE a.Ativo = 1 AND m.Ativo = 1 AND ABS(a.ValorTotal - m.Valor) > 0.10;
