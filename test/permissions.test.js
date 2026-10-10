import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { PERFIS, makeFakePool, makeUser, startServer, tokenFor } from './helpers.js';

// Matriz esperada, escrita de forma independente do código (vem do roteiro de testes).
// E = leitura e escrita · L = só leitura · ausente = sem acesso.
const EXPECTED = {
  usuarios: { Administrador: 'E' },
  categorias: { Administrador: 'E', Financeiro: 'E', GestorFrota: 'L' },
  formas: { Administrador: 'E', Financeiro: 'E', GestorFrota: 'L' },
  movimentacoes: { Administrador: 'E', Financeiro: 'E' },
  dividas: { Administrador: 'E', Financeiro: 'E' },
};

const ENDPOINTS = [
  { modulo: 'usuarios', base: '/api/usuarios' },
  { modulo: 'usuarios', base: '/api/perfis', readOnly: true },
  { modulo: 'categorias', base: '/api/categorias' },
  { modulo: 'formas', base: '/api/formas-pagamento' },
  { modulo: 'movimentacoes', base: '/api/movimentacoes' },
  { modulo: 'dividas', base: '/api/dividas' },
];

const ID = '11111111-1111-4111-8111-111111111111';
const users = PERFIS.map((perfil, i) =>
  makeUser({ id: `22222222-2222-4222-8222-22222222222${i}`, perfil }),
);
const tokens = Object.fromEntries(users.map((u) => [u.PerfilNome, tokenFor(u.Id)]));

let api;
before(async () => {
  api = await startServer({ pool: makeFakePool({ users }) });
});
after(() => api.close());

// "Permitido" = a camada de permissão deixou passar (qualquer status que não seja 401/403).
const allowed = (status) => status !== 401 && status !== 403;

describe('matriz de permissões por perfil', () => {
  for (const { modulo, base, readOnly } of ENDPOINTS) {
    for (const perfil of PERFIS) {
      const level = EXPECTED[modulo][perfil] ?? null;
      const canRead = level === 'E' || level === 'L';
      const canWrite = level === 'E';

      test(`${perfil} · GET ${base} → ${canRead ? 'permitido' : '403'}`, async () => {
        const res = await api.request('GET', base, { token: tokens[perfil] });
        assert.equal(allowed(res.status), canRead, `status ${res.status}`);
        if (!canRead) assert.equal(res.body.error.code, 'FORBIDDEN');
      });

      if (readOnly) continue;

      test(`${perfil} · POST ${base} → ${canWrite ? 'permitido' : '403'}`, async () => {
        const res = await api.request('POST', base, { token: tokens[perfil], body: {} });
        assert.equal(allowed(res.status), canWrite, `status ${res.status}`);
      });

      test(`${perfil} · PATCH ${base}/:id → ${canWrite ? 'permitido' : '403'}`, async () => {
        const res = await api.request('PATCH', `${base}/${ID}`, { token: tokens[perfil], body: {} });
        assert.equal(allowed(res.status), canWrite, `status ${res.status}`);
      });

      test(`${perfil} · DELETE ${base}/:id → ${canWrite ? 'permitido' : '403'}`, async () => {
        const res = await api.request('DELETE', `${base}/${ID}`, { token: tokens[perfil] });
        assert.equal(allowed(res.status), canWrite, `status ${res.status}`);
      });
    }
  }

  test('POST /api/movimentacoes/:id/pagar exige escrita (Gestor e Motorista recebem 403)', async () => {
    for (const perfil of ['GestorFrota', 'OperadorMotorista']) {
      const res = await api.request('POST', `/api/movimentacoes/${ID}/pagar`, {
        token: tokens[perfil],
        body: { dataPagamento: '2026-10-10' },
      });
      assert.equal(res.status, 403, perfil);
    }
  });
});

describe('autenticação nas rotas protegidas', () => {
  test('sem token → 401', async () => {
    const res = await api.request('GET', '/api/categorias');
    assert.equal(res.status, 401);
    assert.equal(res.body.error.code, 'UNAUTHENTICATED');
  });

  test('token inválido → 401', async () => {
    const res = await api.request('GET', '/api/categorias', { token: 'abc.def.ghi' });
    assert.equal(res.status, 401);
    assert.equal(res.body.error.code, 'INVALID_TOKEN');
  });

  test('token de usuário que não existe mais → 401', async () => {
    const res = await api.request('GET', '/api/categorias', { token: tokenFor('99999999-9999-4999-8999-999999999999') });
    assert.equal(res.status, 401);
  });

  for (const [status, code] of [
    ['Bloqueado', 'ACCOUNT_BLOCKED'],
    ['AguardandoAprovacao', 'ACCOUNT_PENDING'],
  ]) {
    test(`conta ${status} com token válido → 403 ${code}`, async () => {
      const user = makeUser({ id: '33333333-3333-4333-8333-333333333333', perfil: 'Administrador', status });
      const local = await startServer({ pool: makeFakePool({ users: [user] }) });
      const res = await local.request('GET', '/api/categorias', { token: tokenFor(user.Id) });
      await local.close();
      assert.equal(res.status, 403);
      assert.equal(res.body.error.code, code);
    });
  }

  test('conta desativada (Ativo = 0) → 403 ACCOUNT_DISABLED', async () => {
    const user = makeUser({ id: '44444444-4444-4444-8444-444444444444', perfil: 'Administrador', ativo: 0 });
    const local = await startServer({ pool: makeFakePool({ users: [user] }) });
    const res = await local.request('GET', '/api/categorias', { token: tokenFor(user.Id) });
    await local.close();
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'ACCOUNT_DISABLED');
  });
});

describe('GET /api/auth/me (navegação dinâmica)', () => {
  const expected = {
    Administrador: { usuarios: 'E', categorias: 'E', movimentacoes: 'E', dividas: 'E', checklist: 'E' },
    Financeiro: { usuarios: null, categorias: 'E', movimentacoes: 'E', veiculos: 'L', checklist: null },
    GestorFrota: { usuarios: null, categorias: 'L', movimentacoes: null, veiculos: 'E', checklist: 'E' },
    OperadorMotorista: { usuarios: null, categorias: null, movimentacoes: null, veiculos: 'L', viagens: 'E' },
  };

  for (const perfil of PERFIS) {
    test(`${perfil} recebe as permissões do próprio perfil`, async () => {
      const res = await api.request('GET', '/api/auth/me', { token: tokens[perfil] });
      assert.equal(res.status, 200);
      assert.equal(res.body.usuario.perfil.nome, perfil);
      for (const [modulo, level] of Object.entries(expected[perfil])) {
        assert.equal(res.body.permissoes[modulo], level, `${perfil}/${modulo}`);
      }
      assert.equal(res.body.usuario.googleSubjectId, undefined, 'não pode expor o GoogleSubjectId');
    });
  }
});
