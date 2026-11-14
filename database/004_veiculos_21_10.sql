-- Migração para instalações que já executaram 001_schema.sql antes da entrega de 21/10.
-- Execute uma vez, com o banco fleetflow selecionado.
CREATE TABLE IF NOT EXISTS veiculos (
 Id CHAR(36) PRIMARY KEY DEFAULT (UUID()), CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Placa VARCHAR(7) NOT NULL, Marca VARCHAR(60) NOT NULL,
 Modelo VARCHAR(60) NOT NULL, AnoFabricacao INT NOT NULL, CapacidadeCargaToneladas DECIMAL(6,2) NOT NULL,
 Status ENUM('Disponivel','EmViagem','EmManutencao','Inativo') NOT NULL DEFAULT 'Disponivel',
 HodometroAtual INT NOT NULL DEFAULT 0, IntervaloManutencaoKm INT NULL, IntervaloManutencaoMeses INT NULL,
 HodometroUltimaManutencao INT NULL, VencimentoLicenciamento DATE NULL, VencimentoSeguro DATE NULL,
 ConsumoMedioReferenciaKmL DECIMAL(3,2) NULL, CONSTRAINT uq_veiculos_placa UNIQUE(Placa),
 CHECK(AnoFabricacao BETWEEN 1950 AND 2100), CHECK(CapacidadeCargaToneladas > 0), CHECK(HodometroAtual >= 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS ordens_servico (
 Id CHAR(36) PRIMARY KEY DEFAULT (UUID()), CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, VeiculoId CHAR(36) NOT NULL, DataManutencao DATE NOT NULL,
 Descricao VARCHAR(200) NOT NULL, Tipo ENUM('Preventiva','Corretiva') NOT NULL, ValorTotal DECIMAL(14,2) NOT NULL,
 HodometroNaManutencao INT NULL, DataConclusao DATE NULL, MovimentacaoId CHAR(36) NOT NULL,
 FOREIGN KEY(VeiculoId) REFERENCES veiculos(Id), FOREIGN KEY(MovimentacaoId) REFERENCES movimentacoes(Id),
 CHECK(ValorTotal > 0), CHECK(DataConclusao IS NULL OR DataConclusao >= DataManutencao),
 INDEX ix_ordem_veiculo_aberta(VeiculoId,Ativo,DataConclusao)
) ENGINE=InnoDB;
DELIMITER $$
DROP TRIGGER IF EXISTS bu_veiculos$$
CREATE TRIGGER bu_veiculos BEFORE UPDATE ON veiculos FOR EACH ROW
BEGIN SET NEW.SyncVersion = OLD.SyncVersion + 1; SET NEW.AtualizadoEm = UTC_TIMESTAMP(3); END$$
DROP TRIGGER IF EXISTS bu_ordens_servico$$
CREATE TRIGGER bu_ordens_servico BEFORE UPDATE ON ordens_servico FOR EACH ROW
BEGIN SET NEW.SyncVersion = OLD.SyncVersion + 1; SET NEW.AtualizadoEm = UTC_TIMESTAMP(3); END$$
DELIMITER ;
