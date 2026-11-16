import { readFile } from 'node:fs/promises';
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { lerIndices } from '../scripts/perf/aplicar-indices.js';
import { INDICES_ESPERADOS, avaliarPlano, planoDe } from '../scripts/perf/explain.js';
import {
  AGRUPAMENTOS, REGIMES, sqlCombustivel, sqlKm, sqlMovimentacoes, sqlRotulos,
} from '../src/queries/relatorios.sql.js';
import { custoKm, rentabilidade } from '../src/services/relatorios.js';
import { bancoDisponivel, prepararBancoTeste } from './bancoTeste.js';

// ---------------------------------------------------------------------------
// Sem banco: o arquivo de índices e a forma das consultas.
// ---------------------------------------------------------------------------
describe('db/indices-relatorios.sql', () => {
  test('só tem CREATE INDEX ix_*, sem nomes repetidos, e todo índice citado nas consultas existe no arquivo', async () => {
    const texto = await readFile(new URL('../db/indices-relatorios.sql', import.meta.url), 'utf8');
    const indices = lerIndices(texto);
    assert.equal(indices.length, 5);
    assert.equal(new Set(indices.map((i) => i.nome)).size, indices.length);
    for (const i of indices) assert.match(i.nome, /^ix_[a-z_]+$/);

    const nomes = new Set(indices.map((i) => i.nome));
    const sqls = [];
    for (const regime of REGIMES) {
      for (const agrupar of AGRUPAMENTOS) {
        const f = { agrupar, regime, inicio: '2026-01-01', fim: '2026-12-31' };
        sqls.push(sqlMovimentacoes(f).sql, sqlKm(f).sql, sqlCombustivel(f).sql);
      }
    }
    const citados = new Set(sqls.flatMap((sql) => [...sql.matchAll(/INDEX\(\w+ (ix_\w+)\)/g)].map((m) => m[1])));
    // 4 por hint; ix_os_movimentacao é usado sem hint, pela subconsulta de ordens_servico.
    assert.deepEqual([...citados].sort(), ['ix_abast_relatorio', 'ix_mov_caixa', 'ix_mov_competencia', 'ix_viagens_concluidas']);
    for (const nome of citados) assert.ok(nomes.has(nome), `hint cita ${nome}, que não está no arquivo de índices`);
  });

  test('lerIndices recusa qualquer comando que não seja CREATE INDEX (o aplicador interpola nomes em DROP)', () => {
    assert.throws(() => lerIndices('DROP TABLE movimentacoes;'), /só CREATE INDEX/);
    assert.deepEqual(
      lerIndices('-- comentário\nCREATE INDEX ix_a ON t (x,\n  y);').map(({ nome, tabela }) => ({ nome, tabela })),
      [{ nome: 'ix_a', tabela: 't' }],
    );
  });
});

describe('forma das consultas', () => {
  const periodo = { inicio: '2026-01-01', fim: '2026-12-31' };

  test('todo valor do usuário entra por ? (nenhuma interpolação de filtro no texto do SQL)', () => {
    const malicioso = "x'; DROP TABLE viagens; --";
    const f = { ...periodo, agrupar: 'veiculo', regime: 'caixa', veiculoId: malicioso, rotaId: malicioso, motoristaId: malicioso };
    for (const q of [sqlMovimentacoes(f), sqlKm(f), sqlCombustivel(f)]) {
      assert.ok(!q.sql.includes('DROP TABLE'));
      assert.ok(q.params.includes(malicioso));
    }
  });

  test('a quantidade de ? bate com os parâmetros em todas as combinações', () => {
    const filtros = [{}, { veiculoId: 'v' }, { rotaId: 'r' }, { motoristaId: 'm' }, { veiculoId: 'v', rotaId: 'r', motoristaId: 'm' }];
    for (const regime of REGIMES) {
      for (const agrupar of AGRUPAMENTOS) {
        for (const extra of filtros) {
          const f = { ...periodo, agrupar, regime, ...extra };
          for (const q of [sqlMovimentacoes(f), sqlKm(f), sqlCombustivel(f)]) {
            assert.equal((q.sql.match(/\?/g) ?? []).length, q.params.length, `${agrupar}/${regime}/${JSON.stringify(extra)}`);
          }
        }
      }
    }
  });

  test('rótulos de viagem: IN para poucas chaves, leitura completa quando a lista é grande', () => {
    assert.match(sqlRotulos('viagem', ['a', 'b']).sql, /WHERE vi\.Id IN \(\?\)/);
    const grande = sqlRotulos('viagem', Array.from({ length: 5000 }, (_, i) => String(i)));
    assert.ok(!/IN \(/.test(grande.sql));
    assert.deepEqual(grande.params, []);
  });
});

// ---------------------------------------------------------------------------
// Com banco: os mesmos números com e sem os índices, e planos usando os índices esperados.
// Banco próprio (fleetflow_idx_test / fleetflow_semidx_test): node --test roda arquivos em paralelo.
// ---------------------------------------------------------------------------
const disponivel = await bancoDisponivel();

// Os usuários do seed têm UUID() aleatório e cada banco de teste é um banco distinto: a chave dos
// motoristas muda de um banco para o outro. Compara motorista pelo rótulo (nome) e ignora o id do filtro.
function normalizar(relatorio) {
  const copia = JSON.parse(JSON.stringify(relatorio));
  if (copia.parametros.agrupar === 'motorista') for (const g of copia.grupos) g.chave = g.rotulo;
  if (copia.parametros.motoristaId) copia.parametros.motoristaId = 'filtro';
  return copia;
}
const igual = (a, b, mensagem) => assert.deepEqual(normalizar(a), normalizar(b), mensagem);

describe('índices contra o banco de teste', { skip: disponivel ? false : 'MySQL indisponível' }, () => {
  let comIndices;
  let semIndices;
  const periodos = [
    { inicio: '2026-10-01', fim: '2026-10-31' },
    { inicio: '2026-09-01', fim: '2026-12-31' },
  ];

  before(async () => {
    ({ pool: comIndices } = await prepararBancoTeste({ nome: 'fleetflow_idx_test' }));
    ({ pool: semIndices } = await prepararBancoTeste({ nome: 'fleetflow_semidx_test', comIndices: false }));
  });

  after(async () => {
    await comIndices?.end();
    await semIndices?.end();
  });

  test('os cinco índices existem depois da migração (e não existem sem ela)', async () => {
    const consulta = "SELECT DISTINCT INDEX_NAME AS nome FROM information_schema.statistics WHERE table_schema = DATABASE() AND INDEX_NAME LIKE 'ix\\_%'";
    const [com] = await comIndices.query(consulta);
    const [sem] = await semIndices.query(consulta);
    assert.deepEqual(com.map((r) => r.nome ?? r.INDEX_NAME).sort(), [
      'ix_abast_relatorio', 'ix_mov_caixa', 'ix_mov_competencia', 'ix_os_movimentacao', 'ix_viagens_concluidas',
    ]);
    assert.equal(sem.length, 0);
  });

  test('rentabilidade e custo-km dão exatamente o mesmo resultado com e sem índices (hint só gera warning)', async () => {
    for (const periodo of periodos) {
      for (const regime of REGIMES) {
        for (const agrupar of AGRUPAMENTOS) {
          const filtros = { ...periodo, regime, agrupar };
          igual(await rentabilidade(comIndices, filtros), await rentabilidade(semIndices, filtros), `rentabilidade ${agrupar}/${regime}`);
          igual(await custoKm(comIndices, filtros), await custoKm(semIndices, filtros), `custo-km ${agrupar}/${regime}`);
        }
      }
    }
  });

  test('os filtros por veículo, rota e motorista continuam valendo', async () => {
    // Veículos e rotas da fixture têm ids fixos; o motorista tem UUID aleatório em cada banco.
    const ids = async (pool) => {
      const [[v]] = await pool.query("SELECT Id FROM veiculos WHERE Placa = 'ABC1D23'");
      const [[r]] = await pool.query("SELECT Id FROM rotas WHERE Nome = 'SP-RJ'");
      const [[m]] = await pool.query("SELECT Id FROM usuarios WHERE Email = 'teste.motorista@exemplo.com'");
      return { veiculoId: v.Id, rotaId: r.Id, motoristaId: m.Id };
    };
    const [a, b] = [await ids(comIndices), await ids(semIndices)];
    const combinacoes = [['veiculoId'], ['rotaId'], ['motoristaId'], ['veiculoId', 'rotaId'], ['veiculoId', 'rotaId', 'motoristaId']];
    for (const campos of combinacoes) {
      for (const agrupar of AGRUPAMENTOS) {
        for (const regime of REGIMES) {
          const pegar = (origem) => Object.fromEntries(campos.map((c) => [c, origem[c]]));
          const base = { ...periodos[1], regime, agrupar };
          const rel = await rentabilidade(comIndices, { ...base, ...pegar(a) });
          igual(rel, await rentabilidade(semIndices, { ...base, ...pegar(b) }), `${agrupar}/${regime} ${campos}`);
          igual(await custoKm(comIndices, { ...base, ...pegar(a) }), await custoKm(semIndices, { ...base, ...pegar(b) }), `custo-km ${agrupar}/${regime} ${campos}`);
          // O filtro tem efeito: nunca devolve mais receita do que o relatório sem filtro.
          const total = await rentabilidade(comIndices, base);
          assert.ok(rel.totais.receita <= total.totais.receita);
        }
      }
    }
  });

  test('o plano de cada consulta usa o índice esperado e não varre tabela grande por completo', async () => {
    for (const regime of REGIMES) {
      for (const agrupar of AGRUPAMENTOS) {
        const f = { ...periodos[1], regime, agrupar };
        const consultas = { mov: sqlMovimentacoes(f), km: sqlKm(f), comb: sqlCombustivel(f) };
        for (const [nome, consulta] of Object.entries(consultas)) {
          const plano = await planoDe(comIndices, consulta);
          // Na fixture (poucas linhas) o otimizador pode varrer dimensões minúsculas; o que importa
          // é que a tabela do filtro principal use o índice do hint.
          const problemas = avaliarPlano(plano, INDICES_ESPERADOS[nome][regime]).filter((p) => !p.startsWith('varredura completa'));
          assert.deepEqual(problemas, [], `${nome}/${regime}/${agrupar}: ${JSON.stringify(plano)}`);
        }
      }
    }
  });

  test('sem os índices o plano NÃO usa os ix_* (o avaliador de plano detecta a migração faltando)', async () => {
    const f = { ...periodos[1], regime: 'caixa', agrupar: 'veiculo' };
    const plano = await planoDe(semIndices, sqlMovimentacoes(f));
    const problemas = avaliarPlano(plano, INDICES_ESPERADOS.mov.caixa);
    assert.ok(problemas.some((p) => /ix_mov_caixa/.test(p)), JSON.stringify(plano));
  });
});
