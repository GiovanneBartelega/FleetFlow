-- Conferência de integridade da massa de 12 meses (migration 009) e, em geral,
-- de viagens, rotas e abastecimentos no schema integrado.
-- Linhas retornadas na PRIMEIRA consulta são problemas a corrigir; vazia = ok.
-- A segunda consulta é só um resumo informativo.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @hoje = CURDATE();

-- 1. Problemas -------------------------------------------------------------
SELECT Verificacao, Id, Detalhe FROM (

  -- Viagem encerrada com hodômetro inválido.
  SELECT 'viagem_hodometro_invalido' AS Verificacao, Id,
         CONCAT('inicial=', HodometroInicial, ' final=', COALESCE(HodometroFinal, 'NULL')) AS Detalhe
    FROM viagens
   WHERE Ativo = 1 AND Status = 'Encerrada' AND (HodometroFinal IS NULL OR HodometroFinal < HodometroInicial)

  UNION ALL
  -- Viagem encerrada sem data de fechamento, ou aberta com fechamento.
  SELECT 'viagem_fechamento_inconsistente', Id, CONCAT('status=', Status)
    FROM viagens
   WHERE Ativo = 1 AND ((Status = 'Encerrada' AND DataFechamento IS NULL)
                     OR (Status = 'Aberta' AND (DataFechamento IS NOT NULL OR HodometroFinal IS NOT NULL)))

  UNION ALL
  -- Mesmo veículo em duas viagens ao mesmo tempo (começa antes da anterior fechar).
  SELECT 'viagem_sobreposta_no_veiculo', x.Id, CONCAT('abre ', x.DataAbertura, ' antes de ', x.FechamentoAnterior)
    FROM (SELECT Id, DataAbertura,
                 LAG(DataFechamento) OVER (PARTITION BY VeiculoId ORDER BY DataAbertura) AS FechamentoAnterior
            FROM viagens WHERE Ativo = 1) x
   WHERE x.FechamentoAnterior IS NOT NULL AND x.DataAbertura < x.FechamentoAnterior

  UNION ALL
  -- Hodômetro que não encadeia: o inicial deve ser o final da viagem anterior do veículo.
  SELECT 'viagem_hodometro_nao_encadeia', x.Id,
         CONCAT('inicial=', x.HodometroInicial, ' final anterior=', x.FinalAnterior)
    FROM (SELECT Id, HodometroInicial,
                 LAG(HodometroFinal) OVER (PARTITION BY VeiculoId ORDER BY DataAbertura) AS FinalAnterior
            FROM viagens WHERE Ativo = 1) x
   WHERE x.FinalAnterior IS NOT NULL AND x.HodometroInicial <> x.FinalAnterior

  UNION ALL
  -- Veículo com mais de uma viagem aberta.
  SELECT 'veiculo_com_varias_viagens_abertas', VeiculoId, CONCAT(COUNT(*), ' abertas')
    FROM viagens WHERE Ativo = 1 AND Status = 'Aberta' GROUP BY VeiculoId HAVING COUNT(*) > 1

  UNION ALL
  -- Veículo EmViagem sem viagem aberta, ou com viagem aberta e outro status.
  SELECT 'veiculo_status_x_viagem_aberta', ve.Id, CONCAT('status=', ve.Status, ' abertas=', COALESCE(a.Abertas, 0))
    FROM veiculos ve
    LEFT JOIN (SELECT VeiculoId, COUNT(*) AS Abertas FROM viagens WHERE Ativo = 1 AND Status = 'Aberta' GROUP BY VeiculoId) a
           ON a.VeiculoId = ve.Id
   WHERE ve.Ativo = 1 AND ((ve.Status = 'EmViagem') <> (COALESCE(a.Abertas, 0) > 0))

  UNION ALL
  -- Hodômetro do veículo abaixo do maior hodômetro já registrado em viagem ou abastecimento.
  SELECT 'veiculo_hodometro_abaixo_do_registrado', ve.Id,
         CONCAT('atual=', ve.HodometroAtual, ' maior=', GREATEST(COALESCE(v.Maior, 0), COALESCE(ab.Maior, 0)))
    FROM veiculos ve
    LEFT JOIN (SELECT VeiculoId, MAX(COALESCE(HodometroFinal, HodometroInicial)) AS Maior FROM viagens WHERE Ativo = 1 GROUP BY VeiculoId) v
           ON v.VeiculoId = ve.Id
    LEFT JOIN (SELECT VeiculoId, MAX(Hodometro) AS Maior FROM abastecimentos WHERE Ativo = 1 GROUP BY VeiculoId) ab
           ON ab.VeiculoId = ve.Id
   WHERE ve.Ativo = 1 AND ve.HodometroAtual < GREATEST(COALESCE(v.Maior, 0), COALESCE(ab.Maior, 0))

  UNION ALL
  -- Abastecimento que não bate com a despesa vinculada (valor, litros, veículo, viagem).
  SELECT 'abastecimento_x_movimentacao', a.Id,
         CONCAT('valor ', a.ValorTotal, '/', m.Valor, ' litros ', a.Litros, '/', COALESCE(m.LitrosCombustivel, 'NULL'))
    FROM abastecimentos a
    JOIN movimentacoes m ON m.Id = a.MovimentacaoId
   WHERE a.Ativo = 1 AND m.Ativo = 1
     AND (ABS(a.ValorTotal - m.Valor) > 0.10
          OR NOT (m.LitrosCombustivel <=> ROUND(a.Litros, 2))
          OR NOT (m.VeiculoId <=> a.VeiculoId)
          OR NOT (m.ViagemId <=> a.ViagemId))

  UNION ALL
  -- Abastecimento cuja despesa não é da categoria Combustível.
  SELECT 'abastecimento_categoria_errada', a.Id, c.Titulo
    FROM abastecimentos a
    JOIN movimentacoes m ON m.Id = a.MovimentacaoId
    JOIN categorias c ON c.Id = m.CategoriaId
   WHERE a.Ativo = 1 AND c.Titulo <> 'Combustível'

  UNION ALL
  -- Abastecimento de viagem em veículo diferente do da viagem.
  SELECT 'abastecimento_veiculo_diferente_da_viagem', a.Id, CONCAT(a.VeiculoId, ' x ', v.VeiculoId)
    FROM abastecimentos a
    JOIN viagens v ON v.Id = a.ViagemId
   WHERE a.Ativo = 1 AND a.VeiculoId <> v.VeiculoId

  UNION ALL
  -- Ordem de serviço que não bate com a despesa vinculada.
  SELECT 'os_x_movimentacao', o.Id, CONCAT('valor ', o.ValorTotal, '/', m.Valor)
    FROM ordens_servico o
    JOIN movimentacoes m ON m.Id = o.MovimentacaoId
   WHERE o.Ativo = 1 AND (ABS(o.ValorTotal - m.Valor) > 0.10 OR NOT (m.VeiculoId <=> o.VeiculoId))

  UNION ALL
  -- Dívida sem todas as parcelas, ou parcelas fora de 1..N.
  SELECT 'divida_parcelas_incompletas', d.Id,
         CONCAT('esperadas=', d.QuantidadeParcelas, ' lançadas=', COUNT(m.Id),
                ' min=', COALESCE(MIN(m.NumeroParcela), 'NULL'), ' max=', COALESCE(MAX(m.NumeroParcela), 'NULL'))
    FROM dividas d
    JOIN movimentacoes m ON m.DividaId = d.Id
   WHERE d.Ativo = 1
   GROUP BY d.Id, d.QuantidadeParcelas
  HAVING COUNT(m.Id) <> d.QuantidadeParcelas OR MIN(m.NumeroParcela) <> 1 OR MAX(m.NumeroParcela) <> d.QuantidadeParcelas

  UNION ALL
  -- Movimentação paga no futuro.
  SELECT 'movimentacao_paga_no_futuro', Id, CONCAT('pagamento=', DataPagamento)
    FROM movimentacoes WHERE Ativo = 1 AND Status = 'Pago' AND DataPagamento > @hoje

  UNION ALL
  -- Viagem ligada a rota ou motorista inativos.
  SELECT 'viagem_rota_ou_motorista_inativo', v.Id, CONCAT('rota ativa=', r.Ativo, ' motorista ativo=', u.Ativo)
    FROM viagens v
    JOIN rotas r ON r.Id = v.RotaId
    JOIN usuarios u ON u.Id = v.MotoristaId
   WHERE v.Ativo = 1 AND (r.Ativo = 0 OR u.Ativo = 0)
) problemas
ORDER BY Verificacao, Id;

-- 2. Resumo informativo ----------------------------------------------------
SELECT 'viagens (ativas)' AS Item, COUNT(*) AS Total FROM viagens WHERE Ativo = 1
UNION ALL SELECT '  encerradas', COUNT(*) FROM viagens WHERE Ativo = 1 AND Status = 'Encerrada'
UNION ALL SELECT '  abertas', COUNT(*) FROM viagens WHERE Ativo = 1 AND Status = 'Aberta'
UNION ALL SELECT 'rotas (ativas)', COUNT(*) FROM rotas WHERE Ativo = 1
UNION ALL SELECT 'abastecimentos (ativos)', COUNT(*) FROM abastecimentos WHERE Ativo = 1
UNION ALL SELECT '  sem viagem (pátio)', COUNT(*) FROM abastecimentos WHERE Ativo = 1 AND ViagemId IS NULL
UNION ALL SELECT 'movimentacoes (ativas)', COUNT(*) FROM movimentacoes WHERE Ativo = 1
UNION ALL SELECT '  pendentes', COUNT(*) FROM movimentacoes WHERE Ativo = 1 AND Status = 'Pendente'
UNION ALL SELECT 'movimentacoes inativas', COUNT(*) FROM movimentacoes WHERE Ativo = 0
UNION ALL SELECT 'ordens de serviço', COUNT(*) FROM ordens_servico WHERE Ativo = 1
UNION ALL SELECT 'veículos em viagem', COUNT(*) FROM veiculos WHERE Ativo = 1 AND Status = 'EmViagem'
UNION ALL SELECT 'veículos em manutenção', COUNT(*) FROM veiculos WHERE Ativo = 1 AND Status = 'EmManutencao'
UNION ALL SELECT 'motoristas com viagens sobrepostas', COUNT(DISTINCT x.MotoristaId)
  FROM (SELECT MotoristaId, DataAbertura,
               LAG(DataFechamento) OVER (PARTITION BY MotoristaId ORDER BY DataAbertura) AS FechamentoAnterior
          FROM viagens WHERE Ativo = 1) x
 WHERE x.FechamentoAnterior IS NOT NULL AND x.DataAbertura < x.FechamentoAnterior;
