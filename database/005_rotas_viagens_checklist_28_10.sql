-- Entrega de 28/10. Execute uma vez depois de 004_veiculos_21_10.sql.
CREATE TABLE IF NOT EXISTS rotas (
  Id CHAR(36) PRIMARY KEY, CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), Ativo BOOLEAN NOT NULL DEFAULT TRUE,
  SyncVersion INT NOT NULL DEFAULT 1, Nome VARCHAR(100) NOT NULL, Origem VARCHAR(120) NOT NULL,
  Destino VARCHAR(120) NOT NULL, DistanciaEstimadaKm INT NOT NULL, Descricao VARCHAR(500) NULL,
  CHECK (DistanciaEstimadaKm > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS viagens (
  Id CHAR(36) PRIMARY KEY, CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), Ativo BOOLEAN NOT NULL DEFAULT TRUE,
  SyncVersion INT NOT NULL DEFAULT 1, RotaId CHAR(36) NOT NULL, VeiculoId CHAR(36) NOT NULL,
  MotoristaId CHAR(36) NOT NULL, HodometroInicial INT NOT NULL, HodometroFinal INT NULL,
  DataAbertura DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), DataFechamento DATETIME(3) NULL,
  Status ENUM('Aberta','Encerrada') NOT NULL DEFAULT 'Aberta', Observacao VARCHAR(500) NULL,
  FOREIGN KEY (RotaId) REFERENCES rotas(Id), FOREIGN KEY (VeiculoId) REFERENCES veiculos(Id),
  FOREIGN KEY (MotoristaId) REFERENCES usuarios(Id), CHECK (HodometroInicial >= 0),
  CHECK (HodometroFinal IS NULL OR HodometroFinal >= HodometroInicial),
  INDEX ix_viagens_veiculo_aberta (VeiculoId, Ativo, Status), INDEX ix_viagens_motorista (MotoristaId, Ativo)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS itens_checklist (
  Id CHAR(36) PRIMARY KEY, CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), Ativo BOOLEAN NOT NULL DEFAULT TRUE,
  SyncVersion INT NOT NULL DEFAULT 1, Nome VARCHAR(120) NOT NULL, Obrigatorio BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uq_itens_checklist_nome (Nome)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS checklists_viagem (
  Id CHAR(36) PRIMARY KEY, CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), Ativo BOOLEAN NOT NULL DEFAULT TRUE,
  SyncVersion INT NOT NULL DEFAULT 1, ViagemId CHAR(36) NOT NULL, ItemChecklistId CHAR(36) NOT NULL,
  Conforme BOOLEAN NOT NULL, Observacao VARCHAR(200) NULL, FotoUrl VARCHAR(2000) NULL,
  FOREIGN KEY (ViagemId) REFERENCES viagens(Id), FOREIGN KEY (ItemChecklistId) REFERENCES itens_checklist(Id),
  UNIQUE KEY uq_checklist_viagem_item (ViagemId, ItemChecklistId),
  CHECK (Conforme = TRUE OR Observacao IS NOT NULL)
) ENGINE=InnoDB;

DELIMITER $$
DROP TRIGGER IF EXISTS bu_rotas$$
CREATE TRIGGER bu_rotas BEFORE UPDATE ON rotas FOR EACH ROW BEGIN SET NEW.SyncVersion = OLD.SyncVersion + 1; SET NEW.AtualizadoEm = UTC_TIMESTAMP(3); END$$
DROP TRIGGER IF EXISTS bu_viagens$$
CREATE TRIGGER bu_viagens BEFORE UPDATE ON viagens FOR EACH ROW BEGIN SET NEW.SyncVersion = OLD.SyncVersion + 1; SET NEW.AtualizadoEm = UTC_TIMESTAMP(3); END$$
DROP TRIGGER IF EXISTS bu_itens_checklist$$
CREATE TRIGGER bu_itens_checklist BEFORE UPDATE ON itens_checklist FOR EACH ROW BEGIN SET NEW.SyncVersion = OLD.SyncVersion + 1; SET NEW.AtualizadoEm = UTC_TIMESTAMP(3); END$$
DROP TRIGGER IF EXISTS bu_checklists_viagem$$
CREATE TRIGGER bu_checklists_viagem BEFORE UPDATE ON checklists_viagem FOR EACH ROW BEGIN SET NEW.SyncVersion = OLD.SyncVersion + 1; SET NEW.AtualizadoEm = UTC_TIMESTAMP(3); END$$
DELIMITER ;

INSERT IGNORE INTO itens_checklist (Id, Nome, Obrigatorio) VALUES
  (UUID(), 'Documentação do veículo', TRUE), (UUID(), 'Pneus e estepe', TRUE),
  (UUID(), 'Luzes e sinalização', TRUE), (UUID(), 'Combustível', TRUE), (UUID(), 'Itens de segurança', TRUE);
