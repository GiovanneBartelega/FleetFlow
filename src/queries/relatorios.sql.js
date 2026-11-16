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
//
// DESEMPENHO (meta: < 2 s com 12 meses de dados; ver docs/sprint/entrega-luis-sql-agregado-indices.md)
// Cada consulta tem DUAS ETAPAS:
//   1) agrega a tabela grande (movimentacoes / abastecimentos / viagens) sozinha, sobre um índice
//      cobrindo de db/indices-relatorios.sql, agrupando pelas chaves cruas (categoria, viagem,
//      dívida, veículo...). Sem JOINs nessa etapa, o MySQL é obrigado a usar o índice do filtro;
//   2) só então junta as dimensões (categorias, viagens, dívidas) sobre o resultado JÁ agregado e
//      agrupa pela chave do relatório.
// Antes, a consulta começava por `categorias` (9 linhas) e entrava em `movimentacoes` pelo índice
// de categoria, ignorando o filtro de status/data, e fazia um JOIN por linha da tabela grande.
// Os hints /*+ ... */ fixam o plano; se um índice não existir, o MySQL só emite um warning.

export const AGRUPAMENTOS = ['veiculo', 'rota', 'motorista', 'viagem', 'mes'];
export const REGIMES = ['caixa', 'competencia'];

// Regime caixa: só o que foi pago, pela data do pagamento.
// Regime competência: pago ou pendente, pela data de vencimento.
const REGIME_SQL = {
  caixa: { filtroStatus: "m.Status = 'Pago'", dataCol: 'm.DataPagamento', indice: 'ix_mov_caixa' },
  competencia: { filtroStatus: '1 = 1', dataCol: 'm.DataVencimento', indice: 'ix_mov_competencia' },
};

// Expressão da chave de agrupamento em cada fonte.
//  - mov:     etapa 2 de sqlMovimentacoes. `a` = resultado agregado, `vi` = viagens, `d` = dívidas.
//             Mês vem como AAAAMM (inteiro) da etapa 1 e é formatado aqui, só sobre grupos.
//  - viagem:  sobre `viagens vi`
//  - abast:   etapa 2 de sqlCombustivel. `g` = resultado agregado, `vi` = viagens (só rota/motorista)
const VEICULO_MOV = 'COALESCE(a.veiculo_mov, vi.VeiculoId, d.VeiculoId)';
const MES_FORMATADO = "CONCAT(LEFT(a.mes_ref, 4), '-', RIGHT(a.mes_ref, 2))";
const CHAVES = {
  veiculo: { mov: VEICULO_MOV, viagem: 'vi.VeiculoId', abast: 'g.veiculo_id' },
  rota: { mov: 'vi.RotaId', viagem: 'vi.RotaId', abast: 'vi.RotaId' },
  motorista: { mov: 'vi.MotoristaId', viagem: 'vi.MotoristaId', abast: 'vi.MotoristaId' },
  viagem: { mov: 'a.viagem_id', viagem: 'vi.Id', abast: 'g.viagem_id' },
  mes: {
    // Custo/receita sem veículo não entra em nenhum mês: vai para "não alocados" (regra 4).
    mov: `CASE WHEN ${VEICULO_MOV} IS NULL THEN NULL ELSE ${MES_FORMATADO} END`,
    viagem: "DATE_FORMAT(vi.DataFim, '%Y-%m')",
    abast: "CONCAT(LEFT(g.mes_ref, 4), '-', RIGHT(g.mes_ref, 2))",
  },
};

const intervalo = (coluna) => `${coluna} >= ? AND ${coluna} < DATE_ADD(?, INTERVAL 1 DAY)`;

// Mês como inteiro AAAAMM só quando o relatório é por mês; senão NULL constante (não multiplica grupos).
const mesRef = (agrupar, dataCol) => (agrupar === 'mes' ? `EXTRACT(YEAR_MONTH FROM ${dataCol})` : 'NULL');

// ---------------------------------------------------------------------------
// 1) Dinheiro: receita e custo por chave + categoria.
//
// Fonte única de valores financeiros = movimentacoes (regra 3). Sinal vem de
// categorias.Tipo (regra 1). Veículo do custo = COALESCE(mov, viagem, dívida) (regra 4).
// `vinculada` marca custo ligado a viagem (base do custo variável, regra 6).
// `os_tipo` classifica manutenção; ordens_servico é pré-agregada por
// MovimentacaoId para o JOIN ser 1:1 mesmo que existam OS duplicadas.
// Linhas com chave NULL são o que não se atribui à dimensão (custosNaoAlocados).
//
// Etapa 1 (`agregada`): movimentacoes, sozinha, pelo índice cobrindo do regime. As movimentações
// COM OS ativa e as SEM OS são agregadas em dois ramos de UNION ALL (anti-join x JOIN): assim as
// ~99% sem OS não pagam uma busca em ordens_servico por linha.
// Etapa 2: junta categorias, viagens e dívidas sobre o resultado agregado e aplica os filtros.
// ---------------------------------------------------------------------------
export function sqlMovimentacoes({ agrupar, regime, inicio, fim, veiculoId, rotaId, motoristaId }) {
  const { filtroStatus, dataCol, indice } = REGIME_SQL[regime];
  const mes = mesRef(agrupar, dataCol);
  const params = [inicio, fim, inicio, fim];
  const filtros = [];
  if (veiculoId) { filtros.push(`${VEICULO_MOV} = ?`); params.push(veiculoId); }
  if (rotaId) { filtros.push('vi.RotaId = ?'); params.push(rotaId); }
  if (motoristaId) { filtros.push('vi.MotoristaId = ?'); params.push(motoristaId); }

  // Só junta o que a chave/filtro realmente usa (por viagem, sem filtros, nada além de categorias).
  const usaVeiculo = agrupar === 'veiculo' || agrupar === 'mes' || Boolean(veiculoId);
  const usaViagem = usaVeiculo || agrupar === 'rota' || agrupar === 'motorista' || Boolean(rotaId) || Boolean(motoristaId);
  const ordem = ['a', 'c', ...(usaViagem ? ['vi'] : []), ...(usaVeiculo ? ['d'] : [])].join(', ');

  const sql = `
    WITH agregada AS (
      SELECT /*+ INDEX(m ${indice}) */
             m.CategoriaId AS categoria_id, m.ViagemId AS viagem_id, m.DividaId AS divida_id,
             m.VeiculoId AS veiculo_mov, NULL AS os_tipo, ${mes} AS mes_ref,
             SUM(m.Valor) AS valor, COUNT(*) AS qtd
        FROM movimentacoes m
       WHERE m.Ativo = 1
         AND ${filtroStatus}
         AND ${intervalo(dataCol)}
         AND m.Id NOT IN (SELECT MovimentacaoId FROM ordens_servico WHERE Ativo = 1)
       GROUP BY m.CategoriaId, m.ViagemId, m.DividaId, m.VeiculoId, mes_ref
      UNION ALL
      SELECT m.CategoriaId, m.ViagemId, m.DividaId, m.VeiculoId, os.Tipo, ${mes} AS mes_ref,
             SUM(m.Valor), COUNT(*)
        FROM (SELECT MovimentacaoId, MIN(Tipo) AS Tipo
                FROM ordens_servico
               WHERE Ativo = 1
               GROUP BY MovimentacaoId) os
        JOIN movimentacoes m ON m.Id = os.MovimentacaoId
       WHERE m.Ativo = 1
         AND ${filtroStatus}
         AND ${intervalo(dataCol)}
       GROUP BY m.CategoriaId, m.ViagemId, m.DividaId, m.VeiculoId, os.Tipo, mes_ref
    )
    SELECT /*+ JOIN_ORDER(${ordem}) */
           ${CHAVES[agrupar].mov}        AS chave,
           c.Tipo                        AS tipo,
           c.Id                          AS categoria_id,
           c.Titulo                      AS categoria,
           a.os_tipo                     AS os_tipo,
           (a.viagem_id IS NOT NULL)     AS vinculada,
           SUM(a.valor)                  AS valor,
           SUM(a.qtd)                    AS qtd
      FROM agregada a
      JOIN categorias c ON c.Id = a.categoria_id AND c.Ativo = 1
      ${usaViagem ? 'LEFT JOIN viagens vi ON vi.Id = a.viagem_id' : ''}
      ${usaVeiculo ? 'LEFT JOIN dividas d ON d.Id = a.divida_id' : ''}
     ${filtros.length ? `WHERE ${filtros.join(' AND ')}` : ''}
     GROUP BY chave, c.Tipo, c.Id, c.Titulo, a.os_tipo, vinculada`;
  return { sql, params };
}

// ---------------------------------------------------------------------------
// 2) Km rodados e quantidade de viagens por chave (regra 5).
//
// Só viagens Concluida e Ativo, atribuídas ao período por DataFim.
// Km = COALESCE(KmRodados, HodometroFinal - HodometroInicial); se não houver
// nenhum dos dois, a viagem conta em `sem_km` e fica fora da soma de km.
// Índice cobrindo ix_viagens_concluidas (Status, Ativo, DataFim, ...): intervalo do índice, sem tabela.
// ---------------------------------------------------------------------------
export function sqlKm({ agrupar, inicio, fim, veiculoId, rotaId, motoristaId }) {
  const params = [inicio, fim];
  const filtros = [];
  if (veiculoId) { filtros.push('vi.VeiculoId = ?'); params.push(veiculoId); }
  if (rotaId) { filtros.push('vi.RotaId = ?'); params.push(rotaId); }
  if (motoristaId) { filtros.push('vi.MotoristaId = ?'); params.push(motoristaId); }

  const sql = `
    WITH viagem_km AS (
      SELECT /*+ INDEX(vi ix_viagens_concluidas) */
             ${CHAVES[agrupar].viagem} AS chave,
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
//
// Etapa 1: percorre o intervalo do período no índice cobrindo de movimentacoes e busca os litros pelo
// índice cobrindo de abastecimentos (ix_abast_relatorio, por MovimentacaoId). Nenhuma das duas lê a
// tabela, e o custo acompanha o tamanho do período (1 mês: ~100 ms; antes ~500 ms). Não fixamos a ordem
// do JOIN: partir de abastecimentos vence só em períodos de 12 meses e perde em todos os menores.
// Viagens só entram na etapa 2 se rota/motorista forem usados.
// ---------------------------------------------------------------------------
export function sqlCombustivel({ agrupar, regime, inicio, fim, veiculoId, rotaId, motoristaId }) {
  const { filtroStatus, dataCol, indice } = REGIME_SQL[regime];
  const params = [inicio, fim];
  const filtrosEtapa1 = [];
  const filtrosEtapa2 = [];
  if (veiculoId) { filtrosEtapa1.push('a.VeiculoId = ?'); params.push(veiculoId); }
  if (rotaId) { filtrosEtapa2.push('vi.RotaId = ?'); params.push(rotaId); }
  if (motoristaId) { filtrosEtapa2.push('vi.MotoristaId = ?'); params.push(motoristaId); }
  const usaViagem = agrupar === 'rota' || agrupar === 'motorista' || Boolean(rotaId) || Boolean(motoristaId);

  const sql = `
    WITH agregada AS (
      SELECT /*+ INDEX(m ${indice}) INDEX(a ix_abast_relatorio) */
             a.VeiculoId AS veiculo_id, a.ViagemId AS viagem_id, ${mesRef(agrupar, dataCol)} AS mes_ref,
             SUM(a.Litros) AS litros,
             SUM(m.Valor)  AS valor
        FROM abastecimentos a
        JOIN movimentacoes m ON m.Id = a.MovimentacaoId AND m.Ativo = 1
        JOIN categorias c ON c.Id = m.CategoriaId AND c.Ativo = 1
       WHERE a.Ativo = 1
         AND ${filtroStatus}
         AND ${intervalo(dataCol)}
         ${filtrosEtapa1.map((f) => `AND ${f}`).join(' ')}
       GROUP BY a.VeiculoId, a.ViagemId, mes_ref
    )
    SELECT ${CHAVES[agrupar].abast} AS chave,
           SUM(g.litros) AS litros,
           SUM(g.valor)  AS valor
      FROM agregada g
      ${usaViagem ? 'LEFT JOIN viagens vi ON vi.Id = g.viagem_id' : ''}
     ${filtrosEtapa2.length ? `WHERE ${filtrosEtapa2.join(' AND ')}` : ''}
     GROUP BY chave`;
  return { sql, params };
}

// ---------------------------------------------------------------------------
// 4) Rótulos legíveis das chaves encontradas (placa, nome da rota, ...).
// `ids` vem dos resultados anteriores, mas segue parametrizado (IN (?)).
// ---------------------------------------------------------------------------
const LIMITE_IN_ROTULOS = 2000;
export function sqlRotulos(agrupar, ids) {
  switch (agrupar) {
    case 'veiculo':
      return { sql: 'SELECT Id AS chave, Placa AS rotulo FROM veiculos WHERE Id IN (?)', params: [ids] };
    case 'rota':
      return { sql: 'SELECT Id AS chave, Nome AS rotulo FROM rotas WHERE Id IN (?)', params: [ids] };
    case 'motorista':
      return { sql: 'SELECT Id AS chave, Nome AS rotulo FROM usuarios WHERE Id IN (?)', params: [ids] };
    case 'viagem': {
      const select = `SELECT vi.Id AS chave,
                     CONCAT(r.Nome, ' - ', v.Placa, ' - ', DATE_FORMAT(vi.DataInicio, '%Y-%m-%d')) AS rotulo
                FROM viagens vi
                JOIN rotas r ON r.Id = vi.RotaId
                JOIN veiculos v ON v.Id = vi.VeiculoId`;
      // Com milhares de viagens, um IN (?) gigante custa mais (parse + 1 busca por id) do que ler
      // todos os rótulos: 34 mil viagens = 789 ms com IN, 325 ms sem. O service só usa os que precisa.
      if (ids.length > LIMITE_IN_ROTULOS) return { sql: select, params: [] };
      return { sql: `${select} WHERE vi.Id IN (?)`, params: [ids] };
    }
    default:
      return null; // mes: a própria chave (AAAA-MM) é o rótulo
  }
}
