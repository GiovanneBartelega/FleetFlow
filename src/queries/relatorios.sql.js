// SQL dos relatórios de rentabilidade e custo por km. Somente leitura.
//
// Regra de ouro: cada fonte (movimentações, viagens, abastecimentos) é agregada
// SEPARADAMENTE, por chave de agrupamento, e o service junta os resultados em
// memória. Nunca fazemos JOIN de movimentações com viagens/abastecimentos para
// somar depois (fan-out: um frete juntado a N abastecimentos seria somado N vezes).
// Os JOINs que existem aqui são sempre N:1 pela chave primária (movimentação ->
// viagem, movimentação -> dívida, abastecimento -> movimentação) e, por isso,
// não multiplicam linhas.
//
// Os trechos que variam (chave de agrupamento, regime) vêm de whitelists
// constantes deste arquivo; todo valor vindo do usuário entra por `?`.

export const AGRUPAMENTOS = ['veiculo', 'rota', 'motorista', 'viagem', 'mes'];
export const REGIMES = ['caixa', 'competencia'];

// Regime caixa: só o que foi pago, pela data do pagamento.
// Regime competência: pago ou pendente, pela data de vencimento.
const REGIME_SQL = {
  caixa: { filtroStatus: "m.Status = 'Pago'", dataCol: 'm.DataPagamento' },
  competencia: { filtroStatus: '1 = 1', dataCol: 'm.DataVencimento' },
};

// Expressão da chave de agrupamento em cada fonte.
//  - mov:     sobre a CTE `base` (colunas em snake_case)
//  - viagem:  sobre `viagens vi`
//  - abast:   sobre `abastecimentos a` (+ `viagens vi` por LEFT JOIN) e a data da movimentação
const CHAVES = {
  veiculo: {
    mov: 'veiculo_id',
    viagem: 'vi.VeiculoId',
    abast: () => 'a.VeiculoId',
  },
  rota: {
    mov: 'rota_id',
    viagem: 'vi.RotaId',
    abast: () => 'vi.RotaId',
  },
  motorista: {
    mov: 'motorista_id',
    viagem: 'vi.MotoristaId',
    abast: () => 'vi.MotoristaId',
  },
  viagem: {
    mov: 'viagem_id',
    viagem: 'vi.Id',
    abast: () => 'a.ViagemId',
  },
  mes: {
    // Custo/receita sem veículo não entra em nenhum mês: vai para "não alocados" (regra 4).
    mov: "CASE WHEN veiculo_id IS NULL THEN NULL ELSE DATE_FORMAT(data_ref, '%Y-%m') END",
    viagem: "DATE_FORMAT(vi.DataFim, '%Y-%m')",
    abast: (dataCol) => `DATE_FORMAT(${dataCol}, '%Y-%m')`,
  },
};

const intervalo = (coluna) => `${coluna} >= ? AND ${coluna} < DATE_ADD(?, INTERVAL 1 DAY)`;

// ---------------------------------------------------------------------------
// 1) Dinheiro: receita e custo por chave + categoria.
//
// Fonte única de valores financeiros = movimentacoes (regra 3). Sinal vem de
// categorias.Tipo (regra 1). Veículo do custo = COALESCE(mov, viagem, dívida) (regra 4).
// `vinculada` marca custo ligado a viagem (base do custo variável, regra 6).
// `os_tipo` classifica manutenção; ordens_servico é pré-agregada por
// MovimentacaoId para o JOIN ser 1:1 mesmo que existam OS duplicadas.
// Linhas com chave NULL são o que não se atribui à dimensão (custosNaoAlocados).
// ---------------------------------------------------------------------------
export function sqlMovimentacoes({ agrupar, regime, inicio, fim, veiculoId, rotaId, motoristaId }) {
  const { filtroStatus, dataCol } = REGIME_SQL[regime];
  const params = [inicio, fim];
  const filtros = [];
  if (veiculoId) { filtros.push('veiculo_id = ?'); params.push(veiculoId); }
  if (rotaId) { filtros.push('rota_id = ?'); params.push(rotaId); }
  if (motoristaId) { filtros.push('motorista_id = ?'); params.push(motoristaId); }

  const sql = `
    WITH base AS (
      SELECT m.Valor                                           AS valor,
             c.Id                                              AS categoria_id,
             c.Titulo                                          AS categoria,
             c.Tipo                                            AS tipo,
             m.ViagemId                                        AS viagem_id,
             vi.RotaId                                         AS rota_id,
             vi.MotoristaId                                    AS motorista_id,
             COALESCE(m.VeiculoId, vi.VeiculoId, d.VeiculoId)  AS veiculo_id,
             ${dataCol}                                        AS data_ref,
             os.Tipo                                           AS os_tipo
        FROM movimentacoes m
        JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
        LEFT JOIN viagens vi ON vi.Id = m.ViagemId
        LEFT JOIN dividas d ON d.Id = m.DividaId
        LEFT JOIN (
              SELECT MovimentacaoId, MIN(Tipo) AS Tipo
                FROM ordens_servico
               WHERE Ativo = 1
               GROUP BY MovimentacaoId
             ) os ON os.MovimentacaoId = m.Id
       WHERE m.Ativo = 1
         AND ${filtroStatus}
         AND ${intervalo(dataCol)}
    ),
    chaveada AS (
      SELECT ${CHAVES[agrupar].mov}  AS chave,
             tipo, categoria_id, categoria, os_tipo,
             (viagem_id IS NOT NULL) AS vinculada,
             valor
        FROM base
       ${filtros.length ? `WHERE ${filtros.join(' AND ')}` : ''}
    )
    SELECT chave, tipo, categoria_id, categoria, os_tipo, vinculada,
           SUM(valor) AS valor,
           COUNT(*)   AS qtd
      FROM chaveada
     GROUP BY chave, tipo, categoria_id, categoria, os_tipo, vinculada`;
  return { sql, params };
}

// ---------------------------------------------------------------------------
// 2) Km rodados e quantidade de viagens por chave (regra 5).
//
// Só viagens Concluida e Ativo, atribuídas ao período por DataFim.
// Km = COALESCE(KmRodados, HodometroFinal - HodometroInicial); se não houver
// nenhum dos dois, a viagem conta em `sem_km` e fica fora da soma de km.
// ---------------------------------------------------------------------------
export function sqlKm({ agrupar, inicio, fim, veiculoId, rotaId, motoristaId }) {
  const params = [inicio, fim];
  const filtros = [];
  if (veiculoId) { filtros.push('vi.VeiculoId = ?'); params.push(veiculoId); }
  if (rotaId) { filtros.push('vi.RotaId = ?'); params.push(rotaId); }
  if (motoristaId) { filtros.push('vi.MotoristaId = ?'); params.push(motoristaId); }

  const sql = `
    WITH viagem_km AS (
      SELECT ${CHAVES[agrupar].viagem} AS chave,
             COALESCE(vi.KmRodados, vi.HodometroFinal - vi.HodometroInicial) AS km
        FROM viagens vi
       WHERE vi.Status = 'Concluida'
         AND vi.Ativo = 1
         AND ${intervalo('vi.DataFim')}
         ${filtros.map((f) => `AND ${f}`).join(' ')}
    )
    SELECT chave,
           COUNT(*)                          AS qtd_viagens,
           COALESCE(SUM(km), 0)              AS km,
           SUM(CASE WHEN km IS NULL THEN 1 ELSE 0 END) AS sem_km
      FROM viagem_km
     GROUP BY chave`;
  return { sql, params };
}

// ---------------------------------------------------------------------------
// 3) Litros e valor do combustível por chave (consumo e preço médio).
//
// abastecimentos fornece só os LITROS. O valor usado para o preço médio é o da
// movimentação vinculada, que é a fonte única de dinheiro. O abastecimento só
// entra se a sua movimentação também entra no relatório (mesmo regime/período).
// ---------------------------------------------------------------------------
export function sqlCombustivel({ agrupar, regime, inicio, fim, veiculoId, rotaId, motoristaId }) {
  const { filtroStatus, dataCol } = REGIME_SQL[regime];
  const params = [inicio, fim];
  const filtros = [];
  if (veiculoId) { filtros.push('a.VeiculoId = ?'); params.push(veiculoId); }
  if (rotaId) { filtros.push('vi.RotaId = ?'); params.push(rotaId); }
  if (motoristaId) { filtros.push('vi.MotoristaId = ?'); params.push(motoristaId); }

  const sql = `
    WITH combustivel AS (
      SELECT ${CHAVES[agrupar].abast(dataCol)} AS chave,
             a.Litros AS litros,
             m.Valor  AS valor
        FROM abastecimentos a
        JOIN movimentacoes m ON m.Id = a.MovimentacaoId AND m.Ativo = 1
        JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
        LEFT JOIN viagens vi ON vi.Id = a.ViagemId
       WHERE a.Ativo = 1
         AND ${filtroStatus}
         AND ${intervalo(dataCol)}
         ${filtros.map((f) => `AND ${f}`).join(' ')}
    )
    SELECT chave,
           SUM(litros) AS litros,
           SUM(valor)  AS valor
      FROM combustivel
     GROUP BY chave`;
  return { sql, params };
}

// ---------------------------------------------------------------------------
// 4) Rótulos legíveis das chaves encontradas (placa, nome da rota, ...).
// `ids` vem dos resultados anteriores, mas segue parametrizado (IN (?)).
// ---------------------------------------------------------------------------
export function sqlRotulos(agrupar, ids) {
  switch (agrupar) {
    case 'veiculo':
      return { sql: 'SELECT Id AS chave, Placa AS rotulo FROM veiculos WHERE Id IN (?)', params: [ids] };
    case 'rota':
      return { sql: 'SELECT Id AS chave, Nome AS rotulo FROM rotas WHERE Id IN (?)', params: [ids] };
    case 'motorista':
      return { sql: 'SELECT Id AS chave, Nome AS rotulo FROM usuarios WHERE Id IN (?)', params: [ids] };
    case 'viagem':
      return {
        sql: `SELECT vi.Id AS chave,
                     CONCAT(r.Nome, ' - ', v.Placa, ' - ', DATE_FORMAT(vi.DataInicio, '%Y-%m-%d')) AS rotulo
                FROM viagens vi
                JOIN rotas r ON r.Id = vi.RotaId
                JOIN veiculos v ON v.Id = vi.VeiculoId
               WHERE vi.Id IN (?)`,
        params: [ids],
      };
    default:
      return null; // mes: a própria chave (AAAA-MM) é o rótulo
  }
}
