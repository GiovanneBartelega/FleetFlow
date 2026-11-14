import test from 'node:test';
import assert from 'node:assert/strict';
import { calcularIndicadoresViagem } from '../src/domain/indicadoresViagem.js';

test('calcula lucro, margem, custo por km e consumo sem erro de ponto flutuante visível', () => {
  const resultado = calcularIndicadoresViagem({
    receita: '1000.00', despesa: '375.50', custoVariavel: '250.00',
    kmRodados: 250, litros: '50.000', valorCombustivel: '300.00',
  });
  assert.deepEqual(resultado, {
    receita: 1000, despesa: 375.5, custoVariavel: 250, lucro: 624.5,
    margemPercentual: 62.45, kmRodados: 250, receitaPorKm: 4,
    custoPorKm: 1.502, custoVariavelPorKm: 1, lucroPorKm: 2.498,
    litros: 50, consumoMedioKmL: 5, precoMedioLitro: 6,
  });
});

test('não produz Infinity quando a viagem ainda não possui km ou abastecimento', () => {
  const resultado = calcularIndicadoresViagem({ receita: 100, despesa: 40, kmRodados: 0, litros: 0 });
  assert.equal(resultado.lucro, 60);
  assert.equal(resultado.custoPorKm, null);
  assert.equal(resultado.consumoMedioKmL, null);
  assert.equal(resultado.precoMedioLitro, null);
});
