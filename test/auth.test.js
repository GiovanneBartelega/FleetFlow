import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import { loadConfig } from '../src/config.js';
import { SECRET, config, makeFakePool, makeUser, startServer } from './helpers.js';

const google = (overrides = {}) => async () => ({
  sub: 'google-sub-123',
  email: 'Pessoa@Gmail.com',
  emailVerified: true,
  nome: 'Pessoa Teste',
  foto: null,
  ...overrides,
});

const ID_TOKEN = 'x'.repeat(30);

async function login({ users = [], verify, handlers = [] }) {
  const pool = makeFakePool({ users, handlers });
  const api = await startServer({ pool, verifyGoogleToken: verify });
  const res = await api.request('POST', '/api/auth/google', { body: { idToken: ID_TOKEN } });
  await api.close();
  return { res, pool };
}

describe('POST /api/auth/google', () => {
  test('usuário ativo já vinculado recebe token e permissões', async () => {
    const user = makeUser({ id: 'u-1', perfil: 'Financeiro', subject: 'google-sub-123', email: 'pessoa@gmail.com' });
    const { res } = await login({ users: [user], verify: google() });

    assert.equal(res.status, 200);
    assert.equal(jwt.verify(res.body.token, SECRET).sub, 'u-1');
    assert.equal(res.body.usuario.perfil.nome, 'Financeiro');
    assert.equal(res.body.permissoes.movimentacoes, 'E');
    assert.equal(res.body.permissoes.usuarios, null);
  });

  test('conta pré-cadastrada (seed-...) troca o subject pelo ID real do Google', async () => {
    const user = makeUser({ id: 'u-2', perfil: 'Administrador', subject: 'seed-pessoa@gmail.com', email: 'pessoa@gmail.com' });
    const { res, pool } = await login({ users: [user], verify: google() });

    assert.equal(res.status, 200);
    const update = pool.calls.find((c) => c.sql.startsWith('UPDATE usuarios SET GoogleSubjectId'));
    assert.ok(update, 'deveria atualizar o GoogleSubjectId');
    assert.deepEqual(update.params, ['google-sub-123', 'u-2']);
  });

  test('e-mail já vinculado a outro subject real → 409', async () => {
    const user = makeUser({ id: 'u-3', perfil: 'Administrador', subject: '999888777', email: 'pessoa@gmail.com' });
    const { res } = await login({ users: [user], verify: google() });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'EMAIL_IN_USE');
  });

  test('pessoa nova é criada como AguardandoAprovacao e não recebe token', async () => {
    const users = [];
    const handlers = [
      (sql) => (sql.startsWith('SELECT Id FROM perfis') ? [[{ Id: 'perfil-OperadorMotorista' }]] : undefined),
      (sql, params) => {
        if (!sql.startsWith('INSERT INTO usuarios')) return undefined;
        const [id, nome, email, subject, perfilId] = params;
        users.push({ ...makeUser({ id, perfil: 'OperadorMotorista', status: 'AguardandoAprovacao', subject, email }), Nome: nome, PerfilId: perfilId });
        return [{ affectedRows: 1 }];
      },
    ];
    const { res, pool } = await login({ users, verify: google(), handlers });

    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'ACCOUNT_PENDING');
    assert.equal(res.body.token, undefined);
    const insert = pool.calls.find((c) => c.sql.startsWith('INSERT INTO usuarios'));
    assert.equal(insert.params[2], 'pessoa@gmail.com', 'e-mail deve ser gravado em minúsculas');
  });

  test('conta bloqueada não recebe token', async () => {
    const user = makeUser({ id: 'u-4', perfil: 'Financeiro', status: 'Bloqueado', subject: 'google-sub-123' });
    const { res } = await login({ users: [user], verify: google() });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'ACCOUNT_BLOCKED');
  });

  test('e-mail não verificado pelo Google → 403', async () => {
    const { res } = await login({ verify: google({ emailVerified: false }) });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'EMAIL_NOT_VERIFIED');
  });

  test('idToken ausente → 400', async () => {
    const api = await startServer({ pool: makeFakePool() });
    const res = await api.request('POST', '/api/auth/google', { body: {} });
    await api.close();
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'VALIDATION_ERROR');
  });
});

describe('POST /api/auth/dev-login', () => {
  test('não existe quando DEV_LOGIN está desligado', async () => {
    const api = await startServer({ pool: makeFakePool() });
    const res = await api.request('POST', '/api/auth/dev-login', { body: { email: 'a@b.com' } });
    await api.close();
    assert.equal(res.status, 404);
  });

  test('nunca é ativado em produção, mesmo com DEV_LOGIN=true', () => {
    const env = { JWT_SECRET: SECRET, DEV_LOGIN: 'true' };
    assert.equal(loadConfig({ ...env, NODE_ENV: 'production' }).devLogin, false);
    assert.equal(loadConfig({ ...env, NODE_ENV: 'development' }).devLogin, true);
  });

  test('emite token para usuário ativo quando habilitado', async () => {
    const user = makeUser({ id: 'u-5', perfil: 'GestorFrota', email: 'gestor@exemplo.com' });
    const api = await startServer({
      pool: makeFakePool({ users: [user] }),
      appConfig: { ...config, devLogin: true },
    });
    const res = await api.request('POST', '/api/auth/dev-login', { body: { email: 'gestor@exemplo.com' } });
    await api.close();
    assert.equal(res.status, 200);
    assert.equal(res.body.usuario.perfil.nome, 'GestorFrota');
  });
});
