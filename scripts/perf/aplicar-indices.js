import 'dotenv/config';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import { loadDbConfig } from '../../src/config.js';
// Divisor de scripts SQL compartilhado com os testes (ignora comentários "--" e respeita DELIMITER).
import { dividirScript } from '../../test/bancoTeste.js';

const ARQUIVO = fileURLToPath(new URL('../../db/indices-relatorios.sql', import.meta.url));
const USO = `Uso: npm run perf:indices -- [--db=fleetflow] [--remover] [--simular]

  --db       banco alvo (padrão: DB_NAME do .env)
  --remover  desfaz: apaga os índices ix_* do arquivo db/indices-relatorios.sql
  --simular  só mostra o que seria feito`;

// Cada CREATE INDEX do arquivo vira { nome, tabela, sql }. O nome e a tabela vêm do ARQUIVO versionado
// (nunca de entrada do usuário), por isso podem ir direto no DROP INDEX.
export function lerIndices(texto) {
  return dividirScript(texto)
    .map((sql) => {
      const m = /^CREATE INDEX\s+(\w+)\s+ON\s+(\w+)\s*\(/i.exec(sql);
      if (!m) throw new Error(`Comando inesperado em db/indices-relatorios.sql (só CREATE INDEX é aceito): ${sql.slice(0, 80)}`);
      return { nome: m[1], tabela: m[2], sql };
    });
}

async function main() {
  const args = {};
  for (const item of process.argv.slice(2)) {
    const m = /^--([a-z]+)(?:=(.*))?$/.exec(item);
    if (!m) throw new Error(`Argumento inválido: ${item}\n${USO}`);
    args[m[1]] = m[2] ?? true;
  }
  const base = loadDbConfig();
  const database = args.db ?? base.database;
  if (!/^[A-Za-z0-9_]+$/.test(database)) throw new Error('Nome de banco inválido.');

  const indices = lerIndices(await readFile(ARQUIVO, 'utf8'));
  const conexao = await mysql.createConnection({
    host: base.host, port: base.port, user: base.user, password: base.password, database,
  });
  try {
    console.log(`Banco: ${database} @ ${base.host}:${base.port} · ${indices.length} índices no arquivo`);
    const [existentes] = await conexao.query(
      'SELECT DISTINCT TABLE_NAME AS tabela, INDEX_NAME AS nome FROM information_schema.statistics WHERE table_schema = ?',
      [database],
    );
    const jaExiste = (i) => existentes.some((e) => e.tabela === i.tabela && e.nome === i.nome);
    const tabelasTocadas = new Set();

    for (const indice of indices) {
      const existe = jaExiste(indice);
      if (args.remover) {
        if (!existe) { console.log(`  = ${indice.nome}: não existe`); continue; }
        console.log(`  - ${indice.nome} (${indice.tabela}): ${args.simular ? 'seria removido' : 'removido'}`);
        if (!args.simular) await conexao.query(`DROP INDEX \`${indice.nome}\` ON \`${indice.tabela}\``);
      } else {
        if (existe) { console.log(`  = ${indice.nome}: já existe`); continue; }
        const t0 = Date.now();
        if (!args.simular) await conexao.query(indice.sql);
        console.log(`  + ${indice.nome} (${indice.tabela}): ${args.simular ? 'seria criado' : `criado em ${Date.now() - t0} ms`}`);
      }
      tabelasTocadas.add(indice.tabela);
    }
    if (!args.simular) {
      // Estatísticas novas para o otimizador escolher os índices certos já na primeira consulta.
      for (const tabela of tabelasTocadas) await conexao.query(`ANALYZE TABLE \`${tabela}\``);
    }
  } finally {
    await conexao.end();
  }
}

// Só executa quando chamado direto (o teste importa lerIndices sem rodar nada).
if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main().catch((erro) => {
    console.error(erro.message);
    process.exitCode = 1;
  });
}
