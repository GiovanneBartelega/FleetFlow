import {
  sqlCombustivel,
  sqlKm,
  sqlMovimentacoes,
  sqlRotulos,
} from '../queries/relatorios.sql.js';

// DECIMAL chega do driver como string. Somamos em centavos (inteiros) para não
// acumular erro de ponto flutuante e só dividimos no final.
const centavos = (valor) => Math.round(Number(valor ?? 0) * 100);
const emReais = (cents) => cents / 100;

// Divisão segura: denominador 0/ausente -> null (nunca Infinity/NaN).
const razao = (numerador, denominador, casas) =>
  denominador ? Number((numerador / denominador).toFixed(casas)) : null;

// Dinheiro e percentuais: 2 casas. Razões (por km, km/l, preço do litro): 4 casas,
// senão custo/km pequeno (ex.: 0,004) viraria 0,00 e a conferência não teria o que comparar.
const CASAS_RAZAO = 4;

const rodar = (pool, { sql, params }) => pool.query(sql, params);

function novoGrupo(chave) {
  return {
    chave,
    receita: 0,
    custo: 0,
    custoVariavel: 0, // custo ligado a viagem (regra 6)
    porCategoria: new Map(), // categoria -> centavos (somente Saída)
    manutencao: { preventiva: 0, corretiva: 0, semClassificacao: 0 },
    km: 0,
    qtdViagens: 0,
    viagensSemKm: 0,
    litros: 0, // em centésimos de litro
    valorCombustivel: 0,
  };
}

function formatarGrupo(g) {
  const lucro = g.receita - g.custo;
  const km = g.km;
  const litros = g.litros / 100;
  return {
    receita: emReais(g.receita),
    custoTotal: emReais(g.custo),
    custoVariavel: emReais(g.custoVariavel),
    lucro: emReais(lucro),
    margemPercentual: razao(lucro * 100, g.receita, 2),
    kmRodados: km,
    qtdViagens: g.qtdViagens,
    viagensSemKm: g.viagensSemKm,
    receitaPorKm: razao(emReais(g.receita), km, CASAS_RAZAO),
    custoPorKm: {
      variavel: razao(emReais(g.custoVariavel), km, CASAS_RAZAO),
      total: razao(emReais(g.custo), km, CASAS_RAZAO),
    },
    lucroPorKm: razao(emReais(lucro), km, CASAS_RAZAO),
    litros,
    consumoMedioKmL: razao(km, litros, CASAS_RAZAO),
    precoMedioLitro: razao(emReais(g.valorCombustivel), litros, CASAS_RAZAO),
    custosPorCategoria: [...g.porCategoria]
      .map(([categoria, cents]) => ({ categoria, valor: emReais(cents) }))
      .sort((a, b) => b.valor - a.valor || a.categoria.localeCompare(b.categoria, 'pt-BR')),
    manutencao: {
      preventiva: emReais(g.manutencao.preventiva),
      corretiva: emReais(g.manutencao.corretiva),
      semClassificacao: emReais(g.manutencao.semClassificacao),
    },
  };
}

function somar(destino, origem) {
  destino.receita += origem.receita;
  destino.custo += origem.custo;
  destino.custoVariavel += origem.custoVariavel;
  destino.km += origem.km;
  destino.qtdViagens += origem.qtdViagens;
  destino.viagensSemKm += origem.viagensSemKm;
  destino.litros += origem.litros;
  destino.valorCombustivel += origem.valorCombustivel;
  for (const [categoria, cents] of origem.porCategoria) {
    destino.porCategoria.set(categoria, (destino.porCategoria.get(categoria) ?? 0) + cents);
  }
  for (const tipo of Object.keys(destino.manutencao)) destino.manutencao[tipo] += origem.manutencao[tipo];
}

// Monta o relatório completo. As duas rotas (rentabilidade e custo-km) projetam
// o que precisam a partir daqui, então os números são sempre os mesmos.
export async function gerarRelatorio(pool, filtros) {
  const { agrupar } = filtros;

  // Cada fonte é consultada e agregada à parte; a junção acontece aqui, por chave.
  const [[movimentacoes], [viagens], [combustivel]] = await Promise.all([
    rodar(pool, sqlMovimentacoes(filtros)),
    rodar(pool, sqlKm(filtros)),
    rodar(pool, sqlCombustivel(filtros)),
  ]);

  const grupos = new Map();
  const grupoDe = (chave) => {
    if (!grupos.has(chave)) grupos.set(chave, novoGrupo(chave));
    return grupos.get(chave);
  };
  const naoAlocado = novoGrupo(null);

  for (const row of movimentacoes) {
    const alvo = row.chave === null ? naoAlocado : grupoDe(row.chave);
    const cents = centavos(row.valor);
    if (row.tipo === 'Entrada') {
      alvo.receita += cents;
      continue;
    }
    alvo.custo += cents;
    if (Number(row.vinculada) === 1) alvo.custoVariavel += cents;
    alvo.porCategoria.set(row.categoria, (alvo.porCategoria.get(row.categoria) ?? 0) + cents);
    // ordens_servico só classifica a manutenção; o valor continua vindo da movimentação.
    if (row.os_tipo === 'Preventiva') alvo.manutencao.preventiva += cents;
    else if (row.os_tipo === 'Corretiva') alvo.manutencao.corretiva += cents;
    else if (row.categoria === 'Manutenção') alvo.manutencao.semClassificacao += cents;
  }

  for (const row of viagens) {
    const alvo = grupoDe(row.chave);
    alvo.km += Number(row.km);
    alvo.qtdViagens += Number(row.qtd_viagens);
    alvo.viagensSemKm += Number(row.sem_km);
  }

  for (const row of combustivel) {
    if (row.chave === null) continue;
    const alvo = grupoDe(row.chave);
    alvo.litros += centavos(row.litros);
    alvo.valorCombustivel += centavos(row.valor);
  }

  const rotulos = await buscarRotulos(pool, agrupar, [...grupos.keys()]);

  const lista = [...grupos.values()].map((g) => ({
    chave: g.chave,
    rotulo: rotulos.get(g.chave) ?? g.chave,
    ...formatarGrupo(g),
  }));
  lista.sort((a, b) =>
    agrupar === 'mes'
      ? a.chave.localeCompare(b.chave)
      : b.lucro - a.lucro || String(a.rotulo).localeCompare(String(b.rotulo), 'pt-BR'),
  );

  const totais = novoGrupo(null);
  for (const g of grupos.values()) somar(totais, g);

  const semAlocacao = formatarGrupo(naoAlocado);

  return {
    parametros: {
      inicio: filtros.inicio,
      fim: filtros.fim,
      agrupar,
      regime: filtros.regime,
      veiculoId: filtros.veiculoId ?? null,
      rotaId: filtros.rotaId ?? null,
      motoristaId: filtros.motoristaId ?? null,
    },
    grupos: lista,
    totais: formatarGrupo(totais),
    // O que não se atribui à dimensão escolhida (regra 4): fica fora dos grupos e dos totais.
    custosNaoAlocados: {
      total: semAlocacao.custoTotal,
      porCategoria: semAlocacao.custosPorCategoria,
    },
    receitaNaoAlocada: semAlocacao.receita,
    viagensSemKm: totais.viagensSemKm,
  };
}

async function buscarRotulos(pool, agrupar, chaves) {
  const consulta = chaves.length ? sqlRotulos(agrupar, chaves) : null;
  if (!consulta) return new Map();
  const [rows] = await pool.query(consulta.sql, consulta.params);
  return new Map(rows.map((r) => [r.chave, r.rotulo]));
}

export async function rentabilidade(pool, filtros) {
  const r = await gerarRelatorio(pool, filtros);
  const campos = (g) => ({
    receita: g.receita,
    custoTotal: g.custoTotal,
    lucro: g.lucro,
    margemPercentual: g.margemPercentual,
    kmRodados: g.kmRodados,
    qtdViagens: g.qtdViagens,
    viagensSemKm: g.viagensSemKm,
    receitaPorKm: g.receitaPorKm,
    custoPorKm: g.custoPorKm,
    lucroPorKm: g.lucroPorKm,
    litros: g.litros,
    consumoMedioKmL: g.consumoMedioKmL,
    precoMedioLitro: g.precoMedioLitro,
    custosPorCategoria: g.custosPorCategoria,
    manutencao: g.manutencao,
  });
  return {
    ...r,
    grupos: r.grupos.map((g) => ({ chave: g.chave, rotulo: g.rotulo, ...campos(g) })),
    totais: campos(r.totais),
  };
}

export async function custoKm(pool, filtros) {
  const r = await gerarRelatorio(pool, filtros);
  const campos = (g) => ({
    kmRodados: g.kmRodados,
    qtdViagens: g.qtdViagens,
    viagensSemKm: g.viagensSemKm,
    custoVariavel: g.custoVariavel,
    custoTotal: g.custoTotal,
    custoVariavelPorKm: g.custoPorKm.variavel,
    custoTotalPorKm: g.custoPorKm.total,
    litros: g.litros,
    consumoMedioKmL: g.consumoMedioKmL,
    precoMedioLitro: g.precoMedioLitro,
    custosPorCategoria: g.custosPorCategoria,
  });
  return {
    parametros: r.parametros,
    grupos: r.grupos.map((g) => ({ chave: g.chave, rotulo: g.rotulo, ...campos(g) })),
    totais: campos(r.totais),
    custosNaoAlocados: r.custosNaoAlocados,
    viagensSemKm: r.viagensSemKm,
  };
}
