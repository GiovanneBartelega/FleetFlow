import 'dotenv/config';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import { loadDbConfig } from '../src/config.js';
import { createPool } from '../src/db.js';

const RAIZ = fileURLToPath(new URL('../', import.meta.url));

// Nome do banco de teste: DB_NAME se ele terminar em "_test"; senão DB_TEST_NAME ou fleetflow_test.
// Nunca o banco de desenvolvimento: o nome precisa terminar em "_test", senão nada é executado.
export function nomeBancoTeste(env = process.env) {
  const candidato = /_test$/.test(env.DB_NAME ?? '') ? env.DB_NAME : env.DB_TEST_NAME || 'fleetflow_test';
  if (!/^[A-Za-z0-9_]+_test$/.test(candidato)) {
    throw new Error(`Nome de banco de teste inválido: ${candidato}. Precisa terminar em _test.`);
  }
  return candidato;
}

// Quebra um script MySQL em comandos, respeitando "DELIMITER $$" (triggers, procedures)
// e descartando CREATE DATABASE / USE, que apontariam para o banco de desenvolvimento.
export function dividirScript(texto) {
  const comandos = [];
  let delimitador = ';';
  let atual = '';
  for (const linha of texto.split(/\r?\n/)) {
    const diretiva = /^DELIMITER\s+(\S+)\s*$/i.exec(linha.trim());
    if (diretiva) {
      delimitador = diretiva[1];
      continue;
    }
    if (/^\s*--/.test(linha) || (atual === '' && linha.trim() === '')) continue;
    atual += `${linha}\n`;
    if (atual.trimEnd().endsWith(delimitador)) {
      comandos.push(atual.trimEnd().slice(0, -delimitador.length).trim());
      atual = '';
    }
  }
  if (atual.trim()) comandos.push(atual.trim());
  return comandos.filter((c) => !/^(CREATE DATABASE|USE)\b/i.test(c));
}

// Recria o banco de teste do zero: schema + dados iniciais + fixture da conferência.
// Retorna um pool conectado a ele. Falha antes de apagar qualquer coisa se o nome não for *_test.
export async function prepararBancoTeste({ comFixture = true } = {}) {
  const nome = nomeBancoTeste();
  const base = loadDbConfig();
  const admin = await mysql.createConnection({
    host: base.host, port: base.port, user: base.user, password: base.password, charset: 'utf8mb4',
  });
  try {
    await admin.query(`DROP DATABASE IF EXISTS \`${nome}\``);
    await admin.query(`CREATE DATABASE \`${nome}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`);
  } finally {
    await admin.end();
  }

  const pool = createPool({ ...base, database: nome });
  const executar = async (arquivo) => {
    const texto = await readFile(RAIZ + arquivo, 'utf8');
    const conexao = await pool.getConnection();
    try {
      await conexao.query('SET NAMES utf8mb4');
      // Variáveis de sessão (@v1...) da fixture precisam viver na mesma conexão.
      for (const comando of dividirScript(texto)) await conexao.query(comando);
    } finally {
      conexao.release();
    }
  };
  await executar('banco-de-dados versao final.sql');
  if (comFixture) await executar('fixtures/conferencia-seed.sql');
  return { pool, nome };
}

export async function bancoDisponivel() {
  try {
    const base = loadDbConfig();
    const c = await mysql.createConnection({ host: base.host, port: base.port, user: base.user, password: base.password });
    await c.end();
    return true;
  } catch {
    return false;
  }
}

export async function lerEsperado() {
  return JSON.parse(await readFile(`${RAIZ}fixtures/esperado.json`, 'utf8'));
}
