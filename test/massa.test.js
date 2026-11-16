import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { contar, gerarMassa, janelaPadrao } from '../scripts/massa/gerador.js';

// Sem banco: o gerador é puro. Aqui garantimos que a massa de 12 meses respeita as regras do schema
// e as verificações de integridade da conferência, e que é reproduzível (mesma semente = mesma massa).

const refs = {
  perfilMotoristaId: 'perfil-motorista',
  categorias: {
    Frete: 'cat-frete', Combustivel: 'cat-comb', Pedagio: 'cat-pedagio', Manutencao: 'cat-manut', Salario: 'cat-salario',
    Financiamento: 'cat-financ', Seguro: 'cat-seguro', Impostos: 'cat-impostos', Outros: 'cat-outros',
  },
  formas: ['forma-pix', 'forma-boleto', 'forma-ted'],
};
const PARAMETROS = { inicio: '2025-11-01', meses: 12, hoje: '2026-11-16', semente: 7, veiculos: 12, motoristas: 14, rotas: 10, refs };
const massa = gerarMassa(PARAMETROS);
const t = massa.tabelas;
const porId = (linhas) => new Map(linhas.map((l) => [l.Id, l]));

describe('janela de 12 meses', () => {
  test('janelaPadrao pega os últimos 12 meses cheios antes de hoje', () => {
    assert.deepEqual(janelaPadrao(new Date('2026-11-16T10:00:00Z')), { inicio: '2025-11-01', fim: '2026-10-31' });
    assert.deepEqual(janelaPadrao(new Date('2026-03-01T00:00:00Z')), { inicio: '2025-03-01', fim: '2026-02-28' });
    assert.deepEqual(janelaPadrao(new Date('2026-03-01T00:00:00Z'), 3), { inicio: '2025-12-01', fim: '2026-02-28' });
  });

  test('toda viagem fica dentro da janela e todos os 12 meses têm viagens e movimentações', () => {
    assert.equal(massa.janela.inicio, '2025-11-01');
    assert.equal(massa.janela.fim, '2026-10-31');
    for (const v of t.viagens) {
      assert.ok(v.DataInicio.slice(0, 10) >= massa.janela.inicio, `início fora da janela: ${v.DataInicio}`);
      assert.ok(v.DataFim.slice(0, 10) <= massa.janela.fim, `fim fora da janela: ${v.DataFim}`);
    }
    const meses = (linhas, campo) => new Set(linhas.filter((l) => l[campo]).map((l) => l[campo].slice(0, 7)));
    assert.equal(meses(t.viagens, 'DataFim').size, 12);
    assert.equal(meses(t.movimentacoes.filter((m) => m.Status === 'Pago'), 'DataPagamento').size >= 12, true);
    assert.equal(meses(t.movimentacoes, 'DataVencimento').size >= 12, true);
  });
});

describe('reprodutibilidade', () => {
  test('mesma semente gera exatamente a mesma massa; semente diferente, outra', () => {
    assert.deepEqual(gerarMassa(PARAMETROS), massa);
    assert.notDeepEqual(gerarMassa({ ...PARAMETROS, semente: 8 }).tabelas.viagens, t.viagens);
  });

  test('escala multiplica veículos e motoristas', () => {
    const dobro = gerarMassa({ ...PARAMETROS, escala: 2 });
    assert.equal(dobro.tabelas.veiculos.length, t.veiculos.length * 2);
    assert.equal(dobro.tabelas.usuarios.length, t.usuarios.length * 2);
    assert.ok(contar(dobro).viagens > contar(massa).viagens * 1.8);
  });
});

describe('regras do schema', () => {
  test('ids, placas e e-mails são únicos e a placa segue o padrão do CHECK', () => {
    for (const [nome, linhas] of Object.entries(t)) {
      assert.equal(new Set(linhas.map((l) => l.Id)).size, linhas.length, `ids repetidos em ${nome}`);
    }
    assert.equal(new Set(t.veiculos.map((v) => v.Placa)).size, t.veiculos.length);
    assert.equal(new Set(t.usuarios.map((u) => u.Email)).size, t.usuarios.length);
    assert.equal(new Set(t.usuarios.map((u) => u.GoogleSubjectId)).size, t.usuarios.length);
    for (const v of t.veiculos) assert.match(v.Placa, /^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$/);
  });

  test('todas as linhas de uma tabela têm as mesmas colunas (INSERT em lote)', () => {
    for (const [nome, linhas] of Object.entries(t)) {
      const colunas = Object.keys(linhas[0]).join();
      assert.ok(linhas.every((l) => Object.keys(l).join() === colunas), `colunas divergentes em ${nome}`);
    }
  });

  test('movimentações: valor > 0, Pago sempre com DataPagamento, nunca pago no futuro, todas com vencimento', () => {
    for (const m of t.movimentacoes) {
      assert.ok(m.Valor > 0);
      assert.equal(m.Status === 'Pago', m.DataPagamento !== null, `Status/DataPagamento inconsistentes: ${m.Id}`);
      assert.ok(m.DataVencimento, `sem vencimento: ${m.Id}`);
      if (m.DataPagamento) assert.ok(m.DataPagamento <= PARAMETROS.hoje, `pago no futuro: ${m.DataPagamento}`);
    }
    assert.ok(t.movimentacoes.some((m) => m.Status === 'Pendente'), 'precisa haver pendentes (regime de competência)');
    assert.ok(t.movimentacoes.some((m) => m.Ativo === 0), 'precisa haver movimentação inativa (filtro Ativo)');
  });

  test('chaves estrangeiras apontam para linhas que existem', () => {
    const veiculos = porId(t.veiculos); const rotas = porId(t.rotas); const usuarios = porId(t.usuarios);
    const viagens = porId(t.viagens); const dividas = porId(t.dividas); const movs = porId(t.movimentacoes);
    const categorias = new Set(Object.values(refs.categorias));
    for (const v of t.viagens) {
      assert.ok(veiculos.has(v.VeiculoId) && rotas.has(v.RotaId) && usuarios.has(v.MotoristaId));
    }
    for (const m of t.movimentacoes) {
      assert.ok(categorias.has(m.CategoriaId) && refs.formas.includes(m.FormaPagamentoId));
      if (m.ViagemId) assert.ok(viagens.has(m.ViagemId));
      if (m.DividaId) assert.ok(dividas.has(m.DividaId));
      if (m.VeiculoId) assert.ok(veiculos.has(m.VeiculoId));
    }
    for (const a of t.abastecimentos) assert.ok(movs.has(a.MovimentacaoId) && veiculos.has(a.VeiculoId));
    for (const o of t.ordens_servico) assert.ok(movs.has(o.MovimentacaoId) && veiculos.has(o.VeiculoId));
  });
});

describe('integridade (mesmas regras de src/conferencia/sql/integridade)', () => {
  test('viagem concluída: hodômetro final, KmRodados = final - inicial e DataFim; cancelada não anda', () => {
    const concluidas = t.viagens.filter((v) => v.Status === 'Concluida');
    assert.ok(concluidas.length > 100);
    for (const v of concluidas) {
      assert.equal(v.KmRodados, v.HodometroFinal - v.HodometroInicial);
      assert.ok(v.KmRodados > 0 && v.DataFim >= v.DataInicio);
    }
    const canceladas = t.viagens.filter((v) => v.Status === 'Cancelada');
    assert.ok(canceladas.length > 0);
    for (const v of canceladas) assert.ok(v.HodometroFinal === null && v.KmRodados === null);
  });

  test('um veículo nunca está em duas viagens ao mesmo tempo e o hodômetro encadeia', () => {
    const porVeiculo = Map.groupBy(t.viagens, (v) => v.VeiculoId);
    for (const viagens of porVeiculo.values()) {
      viagens.sort((a, b) => a.DataInicio.localeCompare(b.DataInicio));
      for (let i = 1; i < viagens.length; i += 1) {
        assert.ok(viagens[i].DataInicio >= viagens[i - 1].DataFim, 'viagens do mesmo veículo se sobrepõem');
      }
      const concluidas = viagens.filter((v) => v.Status === 'Concluida');
      for (let i = 1; i < concluidas.length; i += 1) {
        assert.equal(concluidas[i].HodometroInicial, concluidas[i - 1].HodometroFinal);
      }
    }
  });

  test('abastecimento e OS têm o mesmo valor da movimentação (fonte única de dinheiro) e a movimentação está ativa', () => {
    const movs = porId(t.movimentacoes);
    for (const a of t.abastecimentos) {
      const m = movs.get(a.MovimentacaoId);
      assert.equal(a.ValorTotal, m.Valor);
      assert.equal(m.Ativo, 1);
      assert.equal(m.LitrosCombustivel, a.Litros);
      assert.ok(a.Litros > 0 && a.PrecoLitro > 0 && a.Hodometro >= 0);
    }
    for (const o of t.ordens_servico) {
      const m = movs.get(o.MovimentacaoId);
      assert.equal(o.ValorTotal, m.Valor);
      assert.equal(m.Ativo, 1);
      assert.ok(o.DataConclusao === null || o.DataConclusao >= o.DataManutencao);
    }
    // 1 abastecimento/OS por movimentação (sem fan-out)
    assert.equal(new Set(t.abastecimentos.map((a) => a.MovimentacaoId)).size, t.abastecimentos.length);
    assert.equal(new Set(t.ordens_servico.map((o) => o.MovimentacaoId)).size, t.ordens_servico.length);
  });

  test('dívida: exatamente QuantidadeParcelas movimentações, numeradas de 1 a N sem repetir', () => {
    assert.ok(t.dividas.length > 0);
    for (const d of t.dividas) {
      const parcelas = t.movimentacoes.filter((m) => m.DividaId === d.Id).map((m) => m.NumeroParcela).sort((a, b) => a - b);
      assert.deepEqual(parcelas, Array.from({ length: d.QuantidadeParcelas }, (_, i) => i + 1));
    }
  });

  test('HodometroAtual do veículo nunca é menor que o maior hodômetro de viagem ou abastecimento', () => {
    for (const v of t.veiculos) {
      const maxViagem = Math.max(0, ...t.viagens.filter((x) => x.VeiculoId === v.Id && x.HodometroFinal !== null).map((x) => x.HodometroFinal));
      const maxAbast = Math.max(0, ...t.abastecimentos.filter((x) => x.VeiculoId === v.Id).map((x) => x.Hodometro));
      assert.ok(v.HodometroAtual >= maxViagem && v.HodometroAtual >= maxAbast, `hodômetro regrediu em ${v.Placa}`);
    }
  });

  test('o volume cobre todos os caminhos dos relatórios (receita, custo ligado a viagem, custo sem veículo, litros, OS)', () => {
    const n = (fn) => t.movimentacoes.filter(fn).length;
    assert.ok(n((m) => m.CategoriaId === 'cat-frete') > 100);
    assert.ok(n((m) => m.ViagemId && m.CategoriaId !== 'cat-frete') > 100, 'custo variável (ligado a viagem)');
    assert.ok(n((m) => !m.VeiculoId && !m.ViagemId && !m.DividaId) > 50, 'custo não alocado');
    assert.ok(t.abastecimentos.some((a) => a.ViagemId === null), 'abastecimento sem viagem');
    assert.ok(t.ordens_servico.some((o) => o.Tipo === 'Preventiva') && t.ordens_servico.some((o) => o.Tipo === 'Corretiva'));
  });
});
