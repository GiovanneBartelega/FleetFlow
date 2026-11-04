import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { makeFakePool, makeUser, startServer, tokenFor } from './helpers.js';

const MOTORISTA = makeUser({ id: '88888888-8888-4888-8888-888888888888', perfil: 'OperadorMotorista' });
const FIN = makeUser({ id: '66666666-6666-4666-8666-666666666666', perfil: 'Financeiro' });

const VEIC = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const VIAGEM = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
const VIAGEM_OUTRO_VEICULO = 'eeeeeeee-eeee-4eee-8eee-000000000000';
const FORMA = 'ffffffff-ffff-4fff-8fff-ffffffffffff';
const CATEGORIA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const MOV = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const MOV_EM_USO = 'cccccccc-cccc-4ccc-8ccc-000000000000';
const ABAST = '10000000-0000-4000-8000-000000000001';

const stamps = { CriadoEm: '2026-10-01 10:00:00', AtualizadoEm: '2026-10-01 10:00:00', Ativo: 1, SyncVersion: 1 };

const abastecimento = (id) => ({
  Id: id, ...stamps, VeiculoId: VEIC, VeiculoPlaca: 'ABC1D23', VeiculoMarca: 'Volvo', VeiculoModelo: 'FH 540',
  ViagemId: null, Litros: '100.00', ValorTotal: '600.00', PrecoLitro: '6.000', Posto: 'Posto Central',
  Hodometro: 120500, MovimentacaoId: MOV,
});

const selectAbastecimento = (sql) => sql.includes('FROM abastecimentos a JOIN veiculos v') && sql.includes('a.Id = ?');

let api;
let pool;
before(async () => {
  pool = makeFakePool({
    users: [MOTORISTA, FIN],
    handlers: [
      (sql) => (sql.startsWith('SELECT Placa FROM veiculos') ? [[{ Placa: 'ABC1D23' }]] : undefined),
      (sql, params) => (sql.startsWith('SELECT Id FROM veiculos WHERE Id') ? [[{ Id: params[0] }]] : undefined),
      (sql, params) =>
        sql.startsWith('SELECT VeiculoId FROM viagens')
          ? [[{ VeiculoId: params[0] === VIAGEM_OUTRO_VEICULO ? 'outro' : VEIC }]]
          : undefined,
      (sql, params) => (sql.startsWith('SELECT Id FROM formas_pagamento') ? [[{ Id: params[0] }]] : undefined),
      (sql, params) => (sql.startsWith('SELECT Id FROM movimentacoes WHERE Id') ? [[{ Id: params[0] }]] : undefined),
      (sql) => (sql.includes("FROM categorias WHERE Titulo = 'Combustível'") ? [[{ Id: CATEGORIA }]] : undefined),
      (sql, params) =>
        sql.startsWith('SELECT Id FROM abastecimentos WHERE MovimentacaoId')
          ? [params[0] === MOV_EM_USO ? [{ Id: 'outro-abastecimento' }] : []]
          : undefined,
      (sql, params) => (selectAbastecimento(sql) ? [[abastecimento(params[0])]] : undefined),
      (sql) =>
        sql.startsWith('SELECT COALESCE(SUM(a.Litros)') ? [[{ litros: '250.00', valorTotal: '1500.00' }]] : undefined,
    ],
  });
  api = await startServer({ pool });
});
after(() => api.close());

const motorista = () => tokenFor(MOTORISTA.Id);
const callsFrom = (prefix) => pool.calls.filter((c) => c.sql.startsWith(prefix));
const lastCall = (prefix) => callsFrom(prefix).at(-1);

const valid = { veiculoId: VEIC, litros: 100, valorTotal: '600.00', hodometro: 120500, formaPagamentoId: FORMA };

describe('abastecimentos · criação', () => {
  test('sem movimentacaoId lança a despesa paga e o abastecimento na mesma transação', async () => {
    const start = pool.calls.length;
    const res = await api.request('POST', '/api/abastecimentos', {
      token: motorista(),
      body: { ...valid, posto: ' Posto Central ', viagemId: VIAGEM },
    });
    assert.equal(res.status, 201);
    assert.equal(res.body.veiculo.placa, 'ABC1D23');

    const mov = lastCall('INSERT INTO movimentacoes');
    assert.equal(mov.params[1], '600.00');
    assert.equal(mov.params[2], 'Abastecimento ABC1D23 - Posto Central');
    assert.equal(mov.params[3], CATEGORIA, 'categoria de sistema Combustível');
    assert.equal(mov.params[4], FORMA);
    assert.equal(mov.params[7], '100.00', 'litros na movimentação');
    assert.equal(mov.params[8], MOTORISTA.Id, 'criado por');

    const ab = lastCall('INSERT INTO abastecimentos');
    assert.equal(ab.params[3], '100.00');
    assert.equal(ab.params[5], '6.000', 'preço do litro calculado');
    assert.equal(ab.params[6], 'Posto Central');
    assert.equal(ab.params[8], mov.params[0], 'ligado à movimentação criada');

    const steps = pool.calls.slice(start).map((c) => c.sql.split(' ').slice(0, 3).join(' '));
    assert.ok(steps.indexOf('BEGIN') < steps.findIndex((s) => s.startsWith('INSERT INTO movimentacoes')));
    assert.ok(steps.indexOf('COMMIT') > steps.findIndex((s) => s.startsWith('UPDATE veiculos SET')));
    assert.ok(!steps.includes('ROLLBACK'));
    assert.deepEqual(lastCall('UPDATE veiculos SET HodometroAtual').params, [120500, VEIC]);
  });

  test('com movimentacaoId não cria despesa nova', async () => {
    const antes = callsFrom('INSERT INTO movimentacoes').length;
    const { formaPagamentoId, ...body } = valid;
    const res = await api.request('POST', '/api/abastecimentos', {
      token: motorista(),
      body: { ...body, movimentacaoId: MOV, precoLitro: '6.001' },
    });
    assert.equal(res.status, 201);
    assert.equal(callsFrom('INSERT INTO movimentacoes').length, antes);
    const ab = lastCall('INSERT INTO abastecimentos');
    assert.equal(ab.params[5], '6.001');
    assert.equal(ab.params[8], MOV);
  });

  test('movimentação já ligada a outro abastecimento → 409', async () => {
    const { formaPagamentoId, ...body } = valid;
    const res = await api.request('POST', '/api/abastecimentos', {
      token: motorista(),
      body: { ...body, movimentacaoId: MOV_EM_USO },
    });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'DUPLICATE');
  });

  test('sem movimentacaoId e sem forma de pagamento → 400', async () => {
    const { formaPagamentoId, ...body } = valid;
    const res = await api.request('POST', '/api/abastecimentos', { token: motorista(), body });
    assert.equal(res.status, 400);
    assert.ok(res.body.error.details.some((d) => d.campo === 'formaPagamentoId'));
  });

  test('viagem de outro veículo → 400 apontando viagemId', async () => {
    const res = await api.request('POST', '/api/abastecimentos', {
      token: motorista(),
      body: { ...valid, viagemId: VIAGEM_OUTRO_VEICULO },
    });
    assert.equal(res.status, 400);
    assert.ok(res.body.error.details.some((d) => d.campo === 'viagemId'));
  });

  test('preço do litro que não bate com litros × total → 400', async () => {
    const res = await api.request('POST', '/api/abastecimentos', {
      token: motorista(),
      body: { ...valid, precoLitro: '7.500' },
    });
    assert.equal(res.status, 400);
    assert.ok(res.body.error.details.some((d) => d.campo === 'precoLitro'));
  });

  test('valores fora das regras do banco → 400', async () => {
    const cases = [
      { litros: 0 }, { litros: -5 }, { litros: 1234567 }, { litros: 'abc' }, { valorTotal: 0 }, { valorTotal: '-1' },
      { hodometro: -1 }, { hodometro: 1.5 }, { veiculoId: 'nao-e-uuid' }, { posto: '' }, { posto: 'x'.repeat(121) },
      { precoLitro: 0 },
    ];
    for (const extra of cases) {
      const res = await api.request('POST', '/api/abastecimentos', { token: motorista(), body: { ...valid, ...extra } });
      assert.equal(res.status, 400, JSON.stringify(extra));
    }
  });

  test('falha ao gravar desfaz a transação', async () => {
    const failing = makeFakePool({
      users: [MOTORISTA],
      handlers: [
        (sql) => (sql.startsWith('SELECT Placa FROM veiculos') ? [[{ Placa: 'ABC1D23' }]] : undefined),
        (sql, params) => (sql.startsWith('SELECT Id FROM formas_pagamento') ? [[{ Id: params[0] }]] : undefined),
        (sql) => (sql.includes("FROM categorias WHERE Titulo = 'Combustível'") ? [[{ Id: CATEGORIA }]] : undefined),
        (sql) => (sql.startsWith('INSERT INTO abastecimentos') ? Promise.reject(new Error('boom')) : undefined),
      ],
    });
    const local = await startServer({ pool: failing });
    const res = await local.request('POST', '/api/abastecimentos', { token: motorista(), body: valid });
    await local.close();
    assert.equal(res.status, 500);
    assert.ok(failing.calls.some((c) => c.sql === 'ROLLBACK'));
    assert.ok(!failing.calls.some((c) => c.sql === 'COMMIT'));
  });
});

describe('abastecimentos · leitura, edição e remoção', () => {
  test('Financeiro lê mas não grava', async () => {
    const token = tokenFor(FIN.Id);
    assert.equal((await api.request('GET', '/api/abastecimentos', { token })).status, 200);
    assert.equal((await api.request('POST', '/api/abastecimentos', { token, body: valid })).status, 403);
  });

  test('lista com filtros devolve página e totais', async () => {
    const res = await api.request(
      'GET',
      `/api/abastecimentos?veiculoId=${VEIC}&abastecidoDe=2026-10-01&busca=cen&pageSize=5`,
      { token: motorista() },
    );
    assert.equal(res.status, 200);
    assert.equal(res.body.pageSize, 5);
    assert.deepEqual(res.body.totais, { litros: '250.00', valorTotal: '1500.00' });
    assert.deepEqual(lastCall('SELECT a.Id').params, [VEIC, '2026-10-01', '%cen%', '%cen%', 5, 0]);
  });

  test('filtros inválidos na lista → 400', async () => {
    for (const query of ['veiculoId=x', 'abastecidoDe=ontem', 'pageSize=500']) {
      const res = await api.request('GET', `/api/abastecimentos?${query}`, { token: motorista() });
      assert.equal(res.status, 400, query);
    }
  });

  test('GET por id', async () => {
    const res = await api.request('GET', `/api/abastecimentos/${ABAST}`, { token: motorista() });
    assert.equal(res.status, 200);
    assert.equal(res.body.litros, '100.00');
    assert.equal(res.body.movimentacaoId, MOV);
  });

  test('PATCH sem campos → 400 EMPTY_UPDATE', async () => {
    const res = await api.request('PATCH', `/api/abastecimentos/${ABAST}`, { token: motorista(), body: {} });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'EMPTY_UPDATE');
  });

  test('PATCH de litros e total recalcula o preço e corrige a despesa ligada', async () => {
    const res = await api.request('PATCH', `/api/abastecimentos/${ABAST}`, {
      token: motorista(),
      body: { litros: 120, valorTotal: 720, syncVersion: 1 },
    });
    assert.equal(res.status, 200);
    const update = lastCall('UPDATE abastecimentos SET');
    assert.ok(update.sql.includes('PrecoLitro = ?') && update.sql.includes('SyncVersion = ?'));
    assert.ok(update.params.includes('6.000'));
    assert.deepEqual(lastCall('UPDATE movimentacoes SET Valor').params, ['720.00', '120.00', MOV]);
  });

  test('PATCH só do posto não mexe na despesa', async () => {
    const antes = callsFrom('UPDATE movimentacoes SET Valor').length;
    const res = await api.request('PATCH', `/api/abastecimentos/${ABAST}`, {
      token: motorista(),
      body: { posto: 'Outro' },
    });
    assert.equal(res.status, 200);
    assert.equal(callsFrom('UPDATE movimentacoes SET Valor').length, antes);
  });

  test('PATCH com syncVersion desatualizado → 409 VERSION_CONFLICT e desfaz', async () => {
    const stale = makeFakePool({
      users: [MOTORISTA],
      handlers: [
        (sql, params) => (selectAbastecimento(sql) ? [[abastecimento(params[0])]] : undefined),
        (sql) => (sql.startsWith('UPDATE abastecimentos SET') ? [{ affectedRows: 0 }] : undefined),
        (sql) => (sql.startsWith('SELECT Id FROM abastecimentos WHERE Id') ? [[{ Id: ABAST }]] : undefined),
      ],
    });
    const local = await startServer({ pool: stale });
    const res = await local.request('PATCH', `/api/abastecimentos/${ABAST}`, {
      token: motorista(),
      body: { posto: 'X', syncVersion: 99 },
    });
    await local.close();
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'VERSION_CONFLICT');
    assert.ok(stale.calls.some((c) => c.sql === 'ROLLBACK'));
  });

  test('DELETE faz exclusão lógica só do abastecimento', async () => {
    const res = await api.request('DELETE', `/api/abastecimentos/${ABAST}`, { token: motorista() });
    assert.equal(res.status, 204);
    assert.ok(lastCall('UPDATE abastecimentos SET Ativo = 0'));
  });
});
