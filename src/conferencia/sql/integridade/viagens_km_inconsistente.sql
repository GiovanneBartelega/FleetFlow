-- Viagens concluídas cujo km não fecha com o hodômetro:
--  * sem hodômetro final;
--  * KmRodados diferente de HodometroFinal - HodometroInicial;
--  * KmRodados nulo (o relatório usa o hodômetro como alternativa);
--  * sem DataFim (nunca entra em nenhum período do relatório).
SELECT vi.Id AS viagem_id,
       vi.VeiculoId AS veiculo_id,
       vi.HodometroInicial AS hodometro_inicial,
       vi.HodometroFinal AS hodometro_final,
       vi.KmRodados AS km_rodados,
       vi.HodometroFinal - vi.HodometroInicial AS km_pelo_hodometro,
       vi.DataFim AS data_fim,
       CASE WHEN vi.HodometroFinal IS NULL THEN 'sem hodômetro final'
            WHEN vi.KmRodados IS NULL THEN 'KmRodados nulo (relatório usa o hodômetro)'
            WHEN vi.KmRodados <> vi.HodometroFinal - vi.HodometroInicial THEN 'KmRodados diferente do hodômetro'
            ELSE 'sem DataFim' END AS motivo
  FROM viagens vi
 WHERE vi.Status = 'Concluida'
   AND vi.Ativo = 1
   AND (   vi.HodometroFinal IS NULL
        OR vi.KmRodados IS NULL
        OR vi.KmRodados <> vi.HodometroFinal - vi.HodometroInicial
        OR vi.DataFim IS NULL)
 ORDER BY vi.DataInicio;
