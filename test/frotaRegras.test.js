import assert from 'node:assert/strict';
import test from 'node:test';
import { alertasDoVeiculo, kmRodados } from '../src/frotaRegras.js';

test('calcula km rodados e rejeita hodômetro regressivo', () => {
  assert.equal(kmRodados(120000, 120345), 345);
  assert.throws(() => kmRodados(120000, 119999), { code: 'INVALID_ODOMETER' });
});

test('gera alerta de manutenção por km vencida e documento próximo', () => {
  const alerts = alertasDoVeiculo({
    Id: 'v1', Placa: 'ABC1D23', HodometroAtual: 121000, HodometroUltimaManutencao: 110000,
    IntervaloManutencaoKm: 10000, IntervaloManutencaoMeses: null,
    VencimentoLicenciamento: '2026-11-20', VencimentoSeguro: null,
  }, { hoje: '2026-11-04', diasAntecedencia: 30 });
  assert.deepEqual(alerts.map((alert) => alert.tipo), ['MANUTENCAO_KM_VENCIDA', 'LICENCIAMENTO_PROXIMO']);
});

test('gera alerta temporal quando a manutenção preventiva vence', () => {
  const alerts = alertasDoVeiculo({
    Id: 'v2', Placa: 'DEF2G34', HodometroAtual: 500, HodometroUltimaManutencao: 0,
    IntervaloManutencaoKm: null, IntervaloManutencaoMeses: 6,
    UltimaManutencaoConcluida: '2026-04-01', VencimentoLicenciamento: null, VencimentoSeguro: null,
  }, { hoje: '2026-11-04' });
  assert.equal(alerts[0].tipo, 'MANUTENCAO_TEMPO_VENCIDA');
});
