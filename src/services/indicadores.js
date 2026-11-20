// Indicadores da entrega de 18/11.
// Todo valor financeiro nasce em movimentacoes; categorias definem o sinal.

const numero = (valor) => Number(valor ?? 0);
const dinheiro = (valor) => Number(numero(valor).toFixed(2));

const periodo = (coluna) => `${coluna} >= ? AND ${coluna} < DATE_ADD(?, INTERVAL 1 DAY)`;

function mesesAPartirDe(inicio, quantidade = 12) {
  const data = new Date(`${inicio}T00:00:00Z`);
  return Array.from({ length: quantidade }, (_, indice) => {
    const atual = new Date(Date.UTC(data.getUTCFullYear(), data.getUTCMonth() + indice, 1));
    return atual.toISOString().slice(0, 7);
  });
}

function chaveMes(data) {
  return String(data ?? '').slice(0, 7);
}

function porTipo(rows) {
  const totais = { receitas: 0, despesas: 0 };
  for (const row of rows) {
    if (row.tipo === 'Entrada') totais.receitas += numero(row.total);
    if (row.tipo === 'Saida') totais.despesas += numero(row.total);
  }
  return { receitas: dinheiro(totais.receitas), despesas: dinheiro(totais.despesas) };
}

export async function buscarKpis(pool, { inicio, fim }) {
  const [rows] = await pool.query(
    `/* indicadores:kpis */
     SELECT c.Tipo AS tipo, SUM(m.Valor) AS total
       FROM movimentacoes m
       JOIN categorias c ON c.Id = m.CategoriaId
      WHERE m.Ativo = 1 AND c.Ativo = 1 AND m.Status = 'Pago'
        AND ${periodo('m.DataPagamento')}
      GROUP BY c.Tipo`,
    [inicio, fim],
  );
  const { receitas, despesas } = porTipo(rows);
  return { inicio, fim, receitas, despesas, resultado: dinheiro(receitas - despesas), margemPercentual: receitas ? dinheiro(((receitas - despesas) * 100) / receitas) : null };
}

export async function buscarFluxoCaixa(pool, { inicio, fim }) {
  const [rows] = await pool.query(
    `/* indicadores:fluxo-caixa */
     SELECT DATE_FORMAT(m.DataPagamento, '%Y-%m') AS mes, c.Tipo AS tipo, SUM(m.Valor) AS total
       FROM movimentacoes m
       JOIN categorias c ON c.Id = m.CategoriaId
      WHERE m.Ativo = 1 AND c.Ativo = 1 AND m.Status = 'Pago'
        AND ${periodo('m.DataPagamento')}
      GROUP BY DATE_FORMAT(m.DataPagamento, '%Y-%m'), c.Tipo
      ORDER BY mes`,
    [inicio, fim],
  );
  const meses = new Map();
  for (const row of rows) {
    const mes = chaveMes(row.mes);
    if (!meses.has(mes)) meses.set(mes, { mes, receitas: 0, despesas: 0 });
    const item = meses.get(mes);
    if (row.tipo === 'Entrada') item.receitas += numero(row.total);
    if (row.tipo === 'Saida') item.despesas += numero(row.total);
  }
  let saldoAcumulado = 0;
  return { inicio, fim, meses: [...meses.values()].sort((a, b) => a.mes.localeCompare(b.mes)).map((item) => {
    const resultado = item.receitas - item.despesas;
    saldoAcumulado += resultado;
    return { ...item, receitas: dinheiro(item.receitas), despesas: dinheiro(item.despesas), resultado: dinheiro(resultado), saldoAcumulado: dinheiro(saldoAcumulado) };
  }) };
}

export async function buscarDespesasPorCategoria(pool, { inicio, fim }) {
  const [rows] = await pool.query(
    `/* indicadores:despesas-categoria */
     SELECT c.Id AS categoriaId, c.Titulo AS categoria, SUM(m.Valor) AS total
       FROM movimentacoes m
       JOIN categorias c ON c.Id = m.CategoriaId
      WHERE m.Ativo = 1 AND c.Ativo = 1 AND c.Tipo = 'Saida' AND m.Status = 'Pago'
        AND ${periodo('m.DataPagamento')}
      GROUP BY c.Id, c.Titulo
      ORDER BY total DESC, categoria`,
    [inicio, fim],
  );
  const total = rows.reduce((soma, row) => soma + numero(row.total), 0);
  return { inicio, fim, total: dinheiro(total), categorias: rows.map((row) => ({ categoriaId: row.categoriaId, categoria: row.categoria, valor: dinheiro(row.total), percentual: total ? dinheiro((numero(row.total) * 100) / total) : 0 })) };
}

export async function buscarRankings(pool, { inicio, fim, limite }) {
  const [rows] = await pool.query(
    `/* indicadores:rankings */
     SELECT v.Id AS veiculoId, v.Placa AS placa, c.Tipo AS tipo, SUM(m.Valor) AS total
       FROM movimentacoes m
       JOIN categorias c ON c.Id = m.CategoriaId
       LEFT JOIN viagens vi ON vi.Id = m.ViagemId
       LEFT JOIN dividas d ON d.Id = m.DividaId
       LEFT JOIN veiculos v ON v.Id = COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId)
      WHERE m.Ativo = 1 AND c.Ativo = 1 AND m.Status = 'Pago'
        AND ${periodo('m.DataPagamento')}
      GROUP BY v.Id, v.Placa, c.Tipo`,
    [inicio, fim],
  );
  const veiculos = new Map();
  for (const row of rows) {
    if (!row.veiculoId) continue;
    if (!veiculos.has(row.veiculoId)) veiculos.set(row.veiculoId, { veiculoId: row.veiculoId, placa: row.placa, receitas: 0, despesas: 0 });
    const item = veiculos.get(row.veiculoId);
    if (row.tipo === 'Entrada') item.receitas += numero(row.total);
    if (row.tipo === 'Saida') item.despesas += numero(row.total);
  }
  const ranking = [...veiculos.values()].map((item) => ({ ...item, receitas: dinheiro(item.receitas), despesas: dinheiro(item.despesas), resultado: dinheiro(item.receitas - item.despesas) }));
  return {
    inicio,
    fim,
    maisRentaveis: [...ranking].sort((a, b) => b.resultado - a.resultado || a.placa.localeCompare(b.placa)).slice(0, limite),
    maioresDespesas: [...ranking].sort((a, b) => b.despesas - a.despesas || a.placa.localeCompare(b.placa)).slice(0, limite),
  };
}

export async function buscarPrevisaoCaixa(pool, { inicio, meses = 12 }) {
  const fim = mesesAPartirDe(inicio, meses).at(-1);
  const [saldoRows] = await pool.query(
    `/* indicadores:saldo-inicial */
     SELECT c.Tipo AS tipo, SUM(m.Valor) AS total
       FROM movimentacoes m JOIN categorias c ON c.Id = m.CategoriaId
      WHERE m.Ativo = 1 AND c.Ativo = 1 AND m.Status = 'Pago' AND m.DataPagamento < ?
      GROUP BY c.Tipo`,
    [inicio],
  );
  const [previstos] = await pool.query(
    `/* indicadores:previsao */
     SELECT DATE_FORMAT(m.DataVencimento, '%Y-%m') AS mes, c.Tipo AS tipo, SUM(m.Valor) AS total
       FROM movimentacoes m JOIN categorias c ON c.Id = m.CategoriaId
      WHERE m.Ativo = 1 AND c.Ativo = 1 AND m.Status = 'Pendente'
        AND m.DataVencimento >= ? AND m.DataVencimento < DATE_ADD(?, INTERVAL 1 MONTH)
      GROUP BY DATE_FORMAT(m.DataVencimento, '%Y-%m'), c.Tipo`,
    [inicio, fim],
  );
  const saldoInicial = porTipo(saldoRows).receitas - porTipo(saldoRows).despesas;
  const porMes = new Map(mesesAPartirDe(inicio, meses).map((mes) => [mes, { mes, entradas: 0, saidas: 0 }]));
  for (const row of previstos) {
    const item = porMes.get(chaveMes(row.mes));
    if (!item) continue;
    if (row.tipo === 'Entrada') item.entradas += numero(row.total);
    if (row.tipo === 'Saida') item.saidas += numero(row.total);
  }
  let saldoProjetado = saldoInicial;
  return { inicio, meses, saldoInicial: dinheiro(saldoInicial), previsao: [...porMes.values()].map((item) => {
    saldoProjetado += item.entradas - item.saidas;
    return { ...item, entradas: dinheiro(item.entradas), saidas: dinheiro(item.saidas), resultado: dinheiro(item.entradas - item.saidas), saldoProjetado: dinheiro(saldoProjetado) };
  }) };
}

export async function buscarAlertasSaldo(pool, filtros) {
  const previsao = await buscarPrevisaoCaixa(pool, filtros);
  const alertas = previsao.previsao.filter((item) => item.saldoProjetado < 0).map((item) => ({ tipo: 'SALDO_NEGATIVO_PREVISTO', mes: item.mes, saldoProjetado: item.saldoProjetado, mensagem: `Saldo previsto negativo em ${item.mes}.` }));
  return { inicio: previsao.inicio, meses: previsao.meses, alertas };
}
