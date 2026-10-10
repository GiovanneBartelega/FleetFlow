-- Usuários de teste, um por perfil, para validar o login (dev-login) no app
-- Android sem depender do Google. GoogleSubjectId usa o prefixo "seed-", que a
-- API reconhece como provisório e substitui pelo ID real no primeiro login
-- Google de verdade (ver src/routes/auth.js, isPlaceholderSubject).
--
-- Uso:
--   mysql -u root -p fleetflow < seed_usuarios_teste.sql

INSERT INTO usuarios (Nome, Email, GoogleSubjectId, PerfilId, Status)
SELECT 'Teste Administrador', 'admin.teste@fleetflow.com', 'seed-admin', Id, 'Ativo'
FROM perfis WHERE Nome = 'Administrador'
ON DUPLICATE KEY UPDATE Nome = VALUES(Nome);

INSERT INTO usuarios (Nome, Email, GoogleSubjectId, PerfilId, Status)
SELECT 'Teste Financeiro', 'financeiro.teste@fleetflow.com', 'seed-financeiro', Id, 'Ativo'
FROM perfis WHERE Nome = 'Financeiro'
ON DUPLICATE KEY UPDATE Nome = VALUES(Nome);

INSERT INTO usuarios (Nome, Email, GoogleSubjectId, PerfilId, Status)
SELECT 'Teste Gestor de Frota', 'gestorfrota.teste@fleetflow.com', 'seed-gestorfrota', Id, 'Ativo'
FROM perfis WHERE Nome = 'GestorFrota'
ON DUPLICATE KEY UPDATE Nome = VALUES(Nome);

INSERT INTO usuarios (Nome, Email, GoogleSubjectId, PerfilId, Status)
SELECT 'Teste Operador Motorista', 'motorista.teste@fleetflow.com', 'seed-motorista', Id, 'Ativo'
FROM perfis WHERE Nome = 'OperadorMotorista'
ON DUPLICATE KEY UPDATE Nome = VALUES(Nome);

SELECT u.Nome, u.Email, u.Status, p.Nome AS Perfil
FROM usuarios u JOIN perfis p ON p.Id = u.PerfilId
ORDER BY p.Nome;
