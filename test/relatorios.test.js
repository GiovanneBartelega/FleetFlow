import test from 'node:test';
import assert from 'node:assert/strict';
import { makeFakePool, makeUser, startServer, tokenFor } from './helpers.js';

test('relatórios validam período e respeitam as permissões', async () => {
  const admin = makeUser({ id: '11111111-1111-4111-8111-111111111111', perfil: 'Administrador' });
  const motorista = makeUser({ id: '22222222-2222-4222-8222-222222222222', perfil: 'OperadorMotorista' });
  const app = await startServer({ pool: makeFakePool({ users: [admin, motorista] }) });
  try {
    const ok = await app.request('GET', '/api/relatorios/custo-km?inicio=2026-11-01&fim=2026-11-11', { token: tokenFor(admin.Id) });
    assert.equal(ok.status, 200);
    assert.deepEqual(ok.body.grupos, []);
    assert.equal(ok.body.totais.custoPorKm, null);

    const invalido = await app.request('GET', '/api/relatorios/rentabilidade?inicio=2026-11-12&fim=2026-11-11', { token: tokenFor(admin.Id) });
    assert.equal(invalido.status, 400);

    const proibido = await app.request('GET', '/api/relatorios/custo-km?inicio=2026-11-01&fim=2026-11-11', { token: tokenFor(motorista.Id) });
    assert.equal(proibido.status, 403);
  } finally { await app.close(); }
});

test('relatórios financeiro e logístico expõem contratos tabulares para PDF e CSV', async () => {
  const admin = makeUser({ id: '33333333-3333-4333-8333-333333333333', perfil: 'Administrador' });
  const app = await startServer({ pool: makeFakePool({ users: [admin] }) });
  try {
    const financeiro = await app.request(
      'GET',
      '/api/relatorios/financeiro?inicio=2026-11-01&fim=2026-11-30',
      { token: tokenFor(admin.Id) },
    );
    assert.equal(financeiro.status, 200);
    assert.deepEqual(financeiro.body.totais, { transacoes: 0, receitas: 0, despesas: 0, resultado: 0 });
    assert.deepEqual(financeiro.body.transacoes, []);
    assert.deepEqual(financeiro.body.resumoPorCategoria, []);

    const logistico = await app.request(
      'GET',
      '/api/relatorios/logistico?inicio=2026-11-01&fim=2026-11-30',
      { token: tokenFor(admin.Id) },
    );
    assert.equal(logistico.status, 200);
    assert.deepEqual(logistico.body.linhas, []);
    assert.equal(logistico.body.totais.viagens, 0);
    assert.equal(logistico.body.totais.custoPorKm, null);
    assert.equal(logistico.body.totais.consumoMedio, null);
  } finally { await app.close(); }
});
