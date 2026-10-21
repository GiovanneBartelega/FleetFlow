import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { makeFakePool, makeUser, startServer, tokenFor } from './helpers.js';

const GESTOR = makeUser({ id: '77777777-7777-4777-8777-777777777777', perfil: 'GestorFrota' });
const FIN = makeUser({ id: '66666666-6666-4666-8666-666666666666', perfil: 'Financeiro' });
const MOTORISTA = makeUser({ id: '88888888-8888-4888-8888-888888888888', perfil: 'OperadorMotorista' });

const VEIC = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const VEIC_VIAGEM = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
const VEIC_COM_OS = 'ffffffff-ffff-4fff-8fff-ffffffffffff';
const MOV = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const MOV_INEXISTENTE = '99999999-9999-4999-8999-999999999999';
const OS_ABERTA = '10000000-0000-4000-8000-000000000001';
const OS_CONCLUIDA = '10000000-0000-4000-8000-000000000002';

const stamps = { CriadoEm: '2026-10-01 10:00:00', AtualizadoEm: '2026-10-01 10:00:00', Ativo: 1, SyncVersion: 1 };

const veiculo = (id, status = 'Disponivel') => ({
  Id: id, ...stamps, Placa: 'ABC1D23', Marca: 'Volvo', Modelo: 'FH 540', AnoFabricacao: 2022,
  CapacidadeCargaToneladas: '32.00', Status: status, HodometroAtual: 120000,
  IntervaloManutencaoKm: 10000, IntervaloManutencaoMeses: null, HodometroUltimaManutencao: 110000,
  VencimentoLicenciamento: '2027-03-31', VencimentoSeguro: null, ConsumoMedioReferenciaKmL: '2.50',
});

const ordem = (id, dataConclusao = null) => ({
  Id: id, ...stamps, VeiculoId: VEIC, VeiculoPlaca: 'ABC1D23', VeiculoMarca: 'Volvo', VeiculoModelo: 'FH 540',
  DataManutencao: '2026-10-05', Descricao: 'Troca de óleo', Tipo: 'Preventiva', ValorTotal: '850.00',
  HodometroNaManutencao: 120000, DataConclusao: dataConclusao, MovimentacaoId: MOV,
});

const veiculos = { [VEIC_VIAGEM]: veiculo(VEIC_VIAGEM, 'EmViagem') };
const ordens = { [OS_CONCLUIDA]: ordem(OS_CONCLUIDA, '2026-10-06') };

let api;
let pool;
before(async () => {
  pool = makeFakePool({
    users: [GESTOR, FIN, MOTORISTA],
    handlers: [
      // veículos
      (sql, params) => (sql.startsWith('SELECT * FROM veiculos WHERE Id') ? [[veiculos[params[0]] ?? veiculo(params[0])]] : undefined),
      (sql, params) => (sql.startsWith('SELECT Id FROM veiculos WHERE Id') ? [[{ Id: params[0] }]] : undefined),
      (sql, params) => (sql.includes('FROM ordens_servico WHERE VeiculoId') ? [[{ total: params[0] === VEIC_COM_OS ? 1 : 0 }]] : undefined),
      (sql, params) =>
        sql.startsWith('INSERT INTO veiculos') && params[1] === 'DUP1234'
          ? Promise.reject(Object.assign(new Error('dup'), {
            code: 'ER_DUP_ENTRY', sqlMessage: "Duplicate entry 'DUP1234' for key 'veiculos.uq_veiculos_placa'",
          }))
          : undefined,
      (sql, params) => (sql.startsWith('UPDATE veiculos SET') && params.at(-1) === 99 ? [{ affectedRows: 0 }] : undefined),
      // movimentações e ordens de serviço
      (sql, params) =>
        sql.startsWith('SELECT Id FROM movimentacoes WHERE Id') ? [params[0] === MOV_INEXISTENTE ? [] : [{ Id: params[0] }]] : undefined,
      (sql, params) =>
        sql.includes('FROM ordens_servico o JOIN veiculos v') && sql.includes('o.Id = ?')
          ? [[ordens[params[0]] ?? ordem(params[0])]]
          : undefined,
    ],
  });
  api = await startServer({ pool });
});
after(() => api.close());

const gestor = () => tokenFor(GESTOR.Id);
const lastCall = (prefix) => pool.calls.filter((c) => c.sql.startsWith(prefix)).at(-1);

describe('veículos', () => {
  const valid = {
    placa: 'abc-1234', marca: 'Volvo', modelo: 'FH 540', anoFabricacao: 2022, capacidadeCargaToneladas: 32.5,
  };

  test('cria veículo normalizando placa e aplicando padrões', async () => {
    const res = await api.request('POST', '/api/veiculos', { token: gestor(), body: valid });
    assert.equal(res.status, 201);
    const insert = lastCall('INSERT INTO veiculos');
    assert.equal(insert.params[1], 'ABC1234', 'placa sem hífen e em maiúsculas');
    assert.equal(insert.params[5], '32.50', 'capacidade com 2 casas');
    assert.equal(insert.params[6], 'Disponivel', 'status padrão');
    assert.equal(insert.params[7], 0, 'hodômetro padrão');
    assert.equal(insert.params[8], null);
    assert.equal(res.body.placa, 'ABC1D23');
    assert.equal(res.body.capacidadeCargaToneladas, '32.00');
  });

  test('aceita placa Mercosul', async () => {
    const res = await api.request('POST', '/api/veiculos', { token: gestor(), body: { ...valid, placa: 'bra2e19' } });
    assert.equal(res.status, 201);
    assert.equal(lastCall('INSERT INTO veiculos').params[1], 'BRA2E19');
  });

  test('placas inválidas → 400 apontando o campo', async () => {
    for (const placa of ['', 'ABC12', 'AB1234', '1234ABC', 'ABC-12345', 'ABC1D2E']) {
      const res = await api.request('POST', '/api/veiculos', { token: gestor(), body: { ...valid, placa } });
      assert.equal(res.status, 400, placa);
      assert.ok(res.body.error.details.some((d) => d.campo === 'placa'), placa);
    }
  });

  test('campos fora das regras do banco → 400', async () => {
    const cases = [
      { anoFabricacao: 1949 }, { anoFabricacao: 2101 }, { anoFabricacao: 2020.5 },
      { capacidadeCargaToneladas: 0 }, { capacidadeCargaToneladas: -1 }, { capacidadeCargaToneladas: 1234567 },
      { capacidadeCargaToneladas: 10.123 }, { hodometroAtual: -1 }, { intervaloManutencaoKm: 0 },
      { intervaloManutencaoMeses: -3 }, { hodometroUltimaManutencao: -1 }, { consumoMedioReferenciaKmL: 0 },
      { vencimentoSeguro: '2027-02-30' }, { status: 'Quebrado' }, { marca: '' }, { modelo: 'x'.repeat(61) },
    ];
    for (const extra of cases) {
      const res = await api.request('POST', '/api/veiculos', { token: gestor(), body: { ...valid, ...extra } });
      assert.equal(res.status, 400, JSON.stringify(extra));
    }
  });

  test('placa repetida → 409 DUPLICATE com mensagem amigável', async () => {
    const res = await api.request('POST', '/api/veiculos', { token: gestor(), body: { ...valid, placa: 'DUP-1234' } });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'DUPLICATE');
    assert.match(res.body.error.message, /placa/);
  });

  test('lista com filtros monta a consulta e devolve página', async () => {
    const res = await api.request('GET', '/api/veiculos?status=EmViagem&busca=vol&licenciamentoAte=2027-01-01&pageSize=5', { token: gestor() });
    assert.equal(res.status, 200);
    assert.equal(res.body.pageSize, 5);
    assert.deepEqual(res.body.data, []);
    const select = lastCall('SELECT * FROM veiculos WHERE Ativo');
    assert.ok(select.sql.includes('Status = ?') && select.sql.includes('VencimentoLicenciamento <= ?'));
    assert.deepEqual(select.params, ['EmViagem', '2027-01-01', '%vol%', '%vol%', '%vol%', 5, 0]);
  });

  test('filtros inválidos na lista → 400', async () => {
    for (const query of ['status=Voando', 'pageSize=500', 'licenciamentoAte=ontem']) {
      const res = await api.request('GET', `/api/veiculos?${query}`, { token: gestor() });
      assert.equal(res.status, 400, query);
    }
  });

  test('PATCH sem campos → 400 EMPTY_UPDATE', async () => {
    const res = await api.request('PATCH', `/api/veiculos/${VEIC}`, { token: gestor(), body: {} });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'EMPTY_UPDATE');
  });

  test('PATCH atualiza só os campos enviados e aceita limpar opcionais', async () => {
    const res = await api.request('PATCH', `/api/veiculos/${VEIC}`, {
      token: gestor(), body: { status: 'EmManutencao', vencimentoSeguro: null, placa: 'xyz9k88' },
    });
    assert.equal(res.status, 200);
    const update = lastCall('UPDATE veiculos SET');
    assert.ok(update.sql.includes('Status = ?') && update.sql.includes('VencimentoSeguro = ?') && update.sql.includes('Placa = ?'));
    assert.ok(!update.sql.includes('Marca'));
    assert.ok(update.params.includes('XYZ9K88') && update.params.includes(null));
  });

  test('PATCH com syncVersion desatualizado → 409 VERSION_CONFLICT', async () => {
    const res = await api.request('PATCH', `/api/veiculos/${VEIC}`, { token: gestor(), body: { marca: 'Scania', syncVersion: 99 } });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'VERSION_CONFLICT');
  });

  test('id que não é UUID → 400', async () => {
    const res = await api.request('GET', '/api/veiculos/abc', { token: gestor() });
    assert.equal(res.status, 400);
  });

  test('veículo em viagem não pode ser removido', async () => {
    const res = await api.request('DELETE', `/api/veiculos/${VEIC_VIAGEM}`, { token: gestor() });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'IN_USE');
  });

  test('veículo com ordem de serviço em aberto não pode ser removido', async () => {
    const res = await api.request('DELETE', `/api/veiculos/${VEIC_COM_OS}`, { token: gestor() });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'IN_USE');
  });

  test('exclusão é lógica (Ativo = 0) e devolve 204', async () => {
    const res = await api.request('DELETE', `/api/veiculos/${VEIC}`, { token: gestor() });
    assert.equal(res.status, 204);
    assert.ok(lastCall('UPDATE veiculos SET Ativo = 0'));
  });

  test('Financeiro e Motorista leem mas não escrevem', async () => {
    for (const user of [FIN, MOTORISTA]) {
      const token = tokenFor(user.Id);
      assert.equal((await api.request('GET', '/api/veiculos', { token })).status, 200);
      assert.equal((await api.request('POST', '/api/veiculos', { token, body: valid })).status, 403);
    }
  });
});

describe('ordens de serviço', () => {
  const valid = {
    veiculoId: VEIC, dataManutencao: '2026-10-05', descricao: 'Troca de óleo e filtros',
    tipo: 'Preventiva', valorTotal: '850.5', movimentacaoId: MOV,
  };

  test('cria ordem normalizando o valor e devolve o veículo embutido', async () => {
    const res = await api.request('POST', '/api/ordens-servico', { token: gestor(), body: valid });
    assert.equal(res.status, 201);
    const insert = lastCall('INSERT INTO ordens_servico');
    assert.equal(insert.params[5], '850.50');
    assert.equal(insert.params[6], null, 'hodômetro opcional');
    assert.equal(insert.params[7], null, 'conclusão opcional');
    assert.equal(insert.params[8], MOV);
    assert.equal(res.body.veiculo.placa, 'ABC1D23');
    assert.equal(res.body.concluida, false);
  });

  test('referência inexistente → 400 INVALID_REFERENCE', async () => {
    const res = await api.request('POST', '/api/ordens-servico', { token: gestor(), body: { ...valid, movimentacaoId: MOV_INEXISTENTE } });
    assert.equal(res.status, 400);
    assert.equal(res.body.error.code, 'INVALID_REFERENCE');
    assert.ok(res.body.error.details.some((d) => d.campo === 'movimentacaoId'));
  });

  test('conclusão anterior à manutenção → 400 apontando o campo', async () => {
    const res = await api.request('POST', '/api/ordens-servico', { token: gestor(), body: { ...valid, dataConclusao: '2026-10-04' } });
    assert.equal(res.status, 400);
    assert.ok(res.body.error.details.some((d) => d.campo === 'dataConclusao'));
  });

  test('conclusão no mesmo dia da manutenção é aceita', async () => {
    const res = await api.request('POST', '/api/ordens-servico', { token: gestor(), body: { ...valid, dataConclusao: '2026-10-05' } });
    assert.equal(res.status, 201);
  });

  test('campos inválidos → 400', async () => {
    const cases = [
      { valorTotal: 0 }, { valorTotal: -10 }, { valorTotal: 10.123 }, { tipo: 'Estetica' }, { descricao: '' },
      { descricao: 'x'.repeat(201) }, { hodometroNaManutencao: -1 }, { dataManutencao: '2026-02-31' },
      { veiculoId: '123' }, { movimentacaoId: undefined },
    ];
    for (const extra of cases) {
      const res = await api.request('POST', '/api/ordens-servico', { token: gestor(), body: { ...valid, ...extra } });
      assert.equal(res.status, 400, JSON.stringify(extra));
    }
  });

  test('lista filtra por abertas e por veículo', async () => {
    const res = await api.request('GET', `/api/ordens-servico?concluida=false&veiculoId=${VEIC}&tipo=Corretiva&busca=oleo`, { token: gestor() });
    assert.equal(res.status, 200);
    const select = lastCall('SELECT o.Id');
    assert.ok(select.sql.includes('o.DataConclusao IS NULL'));
    assert.deepEqual(select.params, [VEIC, 'Corretiva', '%oleo%', '%oleo%', 20, 0]);
  });

  test('filtro concluida inválido → 400', async () => {
    const res = await api.request('GET', '/api/ordens-servico?concluida=talvez', { token: gestor() });
    assert.equal(res.status, 400);
  });

  test('PATCH que deixaria a conclusão antes da manutenção → 400', async () => {
    const res = await api.request('PATCH', `/api/ordens-servico/${OS_CONCLUIDA}`, { token: gestor(), body: { dataManutencao: '2026-10-10' } });
    assert.equal(res.status, 400);
    assert.ok(res.body.error.details.some((d) => d.campo === 'dataConclusao'));
  });

  test('PATCH pode reabrir a ordem limpando a conclusão', async () => {
    const res = await api.request('PATCH', `/api/ordens-servico/${OS_CONCLUIDA}`, { token: gestor(), body: { dataConclusao: null } });
    assert.equal(res.status, 200);
    const update = lastCall('UPDATE ordens_servico SET');
    assert.ok(update.sql.includes('DataConclusao = ?'));
    assert.ok(update.params.includes(null));
  });

  test('concluir grava a data de conclusão', async () => {
    const res = await api.request('POST', `/api/ordens-servico/${OS_ABERTA}/concluir`, { token: gestor(), body: { dataConclusao: '2026-10-07' } });
    assert.equal(res.status, 200);
    const update = lastCall('UPDATE ordens_servico SET');
    assert.deepEqual(update.params.slice(0, 2), ['2026-10-07', OS_ABERTA]);
  });

  test('concluir ordem já concluída → 409 ALREADY_COMPLETED', async () => {
    const res = await api.request('POST', `/api/ordens-servico/${OS_CONCLUIDA}/concluir`, { token: gestor(), body: { dataConclusao: '2026-10-08' } });
    assert.equal(res.status, 409);
    assert.equal(res.body.error.code, 'ALREADY_COMPLETED');
  });

  test('concluir antes da data da manutenção → 400; sem data → 400', async () => {
    const early = await api.request('POST', `/api/ordens-servico/${OS_ABERTA}/concluir`, { token: gestor(), body: { dataConclusao: '2026-10-01' } });
    const missing = await api.request('POST', `/api/ordens-servico/${OS_ABERTA}/concluir`, { token: gestor(), body: {} });
    assert.equal(early.status, 400);
    assert.equal(missing.status, 400);
  });

  test('exclusão é lógica e devolve 204', async () => {
    const res = await api.request('DELETE', `/api/ordens-servico/${OS_ABERTA}`, { token: gestor() });
    assert.equal(res.status, 204);
    assert.ok(lastCall('UPDATE ordens_servico SET Ativo = 0'));
  });

  test('Motorista não acessa manutenção; Financeiro só lê', async () => {
    assert.equal((await api.request('GET', '/api/ordens-servico', { token: tokenFor(MOTORISTA.Id) })).status, 403);
    const token = tokenFor(FIN.Id);
    assert.equal((await api.request('GET', '/api/ordens-servico', { token })).status, 200);
    assert.equal((await api.request('POST', '/api/ordens-servico', { token, body: valid })).status, 403);
  });
});

describe('veículo vinculado a dívidas e movimentações', () => {
  const CAT = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const FORMA = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const VEIC_INATIVO = '12345678-1234-4234-8234-123456789abc';
  let local;
  before(async () => {
    const fake = makeFakePool({
      users: [FIN],
      handlers: [
        (sql, params) =>
          sql.startsWith('SELECT Id FROM veiculos WHERE Id') ? [params[0] === VEIC_INATIVO ? [] : [{ Id: params[0] }]] : undefined,
        (sql) => (/^SELECT Id FROM (categorias|formas_pagamento) WHERE Id/.test(sql) ? [[{ Id: 'x' }]] : undefined),
      ],
    });
    local = await startServer({ pool: fake });
  });
  after(() => local.close());

  test('movimentação com veículo inativo/inexistente → 400 apontando veiculoId', async () => {
    const res = await local.request('POST', '/api/movimentacoes', {
      token: tokenFor(FIN.Id),
      body: { valor: 10, descricao: 'x', categoriaId: CAT, formaPagamentoId: FORMA, veiculoId: VEIC_INATIVO },
    });
    assert.equal(res.status, 400);
    assert.ok(res.body.error.details.some((d) => d.campo === 'veiculoId'));
  });

  test('dívida com veículo inativo/inexistente → 400 (POST e PATCH)', async () => {
    const body = {
      descricao: 'x', categoriaId: CAT, quantidadeParcelas: 2, valorParcela: 10,
      dataVencimentoPrimeira: '2026-11-01', veiculoId: VEIC_INATIVO,
    };
    const post = await local.request('POST', '/api/dividas', { token: tokenFor(FIN.Id), body });
    const patch = await local.request('PATCH', `/api/dividas/${CAT}`, { token: tokenFor(FIN.Id), body: { veiculoId: VEIC_INATIVO } });
    assert.equal(post.status, 400);
    assert.equal(patch.status, 400);
  });
});
