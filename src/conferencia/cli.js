import 'dotenv/config';
import { writeFile } from 'node:fs/promises';
import { loadDbConfig } from '../config.js';
import { createPool } from '../db.js';
import { formatarTabela } from './comparar.js';
import { conferir } from './conferir.js';

const USO = 'Uso: npm run conferencia -- --inicio=AAAA-MM-DD --fim=AAAA-MM-DD [--regime=caixa|competencia] [--saida=arquivo.json]';

function lerArgumentos(argv) {
  const args = {};
  for (const item of argv) {
    const match = /^--([a-z]+)=(.*)$/.exec(item);
    if (!match) throw new Error(`Argumento inválido: ${item}\n${USO}`);
    args[match[1]] = match[2];
  }
  const dataValida = (valor = '') => {
    const data = new Date(`${valor}T00:00:00Z`);
    return /^\d{4}-\d{2}-\d{2}$/.test(valor) && !Number.isNaN(data.getTime()) && data.toISOString().startsWith(valor);
  };
  if (!dataValida(args.inicio) || !dataValida(args.fim)) {
    throw new Error(`Informe --inicio e --fim no formato AAAA-MM-DD.\n${USO}`);
  }
  if (args.fim < args.inicio) throw new Error('--fim não pode ser anterior a --inicio.');
  args.regime ??= 'caixa';
  if (!['caixa', 'competencia'].includes(args.regime)) {
    throw new Error(`--regime deve ser caixa ou competencia.\n${USO}`);
  }
  return args;
}

async function main() {
  const args = lerArgumentos(process.argv.slice(2));
  const dbConfig = loadDbConfig();
  const pool = createPool(dbConfig);

  try {
    console.log(`Banco: ${dbConfig.database} @ ${dbConfig.host}:${dbConfig.port}`);
    console.log(`Período: ${args.inicio} a ${args.fim} · regime: ${args.regime}\n`);

    const resultado = await conferir(pool, args);

    console.log(formatarTabela(resultado.comparacoes));
    console.log('\nVerificações de integridade');
    for (const v of resultado.integridade) {
      const marca = v.quantidade === 0 ? 'OK     ' : v.severidade === 'erro' ? 'PROBLEMA' : 'INFO   ';
      console.log(`  [${marca}] ${v.titulo}: ${v.quantidade} linha(s)`);
      for (const linha of v.linhas.slice(0, 10)) console.log(`      ${JSON.stringify(linha)}`);
      if (v.linhas.length > 10) console.log(`      ... e mais ${v.linhas.length - 10} (veja o JSON)`);
    }

    console.log(`\nvw_resumo_financeiro: ${resultado.resumoFinanceiroView.nota}`);
    for (const c of resultado.resumoFinanceiroView.categorias) {
      console.log(`  ${c.categoria}: view ${c.view} · app ${c.app} · diferença esperada ${c.diferencaEsperada} (fora do escopo ${c.foraDoEscopo})`);
    }

    const arquivo = args.saida ?? 'conferencia-resultado.json';
    await writeFile(arquivo, `${JSON.stringify(resultado, null, 2)}\n`, 'utf8');

    const { comparacoes, divergencias, verificacoesComProblema, codigoSaida } = resultado.resumo;
    console.log(`\n${comparacoes} comparações, ${divergencias} divergência(s), ${verificacoesComProblema} verificação(ões) de integridade com problema.`);
    console.log(`Resultado salvo em ${arquivo}`);
    process.exitCode = codigoSaida;
  } finally {
    await pool.end();
  }
}

main().catch((erro) => {
  console.error(erro.message);
  process.exitCode = 1;
});
