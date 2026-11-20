-- Remove TUDO que a migration 009 (massa de 12 meses) criou.
-- Todo registro da massa tem Id começando por 'f1ee7f10-'. Dados reais, que
-- não têm esse prefixo, ficam intactos, a menos que apontem para um veículo,
-- viagem, dívida ou motorista da massa (aí saem junto, porque o pai some).
-- Use em desenvolvimento ou homologação. Executa numa transação só.

START TRANSACTION;

DELETE FROM checklists_viagem WHERE ViagemId LIKE 'f1ee7f10-%';
DELETE FROM abastecimentos WHERE Id LIKE 'f1ee7f10-%' OR ViagemId LIKE 'f1ee7f10-%' OR VeiculoId LIKE 'f1ee7f10-%';
DELETE FROM ordens_servico WHERE Id LIKE 'f1ee7f10-%' OR VeiculoId LIKE 'f1ee7f10-%';
DELETE FROM comprovantes WHERE MovimentacaoId IN
  (SELECT Id FROM movimentacoes WHERE Id LIKE 'f1ee7f10-%' OR VeiculoId LIKE 'f1ee7f10-%'
      OR ViagemId LIKE 'f1ee7f10-%' OR DividaId LIKE 'f1ee7f10-%');
DELETE FROM movimentacoes WHERE Id LIKE 'f1ee7f10-%' OR VeiculoId LIKE 'f1ee7f10-%'
                             OR ViagemId LIKE 'f1ee7f10-%' OR DividaId LIKE 'f1ee7f10-%';
DELETE FROM viagens WHERE Id LIKE 'f1ee7f10-%' OR VeiculoId LIKE 'f1ee7f10-%' OR RotaId LIKE 'f1ee7f10-%'
                       OR MotoristaId LIKE 'f1ee7f10-%';
DELETE FROM dividas WHERE Id LIKE 'f1ee7f10-%' OR VeiculoId LIKE 'f1ee7f10-%';
DELETE FROM veiculos WHERE Id LIKE 'f1ee7f10-%';
DELETE FROM rotas WHERE Id LIKE 'f1ee7f10-%';
DELETE FROM refresh_tokens WHERE UsuarioId LIKE 'f1ee7f10-%';
DELETE FROM usuarios WHERE Id LIKE 'f1ee7f10-%';

COMMIT;

SELECT 'usuarios' AS Tabela, COUNT(*) AS Restantes FROM usuarios WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'veiculos', COUNT(*) FROM veiculos WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'rotas', COUNT(*) FROM rotas WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'viagens', COUNT(*) FROM viagens WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'movimentacoes', COUNT(*) FROM movimentacoes WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'abastecimentos', COUNT(*) FROM abastecimentos WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'ordens_servico', COUNT(*) FROM ordens_servico WHERE Id LIKE 'f1ee7f10-%'
UNION ALL SELECT 'dividas', COUNT(*) FROM dividas WHERE Id LIKE 'f1ee7f10-%';
