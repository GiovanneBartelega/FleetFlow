import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { buildUpdate, moneyTimes } from '../src/sql.js';
import { makeFakePool, makeUser, startServer, tokenFor } from './helpers.js';

const ADMIN = makeUser({ id: '55555555-5555-4555-8555-555555555555', perfil: 'Administrador' });
const FIN = makeUser({ id: '66666666-6666-4666-8666-666666666666', perfil: 'Financeiro' });
const CAT = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const FORMA = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const MOV = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';

const categoriaSistema = { Id: CAT, CriadoEm: '2026-10-01 10:00:00', AtualizadoEm: '2026-10-01 10:00:00', Ativo: 1, SyncVersion: 1, Titulo: 'Frete', Tipo: 'Entrada', EhSistema: 1 };

let api;
let pool;
before(async () => {
  pool = makeFakePool({
    users: [ADMIN, FIN],
    handlers: [
      (sql, params) => (sql.startsWith('SELECT * FROM categorias WHERE Id') && params[0] === CAT ? [[categoriaSistema]] : undefined),
      (sql) => (/^SELECT Id FROM (categorias|formas_pagamento|dividas) WHERE Id/.test(sql) ? [[{ Id: 'x' }]] : undefined),
      (sql, params) =>
        sql.includes('FROM movimentacoes m JOIN') && sql.includes('m.Id = ?') && params[0] === MOV
          ? [[{ Id: MOV, Ativo: 1, SyncVersion: 1, Valor: '10.00', Descricao: 'x', CategoriaId: CAT, CategoriaTitulo: 'Frete', CategoriaTipo: 'Entrada',
                FormaPagamentoId: FORMA, FormaPagamentoNome: 'Pix', Status: 'Pago', DataPagamento: '2026-10-01', CriadoEm: '2026-10-01 10:00:00', AtualizadoEm: '2026-10-01 10:00:00' }]]
          : undefined,
    ],
  });
  api = await startServer({ pool });
});
after(() => api.close());

const admin = () => tokenFor(ADMIN.Id);
const fin = () => tokenFor(FIN.Id);

describe('validação de movimentações', () => {
  const valid = { valor: 150.5, descricao: 'Frete SP-MG', categoriaId: CAT, formaPagamentoId: FORMA };

  test('Pago sem dataPagamento → 400 apontando o campo', async () => {
    const res = await api.request('POST', '/api/movimentacoes', { token: fin(), body: { ...valid, status: 'Pago' } });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'VALIDATION_ERROR');
    assert.ok(res.body.error.details.some((d) => d.campo === 'dataPagamento'));
  });

  test('Pendente com dataPagamento → 400', async () => {
    const res = await api.request('POST', '/api/movimentacoes', { token: fin(), body: { ...valid, dataPagamento: '2026-10-10' } });
    assert.equal(res.status, 400);
  });

  test('valor com 3 casas decimais, zero ou negativo → 400', async () => {
    for (const valor of [10.123, 0, -5, 'abc']) {
      const res = await api.request('POST', '/api/movimentacoes', { token: fin(), body: { ...valid, valor } });
      assert.equal(res.status, 400, String(valor));
    }
  });

  test('data inexistente (31/02) → 400', async () => {
    const res = await api.request('POST', '/api/movimentacoes', { token: fin(), body: { ...valid, dataVencimento: '2026-02-31' } });
    assert.equal(res.status, 400);
  });

  test('movimentação válida é criada com o usuário logado como autor', async () => {
    const created = { ...valid, status: 'Pago', dataPagamento: '2026-10-10' };
    const res = await api.request('POST', '/api/movimentacoes', { token: fin(), body: created });
    // o fake devolve 404 no SELECT final (linha não existe); o que importa é o INSERT emitido
    const insert = pool.calls.filter((c) => c.sql.startsWith('INSERT INTO movimentacoes')).at(-1);
    assert.ok(insert, `status ${res.status}`);
    assert.equal(insert.params[1], '150.50', 'valor normalizado para 2 casas');
    assert.equal(insert.params.at(-1), FIN.Id, 'CriadoPorUsuarioId');
  });

  test('PATCH que deixa Pago sem data → 400', async () => {
    const res = await api.request('PATCH', `/api/movimentacoes/${MOV}`, { token: fin(), body: { dataPagamento: null } });
    assert.equal(res.status, 400);
  });

  test('PATCH voltando para Pendente limpa a data de pagamento', async () => {
    await api.request('PATCH', `/api/movimentacoes/${MOV}`, { token: fin(), body: { status: 'Pendente' } });
    const update = pool.calls.filter((c) => c.sql.startsWith('UPDATE movimentacoes SET')).at(-1);
    assert.ok(update.sql.includes('DataPagamento = ?'));
    assert.ok(update.params.includes(null));
  });

  test('pagar movimentação já paga → 409', async () => {
    const res = await api.request('POST', `/api/movimentacoes/${MOV}/pagar`, { token: fin(), body: { dataPagamento: '2026-10-11' } });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'ALREADY_PAID');
  });

  test('id que não é UUID → 400', async () => {
    const res = await api.request('GET', '/api/movimentacoes/123', { token: fin() });
    assert.equal(res.status, 400);
  });
});

describe('registros de sistema e proteções', () => {
  test('categoria de sistema não pode ser editada nem removida, nem pelo Administrador (CT-31)', async () => {
    const patch = await api.request('PATCH', `/api/categorias/${CAT}`, { token: admin(), body: { titulo: 'Outro' } });
    const del = await api.request('DELETE', `/api/categorias/${CAT}`, { token: admin() });
    assert.equal(patch.status, 403);
    assert.equal(patch.body.error.code, 'SYSTEM_RECORD');
    assert.equal(del.status, 403);
    assert.equal(del.body.error.code, 'SYSTEM_RECORD');
  });

  test('administrador não altera o próprio perfil nem status', async () => {
    const res = await api.request('PATCH', `/api/usuarios/${ADMIN.Id}`, { token: admin(), body: { status: 'Bloqueado' } });
    assert.equal(res.status, 403);
    assert.equal(res.body.error.code, 'SELF_MODIFICATION');
  });

  test('administrador não desativa a própria conta', async () => {
    const res = await api.request('DELETE', `/api/usuarios/${ADMIN.Id}`, { token: admin() });
    assert.equal(res.status, 403);
  });

  test('corpo JSON inválido → 400', async () => {
    const response = await fetch(`${api.base}/api/categorias`, {
      method: 'POST',
      headers: { authorization: `Bearer ${admin()}`, 'content-type': 'application/json' },
      body: '{titulo:',
    });
    assert.equal(response.status, 400);
    assert.equal((await response.json()).error.code, 'INVALID_JSON');
  });

  test('rota inexistente → 404 em JSON', async () => {
    const res = await api.request('GET', '/api/nada', { token: admin() });
    assert.equal(res.status, 404);
    assert.equal(res.body.error.code, 'ROUTE_NOT_FOUND');
  });
});

describe('utilitários', () => {
  test('moneyTimes calcula o total da dívida sem erro de ponto flutuante', () => {
    assert.equal(moneyTimes(36, '1234.56'), '44444.16');
    assert.equal(moneyTimes(480, '0.10'), '48.00');
    assert.equal(moneyTimes(1, '0.05'), '0.05');
    assert.equal(moneyTimes(480, '99999999999999.99'), '47999999999999995.20');
  });

  test('buildUpdate só inclui campos informados e respeita syncVersion', () => {
    const update = buildUpdate('categorias', 'id-1', { titulo: 'A', tipo: undefined }, { titulo: 'Titulo', tipo: 'Tipo' }, 3);
    assert.equal(update.sql, 'UPDATE categorias SET Titulo = ? WHERE Id = ? AND Ativo = 1 AND SyncVersion = ?');
    assert.deepEqual(update.params, ['A', 'id-1', 3]);
    assert.equal(buildUpdate('categorias', 'id-1', {}, { titulo: 'Titulo' }), null);
  });
});
