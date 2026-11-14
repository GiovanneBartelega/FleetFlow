const numero = (valor) => Number(valor ?? 0);
const arredondar = (valor, casas = 2) => Number(numero(valor).toFixed(casas));
const dividir = (numerador, denominador, casas = 4) =>
  numero(denominador) > 0 ? arredondar(numero(numerador) / numero(denominador), casas) : null;

// Regras puras: usadas no detalhe da viagem e nos relatórios agregados.
// Valores monetários vêm do MySQL como DECIMAL/string e são normalizados aqui.
export function calcularIndicadoresViagem({ receita = 0, despesa = 0, custoVariavel = 0, kmRodados = 0, litros = 0, valorCombustivel = 0 } = {}) {
  const lucro = numero(receita) - numero(despesa);
  return {
    receita: arredondar(receita),
    despesa: arredondar(despesa),
    custoVariavel: arredondar(custoVariavel),
    lucro: arredondar(lucro),
    margemPercentual: dividir(lucro * 100, receita, 2),
    kmRodados: numero(kmRodados),
    receitaPorKm: dividir(receita, kmRodados),
    custoPorKm: dividir(despesa, kmRodados),
    custoVariavelPorKm: dividir(custoVariavel, kmRodados),
    lucroPorKm: dividir(lucro, kmRodados),
    litros: arredondar(litros, 3),
    consumoMedioKmL: dividir(kmRodados, litros),
    precoMedioLitro: dividir(valorCombustivel, litros),
  };
}
