import 'dotenv/config';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import { loadDbConfig } from '../../src/config.js';
import { createPool } from '../../src/db.js';
import { conferir } from '../../src/conferencia/conferir.js';
// Reaproveita o divisor de scripts SQL (respeita DELIMITER dos triggers) usado pelos testes.
import { dividirScript } from '../../test/bancoTeste.js';
import { contar, fmtData, gerarMassa, janelaPadrao } from './gerador.js';

const RAIZ = fileURLToPath(new URL('../../', import.meta.url));
const USO = `Uso: npm run massa -- [--db=fleetflow_perf] [--meses=12] [--inicio=AAAA-MM-DD] [--hoje=AAAA-MM-DD]
                  [--escala=1] [--semente=42] [--conferir]

  --db        banco a (RE)CRIAR. Precisa terminar em _perf: o conteúdo é APAGADO. (padrão: fleetflow_perf)
  --meses     tamanho da janela (padrão: 12)
  --inicio    primeiro dia da janela (padrão: primeiro dia de ${janelaPadrao().inicio.slice(0, 7)}, i.e. os últimos 12 meses cheios)
  --hoje      o que vence até esta data e foi "pago" fica Pago; o resto, Pendente (padrão: data de hoje)
  --escala    multiplica veículos e motoristas (padrão 1 = 80 veículos / 90 motoristas; use 5 para estresse)
  --semente   mesma semente = mesma massa (padrão: 42)
  --conferir  ao final, roda a conferência (app x SQL manual + integridade) sobre a janela inteira`;

function lerArgumentos(argv) {
  const args = {};
  for (const item of argv) {
    const m = /^--([a-z]+)(?:=(.*))?$/.exec(item);
    if (!m) throw new Error(`Argumento inválido: ${item}\n${USO}`);
    args[m[1]] = m[2] ?? true;
  }
  const hojeIso = fmtData(new Date());
  const meses = Number(args.meses ?? 12);
  const escala = Number(args.escala ?? 1);
  if (!Number.isInteger(meses) || meses < 1 || meses > 60) throw new Error('--meses deve ser um inteiro de 1 a 60.');
  if (!(escala > 0 && escala <= 50)) throw new Error('--escala deve ser um número entre 0 e 50.');
  const janela = janelaPadrao(new Date(`${args.hoje ?? hojeIso}T00:00:00Z`), meses);
  const inicio = args.inicio ?? janela.inicio;
  const hoje = args.hoje ?? hojeIso;
  for (const [nome, valor] of [['--inicio', inicio], ['--hoje', hoje]]) {
    const d = new Date(`${valor}T00:00:00Z`);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(valor) || Number.isNaN(d.getTime()) || fmtData(d) !== valor) {
      throw new Error(`${nome} inválido (use AAAA-MM-DD).\n${USO}`);
    }
  }
  const db = args.db ?? 'fleetflow_perf';
  if (!/^[A-Za-z0-9_]+_perf$/.test(db)) {
    throw new Error(`Recusado: o banco "${db}" não termina em _perf. Este comando APAGA o banco informado.`);
  }
  return {
    db, meses, escala, inicio, hoje, semente: Number(args.semente ?? 42), conferir: Boolean(args.conferir),
  };
}

// INSERT em lotes: os nomes de coluna vêm do próprio gerador (nunca de entrada do usuário).
async function inserir(conexao, tabela, linhas, tamanhoLote = 1500) {
  if (linhas.length === 0) return;
  const colunas = Object.keys(linhas[0]);
  const sql = `INSERT INTO \`${tabela}\` (${colunas.map((c) => `\`${c}\``).join(', ')}) VALUES ?`;
  for (let i = 0; i < linhas.length; i += tamanhoLote) {
    const lote = linhas.slice(i, i + tamanhoLote).map((linha) => colunas.map((c) => linha[c]));
    await conexao.query(sql, [lote]);
  }
}

async function main() {
  const args = lerArgumentos(process.argv.slice(2));
  const base = loadDbConfig();
  const t0 = Date.now();

  const admin = await mysql.createConnection({
    host: base.host, port: base.port, user: base.user, password: base.password, charset: 'utf8mb4',
  });
  await admin.query(`DROP DATABASE IF EXISTS \`${args.db}\``);
  await admin.query(`CREATE DATABASE \`${args.db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`);
  await admin.end();

  const pool = createPool({ ...base, database: args.db });
  try {
    console.log(`Banco: ${args.db} @ ${base.host}:${base.port} (recriado)`);

    // 1) Schema original (perfis, categorias, formas de pagamento e usuários de teste incluídos).
    const conexaoSchema = await pool.getConnection();
    try {
      await conexaoSchema.query('SET NAMES utf8mb4');
      const script = await readFile(`${RAIZ}banco-de-dados versao final.sql`, 'utf8');
      for (const comando of dividirScript(script)) await conexaoSchema.query(comando);
    } finally {
      conexaoSchema.release();
    }

    // 2) Referências que a massa precisa (ids gerados pelo seed do schema).
    const [[perfil]] = await pool.query("SELECT Id FROM perfis WHERE Nome = 'OperadorMotorista'");
    const [categorias] = await pool.query('SELECT Id, Titulo FROM categorias');
    const [formas] = await pool.query('SELECT Id FROM formas_pagamento');
    const idDe = (titulo) => categorias.find((c) => c.Titulo === titulo).Id;
    const refs = {
      perfilMotoristaId: perfil.Id,
      categorias: {
        Frete: idDe('Frete'), Combustivel: idDe('Combustível'), Pedagio: idDe('Pedágio'), Manutencao: idDe('Manutenção'),
        Salario: idDe('Salário'), Financiamento: idDe('Financiamento'), Seguro: idDe('Seguro'),
        Impostos: idDe('Impostos'), Outros: idDe('Outros'),
      },
      formas: formas.map((f) => f.Id),
    };

    // 3) Geração (em memória) e carga (uma transação; ordem respeita as FKs).
    const tGerar = Date.now();
    const massa = gerarMassa({ ...args, refs });
    console.log(`Janela: ${massa.janela.inicio} a ${massa.janela.fim} · hoje=${args.hoje} · semente=${args.semente} · escala=${args.escala}`);
    console.log(`Geração em memória: ${Date.now() - tGerar} ms`);

    const tCarga = Date.now();
    const conexao = await pool.getConnection();
    try {
      await conexao.query('SET NAMES utf8mb4');
      await conexao.beginTransaction();
      for (const tabela of ['usuarios', 'veiculos', 'rotas', 'dividas', 'viagens', 'movimentacoes', 'abastecimentos', 'ordens_servico']) {
        await inserir(conexao, tabela, massa.tabelas[tabela]);
      }
      await conexao.commit();
    } catch (erro) {
      await conexao.rollback();
      throw erro;
    } finally {
      conexao.release();
    }
    // Estatísticas do otimizador atualizadas, como num banco que já está em uso há meses.
    for (const tabela of ['movimentacoes', 'viagens', 'abastecimentos', 'ordens_servico', 'dividas', 'veiculos']) {
      await pool.query(`ANALYZE TABLE \`${tabela}\``);
    }
    console.log(`Carga no MySQL: ${Date.now() - tCarga} ms\n`);

    console.log('Linhas geradas:');
    for (const [tabela, n] of Object.entries(contar(massa))) console.log(`  ${tabela.padEnd(16)} ${String(n).padStart(8)}`);
    const [resumo] = await pool.query(
      `SELECT COUNT(*) AS total, SUM(Status = 'Pago') AS pagas, SUM(Status = 'Pendente') AS pendentes,
              SUM(Ativo = 0) AS inativas FROM movimentacoes`,
    );
    console.log(`  movimentações: ${resumo[0].pagas} pagas · ${resumo[0].pendentes} pendentes · ${resumo[0].inativas} inativas`);

    let codigoSaida = 0;
    if (args.conferir) {
      console.log('\nConferência (service x SQL manual x integridade) sobre a janela inteira...');
      for (const regime of ['caixa', 'competencia']) {
        const r = await conferir(pool, { inicio: massa.janela.inicio, fim: massa.janela.fim, regime });
        const { comparacoes, divergencias, verificacoesComProblema } = r.resumo;
        console.log(`  regime ${regime}: ${comparacoes} comparações · ${divergencias} divergências · ${verificacoesComProblema} verificações com problema`);
        if (r.resumo.codigoSaida !== 0) codigoSaida = r.resumo.codigoSaida;
      }
    }
    console.log(`\nPronto em ${((Date.now() - t0) / 1000).toFixed(1)} s. Próximo passo: npm run massa:medir -- --db=${args.db}`);
    process.exitCode = codigoSaida;
  } finally {
    await pool.end();
  }
}

main().catch((erro) => {
  console.error(erro.message);
  process.exitCode = 1;
});
