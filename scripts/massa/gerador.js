// Gerador PURO da massa de 12 meses (sem banco, sem I/O): recebe parâmetros e referências
// (ids de perfil/categorias/formas de pagamento) e devolve as linhas de cada tabela, com os
// nomes de coluna do schema. Determinístico: a mesma semente gera exatamente a mesma massa.
//
// A massa respeita as MESMAS regras que `npm run conferencia` verifica (src/conferencia/sql/integridade):
//  - abastecimentos.ValorTotal e ordens_servico.ValorTotal = Valor da movimentação vinculada;
//  - viagem concluída: HodometroFinal, KmRodados = Final - Inicial e DataFim preenchidos;
//  - veiculos.HodometroAtual >= maior hodômetro de viagem/abastecimento;
//  - dívida: exatamente QuantidadeParcelas movimentações, NumeroParcela 1..N sem repetir;
//  - movimentação Pago sempre com DataPagamento (CHECK do schema).
// Assim, a massa serve de prova de desempenho E de corretude dos relatórios.

// ---------------------------------------------------------------------------
// Utilitários determinísticos
// ---------------------------------------------------------------------------
export function criarRng(semente) {
  let a = semente >>> 0;
  const proximo = () => {
    // mulberry32
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    proximo,
    entre: (min, max) => min + proximo() * (max - min),
    inteiro: (min, max) => Math.floor(min + proximo() * (max - min + 1)),
    chance: (p) => proximo() < p,
    escolher: (lista) => lista[Math.floor(proximo() * lista.length)],
  };
}

const arred = (valor, casas = 2) => Number(valor.toFixed(casas));
const DIA_MS = 86_400_000;

const doisDigitos = (n) => String(n).padStart(2, '0');
export const fmtData = (d) => `${d.getUTCFullYear()}-${doisDigitos(d.getUTCMonth() + 1)}-${doisDigitos(d.getUTCDate())}`;
export const fmtDataHora = (d) => `${fmtData(d)} ${doisDigitos(d.getUTCHours())}:${doisDigitos(d.getUTCMinutes())}:00`;
const somarDias = (d, dias) => new Date(d.getTime() + dias * DIA_MS);
const somarMeses = (d, meses) => new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + meses, d.getUTCDate()));
const inicioDoDia = (d) => new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));

// UUIDs legíveis e estáveis: o prefixo identifica a tabela, o final é o contador.
const PREFIXOS = {
  usuario: 'f0000001', veiculo: 'f0000002', rota: 'f0000003', divida: 'f0000004',
  viagem: 'f0000005', movimentacao: 'f0000006', abastecimento: 'f0000007', os: 'f0000008',
};
const uuid = (tipo, n) => `${PREFIXOS[tipo]}-0000-4000-8000-${n.toString(16).padStart(12, '0')}`;

// ---------------------------------------------------------------------------
// Dados de apoio
// ---------------------------------------------------------------------------
const CIDADES = [
  ['São Paulo', 'SP', -23.55, -46.63], ['Rio de Janeiro', 'RJ', -22.91, -43.17],
  ['Belo Horizonte', 'MG', -19.92, -43.94], ['Curitiba', 'PR', -25.43, -49.27],
  ['Porto Alegre', 'RS', -30.03, -51.22], ['Goiânia', 'GO', -16.68, -49.25],
  ['Brasília', 'DF', -15.79, -47.88], ['Salvador', 'BA', -12.97, -38.51],
  ['Recife', 'PE', -8.05, -34.88], ['Campinas', 'SP', -22.91, -47.06],
  ['Ribeirão Preto', 'SP', -21.18, -47.81], ['Uberlândia', 'MG', -18.92, -48.28],
  ['Florianópolis', 'SC', -27.59, -48.55], ['Vitória', 'ES', -20.32, -40.34],
  ['Cuiabá', 'MT', -15.6, -56.1], ['Campo Grande', 'MS', -20.47, -54.62],
];
const MARCAS = [
  ['Volvo', 'FH 540'], ['Scania', 'R 450'], ['Mercedes-Benz', 'Actros 2651'],
  ['DAF', 'XF 480'], ['Iveco', 'S-Way 480'], ['Volkswagen', 'Constellation 24.280'],
];
const POSTOS = ['Posto Graal', 'Posto Ipiranga BR-116', 'Posto Shell Dutra', 'Posto Petrobras Fernão Dias', 'Posto Ale Régis'];
const NOMES = ['Carlos', 'João', 'Marcos', 'Pedro', 'Lucas', 'Rafael', 'Anderson', 'Edson', 'Fábio', 'Sérgio', 'Renato', 'Paulo'];
const SOBRENOMES = ['Silva', 'Souza', 'Oliveira', 'Santos', 'Pereira', 'Costa', 'Almeida', 'Ribeiro', 'Carvalho', 'Gomes'];

function distanciaKm([, , lat1, lon1], [, , lat2, lon2]) {
  const rad = (g) => (g * Math.PI) / 180;
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2
    + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lon2 - lon1) / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(a)) * 1.25; // 1,25 ≈ fator rodoviário
}

const letra = (n) => String.fromCharCode(65 + (n % 26));
// AAA1B23 (Mercosul). As 3 primeiras letras são o índice em base 26, então a placa é única para i < 17.576.
const placaMercosul = (i) =>
  `${letra(Math.floor(i / 676))}${letra(Math.floor(i / 26))}${letra(i)}${i % 10}${letra(Math.floor(i / 10))}${doisDigitos(i % 100)}`;

// ---------------------------------------------------------------------------
// Janela de 12 meses
// ---------------------------------------------------------------------------
// Padrão: os últimos `meses` meses CHEIOS antes de `hoje` (ex.: hoje = 16/11/2026 -> 01/11/2025 a 31/10/2026).
export function janelaPadrao(hoje = new Date(), meses = 12) {
  const fimExclusivo = new Date(Date.UTC(hoje.getUTCFullYear(), hoje.getUTCMonth(), 1));
  const inicio = somarMeses(fimExclusivo, -meses);
  return { inicio: fmtData(inicio), fim: fmtData(somarDias(fimExclusivo, -1)) };
}

// ---------------------------------------------------------------------------
// Geração
// ---------------------------------------------------------------------------
/**
 * @param {object} opcoes
 * @param {string} opcoes.inicio   AAAA-MM-DD (primeiro dia da janela)
 * @param {number} [opcoes.meses=12]
 * @param {string} opcoes.hoje     AAAA-MM-DD: o que vence até aqui e foi "pago" fica Pago; o resto, Pendente
 * @param {number} [opcoes.semente=42]
 * @param {number} [opcoes.escala=1]  multiplica veículos e motoristas (volume para teste de estresse)
 * @param {object} opcoes.refs     { perfilMotoristaId, categorias: {Frete, Combustivel, Pedagio, Manutencao, Salario,
 *                                   Financiamento, Seguro, Impostos, Outros}, formas: [ids] }
 */
export function gerarMassa({
  inicio, meses = 12, hoje, semente = 42, escala = 1, veiculos: qtdVeiculos = 80, motoristas: qtdMotoristas = 90,
  rotas: qtdRotas = 40, refs,
}) {
  const rng = criarRng(semente);
  const nVeiculos = Math.round(qtdVeiculos * escala);
  const nMotoristas = Math.round(qtdMotoristas * escala);
  const ini = new Date(`${inicio}T00:00:00Z`);
  const limite = somarMeses(ini, meses); // exclusivo
  const dataHoje = new Date(`${hoje}T23:59:59Z`);
  const { categorias: cat, formas } = refs;
  const forma = () => rng.escolher(formas);

  const t = {
    usuarios: [], veiculos: [], rotas: [], dividas: [], viagens: [],
    movimentacoes: [], abastecimentos: [], ordens_servico: [],
  };
  let seqMov = 0;

  // Movimentação com TODAS as colunas sempre presentes (o INSERT em lote usa as chaves da 1ª linha).
  function movimentacao({
    valor, descricao, categoriaId, viagemId = null, dividaId = null, veiculoId = null, numeroParcela = null,
    vencimento, pagamento = null, litros = null, ativo = 1, criadoEm,
  }) {
    const id = uuid('movimentacao', ++seqMov);
    const pago = pagamento !== null;
    t.movimentacoes.push({
      Id: id, CriadoEm: criadoEm, AtualizadoEm: criadoEm, Ativo: ativo, SyncVersion: 1,
      Valor: valor, Descricao: descricao.slice(0, 200), CategoriaId: categoriaId, FormaPagamentoId: forma(),
      ViagemId: viagemId, DividaId: dividaId, VeiculoId: veiculoId, NumeroParcela: numeroParcela,
      DataVencimento: vencimento ? fmtData(vencimento) : null,
      DataPagamento: pago ? fmtData(pagamento) : null,
      Status: pago ? 'Pago' : 'Pendente', LitrosCombustivel: litros, CriadoPorUsuarioId: null,
    });
    return id;
  }

  // Decide Pago/Pendente: paga com `probPago`, `atraso` dias após o vencimento, e nunca no futuro de `hoje`.
  function quitacao(vencimento, probPago, atrasoMax = 0) {
    if (!rng.chance(probPago)) return null;
    const pagamento = somarDias(vencimento, rng.inteiro(0, atrasoMax));
    return pagamento <= dataHoje ? pagamento : null;
  }

  // ---- Usuários (motoristas) ------------------------------------------------
  const motoristasIds = [];
  for (let i = 1; i <= nMotoristas; i += 1) {
    const id = uuid('usuario', i);
    motoristasIds.push(id);
    const criado = fmtDataHora(somarDias(ini, -rng.inteiro(30, 400)));
    t.usuarios.push({
      Id: id, CriadoEm: criado, AtualizadoEm: criado, Ativo: 1, SyncVersion: 1,
      Nome: `${rng.escolher(NOMES)} ${rng.escolher(SOBRENOMES)} ${i}`,
      Email: `motorista${String(i).padStart(4, '0')}@massa.fleetflow.test`,
      GoogleSubjectId: `seed-massa-motorista-${i}`,
      PerfilId: refs.perfilMotoristaId, Status: 'Ativo', FotoUrl: null,
      CategoriaCnh: rng.escolher(['D', 'E', 'E']), ValidadeCnh: fmtData(somarDias(dataHoje, rng.inteiro(60, 1500))),
    });
  }
  const livreEm = new Map(motoristasIds.map((id) => [id, 0])); // ms em que o motorista fica livre

  // ---- Rotas ----------------------------------------------------------------
  const rotasLista = [];
  for (let i = 0; rotasLista.length < qtdRotas && i < 10_000; i += 1) {
    const a = CIDADES[rng.inteiro(0, CIDADES.length - 1)];
    const b = CIDADES[rng.inteiro(0, CIDADES.length - 1)];
    if (a === b) continue;
    const nome = `${a[1]}-${b[1]} ${a[0]} > ${b[0]}`;
    if (rotasLista.some((r) => r.Nome === nome)) continue;
    const criado = fmtDataHora(ini);
    rotasLista.push({
      Id: uuid('rota', rotasLista.length + 1), CriadoEm: criado, AtualizadoEm: criado, Ativo: 1, SyncVersion: 1,
      Nome: nome, CidadeOrigem: a[0], UfOrigem: a[1], CidadeDestino: b[0], UfDestino: b[1],
      DistanciaEstimadaKm: arred(Math.max(80, distanciaKm(a, b))),
    });
  }
  t.rotas.push(...rotasLista);

  // ---- Veículos, dívidas e viagens -------------------------------------------
  const precoBase = (data) => 5.9 + 0.35 * Math.sin((data.getUTCMonth() / 12) * 2 * Math.PI); // preço do diesel varia no ano

  for (let v = 1; v <= nVeiculos; v += 1) {
    const [marca, modelo] = MARCAS[(v - 1) % MARCAS.length];
    const veiculoId = uuid('veiculo', v);
    const placa = placaMercosul(v);
    const consumo = arred(rng.entre(2.3, 3.4)); // km/l de referência do veículo
    const intervaloKm = 30_000;
    let odometro = rng.inteiro(60_000, 450_000);
    let ultimaManutencao = odometro - rng.inteiro(0, 25_000);
    const marcos = [{ ms: 0, odo: odometro }]; // hodômetro ao fim de cada viagem concluída, em ordem
    const criado = fmtDataHora(somarDias(ini, -rng.inteiro(100, 900)));
    const veiculo = {
      Id: veiculoId, CriadoEm: criado, AtualizadoEm: criado, Ativo: 1, SyncVersion: 1,
      Placa: placa, Marca: marca, Modelo: modelo, AnoFabricacao: rng.inteiro(2015, 2025),
      CapacidadeCargaToneladas: arred(rng.entre(22, 45)), Status: 'Disponivel', HodometroAtual: odometro,
      IntervaloManutencaoKm: intervaloKm, IntervaloManutencaoMeses: 6, HodometroUltimaManutencao: ultimaManutencao,
      VencimentoLicenciamento: fmtData(somarDias(dataHoje, rng.inteiro(-20, 330))),
      VencimentoSeguro: fmtData(somarDias(dataHoje, rng.inteiro(-10, 330))),
      ConsumoMedioReferenciaKmL: consumo,
    };
    t.veiculos.push(veiculo);

    // Financiamento: metade da frota. Todas as parcelas existem (pagas e a vencer).
    if (rng.chance(0.5)) {
      const quantidade = rng.escolher([36, 48, 60]);
      const valorParcela = arred(rng.entre(4500, 11_000));
      const primeira = somarMeses(new Date(Date.UTC(ini.getUTCFullYear(), ini.getUTCMonth(), 10)), -rng.inteiro(0, 20));
      const dividaId = uuid('divida', t.dividas.length + 1);
      t.dividas.push({
        Id: dividaId, CriadoEm: fmtDataHora(primeira), AtualizadoEm: fmtDataHora(primeira), Ativo: 1, SyncVersion: 1,
        Descricao: `Financiamento ${marca} ${placa}`, CategoriaId: cat.Financiamento, QuantidadeParcelas: quantidade,
        ValorParcela: valorParcela, DataVencimentoPrimeira: fmtData(primeira), ValorQuitacaoAntecipada: null,
        VeiculoId: veiculoId,
      });
      for (let n = 1; n <= quantidade; n += 1) {
        const venc = somarMeses(primeira, n - 1);
        movimentacao({
          valor: valorParcela, descricao: `Financiamento ${placa} ${n}/${quantidade}`, categoriaId: cat.Financiamento,
          dividaId, numeroParcela: n, vencimento: venc, pagamento: quitacao(venc, 0.97, 3), criadoEm: fmtDataHora(primeira),
        });
      }
    }

    // Seguro mensal e impostos trimestrais (custo do veículo, sem viagem).
    for (let m = 0; m < meses; m += 1) {
      const venc = somarMeses(new Date(Date.UTC(ini.getUTCFullYear(), ini.getUTCMonth(), 8)), m);
      movimentacao({
        valor: arred(rng.entre(900, 1800)), descricao: `Seguro ${placa} ${fmtData(venc).slice(0, 7)}`,
        categoriaId: cat.Seguro, veiculoId, vencimento: venc, pagamento: quitacao(venc, 0.98, 2), criadoEm: fmtDataHora(venc),
      });
      if (m % 3 === 0) {
        const vencImp = somarMeses(new Date(Date.UTC(ini.getUTCFullYear(), ini.getUTCMonth(), 15)), m);
        movimentacao({
          valor: arred(rng.entre(1200, 3200)), descricao: `IPVA/licenciamento ${placa}`, categoriaId: cat.Impostos,
          veiculoId, vencimento: vencImp, pagamento: quitacao(vencImp, 0.97, 5), criadoEm: fmtDataHora(vencImp),
        });
      }
    }

    // Viagens em sequência (um veículo faz uma viagem por vez).
    let cursor = new Date(ini.getTime() + rng.inteiro(0, 3) * DIA_MS + rng.inteiro(6, 9) * 3_600_000);
    for (;;) {
      const rota = rng.escolher(rotasLista);
      const dias = Math.ceil(rota.DistanciaEstimadaKm / 650) + (rng.chance(0.3) ? 1 : 0);
      const fim = new Date(cursor.getTime() + dias * DIA_MS + rng.inteiro(0, 6) * 3_600_000);
      if (fim >= limite) break;

      // Motorista que esteja livre (tenta algumas vezes; senão, o que ficar livre antes).
      let motorista = rng.escolher(motoristasIds);
      for (let tentativa = 0; tentativa < 12 && livreEm.get(motorista) > cursor.getTime(); tentativa += 1) {
        motorista = rng.escolher(motoristasIds);
      }
      livreEm.set(motorista, fim.getTime());

      const viagemId = uuid('viagem', t.viagens.length + 1);
      const cancelada = rng.chance(0.04);
      const km = Math.round(rota.DistanciaEstimadaKm * rng.entre(0.97, 1.08));
      const hodoInicial = odometro;
      const base = {
        Id: viagemId, CriadoEm: fmtDataHora(cursor), AtualizadoEm: fmtDataHora(fim), Ativo: 1, SyncVersion: 1,
        VeiculoId: veiculoId, RotaId: rota.Id, MotoristaId: motorista, HodometroInicial: hodoInicial,
        DataInicio: fmtDataHora(cursor), Observacao: null,
      };

      if (cancelada) {
        t.viagens.push({ ...base, HodometroFinal: null, KmRodados: null, DataFim: fmtDataHora(fim), Status: 'Cancelada' });
      } else {
        const hodoFinal = hodoInicial + km;
        odometro = hodoFinal;
        marcos.push({ ms: fim.getTime(), odo: hodoFinal });
        t.viagens.push({
          ...base, HodometroFinal: hodoFinal, KmRodados: km, DataFim: fmtDataHora(fim), Status: 'Concluida',
        });

        // Frete (Entrada): ~R$ 5,5 a 9,0 por km, vencimento 15/30/45 dias após a entrega.
        const vencFrete = somarDias(inicioDoDia(fim), rng.escolher([15, 30, 45]));
        movimentacao({
          valor: arred(km * rng.entre(5.5, 9)), descricao: `Frete ${placa} ${rota.Nome}`.slice(0, 200),
          categoriaId: cat.Frete, viagemId, vencimento: vencFrete, pagamento: quitacao(vencFrete, 0.93, 10),
          criadoEm: fmtDataHora(fim),
        });

        // Pedágio (veículo vem da viagem).
        const diaPedagio = inicioDoDia(somarDias(cursor, 1));
        movimentacao({
          valor: arred(km * rng.entre(0.08, 0.16)), descricao: `Pedágio ${rota.Nome}`.slice(0, 200), categoriaId: cat.Pedagio,
          viagemId, vencimento: diaPedagio, pagamento: quitacao(diaPedagio, 0.99, 0), criadoEm: fmtDataHora(cursor),
        });

        // Abastecimentos ao longo da viagem: um a cada ~550 km.
        const paradas = Math.max(1, Math.round(km / 550));
        for (let p = 0; p < paradas; p += 1) {
          const trecho = km / paradas;
          const quando = new Date(cursor.getTime() + ((p + 0.5) / paradas) * (fim.getTime() - cursor.getTime()));
          const litros = arred((trecho / consumo) * rng.entre(0.92, 1.08));
          const preco = precoBase(quando) + rng.entre(-0.15, 0.15);
          const valorTotal = arred(litros * preco);
          const dia = inicioDoDia(quando);
          const movId = movimentacao({
            valor: valorTotal, descricao: `Diesel ${placa}`, categoriaId: cat.Combustivel, viagemId, veiculoId,
            vencimento: dia, pagamento: quitacao(dia, 0.99, 0), litros, criadoEm: fmtDataHora(quando),
          });
          t.abastecimentos.push({
            Id: uuid('abastecimento', t.abastecimentos.length + 1), CriadoEm: fmtDataHora(quando),
            AtualizadoEm: fmtDataHora(quando), Ativo: 1, SyncVersion: 1, VeiculoId: veiculoId, ViagemId: viagemId,
            Litros: litros, ValorTotal: valorTotal, PrecoLitro: arred(valorTotal / litros, 3),
            Posto: rng.escolher(POSTOS), Hodometro: hodoInicial + Math.round(trecho * (p + 1)), MovimentacaoId: movId,
          });
        }

        // Manutenção: preventiva ao cruzar o intervalo de km; corretiva de vez em quando.
        const preventiva = odometro - ultimaManutencao >= intervaloKm;
        if (preventiva || rng.chance(0.02)) {
          const dataOs = inicioDoDia(somarDias(fim, 1));
          if (dataOs < limite) {
            const conclusao = somarDias(dataOs, rng.inteiro(1, 3));
            const valor = preventiva ? arred(rng.entre(1800, 6500)) : arred(rng.entre(800, 14_000));
            const movId = movimentacao({
              valor, descricao: `${preventiva ? 'Revisão preventiva' : 'Reparo corretivo'} ${placa}`,
              categoriaId: cat.Manutencao, veiculoId, vencimento: conclusao,
              pagamento: quitacao(conclusao, 0.95, 5), criadoEm: fmtDataHora(dataOs),
            });
            t.ordens_servico.push({
              Id: uuid('os', t.ordens_servico.length + 1), CriadoEm: fmtDataHora(dataOs), AtualizadoEm: fmtDataHora(dataOs),
              Ativo: 1, SyncVersion: 1, VeiculoId: veiculoId, DataManutencao: fmtData(dataOs),
              Descricao: `${preventiva ? 'Revisão preventiva' : 'Reparo corretivo'} ${placa}`,
              Tipo: preventiva ? 'Preventiva' : 'Corretiva', ValorTotal: valor, HodometroNaManutencao: odometro,
              DataConclusao: conclusao <= dataHoje ? fmtData(conclusao) : null, MovimentacaoId: movId,
            });
            if (preventiva) ultimaManutencao = odometro;
          }
        }
      }

      cursor = new Date(fim.getTime() + rng.inteiro(0, 2) * DIA_MS + rng.inteiro(2, 10) * 3_600_000);
    }

    // Abastecimentos de pátio (sem viagem): mantém ViagemId NULL no volume.
    // O hodômetro é o da data (último marco anterior), para continuar crescendo com o tempo.
    const hodometroEm = (ms) => marcos.reduce((odo, marco) => (marco.ms <= ms ? marco.odo : odo), marcos[0].odo);
    for (let m = 0; m < meses; m += 1) {
      if (!rng.chance(0.6)) continue;
      const dia = somarDias(somarMeses(ini, m), rng.inteiro(0, 27));
      const litros = arred(rng.entre(120, 380));
      const valorTotal = arred(litros * (precoBase(dia) + rng.entre(-0.1, 0.1)));
      const movId = movimentacao({
        valor: valorTotal, descricao: `Diesel pátio ${placa}`, categoriaId: cat.Combustivel, veiculoId,
        vencimento: dia, pagamento: quitacao(dia, 0.99, 0), litros, criadoEm: fmtDataHora(dia),
      });
      t.abastecimentos.push({
        Id: uuid('abastecimento', t.abastecimentos.length + 1), CriadoEm: fmtDataHora(dia), AtualizadoEm: fmtDataHora(dia),
        Ativo: 1, SyncVersion: 1, VeiculoId: veiculoId, ViagemId: null, Litros: litros, ValorTotal: valorTotal,
        PrecoLitro: arred(valorTotal / litros, 3), Posto: 'Tanque interno', Hodometro: hodometroEm(dia.getTime()), MovimentacaoId: movId,
      });
    }

    veiculo.HodometroAtual = odometro;
    veiculo.HodometroUltimaManutencao = ultimaManutencao;
  }

  // ---- Salários (custo sem veículo = "não alocado") ---------------------------
  motoristasIds.forEach((motoristaId, i) => {
    const salarioBase = arred(rng.entre(3500, 5500));
    for (let m = 0; m < meses; m += 1) {
      const venc = somarMeses(new Date(Date.UTC(ini.getUTCFullYear(), ini.getUTCMonth(), 5)), m);
      movimentacao({
        valor: arred(salarioBase + rng.entre(0, 600)), descricao: `Salário motorista ${i + 1} ${fmtData(venc).slice(0, 7)}`,
        categoriaId: cat.Salario, vencimento: venc, pagamento: quitacao(venc, 0.99, 0), criadoEm: fmtDataHora(venc),
      });
    }
  });

  // ---- Outros: despesas avulsas (parte sem veículo, 4% inativas/"excluídas") ---
  const veiculosIds = t.veiculos.map((x) => x.Id);
  let avulsas = 0;
  for (let m = 0; m < meses; m += 1) {
    const total = Math.round(nVeiculos / 4);
    for (let k = 0; k < total; k += 1) {
      const venc = somarDias(somarMeses(ini, m), rng.inteiro(0, 27));
      movimentacao({
        valor: arred(rng.entre(50, 2500)), descricao: 'Despesa avulsa', categoriaId: cat.Outros,
        veiculoId: rng.chance(0.5) ? rng.escolher(veiculosIds) : null, vencimento: venc,
        pagamento: quitacao(venc, 0.9, 15), ativo: ++avulsas % 25 === 0 ? 0 : 1, // 1 a cada 25 "excluída" (soft delete), sempre exercita o filtro Ativo
        criadoEm: fmtDataHora(venc),
      });
    }
  }

  return { janela: { inicio, fim: fmtData(somarDias(limite, -1)) }, tabelas: t };
}

// Contagem por tabela, para o relatório do CLI e para os testes.
export function contar(massa) {
  return Object.fromEntries(Object.entries(massa.tabelas).map(([nome, linhas]) => [nome, linhas.length]));
}
