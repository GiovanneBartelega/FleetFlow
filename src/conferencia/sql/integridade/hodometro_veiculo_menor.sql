-- veiculos.HodometroAtual menor que o maior hodômetro final de viagem
-- ou que o maior hodômetro de abastecimento do mesmo veículo.
SELECT t.veiculo_id, t.placa, t.hodometro_atual, t.maior_hodometro_viagem, t.maior_hodometro_abastecimento
  FROM (
        SELECT v.Id AS veiculo_id,
               v.Placa AS placa,
               v.HodometroAtual AS hodometro_atual,
               (SELECT MAX(vi.HodometroFinal) FROM viagens vi
                 WHERE vi.VeiculoId = v.Id AND vi.Ativo = 1) AS maior_hodometro_viagem,
               (SELECT MAX(a.Hodometro) FROM abastecimentos a
                 WHERE a.VeiculoId = v.Id AND a.Ativo = 1) AS maior_hodometro_abastecimento
          FROM veiculos v
         WHERE v.Ativo = 1
       ) t
 WHERE t.hodometro_atual < COALESCE(t.maior_hodometro_viagem, 0)
    OR t.hodometro_atual < COALESCE(t.maior_hodometro_abastecimento, 0)
 ORDER BY t.placa;
