-- FleetFlow: rotas, viagens e checklists
-- Migracao incremental. Preserva as tabelas existentes.
-- Pre-requisito: executar database/004_veiculos_21_10.sql antes.

CREATE TABLE IF NOT EXISTS rotas (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    Ativo TINYINT(1) NOT NULL DEFAULT 1,
    SyncVersion INT NOT NULL DEFAULT 1,
    Nome VARCHAR(120) NOT NULL,
    CidadeOrigem VARCHAR(100) NOT NULL,
    UfOrigem CHAR(2) NOT NULL,
    CidadeDestino VARCHAR(100) NOT NULL,
    UfDestino CHAR(2) NOT NULL,
    DistanciaEstimadaKm DECIMAL(7,2) NOT NULL,
    PRIMARY KEY (Id),
    INDEX ix_rotas_ativo_nome (Ativo, Nome),
    CHECK (DistanciaEstimadaKm > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS viagens (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    Ativo TINYINT(1) NOT NULL DEFAULT 1,
    SyncVersion INT NOT NULL DEFAULT 1,
    VeiculoId CHAR(36) NOT NULL,
    RotaId CHAR(36) NOT NULL,
    MotoristaId CHAR(36) NOT NULL,
    HodometroInicial INT NOT NULL,
    HodometroFinal INT NULL,
    KmRodados INT NULL,
    DataInicio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DataFim DATETIME NULL,
    Status ENUM('EmAndamento','Concluida','Cancelada') NOT NULL DEFAULT 'EmAndamento',
    Observacao VARCHAR(400) NULL,
    PRIMARY KEY (Id),
    INDEX ix_viagens_status_inicio (Ativo, Status, DataInicio),
    INDEX ix_viagens_veiculo (VeiculoId, Ativo),
    INDEX ix_viagens_rota (RotaId, Ativo),
    INDEX ix_viagens_motorista (MotoristaId, Ativo),
    CONSTRAINT fk_viagens_veiculo FOREIGN KEY (VeiculoId) REFERENCES veiculos(Id),
    CONSTRAINT fk_viagens_rota FOREIGN KEY (RotaId) REFERENCES rotas(Id),
    CONSTRAINT fk_viagens_motorista FOREIGN KEY (MotoristaId) REFERENCES usuarios(Id),
    CHECK (HodometroInicial >= 0),
    CHECK (HodometroFinal IS NULL OR HodometroFinal >= HodometroInicial),
    CHECK (KmRodados IS NULL OR KmRodados >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS itens_checklist (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    Ativo TINYINT(1) NOT NULL DEFAULT 1,
    SyncVersion INT NOT NULL DEFAULT 1,
    Nome VARCHAR(120) NOT NULL,
    Obrigatorio TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (Id),
    INDEX ix_itens_checklist_ativo_nome (Ativo, Nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checklists_viagem (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    Ativo TINYINT(1) NOT NULL DEFAULT 1,
    SyncVersion INT NOT NULL DEFAULT 1,
    ViagemId CHAR(36) NOT NULL,
    ItemChecklistId CHAR(36) NOT NULL,
    Conforme TINYINT(1) NOT NULL,
    Observacao VARCHAR(200) NULL,
    FotoUrl VARCHAR(2000) NULL,
    PRIMARY KEY (Id),
    UNIQUE KEY uq_checklist_viagem_item (ViagemId, ItemChecklistId),
    INDEX ix_checklists_viagem_ativo (ViagemId, Ativo),
    CONSTRAINT fk_checklists_viagem_viagem FOREIGN KEY (ViagemId) REFERENCES viagens(Id),
    CONSTRAINT fk_checklists_viagem_item FOREIGN KEY (ItemChecklistId) REFERENCES itens_checklist(Id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$

DROP TRIGGER IF EXISTS bu_rotas$$
CREATE TRIGGER bu_rotas
BEFORE UPDATE ON rotas
FOR EACH ROW
BEGIN
    SET NEW.SyncVersion = OLD.SyncVersion + 1;
    SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$

DROP TRIGGER IF EXISTS bu_viagens$$
CREATE TRIGGER bu_viagens
BEFORE UPDATE ON viagens
FOR EACH ROW
BEGIN
    SET NEW.SyncVersion = OLD.SyncVersion + 1;
    SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$

DROP TRIGGER IF EXISTS bu_itens_checklist$$
CREATE TRIGGER bu_itens_checklist
BEFORE UPDATE ON itens_checklist
FOR EACH ROW
BEGIN
    SET NEW.SyncVersion = OLD.SyncVersion + 1;
    SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$

DROP TRIGGER IF EXISTS bu_checklists_viagem$$
CREATE TRIGGER bu_checklists_viagem
BEFORE UPDATE ON checklists_viagem
FOR EACH ROW
BEGIN
    SET NEW.SyncVersion = OLD.SyncVersion + 1;
    SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$

DELIMITER ;
