import { calcularIndicadoresViagem } from '../domain/indicadoresViagem.js';

export const AGRUPAMENTOS = ['veiculo', 'rota', 'motorista', 'viagem', 'mes'];
export const REGIMES = ['caixa', 'competencia'];

const chaveSql = {
  veiculo: { movimentacao: 'COALESCE(VeiculoId, ViagemVeiculoId, DividaVeiculoId)', viagem: 'v.VeiculoId', abastecimento: 'a.VeiculoId' },
  rota: { movimentacao: 'RotaId', viagem: 'v.RotaId', abastecimento: 'v.RotaId' },
  motorista: { movimentacao: 'MotoristaId', viagem: 'v.MotoristaId', abastecimento: 'v.MotoristaId' },
  viagem: { movimentacao: 'ViagemId', viagem: 'v.Id', abastecimento: 'a.ViagemId' },
  mes: { movimentacao: 'DATE_FORMAT(data_ref, \'%Y-%m\')', viagem: "DATE_FORMAT(v.DataFechamento, '%Y-%m')", abastecimento: "DATE_FORMAT(data_ref, '%Y-%m')" },
};

const dataRegime = (regime) => regime === 'caixa'
  ? { coluna: 'm.DataPagamento', status: "m.Status = 'Pago'" }
  : { coluna: 'm.DataVencimento', status: '1 = 1' };
const periodo = (coluna) => `${coluna} >= ? AND ${coluna} < DATE_ADD(?, INTERVAL 1 DAY)`;
const num = (value) => Number(value ?? 0);

function filtrosDaViagem({ veiculoId, rotaId, motoristaId }, alias = 'v') {
  const where = []; const params = [];
  if (veiculoId) { where.push(`${alias}.VeiculoId = ?`); params.push(veiculoId); }
  if (rotaId) { where.push(`${alias}.RotaId = ?`); params.push(rotaId); }
  if (motoristaId) { where.push(`${alias}.MotoristaId = ?`); params.push(motoristaId); }
  return { where, params };
}

function filtrosMovimentacao({ veiculoId, rotaId, motoristaId }) {
  const where = []; const params = [];
  if (veiculoId) { where.push('COALESCE(VeiculoId, ViagemVeiculoId, DividaVeiculoId) = ?'); params.push(veiculoId); }
  if (rotaId) { where.push('RotaId = ?'); params.push(rotaId); }
  if (motoristaId) { where.push('MotoristaId = ?'); params.push(motoristaId); }
  return { where, params };
}

function grupo(chave) {
  return { chave, receita: 0, despesa: 0, custoVariavel: 0, kmRodados: 0, litros: 0, valorCombustivel: 0, viagens: 0, viagensSemKm: 0 };
}

async function rotulos(pool, agrupar, chaves) {
  if (agrupar === 'mes' || chaves.length === 0) return new Map();
  const table = {
    veiculo: ['veiculos', 'Placa'], rota: ['rotas', 'Nome'], motorista: ['usuarios', 'Nome'], viagem: ['viagens', 'Id'],
  }[agrupar];
  if (!table) return new Map();
  const [rows] = await pool.query(`SELECT Id, ${table[1]} AS Rotulo FROM ${table[0]} WHERE Id IN (?)`, [chaves]);
  return new Map(rows.map((row) => [row.Id, row.Rotulo]));
}

export async function gerarRelatorio(pool, filtros) {
  const { agrupar, regime, inicio, fim } = filtros;
  const expr = chaveSql[agrupar];
  const data = dataRegime(regime);
  const { where: tripWhere, params: tripParams } = filtrosDaViagem(filtros);
  const { where: movWhere, params: movParamsFiltros } = filtrosMovimentacao(filtros);
  const movParams = [inicio, fim, ...movParamsFiltros];
  const movSql = `
    WITH base AS (
      SELECT m.Valor, c.Tipo, m.ViagemId, ${data.coluna} AS data_ref,
             m.VeiculoId, d.VeiculoId AS DividaVeiculoId, v.VeiculoId AS ViagemVeiculoId,
             v.RotaId, v.MotoristaId
        FROM movimentacoes m
        JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
        LEFT JOIN viagens v ON v.Id = m.ViagemId
        LEFT JOIN dividas d ON d.Id = m.DividaId
       WHERE m.Ativo = 1 AND ${data.status} AND ${periodo(data.coluna)}
    )
    SELECT ${expr.movimentacao} AS chave,
           Tipo AS tipo, SUM(Valor) AS valor,
           SUM(CASE WHEN ViagemId IS NOT NULL THEN Valor ELSE 0 END) AS custoVariavel
      FROM base
      ${movWhere.length ? `WHERE ${movWhere.join(' AND ')}` : ''}
     GROUP BY chave, Tipo`;

  const viagemSql = `SELECT ${expr.viagem} AS chave,
    COALESCE(SUM(v.HodometroFinal - v.HodometroInicial), 0) AS km,
    COUNT(*) AS viagens,
    SUM(CASE WHEN v.HodometroFinal IS NULL THEN 1 ELSE 0 END) AS semKm
    FROM viagens v WHERE v.Ativo = 1 AND v.Status = 'Encerrada' AND ${periodo('v.DataFechamento')}
    ${tripWhere.map((w) => `AND ${w}`).join(' ')} GROUP BY chave`;
  const abastSql = `SELECT ${expr.abastecimento} AS chave, SUM(a.Litros) AS litros, SUM(a.ValorTotal) AS valor
    FROM abastecimentos a
    JOIN movimentacoes m ON m.Id = a.MovimentacaoId AND m.Ativo = 1
    LEFT JOIN viagens v ON v.Id = a.ViagemId
    WHERE a.Ativo = 1 AND ${data.status} AND ${periodo(data.coluna)}
    ${tripWhere.map((w) => `AND ${w}`).join(' ')} GROUP BY chave`;
  const [[movimentos], [viagens], [abastecimentos]] = await Promise.all([
    pool.query(movSql, movParams),
    pool.query(viagemSql, [inicio, fim, ...tripParams]),
    pool.query(abastSql, [inicio, fim, ...tripParams]),
  ]);
  const grupos = new Map();
  const obter = (key) => { if (!grupos.has(key)) grupos.set(key, grupo(key)); return grupos.get(key); };
  for (const row of movimentos) {
    if (row.chave == null) continue;
    const g = obter(row.chave); if (row.tipo === 'Entrada') g.receita += num(row.valor); else { g.despesa += num(row.valor); g.custoVariavel += num(row.custoVariavel); }
  }
  for (const row of viagens) { const g = obter(row.chave); g.kmRodados += num(row.km); g.viagens += num(row.viagens); g.viagensSemKm += num(row.semKm); }
  for (const row of abastecimentos) { if (row.chave == null) continue; const g = obter(row.chave); g.litros += num(row.litros); g.valorCombustivel += num(row.valor); }
  const labels = await rotulos(pool, agrupar, [...grupos.keys()]);
  const formatar = (g) => ({ chave: g.chave, rotulo: agrupar === 'mes' ? g.chave : labels.get(g.chave) ?? g.chave, viagens: g.viagens, viagensSemKm: g.viagensSemKm, ...calcularIndicadoresViagem(g) });
  const lista = [...grupos.values()].map(formatar).sort((a, b) => String(a.rotulo).localeCompare(String(b.rotulo), 'pt-BR'));
  const total = [...grupos.values()].reduce((acc, item) => ({ ...acc, receita: acc.receita + item.receita, despesa: acc.despesa + item.despesa, custoVariavel: acc.custoVariavel + item.custoVariavel, kmRodados: acc.kmRodados + item.kmRodados, litros: acc.litros + item.litros, valorCombustivel: acc.valorCombustivel + item.valorCombustivel, viagens: acc.viagens + item.viagens, viagensSemKm: acc.viagensSemKm + item.viagensSemKm }), grupo(null));
  return { parametros: filtros, grupos: lista, totais: { viagens: total.viagens, viagensSemKm: total.viagensSemKm, ...calcularIndicadoresViagem(total) } };
}

// Dados tabulares para o PDF/CSV financeiro. Mantém os nomes previsíveis para
// os consumidores externos, em vez de fazê-los reconstruir as informações da
// listagem paginada de movimentações.
export async function gerarRelatorioFinanceiro(pool, filtros) {
  const data = dataRegime(filtros.regime);
  const where = ['m.Ativo = 1', data.status, periodo(data.coluna)];
  const params = [filtros.inicio, filtros.fim];
  const adicionar = (condicao, valor) => {
    if (valor) { where.push(condicao); params.push(valor); }
  };
  adicionar('m.Status = ?', filtros.status);
  adicionar('m.CategoriaId = ?', filtros.categoriaId);
  adicionar('m.FormaPagamentoId = ?', filtros.formaPagamentoId);
  adicionar('COALESCE(m.VeiculoId, v.VeiculoId, d.VeiculoId) = ?', filtros.veiculoId);
  adicionar('v.RotaId = ?', filtros.rotaId);
  adicionar('v.MotoristaId = ?', filtros.motoristaId);
  const clausula = where.join(' AND ');
  const de = `FROM movimentacoes m
    JOIN categorias c ON c.Id = m.CategoriaId
    JOIN formas_pagamento f ON f.Id = m.FormaPagamentoId
    LEFT JOIN viagens v ON v.Id = m.ViagemId
    LEFT JOIN dividas d ON d.Id = m.DividaId`;

  const [transacoes] = await pool.query(
    `SELECT m.Id, ${data.coluna} AS Data, m.Descricao, c.Titulo AS Categoria,
            c.Tipo AS Tipo, f.Nome AS FormaPagamento, m.Valor, m.Status
       ${de} WHERE ${clausula}
       ORDER BY ${data.coluna} ASC, m.CriadoEm ASC`,
    params,
  );
  const [resumo] = await pool.query(
    `SELECT m.CategoriaId, c.Titulo AS Categoria, c.Tipo AS Tipo,
            COUNT(*) AS Quantidade, SUM(m.Valor) AS Valor
       ${de} WHERE ${clausula}
       GROUP BY m.CategoriaId, c.Titulo, c.Tipo
       ORDER BY c.Tipo, c.Titulo`,
    params,
  );
  const linhas = transacoes.map((row) => ({
    id: row.Id,
    data: row.Data,
    descricao: row.Descricao,
    categoria: row.Categoria,
    tipo: row.Tipo,
    formaPagamento: row.FormaPagamento,
    valor: num(row.Valor),
    status: row.Status,
  }));
  const resumoPorCategoria = resumo.map((row) => ({
    categoriaId: row.CategoriaId,
    categoria: row.Categoria,
    tipo: row.Tipo,
    quantidade: num(row.Quantidade),
    valor: num(row.Valor),
  }));
  const totais = linhas.reduce((acc, linha) => {
    if (linha.tipo === 'Entrada') acc.receitas += linha.valor;
    else acc.despesas += linha.valor;
    return acc;
  }, { transacoes: linhas.length, receitas: 0, despesas: 0 });
  return {
    parametros: filtros,
    totais: { ...totais, resultado: totais.receitas - totais.despesas },
    transacoes: linhas,
    resumoPorCategoria,
  };
}

// Uma linha por viagem fechada, já com os indicadores solicitados pelo
// relatório logístico. O PDF/CSV não precisa inferir dados a partir de grupos.
export async function gerarRelatorioLogistico(pool, filtros) {
  const data = dataRegime(filtros.regime);
  const { where, params } = filtrosDaViagem(filtros);
  const [rows] = await pool.query(
    `WITH financeiro AS (
       SELECT m.ViagemId,
              SUM(CASE WHEN c.Tipo = 'Entrada' THEN m.Valor ELSE 0 END) AS Receitas,
              SUM(CASE WHEN c.Tipo = 'Saida' THEN m.Valor ELSE 0 END) AS Despesas
         FROM movimentacoes m
         JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
        WHERE m.Ativo = 1 AND m.ViagemId IS NOT NULL
          AND ${data.status} AND ${periodo(data.coluna)}
        GROUP BY m.ViagemId
     ), combustivel AS (
       SELECT a.ViagemId, SUM(a.Litros) AS Litros
         FROM abastecimentos a
         JOIN movimentacoes m ON m.Id = a.MovimentacaoId
        WHERE a.Ativo = 1 AND a.ViagemId IS NOT NULL
          AND m.Ativo = 1 AND ${data.status} AND ${periodo(data.coluna)}
        GROUP BY a.ViagemId
     )
     SELECT v.Id, v.DataFechamento, r.Nome AS Rota, ve.Placa AS Veiculo,
            u.Nome AS Motorista, (v.HodometroFinal - v.HodometroInicial) AS KmRodados,
            COALESCE(financeiro.Receitas, 0) AS Receitas,
            COALESCE(financeiro.Despesas, 0) AS Despesas,
            COALESCE(combustivel.Litros, 0) AS Litros
       FROM viagens v
       JOIN rotas r ON r.Id = v.RotaId
       JOIN veiculos ve ON ve.Id = v.VeiculoId
       JOIN usuarios u ON u.Id = v.MotoristaId
       LEFT JOIN financeiro ON financeiro.ViagemId = v.Id
       LEFT JOIN combustivel ON combustivel.ViagemId = v.Id
      WHERE v.Ativo = 1 AND v.Status = 'Encerrada'
        AND ${periodo('v.DataFechamento')}
        ${where.map((condicao) => `AND ${condicao}`).join(' ')}
      ORDER BY v.DataFechamento ASC, v.Id ASC`,
    [filtros.inicio, filtros.fim, filtros.inicio, filtros.fim, filtros.inicio, filtros.fim, ...params],
  );
  const linhas = rows.map((row) => {
    const kmRodados = num(row.KmRodados);
    const receitas = num(row.Receitas);
    const despesas = num(row.Despesas);
    const litros = num(row.Litros);
    return {
      id: row.Id,
      data: row.DataFechamento,
      rota: row.Rota,
      veiculo: row.Veiculo,
      motorista: row.Motorista,
      kmRodados,
      receitas,
      despesas,
      resultado: receitas - despesas,
      custoPorKm: kmRodados > 0 ? despesas / kmRodados : null,
      litros,
      consumoMedio: litros > 0 ? kmRodados / litros : null,
    };
  });
  const totais = linhas.reduce((acc, linha) => ({
    viagens: acc.viagens + 1,
    kmRodados: acc.kmRodados + linha.kmRodados,
    receitas: acc.receitas + linha.receitas,
    despesas: acc.despesas + linha.despesas,
    litros: acc.litros + linha.litros,
  }), { viagens: 0, kmRodados: 0, receitas: 0, despesas: 0, litros: 0 });
  return {
    parametros: filtros,
    linhas,
    totais: {
      ...totais,
      resultado: totais.receitas - totais.despesas,
      custoPorKm: totais.kmRodados > 0 ? totais.despesas / totais.kmRodados : null,
      consumoMedio: totais.litros > 0 ? totais.kmRodados / totais.litros : null,
    },
  };
}
