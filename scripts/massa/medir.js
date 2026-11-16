import 'dotenv/config';
import { readFile, writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { loadDbConfig } from '../../src/config.js';
import { createPool } from '../../src/db.js';
import { AGRUPAMENTOS, REGIMES } from '../../src/queries/relatorios.sql.js';
// Sobe a API de verdade (Express + autenticação + rota) numa porta efêmera: o tempo medido é o do HTTP.
import { startServer, tokenFor } from '../../test/helpers.js';

const USO = `Uso: npm run massa:medir -- [--db=fleetflow_perf] [--repeticoes=5] [--meta=2000]
                    [--inicio=AAAA-MM-DD --fim=AAAA-MM-DD] [--saida=medicao.json] [--comparar=antes.json]

  --db           banco já populado por "npm run massa" (padrão: fleetflow_perf; precisa terminar em _perf)
  --repeticoes   execuções cronometradas por cenário, depois de 1 de aquecimento (padrão: 5)
  --meta         limite em ms para o PIOR tempo de cada cenário (padrão: 2000)
  --inicio/--fim período dos cenários de "12 meses" (padrão: de MIN(DataInicio) a MAX(DataFim) das viagens)
  --saida        grava o resultado em JSON (padrão: medicao-resultado.json)
  --comparar     lê um JSON anterior (ex.: antes dos índices) e mostra a variação por cenário

Código de saída: 0 = todos os cenários dentro da meta · 1 = algum estourou a meta · 3 = erro de uso/execução.`;

function lerArgumentos(argv) {
  const args = {};
  for (const item of argv) {
    const m = /^--([a-z]+)=(.*)$/.exec(item);
    if (!m) throw new Error(`Argumento inválido: ${item}\n${USO}`);
    args[m[1]] = m[2];
  }
  const db = args.db ?? 'fleetflow_perf';
  if (!/^[A-Za-z0-9_]+_perf$/.test(db)) throw new Error(`Recusado: o banco "${db}" não termina em _perf.`);
  const repeticoes = Number(args.repeticoes ?? 5);
  const meta = Number(args.meta ?? 2000);
  if (!Number.isInteger(repeticoes) || repeticoes < 1) throw new Error('--repeticoes deve ser um inteiro >= 1.');
  if (!(meta > 0)) throw new Error('--meta deve ser positiva (ms).');
  return { ...args, db, repeticoes, meta, saida: args.saida ?? 'medicao-resultado.json' };
}

const percentil = (ordenado, p) => ordenado[Math.min(ordenado.length - 1, Math.ceil((p / 100) * ordenado.length) - 1)];
const ms = (n) => `${n.toFixed(0)}`.padStart(6);

function montarCenarios({ inicio, fim, veiculoId, mesInicio, mesFim }) {
  const cenarios = [];
  for (const regime of REGIMES) {
    for (const agrupar of AGRUPAMENTOS) {
      for (const endpoint of ['rentabilidade', 'custo-km']) {
        cenarios.push({
          nome: `12 meses · ${endpoint} · ${agrupar} · ${regime}`,
          caminho: `/api/relatorios/${endpoint}?inicio=${inicio}&fim=${fim}&agrupar=${agrupar}&regime=${regime}`,
        });
      }
    }
  }
  // Uso do dia a dia: um mês, e um veículo no ano inteiro.
  for (const regime of REGIMES) {
    cenarios.push({
      nome: `1 mês · rentabilidade · veiculo · ${regime}`,
      caminho: `/api/relatorios/rentabilidade?inicio=${mesInicio}&fim=${mesFim}&agrupar=veiculo&regime=${regime}`,
    });
    cenarios.push({
      nome: `12 meses · 1 veículo · rentabilidade · mes · ${regime}`,
      caminho: `/api/relatorios/rentabilidade?inicio=${inicio}&fim=${fim}&agrupar=mes&regime=${regime}&veiculoId=${veiculoId}`,
    });
  }
  return cenarios;
}

async function main() {
  const args = lerArgumentos(process.argv.slice(2));
  const pool = createPool({ ...loadDbConfig(), database: args.db });
  let servidor;
  try {
    const [[admin]] = await pool.query("SELECT Id FROM usuarios WHERE Email = 'teste.admin@exemplo.com'");
    const [[faixa]] = await pool.query(
      `SELECT DATE(MIN(DataInicio)) AS inicio, DATE(MAX(DataFim)) AS fim, COUNT(*) AS viagens FROM viagens`,
    );
    const [[volume]] = await pool.query(
      `SELECT (SELECT COUNT(*) FROM movimentacoes) AS movimentacoes, (SELECT COUNT(*) FROM abastecimentos) AS abastecimentos,
              (SELECT COUNT(*) FROM viagens) AS viagens, (SELECT COUNT(*) FROM veiculos) AS veiculos`,
    );
    if (!admin || !faixa.inicio) throw new Error(`O banco ${args.db} não tem a massa. Rode antes: npm run massa -- --db=${args.db}`);
    const [[veiculo]] = await pool.query('SELECT VeiculoId FROM viagens GROUP BY VeiculoId ORDER BY COUNT(*) DESC LIMIT 1');

    const inicio = args.inicio ?? faixa.inicio;
    const fim = args.fim ?? faixa.fim;
    const mesFim = fim;
    const mesInicio = `${fim.slice(0, 8)}01`;
    const cenarios = montarCenarios({ inicio, fim, veiculoId: veiculo.VeiculoId, mesInicio, mesFim });

    servidor = await startServer({ pool });
    const token = tokenFor(admin.Id);

    // Índices existentes que não são do schema original: deixa claro em que estado o banco foi medido.
    const [extras] = await pool.query(
      "SELECT DISTINCT TABLE_NAME AS tabela, INDEX_NAME AS indice FROM information_schema.statistics WHERE table_schema = DATABASE() AND INDEX_NAME LIKE 'ix\\_%'",
    );

    console.log(`Banco: ${args.db} · ${volume.movimentacoes} movimentações · ${volume.viagens} viagens · ${volume.abastecimentos} abastecimentos · ${volume.veiculos} veículos`);
    console.log(`Período "12 meses": ${inicio} a ${fim} · meta: pior tempo < ${args.meta} ms · ${args.repeticoes} repetições (+1 de aquecimento)`);
    console.log(`Índices ix_* presentes: ${extras.length === 0 ? 'nenhum (schema original)' : extras.map((e) => `${e.tabela}.${e.indice}`).join(', ')}\n`);
    console.log(`${'cenário'.padEnd(58)} ${'frio'.padStart(6)} ${'p50'.padStart(6)} ${'p95'.padStart(6)} ${'pior'.padStart(6)}  status`);

    const anterior = args.comparar ? JSON.parse(await readFile(args.comparar, 'utf8')) : null;
    const resultados = [];
    for (const cenario of cenarios) {
      const tempos = [];
      let frio = null;
      for (let i = 0; i <= args.repeticoes; i += 1) {
        const t0 = performance.now();
        const resposta = await fetch(servidor.base + cenario.caminho, { headers: { authorization: `Bearer ${token}` } });
        const corpo = await resposta.text(); // inclui o tempo de ler/serializar o corpo
        const dt = performance.now() - t0;
        if (resposta.status !== 200) throw new Error(`${cenario.nome}: HTTP ${resposta.status} ${corpo.slice(0, 200)}`);
        if (i === 0) frio = dt;
        else tempos.push(dt);
      }
      tempos.sort((a, b) => a - b);
      const r = {
        nome: cenario.nome, frioMs: frio, p50Ms: percentil(tempos, 50), p95Ms: percentil(tempos, 95), piorMs: tempos.at(-1),
      };
      r.dentroDaMeta = Math.max(r.piorMs, r.frioMs) < args.meta;
      resultados.push(r);
      const ref = anterior?.resultados.find((x) => x.nome === r.nome);
      const variacao = ref ? `  (antes: pior ${ms(ref.piorMs).trim()} ms → ${(ref.piorMs / Math.max(r.piorMs, 1)).toFixed(1)}x)` : '';
      console.log(`${r.nome.padEnd(58)} ${ms(r.frioMs)} ${ms(r.p50Ms)} ${ms(r.p95Ms)} ${ms(r.piorMs)}  ${r.dentroDaMeta ? 'OK' : 'ESTOUROU'}${variacao}`);
    }

    const fora = resultados.filter((r) => !r.dentroDaMeta);
    const maior = resultados.reduce((a, b) => (Math.max(a.piorMs, a.frioMs) >= Math.max(b.piorMs, b.frioMs) ? a : b));
    console.log(`\n${resultados.length} cenários · ${fora.length} acima de ${args.meta} ms · maior tempo: ${Math.max(maior.piorMs, maior.frioMs).toFixed(0)} ms (${maior.nome})`);

    await writeFile(
      args.saida,
      `${JSON.stringify({ geradoEm: new Date().toISOString(), banco: args.db, volume, periodo: { inicio, fim }, metaMs: args.meta, indices: extras, resultados }, null, 2)}\n`,
      'utf8',
    );
    console.log(`Resultado salvo em ${args.saida}`);
    process.exitCode = fora.length === 0 ? 0 : 1;
  } finally {
    if (servidor) await servidor.close();
    await pool.end();
  }
}

main().catch((erro) => {
  console.error(erro.message);
  process.exitCode = 3;
});
