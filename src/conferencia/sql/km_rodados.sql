-- Referência manual: km das viagens concluídas, por veículo.
-- Atribuição ao período pela DATA de DataFim (DATE(DataFim) BETWEEN ...).
-- Km da viagem: KmRodados; se nulo, HodometroFinal - HodometroInicial; se não
-- houver hodômetro final, a viagem não tem km (conta em viagens_sem_km).
-- Parâmetros: :inicio, :fim
SELECT v.Id    AS veiculo_id,
       v.Placa AS placa,
       (SELECT COALESCE(SUM(
                   CASE WHEN t.KmRodados IS NOT NULL THEN t.KmRodados
                        WHEN t.HodometroFinal IS NOT NULL THEN t.HodometroFinal - t.HodometroInicial
                        ELSE 0 END), 0)
          FROM viagens t
         WHERE t.VeiculoId = v.Id AND t.Status = 'Concluida' AND t.Ativo = 1
           AND DATE(t.DataFim) BETWEEN :inicio AND :fim) AS km,
       (SELECT COUNT(*)
          FROM viagens t
         WHERE t.VeiculoId = v.Id AND t.Status = 'Concluida' AND t.Ativo = 1
           AND DATE(t.DataFim) BETWEEN :inicio AND :fim) AS viagens,
       (SELECT COUNT(*)
          FROM viagens t
         WHERE t.VeiculoId = v.Id AND t.Status = 'Concluida' AND t.Ativo = 1
           AND t.KmRodados IS NULL AND t.HodometroFinal IS NULL
           AND DATE(t.DataFim) BETWEEN :inicio AND :fim) AS viagens_sem_km
  FROM veiculos v;
