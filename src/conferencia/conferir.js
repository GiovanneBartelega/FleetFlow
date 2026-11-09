import { custoKm, rentabilidade } from '../services/relatorios.js';
import { TOLERANCIA, compararValores } from './comparar.js';
import { consultar, numero } from './consultas.js';
import { verificarIntegridade } from './integridade.js';

const NOTA_VIEW =
  'vw_resumo_financeiro soma TODAS as movimentações ativas, de qualquer Status e de qualquer data, ' +
  'incluindo as sem veículo. O relatório filtra por regime e período. A diferença entre os dois é esperada ' +
  'e igual ao que ficou fora do escopo; só é divergência se sobrar resíduo depois de descontá-la.';

const somaPor = (rows, campo) => rows.reduce((total, row) => total + (numero(row[campo]) ?? 0), 0);
const arredonda = (valor) => Math.round(valor * 100) / 100;

const ESTRUTURA_NECESSARIA = [
  'abastecimentos', 'categorias', 'dividas', 'movimentacoes', 'ordens_servico', 'veiculos', 'viagens',
  'vw_contas_pagar', 'vw_resumo_financeiro',
];

// Evita um erro de SQL obscuro quando o banco está numa versão anterior do schema.
export async function verificarEstrutura(pool) {
  const [rows] = await pool.query(
    'SELECT table_name AS nome FROM information_schema.tables WHERE table_schema = DATABASE()',
  );
  const existentes = new Set(rows.map((r) => String(r.nome ?? r.TABLE_NAME).toLowerCase()));
  const faltando = ESTRUTURA_NECESSARIA.filter((nome) => !existentes.has(nome));
  if (faltando.length > 0) {
    throw new Error(
      `O banco está numa versão anterior do schema. Faltam: ${faltando.join(', ')}. ` +
        'Aplique "banco-de-dados versao final.sql" (em um banco novo) antes de conferir.',
    );
  }
}

// Roda o service do app (a função, sem HTTP) e compara, métrica a métrica e chave a chave,
// com as consultas manuais de src/conferencia/sql.
export async function conferir(pool, { inicio, fim, regime = 'caixa' }) {
  await verificarEstrutura(pool);
  const valores = { inicio, fim, regime };
  const filtros = { inicio, fim, regime, agrupar: 'veiculo' };

  const rent = await rentabilidade(pool, filtros);
  const ckm = await custoKm(pool, filtros);

  const [receita] = await consultar(pool, 'receita_total.sql', valores);
  const [custo] = await consultar(pool, 'custo_total.sql', valores);
  const kmRows = await consultar(pool, 'km_rodados.sql', valores);
  const categoriasBanco = await consultar(pool, 'custo_por_categoria.sql', valores);
  const resultadoRows = await consultar(pool, 'resultado_por_veiculo.sql', valores);
  const custoKmRows = await consultar(pool, 'custo_km_por_veiculo.sql', valores);
  const litrosRows = await consultar(pool, 'litros_consumo_por_veiculo.sql', valores);
  const pendentesBanco = await consultar(pool, 'contas_pagar_pendentes.sql', valores);

  const linhas = [];
  const comparar = (metrica, chave, app, banco, tolerancia) =>
    linhas.push(compararValores(metrica, chave, app, banco, tolerancia));

  const placa = new Map(kmRows.map((r) => [r.veiculo_id, r.placa]));
  const nome = (id) => placa.get(id) ?? id;
  const grupoApp = new Map(rent.grupos.map((g) => [g.chave, g]));
  const grupoCustoKm = new Map(ckm.grupos.map((g) => [g.chave, g]));
  const porId = (rows) => new Map(rows.map((r) => [r.veiculo_id, r]));

  // ---- Totais -------------------------------------------------------------
  const receitaBanco = numero(receita.receita_alocada);
  const custoBanco = numero(custo.custo_alocado);
  comparar('receita total', null, rent.totais.receita, receitaBanco, TOLERANCIA.dinheiro);
  comparar('receita não alocada', null, rent.receitaNaoAlocada, numero(receita.receita_nao_alocada), TOLERANCIA.dinheiro);
  comparar('custo total', null, rent.totais.custoTotal, custoBanco, TOLERANCIA.dinheiro);
  comparar('custos não alocados', null, rent.custosNaoAlocados.total, numero(custo.custo_nao_alocado), TOLERANCIA.dinheiro);
  comparar('lucro', null, rent.totais.lucro, arredonda(receitaBanco - custoBanco), TOLERANCIA.dinheiro);
  comparar('custo variável', null, ckm.totais.custoVariavel, numero(custo.custo_variavel_alocado), TOLERANCIA.dinheiro);
  comparar('km rodados', null, rent.totais.kmRodados, somaPor(kmRows, 'km'), TOLERANCIA.inteiro);
  comparar('viagens concluídas', null, rent.totais.qtdViagens, somaPor(kmRows, 'viagens'), TOLERANCIA.inteiro);
  comparar('viagens sem km', null, rent.viagensSemKm, somaPor(kmRows, 'viagens_sem_km'), TOLERANCIA.inteiro);

  // ---- Custo por categoria (somando os veículos do app) -------------------
  const categoriasApp = new Map();
  for (const g of rent.grupos) {
    for (const { categoria, valor } of g.custosPorCategoria) {
      categoriasApp.set(categoria, arredonda((categoriasApp.get(categoria) ?? 0) + valor));
    }
  }
  const categoriasBancoMap = new Map(categoriasBanco.map((r) => [r.categoria, numero(r.valor)]));
  for (const categoria of new Set([...categoriasApp.keys(), ...categoriasBancoMap.keys()])) {
    comparar(
      'custo por categoria',
      categoria,
      categoriasApp.get(categoria) ?? 0,
      categoriasBancoMap.get(categoria) ?? 0,
      TOLERANCIA.dinheiro,
    );
  }

  // ---- Por veículo --------------------------------------------------------
  const resultadoBanco = porId(resultadoRows);
  const custoKmBanco = porId(custoKmRows);
  const litrosBanco = porId(litrosRows);
  const kmBanco = porId(kmRows);

  const veiculos = new Set([
    ...grupoApp.keys(),
    ...[...resultadoBanco.keys()],
    ...[...kmBanco.values()].filter((r) => numero(r.km) || numero(r.viagens)).map((r) => r.veiculo_id),
    ...[...litrosBanco.values()].filter((r) => numero(r.litros)).map((r) => r.veiculo_id),
  ]);

  for (const id of [...veiculos].sort((a, b) => nome(a).localeCompare(nome(b)))) {
    const chave = nome(id);
    const g = grupoApp.get(id);
    const c = grupoCustoKm.get(id);
    const rb = resultadoBanco.get(id);
    const receitaV = numero(rb?.receita) ?? 0;
    const custoV = numero(rb?.custo) ?? 0;

    comparar('resultado: receita', chave, g?.receita ?? 0, receitaV, TOLERANCIA.dinheiro);
    comparar('resultado: custo', chave, g?.custoTotal ?? 0, custoV, TOLERANCIA.dinheiro);
    comparar('resultado: lucro', chave, g?.lucro ?? 0, arredonda(receitaV - custoV), TOLERANCIA.dinheiro);
    comparar('km rodados', chave, g?.kmRodados ?? 0, numero(kmBanco.get(id)?.km) ?? 0, TOLERANCIA.inteiro);

    const ck = custoKmBanco.get(id);
    comparar('custo variável/km', chave, c?.custoVariavelPorKm ?? null, numero(ck?.custo_variavel_por_km), TOLERANCIA.razao);
    comparar('custo total/km', chave, c?.custoTotalPorKm ?? null, numero(ck?.custo_total_por_km), TOLERANCIA.razao);

    const lb = litrosBanco.get(id);
    comparar('litros', chave, g?.litros ?? 0, numero(lb?.litros) ?? 0, TOLERANCIA.dinheiro);
    comparar('km/l', chave, g?.consumoMedioKmL ?? null, numero(lb?.consumo_km_l), TOLERANCIA.razao);
    comparar('preço médio do litro', chave, g?.precoMedioLitro ?? null, numero(lb?.preco_medio_litro), TOLERANCIA.razao);
  }

  // ---- Contas a pagar pendentes (app = vw_contas_pagar) -------------------
  const [pendentesApp] = await pool.query(
    "SELECT Id AS id, Valor AS valor FROM vw_contas_pagar WHERE Status = 'Pendente'",
  );
  comparar('contas a pagar pendentes: quantidade', null, pendentesApp.length, pendentesBanco.length, TOLERANCIA.inteiro);
  comparar('contas a pagar pendentes: valor', null, arredonda(somaPor(pendentesApp, 'valor')), arredonda(somaPor(pendentesBanco, 'valor')), TOLERANCIA.dinheiro);
  const pendentesBancoMap = new Map(pendentesBanco.map((r) => [r.id, numero(r.valor)]));
  const pendentesAppMap = new Map(pendentesApp.map((r) => [r.id, numero(r.valor)]));
  for (const id of new Set([...pendentesAppMap.keys(), ...pendentesBancoMap.keys()])) {
    comparar('conta a pagar pendente', id, pendentesAppMap.get(id) ?? null, pendentesBancoMap.get(id) ?? null, TOLERANCIA.dinheiro);
  }

  // ---- vw_resumo_financeiro x relatório -----------------------------------
  const [viewRows] = await pool.query('SELECT Categoria, Tipo, Quantidade, ValorTotal FROM vw_resumo_financeiro');
  const foraRows = await consultar(pool, 'integridade/fora_do_escopo_por_categoria.sql', valores);
  const foraPor = new Map(foraRows.map((r) => [r.categoria, numero(r.fora_do_escopo)]));
  const resumoView = [];

  for (const r of viewRows.filter((row) => row.Tipo === 'Saida')) {
    const viewValor = numero(r.ValorTotal);
    const fora = foraPor.get(r.Categoria) ?? 0;
    const appValor = arredonda((categoriasApp.get(r.Categoria) ?? 0) + naoAlocado(rent, r.Categoria));
    resumoView.push({ categoria: r.Categoria, view: viewValor, app: appValor, foraDoEscopo: fora, diferencaEsperada: arredonda(viewValor - appValor) });
    comparar('view − fora do escopo = app', r.Categoria, appValor, arredonda(viewValor - fora), TOLERANCIA.dinheiro);
  }
  const viewEntrada = arredonda(somaPor(viewRows.filter((r) => r.Tipo === 'Entrada'), 'ValorTotal'));
  const foraEntrada = arredonda(somaPor(foraRows.filter((r) => r.tipo === 'Entrada'), 'fora_do_escopo'));
  const appEntrada = arredonda(rent.totais.receita + rent.receitaNaoAlocada);
  resumoView.push({ categoria: '(todas as Entradas)', view: viewEntrada, app: appEntrada, foraDoEscopo: foraEntrada, diferencaEsperada: arredonda(viewEntrada - appEntrada) });
  comparar('view − fora do escopo = app', '(todas as Entradas)', appEntrada, arredonda(viewEntrada - foraEntrada), TOLERANCIA.dinheiro);

  // ---- Integridade --------------------------------------------------------
  const integridade = await verificarIntegridade(pool, valores);

  const divergencias = linhas.filter((l) => !l.ok);
  const problemasIntegridade = integridade.filter((v) => v.severidade === 'erro' && v.quantidade > 0);

  return {
    parametros: { inicio, fim, regime },
    comparacoes: linhas,
    integridade,
    resumoFinanceiroView: { nota: NOTA_VIEW, categorias: resumoView },
    resumo: {
      comparacoes: linhas.length,
      divergencias: divergencias.length,
      verificacoesComProblema: problemasIntegridade.length,
      // 1 = app diverge do banco; 2 = números batem mas há dados inconsistentes; 0 = tudo certo.
      codigoSaida: divergencias.length > 0 ? 1 : problemasIntegridade.length > 0 ? 2 : 0,
    },
  };
}

// Custo da categoria que o relatório deixou como "não alocado" (sem veículo).
const naoAlocado = (rent, categoria) =>
  rent.custosNaoAlocados.porCategoria.find((c) => c.categoria === categoria)?.valor ?? 0;
