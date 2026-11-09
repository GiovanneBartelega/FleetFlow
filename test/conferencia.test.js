import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { conferir } from '../src/conferencia/conferir.js';
import { verificarIntegridade } from '../src/conferencia/integridade.js';
import { compararValores, formatarTabela } from '../src/conferencia/comparar.js';
import { prepararSql } from '../src/conferencia/consultas.js';
import { custoKm, rentabilidade } from '../src/services/relatorios.js';
import { startServer, tokenFor } from './helpers.js';
import { bancoDisponivel, dividirScript, lerEsperado, nomeBancoTeste, prepararBancoTeste } from './bancoTeste.js';

// ---------------------------------------------------------------------------
// Testes sem banco: utilitários da conferência.
// ---------------------------------------------------------------------------
describe('utilitários da conferência', () => {
  test('nomeBancoTeste nunca devolve o banco de desenvolvimento', () => {
    assert.equal(nomeBancoTeste({ DB_NAME: 'fleetflow' }), 'fleetflow_test');
    assert.equal(nomeBancoTeste({ DB_NAME: 'outro_test' }), 'outro_test');
    assert.equal(nomeBancoTeste({ DB_NAME: 'fleetflow', DB_TEST_NAME: 'ci_test' }), 'ci_test');
    assert.throws(() => nomeBancoTeste({ DB_NAME: 'fleetflow', DB_TEST_NAME: 'producao' }), /_test/);
  });

  test('dividirScript respeita DELIMITER e remove CREATE DATABASE/USE', () => {
    const comandos = dividirScript(
      [
        'CREATE DATABASE IF NOT EXISTS fleetflow;',
        'USE fleetflow;',
        '-- comentário; com ponto e vírgula',
        'CREATE TABLE a (id INT);',
        'DELIMITER $$',
        'CREATE TRIGGER t BEFORE UPDATE ON a FOR EACH ROW',
        'BEGIN',
        '  SET NEW.id = 1;',
        'END$$',
        'DELIMITER ;',
        'INSERT INTO a VALUES (1);',
      ].join('\n'),
    );
    assert.equal(comandos.length, 3);
    assert.match(comandos[1], /^CREATE TRIGGER t[\s\S]*SET NEW\.id = 1;[\s\S]*END$/);
    assert.equal(comandos[2], 'INSERT INTO a VALUES (1)');
  });

  test('prepararSql troca marcadores por ? na ordem e ignora comentários', () => {
    const { sql, params } = prepararSql(
      '-- usa :regime aqui\nSELECT 1 WHERE :regime = \'caixa\' AND d BETWEEN :inicio AND :fim',
      { inicio: 'A', fim: 'B', regime: 'C' },
    );
    assert.equal(sql, "SELECT 1 WHERE ? = 'caixa' AND d BETWEEN ? AND ?");
    assert.deepEqual(params, ['C', 'A', 'B']);
  });

  test('compararValores respeita a tolerância e trata null', () => {
    assert.equal(compararValores('m', null, 10.004, 10, 0.01).ok, true);
    assert.equal(compararValores('m', null, 10.02, 10, 0.01).ok, false);
    assert.equal(compararValores('m', null, null, null, 0.001).ok, true);
    assert.equal(compararValores('m', null, null, 0, 0.001).ok, false);
    assert.match(formatarTabela([compararValores('km', 'ABC1D23', 1, 2, 0)]), /DIVERGE/);
  });
});

// ---------------------------------------------------------------------------
// Testes contra o banco de teste (schema + fixture), comparando com esperado.json.
// ---------------------------------------------------------------------------
const disponivel = await bancoDisponivel();

describe('conferência contra o banco de teste', { skip: disponivel ? false : 'MySQL indisponível' }, () => {
  let pool;
  let esperado;
  let ids;
  let api;

  before(async () => {
    ({ pool } = await prepararBancoTeste());
    esperado = await lerEsperado();
    const [usuarios] = await pool.query('SELECT Id, Email FROM usuarios');
    ids = Object.fromEntries(usuarios.map((u) => [u.Email, u.Id]));
    api = await startServer({ pool });
  });

  after(async () => {
    await api?.close();
    await pool?.end();
  });

  // Une as duas visões (rentabilidade + custo-km) de cada grupo num objeto só.
  const executar = async (parametros) => {
    const filtros = { ...parametros };
    if (filtros.motoristaId === '@M2') filtros.motoristaId = ids['teste.admin@exemplo.com'];
    const rent = await rentabilidade(pool, filtros);
    const ckm = await custoKm(pool, filtros);
    const unir = (a, b) => ({ ...a, ...b });
    const porRotulo = new Map(
      rent.grupos.map((g) => [g.rotulo, unir(g, ckm.grupos.find((x) => x.chave === g.chave))]),
    );
    return {
      grupos: porRotulo,
      totais: unir(rent.totais, ckm.totais),
      custosNaoAlocados: rent.custosNaoAlocados,
      receitaNaoAlocada: rent.receitaNaoAlocada,
      viagensSemKm: rent.viagensSemKm,
    };
  };

  const objetoDeCategorias = (lista) => Object.fromEntries(lista.map((c) => [c.categoria, c.valor]));

  function conferirCampos(esp, real, onde) {
    for (const [campo, valor] of Object.entries(esp)) {
      if (campo.startsWith('_')) continue;
      const atual = real[campo];
      if (campo === 'custosPorCategoria' || campo === 'porCategoria') {
        assert.deepEqual(objetoDeCategorias(atual), valor, `${onde}.${campo}`);
      } else if (valor !== null && typeof valor === 'object') {
        assert.deepEqual(atual, valor, `${onde}.${campo}`);
      } else if (typeof valor === 'number') {
        assert.ok(typeof atual === 'number' && Math.abs(atual - valor) < 1e-9, `${onde}.${campo}: esperado ${valor}, veio ${atual}`);
      } else {
        assert.equal(atual, valor, `${onde}.${campo}`);
      }
    }
  }

  test('cada cenário do esperado.json (calculado à mão) bate com o service', async () => {
    for (const [nome, esp] of Object.entries(esperado.cenarios)) {
      const real = await executar(esp.parametros);

      assert.deepEqual(
        [...real.grupos.keys()].sort(),
        Object.keys(esp.grupos).sort(),
        `${nome}: conjunto de grupos`,
      );
      for (const [rotulo, campos] of Object.entries(esp.grupos)) {
        conferirCampos(campos, real.grupos.get(rotulo), `${nome}/${rotulo}`);
      }
      if (esp.totais) conferirCampos(esp.totais, real.totais, `${nome}/totais`);
      if (esp.custosNaoAlocados) conferirCampos(esp.custosNaoAlocados, real.custosNaoAlocados, `${nome}/naoAlocados`);
      if (esp.receitaNaoAlocada !== undefined) assert.equal(real.receitaNaoAlocada, esp.receitaNaoAlocada, nome);
      if (esp.viagensSemKm !== undefined) assert.equal(real.viagensSemKm, esp.viagensSemKm, nome);
    }
  });

  test('nenhum campo numérico do relatório vira NaN ou Infinity', async () => {
    for (const agrupar of ['veiculo', 'rota', 'motorista', 'viagem', 'mes']) {
      for (const regime of ['caixa', 'competencia']) {
        const r = await rentabilidade(pool, { inicio: '2026-01-01', fim: '2026-12-31', agrupar, regime });
        const percorrer = (valor) => {
          if (typeof valor === 'number') assert.ok(Number.isFinite(valor), `${agrupar}/${regime}: ${valor}`);
          else if (valor && typeof valor === 'object') Object.values(valor).forEach(percorrer);
        };
        percorrer(r);
      }
    }
  });

  test('período sem dados devolve grupos vazios e zeros, não erro', async () => {
    const r = await rentabilidade(pool, { inicio: '2030-01-01', fim: '2030-01-31', agrupar: 'veiculo', regime: 'caixa' });
    assert.deepEqual(r.grupos, []);
    assert.equal(r.totais.receita, 0);
    assert.equal(r.totais.margemPercentual, null);
    assert.equal(r.totais.custoPorKm.total, null);
  });

  test('o runner de conferência não encontra divergência entre app e banco na fixture', async () => {
    for (const regime of ['caixa', 'competencia']) {
      const resultado = await conferir(pool, { inicio: '2026-10-01', fim: '2026-10-31', regime });
      const ruins = resultado.comparacoes.filter((c) => !c.ok);
      assert.deepEqual(ruins, [], `regime ${regime}: ${JSON.stringify(ruins)}`);
      assert.ok(resultado.comparacoes.length > 40);
    }
  });

  test('integridade da fixture bate com o esperado.json', async () => {
    const resultado = await conferir(pool, { inicio: '2026-10-01', fim: '2026-10-31', regime: 'caixa' });
    const quantidade = Object.fromEntries(resultado.integridade.map((v) => [v.id, v.quantidade]));
    const esp = esperado.integridade;
    for (const id of Object.keys(quantidade)) assert.equal(quantidade[id], esp[id], id);

    // T3 (KmRodados nulo) e T5 (sem hodômetro final) são as linhas problemáticas das viagens.
    const viagens = resultado.integridade.find((v) => v.id === 'viagens_km_inconsistente');
    assert.deepEqual(viagens.linhas.map((l) => l.viagem_id).sort(), [
      '30000000-0000-4000-8000-000000000003',
      '30000000-0000-4000-8000-000000000005',
    ]);
    assert.equal(resultado.resumo.codigoSaida, 2);

    const pendentes = resultado.comparacoes.filter((c) => c.metrica.startsWith('contas a pagar pendentes'));
    assert.equal(pendentes.find((c) => c.metrica.endsWith('quantidade')).app, esp.contas_a_pagar_pendentes.quantidade);
    assert.equal(pendentes.find((c) => c.metrica.endsWith('valor')).banco, esp.contas_a_pagar_pendentes.valor);
  });

  test('diferença entre vw_resumo_financeiro e o relatório é a esperada (pendentes + fora do período)', async () => {
    const resultado = await conferir(pool, { inicio: '2026-10-01', fim: '2026-10-31', regime: 'caixa' });
    const categorias = new Map(resultado.resumoFinanceiroView.categorias.map((c) => [c.categoria, c]));
    const { _calculo, ...casos } = esperado.integridade.view_resumo_financeiro;
    for (const [categoria, esp] of Object.entries(casos)) {
      const real = categorias.get(categoria);
      assert.equal(real.view, esp.view, `${categoria}: view`);
      assert.equal(real.app, esp.app, `${categoria}: app`);
      assert.equal(real.diferencaEsperada, esp.diferencaEsperada, `${categoria}: diferença`);
      assert.equal(real.foraDoEscopo, esp.diferencaEsperada, `${categoria}: fora do escopo`);
    }
  });

  test('as verificações de integridade apontam as linhas ruins quando há problema', async () => {
    const conexao = await pool.getConnection();
    try {
      await conexao.query('START TRANSACTION');
      // abastecimento e OS com valor diferente da movimentação
      await conexao.query("UPDATE abastecimentos SET ValorTotal = 1.00 WHERE Id = '60000000-0000-4000-8000-000000000001'");
      await conexao.query("UPDATE ordens_servico SET ValorTotal = 5.00 WHERE Id = '70000000-0000-4000-8000-000000000001'");
      // viagem com KmRodados diferente do hodômetro
      await conexao.query("UPDATE viagens SET KmRodados = 999 WHERE Id = '30000000-0000-4000-8000-000000000001'");
      // hodômetro do veículo abaixo do maior hodômetro final de viagem
      await conexao.query("UPDATE veiculos SET HodometroAtual = 100 WHERE Id = '10000000-0000-4000-8000-000000000002'");
      // parcela duplicada (nº 2 repetido) e dívida com parcela faltando (3 vira 2)
      await conexao.query(
        `INSERT INTO movimentacoes (Id, Valor, Descricao, CategoriaId, FormaPagamentoId, DividaId, NumeroParcela, DataVencimento, Status)
         SELECT '40000000-0000-4000-8000-000000000099', 1000, 'Parcela repetida', CategoriaId, FormaPagamentoId, DividaId, 2, '2027-01-10', 'Pendente'
           FROM movimentacoes WHERE Id = '40000000-0000-4000-8000-000000000015'`,
      );

      const resultado = await verificarIntegridade(conexao, { inicio: '2026-10-01', fim: '2026-10-31', regime: 'caixa' });
      const por = Object.fromEntries(resultado.map((v) => [v.id, v]));

      assert.deepEqual(por.abastecimento_valor_vs_movimentacao.linhas.map((l) => [l.abastecimento_id, Number(l.diferenca)]),
        [['60000000-0000-4000-8000-000000000001', -1499]]);
      assert.deepEqual(por.os_valor_vs_movimentacao.linhas.map((l) => [l.ordem_servico_id, Number(l.diferenca)]),
        [['70000000-0000-4000-8000-000000000001', -795]]);
      assert.ok(por.viagens_km_inconsistente.linhas.some(
        (l) => l.viagem_id === '30000000-0000-4000-8000-000000000001' && l.motivo === 'KmRodados diferente do hodômetro'));
      assert.deepEqual(por.hodometro_veiculo_menor.linhas.map((l) => l.placa), ['DEF2E34']);
      assert.equal(por.dividas_parcelas.quantidade, 1);
      const divida = por.dividas_parcelas.linhas[0];
      assert.equal(Number(divida.movimentacoes), 4);
      assert.equal(Number(divida.duplicadas_ou_sem_numero), 1);
    } finally {
      await conexao.query('ROLLBACK');
      conexao.release();
    }
  });

  describe('endpoints', () => {
    const query = '?inicio=2026-10-01&fim=2026-10-31&agrupar=veiculo';
    const tokenDe = (email) => tokenFor(ids[email]);

    test('Administrador, Financeiro e GestorFrota acessam; OperadorMotorista não', async () => {
      for (const email of ['teste.admin@exemplo.com', 'teste.financeiro@exemplo.com', 'teste.gestor@exemplo.com']) {
        for (const rota of ['rentabilidade', 'custo-km']) {
          const r = await api.request('GET', `/api/relatorios/${rota}${query}`, { token: tokenDe(email) });
          assert.equal(r.status, 200, `${email} ${rota}`);
        }
      }
      const negado = await api.request('GET', `/api/relatorios/rentabilidade${query}`, {
        token: tokenDe('teste.motorista@exemplo.com'),
      });
      assert.equal(negado.status, 403);
      const semToken = await api.request('GET', `/api/relatorios/custo-km${query}`);
      assert.equal(semToken.status, 401);
    });

    test('rentabilidade devolve os números da fixture no formato documentado', async () => {
      const r = await api.request('GET', `/api/relatorios/rentabilidade${query}`, {
        token: tokenDe('teste.financeiro@exemplo.com'),
      });
      const v1 = r.body.grupos.find((g) => g.rotulo === 'ABC1D23');
      assert.equal(v1.receita, 12000);
      assert.equal(v1.custoTotal, 5550);
      assert.equal(v1.lucro, 6450);
      assert.equal(v1.margemPercentual, 53.75);
      assert.deepEqual(v1.custoPorKm, { variavel: 3.6765, total: 5.4412 });
      assert.equal(r.body.custosNaoAlocados.total, 3000);
      assert.equal(r.body.viagensSemKm, 1);
    });

    test('custo-km traz as duas variantes e o regime competência muda a base', async () => {
      const r = await api.request('GET', `/api/relatorios/custo-km?inicio=2026-10-01&fim=2026-10-31&agrupar=veiculo&regime=competencia`, {
        token: tokenDe('teste.gestor@exemplo.com'),
      });
      const v2 = r.body.grupos.find((g) => g.rotulo === 'DEF2E34');
      assert.equal(v2.custoVariavelPorKm, 2.5);
      assert.equal(v2.custoTotalPorKm, 4.7727);
      assert.equal(r.body.parametros.regime, 'competencia');
    });

    test('parâmetros inválidos devolvem 400 com mensagem em português', async () => {
      const token = tokenDe('teste.admin@exemplo.com');
      const casos = [
        ['', /inicial/],
        ['?inicio=2026-10-01', /final/],
        ['?inicio=01/10/2026&fim=2026-10-31', /AAAA-MM-DD/],
        ['?inicio=2026-10-31&fim=2026-10-01', /anterior/],
        [`${query}&regime=lucro`, /regime deve ser/],
        ['?inicio=2026-10-01&fim=2026-10-31&agrupar=drop', /agrupar deve ser/],
        [`${query}&veiculoId=1' OR '1'='1`, /UUID/],
      ];
      for (const [qs, texto] of casos) {
        const r = await api.request('GET', `/api/relatorios/rentabilidade${qs}`, { token });
        assert.equal(r.status, 400, qs);
        assert.equal(r.body.error.code, 'VALIDATION_ERROR');
        assert.match(JSON.stringify(r.body.error.details), texto, qs);
      }
    });
  });
});
