-- =====================================================================
-- FleetFlow - Script MySQL (convertido de PostgreSQL)
-- Requer MySQL 8.0.16+ (CHECK constraints e DEFAULT (UUID()))
-- =====================================================================

CREATE DATABASE IF NOT EXISTS fleetflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE fleetflow;

SET NAMES utf8mb4;

-- =====================================================================
-- TABELAS
-- (ENUMs do PostgreSQL viraram ENUM inline nas colunas)
-- =====================================================================

CREATE TABLE perfis (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Nome VARCHAR(50) NOT NULL,
    PRIMARY KEY (Id),
    UNIQUE KEY uq_perfis_nome (Nome)
) ENGINE=InnoDB;

CREATE TABLE usuarios (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Nome VARCHAR(120) NOT NULL,
    Email VARCHAR(180) NOT NULL,
    GoogleSubjectId VARCHAR(64) NOT NULL,
    PerfilId CHAR(36) NOT NULL,
    Status ENUM('AguardandoAprovacao','Ativo','Bloqueado') NOT NULL DEFAULT 'AguardandoAprovacao',
    FotoUrl TEXT NULL,
    CategoriaCnh VARCHAR(5) NULL,
    ValidadeCnh DATE NULL,
    PRIMARY KEY (Id),
    UNIQUE KEY uq_usuarios_email (Email),
    UNIQUE KEY uq_usuarios_google (GoogleSubjectId),
    CONSTRAINT fk_usuarios_perfil
        FOREIGN KEY (PerfilId) REFERENCES perfis (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_usuarios_categoria_cnh
        CHECK (CategoriaCnh IS NULL OR CategoriaCnh IN ('A','B','C','D','E','AB','AC','AD','AE'))
) ENGINE=InnoDB;

-- O índice único parcial do PostgreSQL (WHERE Ativo = TRUE) não existe no MySQL.
-- Solução: coluna gerada que vale NULL quando inativa (NULLs não colidem em UNIQUE).
CREATE TABLE categorias (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Titulo VARCHAR(60) NOT NULL,
    Tipo ENUM('Entrada','Saida') NOT NULL,
    EhSistema BOOLEAN NOT NULL DEFAULT FALSE,
    TituloAtivoUnico VARCHAR(60)
        GENERATED ALWAYS AS (CASE WHEN Ativo = 1 THEN LOWER(Titulo) ELSE NULL END) STORED,
    PRIMARY KEY (Id),
    UNIQUE KEY uq_categorias_titulo_ativas (TituloAtivoUnico)
) ENGINE=InnoDB;

CREATE TABLE formas_pagamento (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Nome VARCHAR(40) NOT NULL,
    EhSistema BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (Id),
    UNIQUE KEY uq_formas_pagamento_nome (Nome)
) ENGINE=InnoDB;

-- ck_veiculos_ano: o MySQL não aceita CURRENT_DATE em CHECK, então o limite
-- superior virou 2100 fixo.
CREATE TABLE veiculos (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Placa VARCHAR(8) NOT NULL,
    Marca VARCHAR(60) NOT NULL,
    Modelo VARCHAR(60) NOT NULL,
    AnoFabricacao INT NOT NULL,
    CapacidadeCargaToneladas DECIMAL(8,2) NOT NULL,
    Status ENUM('Disponivel','EmViagem','EmManutencao','Inativo') NOT NULL DEFAULT 'Disponivel',
    HodometroAtual INT NOT NULL DEFAULT 0,
    IntervaloManutencaoKm INT NULL,
    IntervaloManutencaoMeses INT NULL,
    HodometroUltimaManutencao INT NULL,
    VencimentoLicenciamento DATE NULL,
    VencimentoSeguro DATE NULL,
    ConsumoMedioReferenciaKmL DECIMAL(5,2) NULL,
    PRIMARY KEY (Id),
    UNIQUE KEY uq_veiculos_placa (Placa),
    CONSTRAINT ck_veiculos_placa
        CHECK (Placa REGEXP '^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$' OR Placa REGEXP '^[A-Z]{3}-?[0-9]{4}$'),
    CONSTRAINT ck_veiculos_ano CHECK (AnoFabricacao BETWEEN 1950 AND 2100),
    CONSTRAINT ck_veiculos_capacidade CHECK (CapacidadeCargaToneladas > 0),
    CONSTRAINT ck_veiculos_hodometro CHECK (HodometroAtual >= 0),
    CONSTRAINT ck_veiculos_intervalo_km CHECK (IntervaloManutencaoKm IS NULL OR IntervaloManutencaoKm > 0),
    CONSTRAINT ck_veiculos_intervalo_meses CHECK (IntervaloManutencaoMeses IS NULL OR IntervaloManutencaoMeses > 0),
    CONSTRAINT ck_veiculos_ultima_manutencao CHECK (HodometroUltimaManutencao IS NULL OR HodometroUltimaManutencao >= 0),
    CONSTRAINT ck_veiculos_consumo CHECK (ConsumoMedioReferenciaKmL IS NULL OR ConsumoMedioReferenciaKmL > 0)
) ENGINE=InnoDB;

CREATE TABLE rotas (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Nome VARCHAR(120) NOT NULL,
    CidadeOrigem VARCHAR(100) NOT NULL,
    UfOrigem VARCHAR(2) NOT NULL,
    CidadeDestino VARCHAR(100) NOT NULL,
    UfDestino VARCHAR(2) NOT NULL,
    DistanciaEstimadaKm DECIMAL(9,2) NOT NULL,
    PRIMARY KEY (Id),
    CONSTRAINT ck_rotas_uf_origem CHECK (CHAR_LENGTH(UfOrigem) = 2),
    CONSTRAINT ck_rotas_uf_destino CHECK (CHAR_LENGTH(UfDestino) = 2),
    CONSTRAINT ck_rotas_distancia CHECK (DistanciaEstimadaKm > 0)
) ENGINE=InnoDB;

CREATE TABLE dividas (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Descricao VARCHAR(200) NOT NULL,
    CategoriaId CHAR(36) NOT NULL,
    QuantidadeParcelas INT NOT NULL,
    ValorParcela DECIMAL(14,2) NOT NULL,
    DataVencimentoPrimeira DATE NOT NULL,
    ValorQuitacaoAntecipada DECIMAL(14,2) NULL,
    VeiculoId CHAR(36) NULL,
    PRIMARY KEY (Id),
    CONSTRAINT fk_dividas_categoria
        FOREIGN KEY (CategoriaId) REFERENCES categorias (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_dividas_veiculo
        FOREIGN KEY (VeiculoId) REFERENCES veiculos (Id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_dividas_quantidade CHECK (QuantidadeParcelas BETWEEN 1 AND 480),
    CONSTRAINT ck_dividas_valor CHECK (ValorParcela > 0),
    CONSTRAINT ck_dividas_quitacao CHECK (ValorQuitacaoAntecipada IS NULL OR ValorQuitacaoAntecipada > 0)
) ENGINE=InnoDB;

CREATE TABLE viagens (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
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
    CONSTRAINT fk_viagens_veiculo
        FOREIGN KEY (VeiculoId) REFERENCES veiculos (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_viagens_rota
        FOREIGN KEY (RotaId) REFERENCES rotas (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_viagens_motorista
        FOREIGN KEY (MotoristaId) REFERENCES usuarios (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_viagens_hodometro_inicial CHECK (HodometroInicial >= 0),
    CONSTRAINT ck_viagens_hodometro_final CHECK (HodometroFinal IS NULL OR HodometroFinal >= HodometroInicial),
    CONSTRAINT ck_viagens_km CHECK (KmRodados IS NULL OR KmRodados >= 0),
    CONSTRAINT ck_viagens_data CHECK (DataFim IS NULL OR DataFim >= DataInicio)
) ENGINE=InnoDB;

CREATE TABLE movimentacoes (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Valor DECIMAL(14,2) NOT NULL,
    Descricao VARCHAR(200) NOT NULL,
    CategoriaId CHAR(36) NOT NULL,
    FormaPagamentoId CHAR(36) NOT NULL,
    ViagemId CHAR(36) NULL,
    DividaId CHAR(36) NULL,
    VeiculoId CHAR(36) NULL,
    NumeroParcela INT NULL,
    DataVencimento DATE NULL,
    DataPagamento DATE NULL,
    Status ENUM('Pendente','Pago') NOT NULL DEFAULT 'Pendente',
    LitrosCombustivel DECIMAL(8,2) NULL,
    CriadoPorUsuarioId CHAR(36) NULL,
    PRIMARY KEY (Id),
    CONSTRAINT fk_movimentacoes_categoria
        FOREIGN KEY (CategoriaId) REFERENCES categorias (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_movimentacoes_forma_pagamento
        FOREIGN KEY (FormaPagamentoId) REFERENCES formas_pagamento (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_movimentacoes_viagem
        FOREIGN KEY (ViagemId) REFERENCES viagens (Id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_movimentacoes_divida
        FOREIGN KEY (DividaId) REFERENCES dividas (Id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_movimentacoes_veiculo
        FOREIGN KEY (VeiculoId) REFERENCES veiculos (Id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_movimentacoes_usuario
        FOREIGN KEY (CriadoPorUsuarioId) REFERENCES usuarios (Id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_movimentacoes_valor CHECK (Valor > 0),
    CONSTRAINT ck_movimentacoes_parcela CHECK (NumeroParcela IS NULL OR NumeroParcela > 0),
    CONSTRAINT ck_movimentacoes_litros CHECK (LitrosCombustivel IS NULL OR LitrosCombustivel > 0),
    CONSTRAINT ck_movimentacoes_pago CHECK (Status <> 'Pago' OR DataPagamento IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE ordens_servico (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    VeiculoId CHAR(36) NOT NULL,
    DataManutencao DATE NOT NULL,
    Descricao VARCHAR(200) NOT NULL,
    Tipo ENUM('Preventiva','Corretiva') NOT NULL,
    ValorTotal DECIMAL(14,2) NOT NULL,
    HodometroNaManutencao INT NULL,
    DataConclusao DATE NULL,
    MovimentacaoId CHAR(36) NOT NULL,
    PRIMARY KEY (Id),
    CONSTRAINT fk_os_veiculo
        FOREIGN KEY (VeiculoId) REFERENCES veiculos (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_os_movimentacao
        FOREIGN KEY (MovimentacaoId) REFERENCES movimentacoes (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_os_valor CHECK (ValorTotal > 0),
    CONSTRAINT ck_os_hodometro CHECK (HodometroNaManutencao IS NULL OR HodometroNaManutencao >= 0),
    CONSTRAINT ck_os_data_conclusao CHECK (DataConclusao IS NULL OR DataConclusao >= DataManutencao)
) ENGINE=InnoDB;

CREATE TABLE anexos (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    MovimentacaoId CHAR(36) NOT NULL,
    CaminhoLocal TEXT NOT NULL,
    UrlRemota TEXT NULL,
    Tamanho BIGINT NULL,
    MimeType VARCHAR(100) NULL,
    PRIMARY KEY (Id),
    CONSTRAINT fk_anexos_movimentacao
        FOREIGN KEY (MovimentacaoId) REFERENCES movimentacoes (Id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_anexos_tamanho CHECK (Tamanho IS NULL OR Tamanho > 0),
    CONSTRAINT ck_anexos_limite CHECK (Tamanho IS NULL OR Tamanho <= 5242880)
) ENGINE=InnoDB;

CREATE TABLE abastecimentos (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    VeiculoId CHAR(36) NOT NULL,
    ViagemId CHAR(36) NULL,
    Litros DECIMAL(8,2) NOT NULL,
    ValorTotal DECIMAL(14,2) NOT NULL,
    PrecoLitro DECIMAL(6,3) NOT NULL,
    Posto VARCHAR(120) NULL,
    Hodometro INT NOT NULL,
    MovimentacaoId CHAR(36) NOT NULL,
    PRIMARY KEY (Id),
    CONSTRAINT fk_abastecimentos_veiculo
        FOREIGN KEY (VeiculoId) REFERENCES veiculos (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_abastecimentos_viagem
        FOREIGN KEY (ViagemId) REFERENCES viagens (Id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_abastecimentos_movimentacao
        FOREIGN KEY (MovimentacaoId) REFERENCES movimentacoes (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_abastecimentos_litros CHECK (Litros > 0),
    CONSTRAINT ck_abastecimentos_valor CHECK (ValorTotal > 0),
    CONSTRAINT ck_abastecimentos_preco CHECK (PrecoLitro > 0),
    CONSTRAINT ck_abastecimentos_hodometro CHECK (Hodometro >= 0)
) ENGINE=InnoDB;

CREATE TABLE itens_checklist (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    Nome VARCHAR(120) NOT NULL,
    Obrigatorio BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (Id)
) ENGINE=InnoDB;

CREATE TABLE checklists_viagem (
    Id CHAR(36) NOT NULL DEFAULT (UUID()),
    CriadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    AtualizadoEm DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Ativo BOOLEAN NOT NULL DEFAULT TRUE,
    SyncVersion BIGINT NOT NULL DEFAULT 1,
    ViagemId CHAR(36) NOT NULL,
    ItemChecklistId CHAR(36) NOT NULL,
    Conforme BOOLEAN NOT NULL,
    Observacao VARCHAR(200) NULL,
    FotoUrl TEXT NULL,
    PRIMARY KEY (Id),
    CONSTRAINT fk_checklist_viagem_viagem
        FOREIGN KEY (ViagemId) REFERENCES viagens (Id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_checklist_viagem_item
        FOREIGN KEY (ItemChecklistId) REFERENCES itens_checklist (Id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_checklist_viagem_item UNIQUE (ViagemId, ItemChecklistId),
    CONSTRAINT ck_checklist_observacao
        CHECK (Conforme = TRUE OR NULLIF(TRIM(Observacao), '') IS NOT NULL)
) ENGINE=InnoDB;

-- =====================================================================
-- ÍNDICES
-- (índices parciais "WHERE ..." não existem no MySQL; viraram índices comuns)
-- =====================================================================

CREATE INDEX idx_usuarios_perfil ON usuarios (PerfilId);
CREATE INDEX idx_usuarios_status ON usuarios (Status);
CREATE INDEX idx_usuarios_cnh ON usuarios (ValidadeCnh);

CREATE INDEX idx_veiculos_status ON veiculos (Status);
CREATE INDEX idx_veiculos_licenciamento ON veiculos (VencimentoLicenciamento);
CREATE INDEX idx_veiculos_seguro ON veiculos (VencimentoSeguro);

CREATE INDEX idx_dividas_categoria ON dividas (CategoriaId);
CREATE INDEX idx_dividas_veiculo ON dividas (VeiculoId);

CREATE INDEX idx_movimentacoes_status_vencimento ON movimentacoes (Status, DataVencimento);
CREATE INDEX idx_movimentacoes_viagem ON movimentacoes (ViagemId);
CREATE INDEX idx_movimentacoes_categoria_pagamento ON movimentacoes (CategoriaId, DataPagamento);
CREATE INDEX idx_movimentacoes_divida ON movimentacoes (DividaId);
CREATE INDEX idx_movimentacoes_veiculo ON movimentacoes (VeiculoId);
CREATE INDEX idx_movimentacoes_criado_por ON movimentacoes (CriadoPorUsuarioId);

CREATE INDEX idx_viagens_veiculo ON viagens (VeiculoId);
CREATE INDEX idx_viagens_rota ON viagens (RotaId);
CREATE INDEX idx_viagens_motorista ON viagens (MotoristaId);
CREATE INDEX idx_viagens_status ON viagens (Status);
CREATE INDEX idx_viagens_data_inicio ON viagens (DataInicio);

CREATE INDEX idx_os_veiculo ON ordens_servico (VeiculoId);
CREATE INDEX idx_os_data ON ordens_servico (DataManutencao);
CREATE INDEX idx_os_tipo ON ordens_servico (Tipo);

CREATE INDEX idx_abastecimentos_veiculo ON abastecimentos (VeiculoId);
CREATE INDEX idx_abastecimentos_viagem ON abastecimentos (ViagemId);
CREATE INDEX idx_abastecimentos_hodometro ON abastecimentos (VeiculoId, Hodometro);

CREATE INDEX idx_anexos_movimentacao ON anexos (MovimentacaoId);

CREATE INDEX idx_checklists_viagem ON checklists_viagem (ViagemId);
CREATE INDEX idx_checklists_item ON checklists_viagem (ItemChecklistId);

-- =====================================================================
-- TRIGGERS (atualizam AtualizadoEm e SyncVersion a cada UPDATE)
-- No MySQL não existe função de trigger compartilhada: um trigger por tabela.
-- =====================================================================

DELIMITER $$

CREATE TRIGGER trg_perfis_controle BEFORE UPDATE ON perfis
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_usuarios_controle BEFORE UPDATE ON usuarios
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_categorias_controle BEFORE UPDATE ON categorias
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_formas_pagamento_controle BEFORE UPDATE ON formas_pagamento
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_veiculos_controle BEFORE UPDATE ON veiculos
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_rotas_controle BEFORE UPDATE ON rotas
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_dividas_controle BEFORE UPDATE ON dividas
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_movimentacoes_controle BEFORE UPDATE ON movimentacoes
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_viagens_controle BEFORE UPDATE ON viagens
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_ordens_servico_controle BEFORE UPDATE ON ordens_servico
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_anexos_controle BEFORE UPDATE ON anexos
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_abastecimentos_controle BEFORE UPDATE ON abastecimentos
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_itens_checklist_controle BEFORE UPDATE ON itens_checklist
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

CREATE TRIGGER trg_checklists_viagem_controle BEFORE UPDATE ON checklists_viagem
FOR EACH ROW
BEGIN
    SET NEW.AtualizadoEm = CURRENT_TIMESTAMP;
    SET NEW.SyncVersion = COALESCE(OLD.SyncVersion, 0) + 1;
END$$

DELIMITER ;

-- =====================================================================
-- DADOS INICIAIS (ON CONFLICT DO NOTHING -> INSERT IGNORE)
-- =====================================================================

INSERT IGNORE INTO perfis (Nome) VALUES
    ('Administrador'),
    ('Financeiro'),
    ('GestorFrota'),
    ('OperadorMotorista');

INSERT IGNORE INTO categorias (Titulo, Tipo, EhSistema) VALUES
    ('Frete', 'Entrada', TRUE),
    ('Combustível', 'Saida', TRUE),
    ('Pedágio', 'Saida', TRUE),
    ('Manutenção', 'Saida', TRUE),
    ('Salário', 'Saida', TRUE),
    ('Financiamento', 'Saida', TRUE),
    ('Seguro', 'Saida', TRUE),
    ('Impostos', 'Saida', TRUE),
    ('Outros', 'Saida', TRUE);

INSERT IGNORE INTO formas_pagamento (Nome, EhSistema) VALUES
    ('Boleto', TRUE),
    ('Pix', TRUE),
    ('Transferência TED', TRUE),
    ('Cartão Corporativo', TRUE),
    ('Dinheiro', TRUE);

-- Usuários de teste: um por perfil, já ativos.
-- ATENÇÃO: troque os e-mails pelos Gmails reais usados no teste de login Google.
-- GoogleSubjectId é provisório ('seed-<email>'); o app deve substituí-lo pelo ID
-- real do Google no primeiro login, localizando o usuário pelo Email.
-- A conta de motorista recebe CNH válida para liberar o fluxo de viagem.
INSERT IGNORE INTO usuarios
    (Nome, Email, GoogleSubjectId, PerfilId, Status, CategoriaCnh, ValidadeCnh)
SELECT t.Nome, t.Email, CONCAT('seed-', t.Email), p.Id, 'Ativo', t.CategoriaCnh, t.ValidadeCnh
FROM (
    SELECT 'Teste Administrador' AS Nome, 'teste.admin@exemplo.com' AS Email,
           'Administrador' AS Perfil, NULL AS CategoriaCnh, NULL AS ValidadeCnh
    UNION ALL
    SELECT 'Teste Financeiro', 'teste.financeiro@exemplo.com',
           'Financeiro', NULL, NULL
    UNION ALL
    SELECT 'Teste Gestor de Frota', 'teste.gestor@exemplo.com',
           'GestorFrota', NULL, NULL
    UNION ALL
    SELECT 'Teste Motorista', 'teste.motorista@exemplo.com',
           'OperadorMotorista', 'D', '2030-12-31'
) t
JOIN perfis p ON p.Nome = t.Perfil;

-- itens_checklist não tem UNIQUE em Nome: se rodar o script de novo, duplica.
INSERT INTO itens_checklist (Nome, Obrigatorio) VALUES
    ('Pneus', TRUE),
    ('Luzes', TRUE),
    ('Freios', TRUE),
    ('Óleo', TRUE),
    ('Documentos', TRUE),
    ('Extintor', TRUE),
    ('Carga amarrada', TRUE);

-- =====================================================================
-- VIEWS
-- =====================================================================

CREATE OR REPLACE VIEW vw_resumo_financeiro AS
SELECT
    c.Titulo AS Categoria,
    c.Tipo,
    COUNT(m.Id) AS Quantidade,
    COALESCE(SUM(m.Valor), 0) AS ValorTotal
FROM categorias c
LEFT JOIN movimentacoes m
    ON m.CategoriaId = c.Id
    AND m.Ativo = TRUE
WHERE c.Ativo = TRUE
GROUP BY c.Id, c.Titulo, c.Tipo
ORDER BY c.Tipo, c.Titulo;

CREATE OR REPLACE VIEW vw_status_frota AS
SELECT
    v.Id,
    v.Placa,
    v.Marca,
    v.Modelo,
    v.AnoFabricacao,
    v.Status,
    v.HodometroAtual,
    v.HodometroUltimaManutencao,
    v.IntervaloManutencaoKm,
    CASE
        WHEN v.IntervaloManutencaoKm IS NOT NULL
         AND v.HodometroUltimaManutencao IS NOT NULL
        THEN v.HodometroAtual >= v.HodometroUltimaManutencao + v.IntervaloManutencaoKm
        ELSE FALSE
    END AS ManutencaoKmVencida,
    v.VencimentoLicenciamento,
    v.VencimentoSeguro
FROM veiculos v
WHERE v.Ativo = TRUE;

CREATE OR REPLACE VIEW vw_viagens_detalhadas AS
SELECT
    vi.Id AS ViagemId,
    v.Placa,
    v.Marca,
    v.Modelo,
    r.Nome AS Rota,
    r.CidadeOrigem,
    r.UfOrigem,
    r.CidadeDestino,
    r.UfDestino,
    u.Nome AS Motorista,
    vi.HodometroInicial,
    vi.HodometroFinal,
    vi.KmRodados,
    vi.DataInicio,
    vi.DataFim,
    vi.Status,
    vi.Observacao
FROM viagens vi
INNER JOIN veiculos v ON v.Id = vi.VeiculoId
INNER JOIN rotas r ON r.Id = vi.RotaId
INNER JOIN usuarios u ON u.Id = vi.MotoristaId;

CREATE OR REPLACE VIEW vw_historico_manutencao AS
SELECT
    os.Id,
    v.Placa,
    v.Marca,
    v.Modelo,
    os.DataManutencao,
    os.Descricao,
    os.Tipo,
    os.ValorTotal,
    os.HodometroNaManutencao,
    os.DataConclusao,
    CASE
        WHEN os.DataConclusao IS NULL THEN 'Em andamento'
        ELSE 'Concluída'
    END AS Situacao
FROM ordens_servico os
INNER JOIN veiculos v ON v.Id = os.VeiculoId
WHERE os.Ativo = TRUE;

CREATE OR REPLACE VIEW vw_abastecimentos AS
SELECT
    a.Id,
    v.Placa,
    v.Marca,
    v.Modelo,
    a.Litros,
    a.ValorTotal,
    a.PrecoLitro,
    a.Posto,
    a.Hodometro,
    a.CriadoEm,
    vi.Id AS ViagemId,
    vi.KmRodados
FROM abastecimentos a
INNER JOIN veiculos v ON v.Id = a.VeiculoId
LEFT JOIN viagens vi ON vi.Id = a.ViagemId
WHERE a.Ativo = TRUE;

CREATE OR REPLACE VIEW vw_alertas_documentacao AS
SELECT
    v.Id,
    v.Placa,
    v.Marca,
    v.Modelo,
    v.VencimentoLicenciamento,
    v.VencimentoSeguro,
    CASE
        WHEN v.VencimentoLicenciamento IS NOT NULL
         AND v.VencimentoLicenciamento <= CURRENT_DATE + INTERVAL 30 DAY
        THEN TRUE ELSE FALSE
    END AS LicenciamentoAlerta,
    CASE
        WHEN v.VencimentoSeguro IS NOT NULL
         AND v.VencimentoSeguro <= CURRENT_DATE + INTERVAL 30 DAY
        THEN TRUE ELSE FALSE
    END AS SeguroAlerta
FROM veiculos v
WHERE v.Ativo = TRUE;

CREATE OR REPLACE VIEW vw_alertas_cnh AS
SELECT
    u.Id,
    u.Nome,
    u.Email,
    u.CategoriaCnh,
    u.ValidadeCnh,
    CASE
        WHEN u.ValidadeCnh IS NOT NULL
         AND u.ValidadeCnh <= CURRENT_DATE + INTERVAL 30 DAY
        THEN TRUE ELSE FALSE
    END AS AlertaVencimento
FROM usuarios u
WHERE u.Ativo = TRUE
  AND u.CategoriaCnh IS NOT NULL;

CREATE OR REPLACE VIEW vw_contas_pagar AS
SELECT
    m.Id,
    m.Descricao,
    c.Titulo AS Categoria,
    fp.Nome AS FormaPagamento,
    m.Valor,
    m.DataVencimento,
    m.DataPagamento,
    m.Status,
    v.Placa
FROM movimentacoes m
INNER JOIN categorias c ON c.Id = m.CategoriaId
INNER JOIN formas_pagamento fp ON fp.Id = m.FormaPagamentoId
LEFT JOIN veiculos v ON v.Id = m.VeiculoId
WHERE m.Ativo = TRUE
  AND c.Tipo = 'Saida';

-- =====================================================================
-- PROCEDURE E FUNÇÕES
-- =====================================================================

DELIMITER $$

-- fn_soft_delete virou PROCEDURE (SQL dinâmico não é permitido em FUNCTION).
-- Uso: CALL sp_soft_delete('veiculos', 'uuid-aqui');
-- AtualizadoEm e SyncVersion são atualizados pelo trigger BEFORE UPDATE.
CREATE PROCEDURE sp_soft_delete(IN p_tabela VARCHAR(64), IN p_id CHAR(36))
BEGIN
    SET @sql_soft_delete = CONCAT(
        'UPDATE `', REPLACE(p_tabela, '`', ''), '` SET Ativo = FALSE WHERE Id = ?'
    );
    SET @id_soft_delete = p_id;
    PREPARE stmt FROM @sql_soft_delete;
    EXECUTE stmt USING @id_soft_delete;
    DEALLOCATE PREPARE stmt;
END$$

CREATE FUNCTION fn_calcular_valor_divida(
    p_quantidade INT,
    p_valor_parcela DECIMAL(14,2)
)
RETURNS DECIMAL(14,2)
DETERMINISTIC
BEGIN
    RETURN p_quantidade * p_valor_parcela;
END$$

CREATE FUNCTION fn_calcular_consumo_kml(
    p_km DECIMAL(12,2),
    p_litros DECIMAL(12,2)
)
RETURNS DECIMAL(10,2)
DETERMINISTIC
BEGIN
    RETURN CASE
        WHEN p_litros > 0 THEN ROUND(p_km / p_litros, 2)
        ELSE NULL
    END;
END$$

DELIMITER ;
