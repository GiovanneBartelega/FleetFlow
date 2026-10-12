-- Inicialização para MySQL 8.0.16+ ou 8.4. Não remove dados existentes.
CREATE DATABASE IF NOT EXISTS fleetflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fleetflow;
SET time_zone = '+00:00';
CREATE TABLE IF NOT EXISTS perfis (
Id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
 CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Nome VARCHAR(40) NOT NULL, CONSTRAINT uq_perfis_nome UNIQUE(Nome)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS usuarios (
Id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
 CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Nome VARCHAR(120) NOT NULL, Email VARCHAR(180) NOT NULL,
 GoogleSubjectId VARCHAR(255) NOT NULL, PerfilId CHAR(36) NOT NULL,
 Status ENUM('Ativo','AguardandoAprovacao','Bloqueado') NOT NULL DEFAULT 'AguardandoAprovacao',
 FotoUrl VARCHAR(2048), CategoriaCnh VARCHAR(2), ValidadeCnh DATE,
 CONSTRAINT uq_usuarios_email UNIQUE(Email), CONSTRAINT uq_usuarios_google UNIQUE(GoogleSubjectId),
 FOREIGN KEY(PerfilId) REFERENCES perfis(Id), INDEX ix_usuario_perfil_status(PerfilId,Status,Ativo)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS categorias (
Id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
 CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Titulo VARCHAR(60) NOT NULL, Tipo ENUM('Entrada','Saida') NOT NULL, EhSistema BOOLEAN NOT NULL DEFAULT FALSE,
 TituloAtivo VARCHAR(60) GENERATED ALWAYS AS (CASE WHEN Ativo=1 THEN Titulo ELSE NULL END) STORED,
 CONSTRAINT uq_categorias_titulo_ativas UNIQUE(TituloAtivo)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS formas_pagamento (
Id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
 CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Nome VARCHAR(40) NOT NULL, EhSistema BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT uq_formas_pagamento_nome UNIQUE(Nome)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dividas (
Id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
 CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Descricao VARCHAR(200) NOT NULL, CategoriaId CHAR(36) NOT NULL,
 QuantidadeParcelas INT NOT NULL, ValorParcela DECIMAL(14,2) NOT NULL,
 DataVencimentoPrimeira DATE NOT NULL, ValorQuitacaoAntecipada DECIMAL(14,2), VeiculoId CHAR(36),
 FOREIGN KEY(CategoriaId) REFERENCES categorias(Id),
 CHECK(QuantidadeParcelas BETWEEN 1 AND 480), CHECK(ValorParcela>0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS movimentacoes (
Id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
 CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 AtualizadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 Ativo BOOLEAN NOT NULL DEFAULT TRUE,
 SyncVersion INT NOT NULL DEFAULT 1, Valor DECIMAL(14,2) NOT NULL, Descricao VARCHAR(200) NOT NULL,
 CategoriaId CHAR(36) NOT NULL, FormaPagamentoId CHAR(36) NOT NULL,
 ViagemId CHAR(36), DividaId CHAR(36), VeiculoId CHAR(36), NumeroParcela INT,
 DataVencimento DATE, DataPagamento DATE, Status ENUM('Pendente','Pago') NOT NULL DEFAULT 'Pendente',
 LitrosCombustivel DECIMAL(14,2), CriadoPorUsuarioId CHAR(36) NOT NULL,
 FOREIGN KEY(CategoriaId) REFERENCES categorias(Id), FOREIGN KEY(FormaPagamentoId) REFERENCES formas_pagamento(Id),
 FOREIGN KEY(DividaId) REFERENCES dividas(Id), FOREIGN KEY(CriadoPorUsuarioId) REFERENCES usuarios(Id),
 CHECK(Valor>0), CONSTRAINT ck_movimentacoes_pago CHECK((Status='Pago' AND DataPagamento IS NOT NULL) OR (Status='Pendente' AND DataPagamento IS NULL)),
 INDEX ix_mov_status_vencimento(Ativo,Status,DataVencimento), INDEX ix_mov_pagamento(Ativo,Status,DataPagamento),
 INDEX ix_mov_categoria(CategoriaId,Ativo), INDEX ix_mov_divida(DividaId,NumeroParcela),
 CONSTRAINT uq_parcela UNIQUE(DividaId,NumeroParcela)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS comprovantes (
MovimentacaoId CHAR(36) PRIMARY KEY, MimeType VARCHAR(40) NOT NULL,
 Conteudo MEDIUMBLOB NOT NULL, CriadoEm DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(MovimentacaoId) REFERENCES movimentacoes(Id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS refresh_tokens (
TokenHash CHAR(64) PRIMARY KEY, UsuarioId CHAR(36) NOT NULL,
 ExpiraEm DATETIME(3) NOT NULL, Revogado BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(UsuarioId) REFERENCES usuarios(Id), INDEX ix_refresh_usuario(UsuarioId)
) ENGINE=InnoDB;

DELIMITER $$
DROP TRIGGER IF EXISTS bu_perfis$$
CREATE TRIGGER bu_perfis BEFORE UPDATE ON perfis FOR EACH ROW
BEGIN
 SET NEW.SyncVersion = OLD.SyncVersion + 1;
 SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DROP TRIGGER IF EXISTS bu_usuarios$$
CREATE TRIGGER bu_usuarios BEFORE UPDATE ON usuarios FOR EACH ROW
BEGIN
 SET NEW.SyncVersion = OLD.SyncVersion + 1;
 SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DROP TRIGGER IF EXISTS bu_categorias$$
CREATE TRIGGER bu_categorias BEFORE UPDATE ON categorias FOR EACH ROW
BEGIN
 SET NEW.SyncVersion = OLD.SyncVersion + 1;
 SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DROP TRIGGER IF EXISTS bu_formas_pagamento$$
CREATE TRIGGER bu_formas_pagamento BEFORE UPDATE ON formas_pagamento FOR EACH ROW
BEGIN
 SET NEW.SyncVersion = OLD.SyncVersion + 1;
 SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DROP TRIGGER IF EXISTS bu_dividas$$
CREATE TRIGGER bu_dividas BEFORE UPDATE ON dividas FOR EACH ROW
BEGIN
 SET NEW.SyncVersion = OLD.SyncVersion + 1;
 SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DROP TRIGGER IF EXISTS bu_movimentacoes$$
CREATE TRIGGER bu_movimentacoes BEFORE UPDATE ON movimentacoes FOR EACH ROW
BEGIN
 SET NEW.SyncVersion = OLD.SyncVersion + 1;
 SET NEW.AtualizadoEm = UTC_TIMESTAMP(3);
END$$
DELIMITER ;
INSERT INTO perfis (Nome) VALUES ('Administrador') ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO perfis (Nome) VALUES ('Financeiro') ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO perfis (Nome) VALUES ('GestorFrota') ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO perfis (Nome) VALUES ('OperadorMotorista') ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Frete','Entrada',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Outras receitas','Entrada',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Combustível','Saida',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Pedágio','Saida',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Manutenção','Saida',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Salário','Saida',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Financiamento','Saida',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO categorias (Titulo,Tipo,EhSistema) VALUES ('Outros','Saida',1) ON DUPLICATE KEY UPDATE Titulo=VALUES(Titulo);
INSERT INTO formas_pagamento (Nome,EhSistema) VALUES ('Pix',1) ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO formas_pagamento (Nome,EhSistema) VALUES ('Boleto',1) ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO formas_pagamento (Nome,EhSistema) VALUES ('Transferência TED',1) ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO formas_pagamento (Nome,EhSistema) VALUES ('Cartão Corporativo',1) ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
INSERT INTO formas_pagamento (Nome,EhSistema) VALUES ('Dinheiro',1) ON DUPLICATE KEY UPDATE Nome=VALUES(Nome);
