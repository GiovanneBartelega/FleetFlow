-- Entrega de 04/11. Execute depois de 001_schema.sql, 004_veiculos_21_10.sql
-- e 005_rotas_viagens_checklist_28_10.sql, com o banco fleetflow selecionado.
-- Esta é a única definição da tabela de abastecimentos no projeto integrado.

CREATE TABLE IF NOT EXISTS abastecimentos (
  Id CHAR(36) PRIMARY KEY,
  CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  Ativo BOOLEAN NOT NULL DEFAULT TRUE,
  SyncVersion INT NOT NULL DEFAULT 1,
  VeiculoId CHAR(36) NOT NULL,
  ViagemId CHAR(36) NULL,
  Litros DECIMAL(14,3) NOT NULL,
  ValorTotal DECIMAL(14,2) NOT NULL,
  PrecoLitro DECIMAL(14,3) NOT NULL,
  Posto VARCHAR(160) NOT NULL,
  Hodometro INT NOT NULL,
  MovimentacaoId CHAR(36) NOT NULL,
  CONSTRAINT fk_abastecimentos_veiculo FOREIGN KEY (VeiculoId) REFERENCES veiculos(Id),
  CONSTRAINT fk_abastecimentos_viagem FOREIGN KEY (ViagemId) REFERENCES viagens(Id),
  CONSTRAINT fk_abastecimentos_movimentacao FOREIGN KEY (MovimentacaoId) REFERENCES movimentacoes(Id),
  CONSTRAINT uq_abastecimentos_movimentacao UNIQUE (MovimentacaoId),
  CONSTRAINT ck_abastecimentos_litros CHECK (Litros > 0),
  CONSTRAINT ck_abastecimentos_valor CHECK (ValorTotal > 0),
  CONSTRAINT ck_abastecimentos_preco CHECK (PrecoLitro > 0),
  CONSTRAINT ck_abastecimentos_hodometro CHECK (Hodometro >= 0),
  INDEX ix_abastecimentos_veiculo (VeiculoId, Ativo, CriadoEm),
  INDEX ix_abastecimentos_viagem (ViagemId, Ativo),
  INDEX ix_abastecimentos_posto (Posto)
) ENGINE=InnoDB;

DELIMITER $$
DROP TRIGGER IF EXISTS bu_abastecimentos$$
CREATE TRIGGER bu_abastecimentos BEFORE UPDATE ON abastecimentos FOR EACH ROW
BEGIN
  SET NEW.SyncVersion = OLD.SyncVersion + 1;
  SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DELIMITER ;
