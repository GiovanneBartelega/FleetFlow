-- Entrega de 18/11 (banco): massa sintética de 12 meses, no schema integrado.
--
-- Execute depois de 001 e 004 a 008, com o banco fleetflow selecionado (a 002 e
-- a 003 são opcionais e independentes desta). Use em desenvolvimento ou
-- homologação: NÃO execute em produção. Leva cerca de 10 segundos.
--
-- O que carrega (padrão: 80 veículos, janela de 01/11/2025 a 31/10/2026):
--   usuarios (motoristas) · veiculos · rotas · viagens (encerradas + algumas
--   abertas) · movimentacoes (frete, pedágio, combustível, seguro, impostos,
--   manutenção, parcelas de financiamento, salários, avulsas) · abastecimentos ·
--   ordens_servico · dividas.
-- Usa somente colunas que existem nas migrations 001 a 007. Nada de KmRodados,
-- DataFim, 'Concluida': o km vem de HodometroFinal - HodometroInicial e a viagem
-- fechada é Status = 'Encerrada' com DataFechamento.
--
-- Determinística: mesma entrada, mesma massa (sem RAND). Todo registro criado
-- tem Id começando por 'f1ee7f10-', o que permite remover tudo depois com
-- database/rollback/009_remover_massa_12_meses.sql.
-- Reexecutar não duplica: se a massa já existe, não insere nada.
--
-- Ajuste os parâmetros abaixo se precisar (todos são opcionais).
-- Dados fictícios: nada daqui representa a operação real.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';
SET SESSION cte_max_recursion_depth = 100000;

SET @massa_inicio     = DATE('2025-11-01');  -- primeiro dia da janela (use dia 1)
SET @massa_fim        = DATE('2026-10-31');  -- último dia em que viagens começam
SET @massa_hoje       = DATE('2026-11-17');  -- o que vence depois disso fica Pendente
SET @massa_veiculos   = 80;                  -- 1 a 9999
SET @massa_motoristas = @massa_veiculos + @massa_veiculos DIV 8;  -- 1 por veículo + reservas
SET @massa_rotas      = 40;                  -- 1 a 48

-- ---------------------------------------------------------------------------
-- Referências já existentes (criadas pela 001).
-- ---------------------------------------------------------------------------
SET @perfil_motorista = (SELECT Id FROM perfis WHERE Nome = 'OperadorMotorista' LIMIT 1);
SET @perfil_financeiro = (SELECT Id FROM perfis WHERE Nome = 'Financeiro' LIMIT 1);
SET @cat_frete        = (SELECT Id FROM categorias WHERE Titulo = 'Frete' AND Ativo = 1 LIMIT 1);
SET @cat_outras_rec   = (SELECT Id FROM categorias WHERE Titulo = 'Outras receitas' AND Ativo = 1 LIMIT 1);
SET @cat_combustivel  = (SELECT Id FROM categorias WHERE Titulo = 'Combustível' AND Ativo = 1 LIMIT 1);
SET @cat_pedagio      = (SELECT Id FROM categorias WHERE Titulo = 'Pedágio' AND Ativo = 1 LIMIT 1);
SET @cat_manutencao   = (SELECT Id FROM categorias WHERE Titulo = 'Manutenção' AND Ativo = 1 LIMIT 1);
SET @cat_salario      = (SELECT Id FROM categorias WHERE Titulo = 'Salário' AND Ativo = 1 LIMIT 1);
SET @cat_financiamento = (SELECT Id FROM categorias WHERE Titulo = 'Financiamento' AND Ativo = 1 LIMIT 1);
SET @cat_outros       = (SELECT Id FROM categorias WHERE Titulo = 'Outros' AND Ativo = 1 LIMIT 1);
SET @fp_pix           = (SELECT Id FROM formas_pagamento WHERE Nome = 'Pix' LIMIT 1);
SET @fp_boleto        = (SELECT Id FROM formas_pagamento WHERE Nome = 'Boleto' LIMIT 1);
SET @fp_ted           = (SELECT Id FROM formas_pagamento WHERE Nome = 'Transferência TED' LIMIT 1);
SET @fp_cartao        = (SELECT Id FROM formas_pagamento WHERE Nome = 'Cartão Corporativo' LIMIT 1);
SET @fp_dinheiro      = (SELECT Id FROM formas_pagamento WHERE Nome = 'Dinheiro' LIMIT 1);

-- Id do usuário que "criou" os lançamentos (bloqueado: não consegue entrar).
SET @massa_criador = CONCAT('f1ee7f10-', SUBSTR(MD5('usr|0'), 1, 4), '-4', SUBSTR(MD5('usr|0'), 5, 3),
                            '-8', SUBSTR(MD5('usr|0'), 8, 3), '-', SUBSTR(MD5('usr|0'), 11, 12));
SET @massa_ja_existe = (SELECT COUNT(*) FROM usuarios WHERE Id = @massa_criador);

-- ---------------------------------------------------------------------------
-- Tabelas de trabalho (tmp_massa_*, apagadas no fim).
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_massa_mov;
DROP TABLE IF EXISTS tmp_massa_abast;
DROP TABLE IF EXISTS tmp_massa_os;
DROP TABLE IF EXISTS tmp_massa_divida;
DROP TABLE IF EXISTS tmp_massa_viagem;
DROP TABLE IF EXISTS tmp_massa_usuario;
DROP TABLE IF EXISTS tmp_massa_rota;
DROP TABLE IF EXISTS tmp_massa_veic;
DROP TABLE IF EXISTS tmp_massa_mes;
DROP TABLE IF EXISTS tmp_massa_seq;

CREATE TABLE tmp_massa_seq (n INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO tmp_massa_seq (n)
WITH RECURSIVE s (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 10100)
SELECT n FROM s;

-- Doze meses da janela. Vazia se a massa já existe (todas as demais tabelas
-- derivam desta, de tmp_massa_veic ou de tmp_massa_usuario, e ficam vazias).
CREATE TABLE tmp_massa_mes AS
SELECT s.n - 1 AS i, DATE_ADD(@massa_inicio, INTERVAL s.n - 1 MONTH) AS inicio_mes
  FROM tmp_massa_seq s
 WHERE s.n <= 12 AND @massa_ja_existe = 0;

-- ---------------------------------------------------------------------------
-- Veículos (hodômetro base, consumo e periodicidade das viagens).
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_veic AS
SELECT s.n,
       CONCAT('vei|', s.n) AS k,
       CAST(NULL AS CHAR(36)) AS Id,
       CONCAT('MSA', LPAD(s.n, 4, '0')) AS Placa,
       ELT(1 + MOD(s.n, 6), 'Volvo', 'Scania', 'Mercedes-Benz', 'DAF', 'Iveco', 'Volkswagen') AS Marca,
       ELT(1 + MOD(s.n, 6), 'FH 540', 'R 450', 'Actros 2651', 'XF 530', 'S-Way 540', 'Constellation 24.280') AS Modelo,
       2015 + MOD(CRC32(CONCAT('ano|', s.n)), 11) AS Ano,
       ROUND(14 + (CRC32(CONCAT('cap|', s.n)) / 4294967296) * 26, 2) AS Cap,
       60000 + MOD(CRC32(CONCAT('hod|', s.n)), 390000) AS hod_base,
       ROUND(2.40 + (CRC32(CONCAT('cons|', s.n)) / 4294967296) * 1.20, 2) AS consumo,
       4 + MOD(s.n, 2) AS periodo
  FROM tmp_massa_seq s
 WHERE s.n <= @massa_veiculos AND @massa_ja_existe = 0;

UPDATE tmp_massa_veic
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12));
ALTER TABLE tmp_massa_veic ADD PRIMARY KEY (n);

-- ---------------------------------------------------------------------------
-- Rotas (distância entre 150 e 1.800 km).
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_rota AS
SELECT s.n,
       CONCAT('rot|', s.n) AS k,
       CAST(NULL AS CHAR(36)) AS Id,
       ELT(1 + MOD(s.n - 1, 16), 'São Paulo/SP', 'Rio de Janeiro/RJ', 'Belo Horizonte/MG', 'Curitiba/PR',
           'Porto Alegre/RS', 'Campinas/SP', 'Goiânia/GO', 'Brasília/DF', 'Salvador/BA', 'Vitória/ES',
           'Ribeirão Preto/SP', 'Uberlândia/MG', 'Varginha/MG', 'Londrina/PR', 'Florianópolis/SC', 'Cuiabá/MT') AS Origem,
       ELT(1 + MOD((s.n - 1) + 1 + MOD(((s.n - 1) DIV 16) * 5, 15), 16), 'São Paulo/SP', 'Rio de Janeiro/RJ',
           'Belo Horizonte/MG', 'Curitiba/PR', 'Porto Alegre/RS', 'Campinas/SP', 'Goiânia/GO', 'Brasília/DF',
           'Salvador/BA', 'Vitória/ES', 'Ribeirão Preto/SP', 'Uberlândia/MG', 'Varginha/MG', 'Londrina/PR',
           'Florianópolis/SC', 'Cuiabá/MT') AS Destino,
       150 + 5 * MOD(CRC32(CONCAT('dist|', s.n)), 330) AS Dist
  FROM tmp_massa_seq s
 WHERE s.n <= @massa_rotas AND @massa_ja_existe = 0;

UPDATE tmp_massa_rota
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12));
ALTER TABLE tmp_massa_rota ADD PRIMARY KEY (n);

-- ---------------------------------------------------------------------------
-- Usuários: n = 0 é quem criou os lançamentos; n >= 1 são os motoristas.
-- E-mails em domínio inexistente (.test): ninguém consegue entrar com eles.
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_usuario AS
SELECT s.n,
       CONCAT('usr|', s.n) AS k,
       CAST(NULL AS CHAR(36)) AS Id,
       CONCAT(ELT(1 + MOD(s.n, 12), 'Carlos', 'João', 'Marcos', 'Paulo', 'Rafael', 'Anderson', 'Luiz',
                  'Fernando', 'Ricardo', 'Sérgio', 'José', 'Roberto'), ' ',
              ELT(1 + MOD(s.n DIV 12 + s.n, 10), 'Silva', 'Souza', 'Oliveira', 'Santos', 'Pereira', 'Costa',
                  'Rodrigues', 'Almeida', 'Nascimento', 'Lima'),
              ' [massa ', LPAD(s.n, 3, '0'), ']') AS Nome,
       CONCAT('motorista.', LPAD(s.n, 3, '0'), '@massa.fleetflow.test') AS Email,
       CONCAT('massa-motorista-', LPAD(s.n, 3, '0')) AS Sub,
       ELT(1 + MOD(s.n, 3), 'C', 'D', 'E') AS Cnh,
       DATE_ADD(@massa_hoje, INTERVAL 200 + MOD(CRC32(CONCAT('cnh|', s.n)), 900) DAY) AS ValidadeCnh
  FROM tmp_massa_seq s
 WHERE s.n BETWEEN 1 AND @massa_motoristas AND @massa_ja_existe = 0
UNION ALL
SELECT 0, 'usr|0', NULL, 'Carga de massa (não é pessoa)', 'carga.massa@massa.fleetflow.test',
       'massa-carga-000', NULL, NULL
 WHERE @massa_ja_existe = 0;

UPDATE tmp_massa_usuario
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12));
ALTER TABLE tmp_massa_usuario ADD PRIMARY KEY (n);

-- ---------------------------------------------------------------------------
-- Viagens. Cada veículo faz uma viagem a cada 4 ou 5 dias, em sequência (nunca
-- duas ao mesmo tempo), com o hodômetro encadeado: o inicial de uma viagem é o
-- final da anterior. Todas as encerradas têm HodometroFinal >= HodometroInicial.
-- Motorista: cada veículo tem o seu, fixo. Em 1 a cada 8 veículos um motorista
-- reserva (um por veículo, nunca em dois ao mesmo tempo) cobre 10 viagens.
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_viagem (
  tg INT NOT NULL PRIMARY KEY,
  vn INT NOT NULL,
  kn INT NOT NULL,
  Id CHAR(36) NULL,
  RotaId CHAR(36) NOT NULL,
  mot_n INT NOT NULL,
  MotoristaId CHAR(36) NULL,
  Dist INT NOT NULL,
  ini_dt DATETIME NOT NULL,
  horas INT NOT NULL,
  end_dt DATETIME NULL,
  km INT NULL,
  hod_ini INT NOT NULL,
  hod_fim INT NULL,
  Status VARCHAR(10) NOT NULL,
  KEY ix_tmp_viagem_vn (vn, kn),
  KEY ix_tmp_viagem_status (Status)
) ENGINE=InnoDB;

INSERT INTO tmp_massa_viagem (tg, vn, kn, RotaId, mot_n, Dist, ini_dt, horas, end_dt, km, hod_ini, hod_fim, Status)
WITH t AS (
  SELECT v.n AS vn, k.n AS kn, (v.n - 1) * 1000 + k.n AS tg, v.hod_base, r.Id AS RotaId, r.Dist,
         DATE_ADD(@massa_inicio,
                  INTERVAL (MOD(v.n, 24) + (k.n - 1) * v.periodo * 24
                            + FLOOR(CRC32(CONCAT('jit|', (v.n - 1) * 1000 + k.n)) / 4294967296 * 12)) HOUR) AS ini_dt
    FROM tmp_massa_veic v
    JOIN tmp_massa_seq k ON k.n <= 93
    JOIN tmp_massa_rota r ON r.n = 1 + MOD(CRC32(CONCAT('rota|', (v.n - 1) * 1000 + k.n)), @massa_rotas)
), u AS (
  SELECT t.*,
         ROUND(t.Dist / (45 + MOD(CRC32(CONCAT('vel|', t.tg)), 15))) + 4 AS horas,
         GREATEST(1, ROUND(t.Dist * (0.98 + 0.07 * (CRC32(CONCAT('km|', t.tg)) / 4294967296)))) AS km
    FROM t
   WHERE DATE(t.ini_dt) <= @massa_fim
)
SELECT u.tg, u.vn, u.kn, u.RotaId,
       CASE WHEN MOD(u.vn, 8) = 0 AND u.kn BETWEEN 30 AND 39 THEN @massa_veiculos + u.vn DIV 8 ELSE u.vn END,
       u.Dist, u.ini_dt, u.horas,
       DATE_ADD(u.ini_dt, INTERVAL u.horas HOUR),
       u.km,
       CAST(u.hod_base + SUM(u.km) OVER w - u.km AS SIGNED),
       CAST(u.hod_base + SUM(u.km) OVER w AS SIGNED),
       'Encerrada'
  FROM u
WINDOW w AS (PARTITION BY u.vn ORDER BY u.kn);

-- Uma viagem aberta (iniciada nos últimos dias) para cada 13º veículo.
INSERT INTO tmp_massa_viagem (tg, vn, kn, RotaId, mot_n, Dist, ini_dt, horas, end_dt, km, hod_ini, hod_fim, Status)
SELECT (v.n - 1) * 1000 + 999, v.n, 999, r.Id,
       v.n,
       r.Dist,
       DATE_ADD(TIMESTAMP(DATE_SUB(@massa_hoje, INTERVAL 1 + MOD(v.n, 3) DAY)), INTERVAL 6 HOUR),
       0, NULL, NULL,
       COALESCE((SELECT MAX(t.hod_fim) FROM tmp_massa_viagem t WHERE t.vn = v.n AND t.Status = 'Encerrada'), v.hod_base),
       NULL, 'Aberta'
  FROM tmp_massa_veic v
  JOIN tmp_massa_rota r ON r.n = 1 + MOD(CRC32(CONCAT('rota|', (v.n - 1) * 1000 + 999)), @massa_rotas)
 WHERE MOD(v.n, 13) = 0;

UPDATE tmp_massa_viagem
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(CONCAT('via|', tg)), 1, 4), '-4', SUBSTR(MD5(CONCAT('via|', tg)), 5, 3),
                   '-8', SUBSTR(MD5(CONCAT('via|', tg)), 8, 3), '-', SUBSTR(MD5(CONCAT('via|', tg)), 11, 12));
UPDATE tmp_massa_viagem t JOIN tmp_massa_usuario u ON u.n = t.mot_n SET t.MotoristaId = u.Id;

-- ---------------------------------------------------------------------------
-- Abastecimentos: um a cada ~550 km (de 1 a 4 por viagem), com o preço do
-- diesel variando ao longo do ano; mais abastecimentos de pátio, sem viagem,
-- logo após a chegada de ~21% das viagens.
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_abast (
  k VARCHAR(40) NOT NULL PRIMARY KEY,
  mov_k VARCHAR(40) NOT NULL,
  Id CHAR(36) NULL,
  VeiculoId CHAR(36) NOT NULL,
  Placa VARCHAR(7) NOT NULL,
  ViagemId CHAR(36) NULL,
  Litros DECIMAL(14,2) NOT NULL,
  Valor DECIMAL(14,2) NOT NULL,
  PrecoLitro DECIMAL(14,3) NOT NULL,
  Posto VARCHAR(160) NOT NULL,
  Hodometro INT NOT NULL,
  dt DATETIME NOT NULL
) ENGINE=InnoDB;

INSERT INTO tmp_massa_abast (k, mov_k, VeiculoId, Placa, ViagemId, Litros, Valor, PrecoLitro, Posto, Hodometro, dt)
WITH p AS (
  SELECT t.tg, t.Id AS ViagemId, v.Id AS VeiculoId, v.Placa, v.consumo, t.km, t.hod_ini, t.ini_dt, t.horas,
         LEAST(4, GREATEST(1, CEIL(t.km / 550))) AS paradas
    FROM tmp_massa_viagem t
    JOIN tmp_massa_veic v ON v.n = t.vn
   WHERE t.Status = 'Encerrada'
), q AS (
  SELECT p.*, j.n AS j,
         DATE_ADD(p.ini_dt, INTERVAL ROUND(p.horas * 60 * (j.n - 0.5) / p.paradas) MINUTE) AS dt,
         ROUND(p.km / p.paradas / (p.consumo * (0.92 + 0.16 * (CRC32(CONCAT('lit|', p.tg, '|', j.n)) / 4294967296))), 2) AS litros
    FROM p
    JOIN tmp_massa_seq j ON j.n <= p.paradas
), r AS (
  SELECT q.*,
         ROUND(5.70 + 0.30 * SIN(2 * PI() * TIMESTAMPDIFF(MONTH, @massa_inicio, q.dt) / 12)
               + 0.015 * TIMESTAMPDIFF(MONTH, @massa_inicio, q.dt)
               + ((CRC32(CONCAT('prc|', q.tg, '|', q.j)) / 4294967296) - 0.5) * 0.16, 3) AS preco
    FROM q
)
SELECT CONCAT('abs|', r.tg * 10 + r.j), CONCAT('mfu|', r.tg * 10 + r.j),
       r.VeiculoId, r.Placa, r.ViagemId, r.litros,
       ROUND(r.litros * r.preco, 2),
       ROUND(ROUND(r.litros * r.preco, 2) / r.litros, 3),
       ELT(1 + MOD(CRC32(CONCAT('pst|', r.tg, '|', r.j)), 8), 'Posto Ipiranga BR-381', 'Shell Select Fernão Dias',
           'Posto Graal Dutra', 'Petrobras Régis Bittencourt', 'Posto Rodoil BR-116', 'Ale Via Anhanguera',
           'Posto Camionista BR-040', 'Posto Atlântico BR-101'),
       CAST(r.hod_ini + ROUND(r.km * (r.j - 0.5) / r.paradas) AS SIGNED),
       r.dt
  FROM r;

INSERT INTO tmp_massa_abast (k, mov_k, VeiculoId, Placa, ViagemId, Litros, Valor, PrecoLitro, Posto, Hodometro, dt)
WITH p AS (
  SELECT t.tg, v.Id AS VeiculoId, v.Placa, t.hod_fim, DATE_ADD(t.end_dt, INTERVAL 2 HOUR) AS dt,
         ROUND(100 + 180 * (CRC32(CONCAT('plt|', t.tg)) / 4294967296), 2) AS litros
    FROM tmp_massa_viagem t
    JOIN tmp_massa_veic v ON v.n = t.vn
   WHERE t.Status = 'Encerrada' AND (CRC32(CONCAT('pat|', t.tg)) / 4294967296) < 0.21
), r AS (
  SELECT p.*,
         ROUND(5.70 + 0.30 * SIN(2 * PI() * TIMESTAMPDIFF(MONTH, @massa_inicio, p.dt) / 12)
               + 0.015 * TIMESTAMPDIFF(MONTH, @massa_inicio, p.dt)
               + ((CRC32(CONCAT('ppr|', p.tg)) / 4294967296) - 0.5) * 0.16, 3) AS preco
    FROM p
)
SELECT CONCAT('abp|', r.tg), CONCAT('mpa|', r.tg), r.VeiculoId, r.Placa, NULL, r.litros,
       ROUND(r.litros * r.preco, 2), ROUND(ROUND(r.litros * r.preco, 2) / r.litros, 3),
       'Posto do pátio (tanque interno)', r.hod_fim, r.dt
  FROM r;

UPDATE tmp_massa_abast
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12));

-- ---------------------------------------------------------------------------
-- Ordens de serviço: preventiva a cada 30.000 km cruzados na viagem; corretiva
-- ocasional (~3,5% das viagens); e uma OS aberta nos veículos em manutenção.
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_os (
  k VARCHAR(40) NOT NULL PRIMARY KEY,
  mov_k VARCHAR(40) NOT NULL,
  Id CHAR(36) NULL,
  vn INT NOT NULL,
  VeiculoId CHAR(36) NOT NULL,
  Tipo VARCHAR(10) NOT NULL,
  Descricao VARCHAR(200) NOT NULL,
  Valor DECIMAL(14,2) NOT NULL,
  DataManutencao DATE NOT NULL,
  DataConclusao DATE NULL,
  Hodometro INT NULL
) ENGINE=InnoDB;

INSERT INTO tmp_massa_os (k, mov_k, vn, VeiculoId, Tipo, Descricao, Valor, DataManutencao, DataConclusao, Hodometro)
SELECT CONCAT('os|', t.tg), CONCAT('mos|', t.tg), t.vn, v.Id,
       CASE WHEN FLOOR(t.hod_fim / 30000) > FLOOR(t.hod_ini / 30000) THEN 'Preventiva' ELSE 'Corretiva' END,
       CASE WHEN FLOOR(t.hod_fim / 30000) > FLOOR(t.hod_ini / 30000)
            THEN CONCAT('Revisão preventiva dos ', FORMAT(FLOOR(t.hod_fim / 30000) * 30000, 0, 'pt_BR'), ' km')
            ELSE ELT(1 + MOD(CRC32(CONCAT('osd|', t.tg)), 5), 'Troca de pneus', 'Reparo de freios', 'Reparo de suspensão',
                     'Troca de embreagem', 'Reparo elétrico') END,
       CASE WHEN FLOOR(t.hod_fim / 30000) > FLOOR(t.hod_ini / 30000)
            THEN ROUND(1800 + 2600 * (CRC32(CONCAT('osv|', t.tg)) / 4294967296), 2)
            ELSE ROUND(600 + 8400 * POW(CRC32(CONCAT('osv|', t.tg)) / 4294967296, 2), 2) END,
       DATE(t.end_dt),
       DATE_ADD(DATE(t.end_dt), INTERVAL MOD(CRC32(CONCAT('osc|', t.tg)), 2) DAY),
       CASE WHEN FLOOR(t.hod_fim / 30000) > FLOOR(t.hod_ini / 30000)
            THEN CAST(FLOOR(t.hod_fim / 30000) * 30000 AS SIGNED) ELSE t.hod_fim END
  FROM tmp_massa_viagem t
  JOIN tmp_massa_veic v ON v.n = t.vn
 WHERE t.Status = 'Encerrada'
   AND (FLOOR(t.hod_fim / 30000) > FLOOR(t.hod_ini / 30000)
        OR (CRC32(CONCAT('cor|', t.tg)) / 4294967296) < 0.035);

-- OS aberta (sem conclusão) para os veículos em manutenção: 5º, 32º, 59º...
INSERT INTO tmp_massa_os (k, mov_k, vn, VeiculoId, Tipo, Descricao, Valor, DataManutencao, DataConclusao, Hodometro)
SELECT CONCAT('osa|', v.n), CONCAT('moa|', v.n), v.n, v.Id, 'Corretiva', 'Reparo de motor (em andamento)',
       ROUND(4000 + 6000 * (CRC32(CONCAT('osa|', v.n)) / 4294967296), 2),
       DATE_SUB(@massa_hoje, INTERVAL 2 DAY), NULL,
       COALESCE((SELECT MAX(t.hod_fim) FROM tmp_massa_viagem t WHERE t.vn = v.n AND t.Status = 'Encerrada'), v.hod_base)
  FROM tmp_massa_veic v
 WHERE MOD(v.n, 27) = 5 AND MOD(v.n, 13) <> 0;

UPDATE tmp_massa_os
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12));

-- ---------------------------------------------------------------------------
-- Dívidas: financiamento em metade da frota (veículos ímpares), com todas as
-- parcelas (36, 48 ou 60), pagas e a vencer.
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_divida AS
SELECT v.n AS vn,
       CONCAT('div|', v.n) AS k,
       CAST(NULL AS CHAR(36)) AS Id,
       v.Id AS VeiculoId,
       v.Placa,
       ELT(1 + MOD(CRC32(CONCAT('npa|', v.n)), 3), 36, 48, 60) AS N,
       ROUND(4200 + 5200 * (CRC32(CONCAT('vpa|', v.n)) / 4294967296), 2) AS Valor,
       DATE_ADD(DATE_ADD(@massa_inicio, INTERVAL CAST(MOD(CRC32(CONCAT('prm|', v.n)), 18) AS SIGNED) - 6 MONTH),
                INTERVAL 4 + 5 * MOD(CRC32(CONCAT('dia|', v.n)), 4) DAY) AS Primeira
  FROM tmp_massa_veic v
 WHERE MOD(v.n, 2) = 1;

UPDATE tmp_massa_divida
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12));
ALTER TABLE tmp_massa_divida ADD PRIMARY KEY (vn);

-- ---------------------------------------------------------------------------
-- Movimentações. Todas entram primeiro em tmp_massa_mov; Status e
-- DataPagamento são decididos no final, numa regra só:
--   * forcar_pago = 1 (combustível, pedágio, salário): pago no vencimento;
--   * senão, vencido até @massa_hoje fica Pago (~94%), com pagamento de 2 dias
--     antes a 6 dias depois do vencimento (nunca no futuro); o resto, Pendente;
--   * vence depois de @massa_hoje: Pendente.
-- ---------------------------------------------------------------------------
CREATE TABLE tmp_massa_mov (
  k VARCHAR(40) NOT NULL PRIMARY KEY,
  Id CHAR(36) NULL,
  Valor DECIMAL(14,2) NOT NULL,
  Descricao VARCHAR(200) NOT NULL,
  CategoriaId CHAR(36) NOT NULL,
  FormaPagamentoId CHAR(36) NOT NULL,
  ViagemId CHAR(36) NULL,
  DividaId CHAR(36) NULL,
  VeiculoId CHAR(36) NULL,
  NumeroParcela INT NULL,
  DataVencimento DATE NOT NULL,
  DataPagamento DATE NULL,
  Status VARCHAR(10) NOT NULL DEFAULT 'Pendente',
  Litros DECIMAL(14,2) NULL,
  Ativo TINYINT NOT NULL DEFAULT 1,
  base_dt DATETIME NULL,
  forcar_pago TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- Frete: um por viagem encerrada, vence 15, 30 ou 45 dias após a entrega.
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, ViagemId, DataVencimento, base_dt)
SELECT CONCAT('mfr|', t.tg),
       ROUND(t.km * (4.60 + 3.40 * (CRC32(CONCAT('frt|', t.tg)) / 4294967296)), 2),
       CONCAT('Frete - ', r.Origem, ' x ', r.Destino),
       @cat_frete,
       ELT(1 + MOD(CRC32(CONCAT('ffp|', t.tg)), 3), @fp_pix, @fp_boleto, @fp_ted),
       t.Id,
       DATE_ADD(DATE(t.end_dt), INTERVAL ELT(1 + MOD(CRC32(CONCAT('prz|', t.tg)), 3), 15, 30, 45) DAY),
       t.end_dt
  FROM tmp_massa_viagem t
  JOIN tmp_massa_rota r ON r.Id = t.RotaId
 WHERE t.Status = 'Encerrada';

-- Pedágio: viagens com mais de 250 km, pago no ato.
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, ViagemId, DataVencimento, base_dt, forcar_pago)
SELECT CONCAT('mpe|', t.tg),
       ROUND(30 + t.Dist * 0.12 * (0.6 + 0.8 * (CRC32(CONCAT('ped|', t.tg)) / 4294967296)), 2),
       CONCAT('Pedágios - ', r.Origem, ' x ', r.Destino),
       @cat_pedagio,
       ELT(1 + MOD(CRC32(CONCAT('pfp|', t.tg)), 2), @fp_cartao, @fp_dinheiro),
       t.Id,
       DATE(t.end_dt), t.end_dt, 1
  FROM tmp_massa_viagem t
  JOIN tmp_massa_rota r ON r.Id = t.RotaId
 WHERE t.Status = 'Encerrada' AND t.Dist > 250;

-- Combustível: uma despesa por abastecimento, com a mesma viagem/veículo e os litros.
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, ViagemId, VeiculoId, DataVencimento, Litros, base_dt, forcar_pago)
SELECT a.mov_k, a.Valor, CONCAT('Abastecimento ', a.Placa, ' - ', a.Posto), @cat_combustivel, @fp_cartao,
       a.ViagemId, a.VeiculoId, DATE(a.dt), a.Litros, a.dt, 1
  FROM tmp_massa_abast a;

-- Manutenção: uma despesa por ordem de serviço (vence na conclusão).
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, VeiculoId, DataVencimento, base_dt)
SELECT o.mov_k, o.Valor, CONCAT('Manutenção: ', o.Descricao), @cat_manutencao,
       ELT(1 + MOD(CRC32(CONCAT('ofp|', o.k)), 2), @fp_boleto, @fp_pix),
       o.VeiculoId,
       COALESCE(o.DataConclusao, DATE_ADD(o.DataManutencao, INTERVAL 15 DAY)),
       TIMESTAMP(o.DataManutencao, '09:00:00')
  FROM tmp_massa_os o;

-- Seguro mensal por veículo (dia 10).
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, VeiculoId, DataVencimento)
SELECT CONCAT('mse|', v.n, '|', m.i),
       ROUND(700 + 800 * (CRC32(CONCAT('seg|', v.n)) / 4294967296), 2),
       CONCAT('Seguro mensal ', v.Placa),
       @cat_outros, @fp_boleto, v.Id, DATE_ADD(m.inicio_mes, INTERVAL 9 DAY)
  FROM tmp_massa_veic v
  CROSS JOIN tmp_massa_mes m;

-- Impostos e taxas veiculares, trimestrais (jan, abr, jul, out), dia 20.
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, VeiculoId, DataVencimento)
SELECT CONCAT('mim|', v.n, '|', m.i),
       ROUND(1100 + 1700 * (CRC32(CONCAT('imp|', v.n, '|', m.i)) / 4294967296), 2),
       CONCAT('Impostos e taxas ', v.Placa),
       @cat_outros, @fp_boleto, v.Id, DATE_ADD(m.inicio_mes, INTERVAL 19 DAY)
  FROM tmp_massa_veic v
  CROSS JOIN tmp_massa_mes m
 WHERE MONTH(m.inicio_mes) IN (1, 4, 7, 10);

-- Parcelas dos financiamentos (todas: pagas e a vencer).
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, DividaId, NumeroParcela, DataVencimento)
SELECT CONCAT('mpc|', d.vn, '|', s.n), d.Valor,
       CONCAT('Financiamento ', d.Placa, ' (parcela ', s.n, '/', d.N, ')'),
       @cat_financiamento, @fp_boleto, d.Id, s.n,
       DATE_ADD(d.Primeira, INTERVAL s.n - 1 MONTH)
  FROM tmp_massa_divida d
  JOIN tmp_massa_seq s ON s.n <= d.N;

-- Salários dos motoristas, dia 5: sem veículo nem viagem (custo não alocado).
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, DataVencimento, forcar_pago)
SELECT CONCAT('msa|', u.n, '|', m.i),
       ROUND(3100 + 1900 * (CRC32(CONCAT('sal|', u.n)) / 4294967296), 2),
       CONCAT('Salário ', u.Nome),
       @cat_salario, @fp_ted, DATE_ADD(m.inicio_mes, INTERVAL 4 DAY), 1
  FROM tmp_massa_usuario u
  CROSS JOIN tmp_massa_mes m
 WHERE u.n >= 1;

-- Despesas avulsas (sem veículo). Uma a cada 25 fica inativa (Ativo = 0).
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, DataVencimento, Ativo)
SELECT CONCAT('mav|', m.i, '|', s.n),
       ROUND(150 + 4650 * (CRC32(CONCAT('avv|', m.i, '|', s.n)) / 4294967296), 2),
       ELT(1 + MOD(CRC32(CONCAT('avd|', m.i, '|', s.n)), 6), 'Material de escritório', 'Lavagem de veículos',
           'Serviços contábeis', 'Material de limpeza', 'Compra avulsa de pneus', 'Taxa administrativa'),
       @cat_outros,
       ELT(1 + MOD(CRC32(CONCAT('avf|', m.i, '|', s.n)), 3), @fp_pix, @fp_boleto, @fp_dinheiro),
       DATE_ADD(m.inicio_mes, INTERVAL MOD(CRC32(CONCAT('avt|', m.i, '|', s.n)), 28) DAY),
       IF(MOD(m.i * 8 + s.n, 25) = 0, 0, 1)
  FROM tmp_massa_mes m
  JOIN tmp_massa_seq s ON s.n <= 8;

-- Outras receitas (sem veículo): duas por mês.
INSERT INTO tmp_massa_mov (k, Valor, Descricao, CategoriaId, FormaPagamentoId, DataVencimento)
SELECT CONCAT('mor|', m.i, '|', s.n),
       ROUND(800 + 5700 * (CRC32(CONCAT('orv|', m.i, '|', s.n)) / 4294967296), 2),
       ELT(1 + MOD(CRC32(CONCAT('ord|', m.i, '|', s.n)), 3), 'Venda de sucata', 'Aluguel de pátio', 'Reembolso de seguradora'),
       @cat_outras_rec, @fp_pix,
       DATE_ADD(m.inicio_mes, INTERVAL 4 + MOD(CRC32(CONCAT('ort|', m.i, '|', s.n)), 22) DAY)
  FROM tmp_massa_mes m
  JOIN tmp_massa_seq s ON s.n <= 2;

UPDATE tmp_massa_mov
   SET Id = CONCAT('f1ee7f10-', SUBSTR(MD5(k), 1, 4), '-4', SUBSTR(MD5(k), 5, 3), '-8', SUBSTR(MD5(k), 8, 3), '-', SUBSTR(MD5(k), 11, 12)),
       base_dt = COALESCE(base_dt, TIMESTAMP(DataVencimento, '08:00:00')),
       Status = CASE WHEN forcar_pago = 1
                       OR (DataVencimento <= @massa_hoje AND (CRC32(CONCAT('pend|', k)) / 4294967296) >= 0.06)
                     THEN 'Pago' ELSE 'Pendente' END;

UPDATE tmp_massa_mov
   SET DataPagamento = CASE WHEN Status <> 'Pago' THEN NULL
                            WHEN forcar_pago = 1 THEN DataVencimento
                            ELSE LEAST(DATE_ADD(DataVencimento, INTERVAL CAST(MOD(CRC32(CONCAT('atr|', k)), 9) AS SIGNED) - 2 DAY), @massa_hoje) END;

-- ---------------------------------------------------------------------------
-- Gravação nas tabelas reais, numa transação só (ou entra tudo, ou nada).
-- ---------------------------------------------------------------------------
START TRANSACTION;

INSERT INTO usuarios (Id, Nome, Email, GoogleSubjectId, PerfilId, Status, CategoriaCnh, ValidadeCnh)
SELECT u.Id, u.Nome, u.Email, u.Sub,
       IF(u.n = 0, @perfil_financeiro, @perfil_motorista),
       IF(u.n = 0, 'Bloqueado', 'Ativo'),
       u.Cnh, u.ValidadeCnh
  FROM tmp_massa_usuario u;

INSERT INTO rotas (Id, Nome, Origem, Destino, DistanciaEstimadaKm, Descricao)
SELECT r.Id, CONCAT(r.Origem, ' x ', r.Destino), r.Origem, r.Destino, r.Dist, 'Rota de teste (massa de 12 meses)'
  FROM tmp_massa_rota r;

-- Veículos: hodômetro, status e manutenção já refletem as viagens e as OS geradas.
INSERT INTO veiculos (Id, Placa, Marca, Modelo, AnoFabricacao, CapacidadeCargaToneladas, Status, HodometroAtual,
                      IntervaloManutencaoKm, IntervaloManutencaoMeses, HodometroUltimaManutencao,
                      VencimentoLicenciamento, VencimentoSeguro, ConsumoMedioReferenciaKmL)
SELECT v.Id, v.Placa, v.Marca, v.Modelo, v.Ano, v.Cap,
       CASE WHEN EXISTS (SELECT 1 FROM tmp_massa_viagem t WHERE t.vn = v.n AND t.Status = 'Aberta') THEN 'EmViagem'
            WHEN EXISTS (SELECT 1 FROM tmp_massa_os o WHERE o.vn = v.n AND o.DataConclusao IS NULL) THEN 'EmManutencao'
            ELSE 'Disponivel' END,
       COALESCE((SELECT MAX(COALESCE(t.hod_fim, t.hod_ini)) FROM tmp_massa_viagem t WHERE t.vn = v.n), v.hod_base),
       IF(MOD(v.n, 7) = 0, NULL, 30000),
       IF(MOD(v.n, 7) = 0, NULL, 12),
       IF(MOD(v.n, 7) = 0, NULL,
          COALESCE((SELECT MAX(o.Hodometro) FROM tmp_massa_os o WHERE o.vn = v.n AND o.Tipo = 'Preventiva'),
                   v.hod_base - MOD(v.hod_base, 30000))),
       DATE_ADD(@massa_hoje, INTERVAL CAST(MOD(CRC32(CONCAT('lic|', v.n)), 330) AS SIGNED) - 25 DAY),
       DATE_ADD(@massa_hoje, INTERVAL CAST(MOD(CRC32(CONCAT('sgr|', v.n)), 330) AS SIGNED) - 25 DAY),
       v.consumo
  FROM tmp_massa_veic v;

INSERT INTO viagens (Id, CriadoEm, AtualizadoEm, RotaId, VeiculoId, MotoristaId, HodometroInicial, HodometroFinal,
                     DataAbertura, DataFechamento, Status, Observacao)
SELECT t.Id, t.ini_dt, COALESCE(t.end_dt, t.ini_dt), t.RotaId, v.Id, t.MotoristaId, t.hod_ini, t.hod_fim,
       t.ini_dt, t.end_dt, t.Status,
       CASE WHEN t.Status = 'Aberta' THEN 'Viagem em andamento (massa)' ELSE NULL END
  FROM tmp_massa_viagem t
  JOIN tmp_massa_veic v ON v.n = t.vn;

INSERT INTO dividas (Id, Descricao, CategoriaId, QuantidadeParcelas, ValorParcela, DataVencimentoPrimeira,
                     ValorQuitacaoAntecipada, VeiculoId)
SELECT d.Id, CONCAT('Financiamento do veículo ', d.Placa), @cat_financiamento, d.N, d.Valor, d.Primeira,
       ROUND(d.N * d.Valor * 0.62, 2), d.VeiculoId
  FROM tmp_massa_divida d;

INSERT INTO movimentacoes (Id, CriadoEm, AtualizadoEm, Ativo, Valor, Descricao, CategoriaId, FormaPagamentoId,
                           ViagemId, DividaId, VeiculoId, NumeroParcela, DataVencimento, DataPagamento, Status,
                           LitrosCombustivel, CriadoPorUsuarioId)
SELECT m.Id, LEAST(m.base_dt, TIMESTAMP(@massa_hoje, '12:00:00')), LEAST(m.base_dt, TIMESTAMP(@massa_hoje, '12:00:00')),
       m.Ativo, m.Valor, m.Descricao, m.CategoriaId, m.FormaPagamentoId, m.ViagemId, m.DividaId, m.VeiculoId,
       m.NumeroParcela, m.DataVencimento, m.DataPagamento, m.Status, m.Litros, @massa_criador
  FROM tmp_massa_mov m;

INSERT INTO abastecimentos (Id, CriadoEm, AtualizadoEm, VeiculoId, ViagemId, Litros, ValorTotal, PrecoLitro, Posto,
                            Hodometro, MovimentacaoId)
SELECT a.Id, a.dt, a.dt, a.VeiculoId, a.ViagemId, a.Litros, a.Valor, a.PrecoLitro, a.Posto, a.Hodometro, m.Id
  FROM tmp_massa_abast a
  JOIN tmp_massa_mov m ON m.k = a.mov_k;

INSERT INTO ordens_servico (Id, CriadoEm, AtualizadoEm, VeiculoId, DataManutencao, Descricao, Tipo, ValorTotal,
                            HodometroNaManutencao, DataConclusao, MovimentacaoId)
SELECT o.Id, TIMESTAMP(o.DataManutencao, '09:00:00'), TIMESTAMP(COALESCE(o.DataConclusao, o.DataManutencao), '17:00:00'),
       o.VeiculoId, o.DataManutencao, o.Descricao, o.Tipo, o.Valor, o.Hodometro, o.DataConclusao, m.Id
  FROM tmp_massa_os o
  JOIN tmp_massa_mov m ON m.k = o.mov_k;

COMMIT;

-- ---------------------------------------------------------------------------
-- Resumo do que entrou nesta execução (zero em tudo = a massa já existia).
-- ---------------------------------------------------------------------------
SELECT 'veiculos' AS Tabela, COUNT(*) AS Inseridos FROM tmp_massa_veic
UNION ALL SELECT 'motoristas', COUNT(*) FROM tmp_massa_usuario WHERE n >= 1
UNION ALL SELECT 'rotas', COUNT(*) FROM tmp_massa_rota
UNION ALL SELECT 'viagens', COUNT(*) FROM tmp_massa_viagem
UNION ALL SELECT 'movimentacoes', COUNT(*) FROM tmp_massa_mov
UNION ALL SELECT 'abastecimentos', COUNT(*) FROM tmp_massa_abast
UNION ALL SELECT 'ordens_servico', COUNT(*) FROM tmp_massa_os
UNION ALL SELECT 'dividas', COUNT(*) FROM tmp_massa_divida;

DROP TABLE IF EXISTS tmp_massa_mov;
DROP TABLE IF EXISTS tmp_massa_abast;
DROP TABLE IF EXISTS tmp_massa_os;
DROP TABLE IF EXISTS tmp_massa_divida;
DROP TABLE IF EXISTS tmp_massa_viagem;
DROP TABLE IF EXISTS tmp_massa_usuario;
DROP TABLE IF EXISTS tmp_massa_rota;
DROP TABLE IF EXISTS tmp_massa_veic;
DROP TABLE IF EXISTS tmp_massa_mes;
DROP TABLE IF EXISTS tmp_massa_seq;
