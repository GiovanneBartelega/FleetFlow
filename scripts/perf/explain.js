import 'dotenv/config';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadDbConfig } from '../../src/config.js';
import { createPool } from '../../src/db.js';
import { AGRUPAMENTOS, REGIMES, sqlCombustivel, sqlKm, sqlMovimentacoes } from '../../src/queries/relatorios.sql.js';

const USO = `Uso: npm run perf:explain -- [--db=fleetflow_perf] [--inicio=AAAA-MM-DD --fim=AAAA-MM-DD]

Roda EXPLAIN nas consultas dos relatórios (todas as dimensões e os dois regimes) e confere o plano:
  - nenhuma tabela grande lida por varredura completa (type=ALL);
  - cada consulta usa o índice esperado (ix_*), em vez de cair num índice genérico.
Código de saída: 0 = planos esperados · 1 = algum plano fugiu do esperado.`;

// Tabelas pequenas (dimensões): varredura completa delas não é problema.
const TABELAS_PEQUENAS = new Set(['c', 'categorias', 'perfis']);

// Índice que o filtro principal de cada consulta DEVE usar (alias da tabela -> nome do índice).
// Mantido junto com db/indices-relatorios.sql: se um índice for renomeado, este mapa acusa.
export const INDICES_ESPERADOS = {
  mov: { caixa: { m: 'ix_mov_caixa' }, competencia: { m: 'ix_mov_competencia' } },
  km: { caixa: { vi: 'ix_viagens_concluidas' }, competencia: { vi: 'ix_viagens_concluidas' } },
  comb: { caixa: { m: 'ix_mov_caixa' }, competencia: { m: 'ix_mov_competencia' } },
};

export async function planoDe(pool, { sql, params }) {
  const [linhas] = await pool.query(`EXPLAIN ${sql}`, params);
  return linhas.map((l) => ({
    tabela: l.table, tipo: l.type, indice: l.key, linhas: Number(l.rows), extra: l.Extra ?? '',
  }));
}

// Devolve a lista de problemas do plano (vazia = ok).
export function avaliarPlano(plano, esperados) {
  const problemas = [];
  for (const passo of plano) {
    if (passo.tipo === 'ALL' && !TABELAS_PEQUENAS.has(passo.tabela) && !String(passo.tabela).startsWith('<')) {
      problemas.push(`varredura completa em ${passo.tabela} (~${passo.linhas} linhas)`);
    }
  }
  for (const [alias, indice] of Object.entries(esperados)) {
    const passo = plano.find((p) => p.tabela === alias);
    if (!passo) problemas.push(`tabela ${alias} não aparece no plano`);
    else if (passo.indice !== indice) problemas.push(`${alias} usa ${passo.indice ?? 'nenhum índice'}, esperado ${indice}`);
  }
  return problemas;
}

async function main() {
  const args = {};
  for (const item of process.argv.slice(2)) {
    const m = /^--([a-z]+)=(.*)$/.exec(item);
    if (!m) throw new Error(`Argumento inválido: ${item}\n${USO}`);
    args[m[1]] = m[2];
  }
  const database = args.db ?? loadDbConfig().database;
  const pool = createPool({ ...loadDbConfig(), database });
  const inicio = args.inicio ?? '2025-11-01';
  const fim = args.fim ?? '2026-10-31';
  let falhas = 0;
  try {
    console.log(`Banco: ${database} · período ${inicio} a ${fim}\n`);
    for (const regime of REGIMES) {
      for (const agrupar of AGRUPAMENTOS) {
        const filtros = { agrupar, regime, inicio, fim };
        const consultas = { mov: sqlMovimentacoes(filtros), km: sqlKm(filtros), comb: sqlCombustivel(filtros) };
        for (const [nome, consulta] of Object.entries(consultas)) {
          const plano = await planoDe(pool, consulta);
          const problemas = avaliarPlano(plano, INDICES_ESPERADOS[nome][regime]);
          const resumo = plano.map((p) => `${p.tabela}:${p.tipo}${p.indice ? `(${p.indice})` : ''}`).join(' → ');
          console.log(`${problemas.length === 0 ? 'OK ' : 'XX '} ${regime.padEnd(11)} ${agrupar.padEnd(9)} ${nome.padEnd(4)} ${resumo}`);
          for (const p of problemas) console.log(`       ! ${p}`);
          falhas += problemas.length;
        }
      }
    }
    console.log(falhas === 0 ? '\nTodos os planos usam os índices esperados.' : `\n${falhas} problema(s) nos planos.`);
    process.exitCode = falhas === 0 ? 0 : 1;
  } finally {
    await pool.end();
  }
}

// Só executa quando chamado direto (o teste importa avaliarPlano sem rodar nada).
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main().catch((erro) => {
    console.error(erro.message);
    process.exitCode = 1;
  });
}
