import test from 'node:test';
import assert from 'node:assert/strict';
import { makeFakePool, makeUser, startServer, tokenFor } from './helpers.js';

const admin = makeUser({ id: '11111111-1111-4111-8111-111111111111', perfil: 'Administrador' });
const motorista = makeUser({ id: '22222222-2222-4222-8222-222222222222', perfil: 'OperadorMotorista' });

function poolIndicadores() {
  return makeFakePool({
    users: [admin, motorista],
    handlers: [(sql) => {
      if (sql.includes('indicadores:kpis')) return [[{ tipo: 'Entrada', total: '500.00' }, { tipo: 'Saida', total: '125.00' }]];
      if (sql.includes('indicadores:fluxo-caixa')) return [[{ mes: '2026-11', tipo: 'Entrada', total: '500.00' }, { mes: '2026-11', tipo: 'Saida', total: '125.00' }]];
      if (sql.includes('indicadores:despesas-categoria')) return [[{ categoriaId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', categoria: 'Combustível', total: '125.00' }]];
      if (sql.includes('indicadores:rankings')) return [[{ veiculoId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', placa: 'ABC1D23', tipo: 'Entrada', total: '500.00' }, { veiculoId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', placa: 'ABC1D23', tipo: 'Saida', total: '125.00' }]];
      if (sql.includes('indicadores:saldo-inicial')) return [[{ tipo: 'Entrada', total: '100.00' }]];
      if (sql.includes('indicadores:previsao')) return [[{ mes: '2026-11', tipo: 'Saida', total: '150.00' }]];
      return undefined;
    }],
  });
}

test('indicadores devolvem KPIs, gráficos, ranking, previsão e alertas', async () => {
  const app = await startServer({ pool: poolIndicadores() });
  const token = tokenFor(admin.Id);
  try {
    const query = '?inicio=2026-11-01&fim=2026-11-30';
    const kpis = await app.request('GET', `/api/indicadores/kpis${query}`, { token });
    assert.equal(kpis.status, 200);
    assert.deepEqual(kpis.body, { inicio: '2026-11-01', fim: '2026-11-30', receitas: 500, despesas: 125, resultado: 375, margemPercentual: 75 });

    const fluxo = await app.request('GET', `/api/indicadores/fluxo-caixa${query}`, { token });
    assert.equal(fluxo.status, 200);
    assert.equal(fluxo.body.meses[0].saldoAcumulado, 375);

    const categorias = await app.request('GET', `/api/indicadores/despesas-por-categoria${query}`, { token });
    assert.equal(categorias.status, 200);
    assert.equal(categorias.body.categorias[0].percentual, 100);

    const ranking = await app.request('GET', `/api/indicadores/rankings${query}`, { token });
    assert.equal(ranking.status, 200);
    assert.equal(ranking.body.maisRentaveis[0].resultado, 375);

    const previsao = await app.request('GET', '/api/indicadores/previsao-caixa?inicio=2026-11-01', { token });
    assert.equal(previsao.status, 200);
    assert.equal(previsao.body.previsao.length, 12);
    assert.equal(previsao.body.previsao[0].saldoProjetado, -50);

    const alertas = await app.request('GET', '/api/indicadores/alertas-saldo?inicio=2026-11-01', { token });
    assert.equal(alertas.status, 200);
    assert.equal(alertas.body.alertas[0].tipo, 'SALDO_NEGATIVO_PREVISTO');
  } finally { await app.close(); }
});

test('indicadores validam período e bloqueiam motorista', async () => {
  const app = await startServer({ pool: poolIndicadores() });
  try {
    const invalido = await app.request('GET', '/api/indicadores/kpis?inicio=2026-12-01&fim=2026-11-01', { token: tokenFor(admin.Id) });
    assert.equal(invalido.status, 400);
    const proibido = await app.request('GET', '/api/indicadores/kpis?inicio=2026-11-01&fim=2026-11-30', { token: tokenFor(motorista.Id) });
    assert.equal(proibido.status, 403);
  } finally { await app.close(); }
});
