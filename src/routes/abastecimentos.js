import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { ABASTECIMENTO_SELECT, mapAbastecimento } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import {
  dateOnly,
  intNonNegative,
  money,
  pageQuery,
  positiveDecimal,
  search,
  syncVersion,
  uuid,
} from '../schemas.js';
import {
  assertActive,
  buildUpdate,
  likeTerm,
  pageResult,
  pagination,
  runUpdate,
  softDelete,
  withTransaction,
} from '../sql.js';

// Diferença aceita (R$ 0,10) entre litros × preço e o total: bombas arredondam o preço do litro.
const TOLERANCIA_TOTAL_CENTAVOS = 10;
const PRECO_LITRO_MAX = 999.999;

const listQuery = pageQuery.extend({
  veiculoId: uuid.optional(),
  viagemId: uuid.optional(),
  movimentacaoId: uuid.optional(),
  abastecidoDe: dateOnly.optional(),
  abastecidoAte: dateOnly.optional(),
  busca: search.optional(),
});

const fields = {
  veiculoId: uuid,
  viagemId: uuid.nullable(),
  litros: positiveDecimal(6, 2),
  valorTotal: money,
  // Se omitido, a API calcula valorTotal ÷ litros.
  precoLitro: positiveDecimal(3, 3),
  posto: z.string().trim().min(1).max(120).nullable(),
  hodometro: intNonNegative,
  movimentacaoId: uuid,
};

const createBody = z
  .object({
    ...fields,
    viagemId: fields.viagemId.optional(),
    precoLitro: fields.precoLitro.optional(),
    posto: fields.posto.optional(),
    // Se omitido, a API lança a despesa em Movimentações (categoria Combustível)
    // e, nesse caso, exige a forma de pagamento.
    movimentacaoId: fields.movimentacaoId.optional(),
    formaPagamentoId: uuid.optional(),
  })
  .superRefine((value, ctx) => {
    if (!value.movimentacaoId && !value.formaPagamentoId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['formaPagamentoId'],
        message: 'Informe a forma de pagamento (ou o movimentacaoId de uma despesa já lançada)',
      });
    }
  });

const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const COLUMNS = {
  veiculoId: 'VeiculoId',
  viagemId: 'ViagemId',
  litros: 'Litros',
  valorTotal: 'ValorTotal',
  precoLitro: 'PrecoLitro',
  posto: 'Posto',
  hodometro: 'Hodometro',
  movimentacaoId: 'MovimentacaoId',
};

function validationError(campo, mensagem) {
  return new AppError(400, 'VALIDATION_ERROR', 'Dados inválidos.', [{ campo, mensagem }]);
}

// Devolve o preço do litro final: calcula quando não foi informado e confere
// a coerência com litros e total quando foi.
function resolvePrecoLitro({ litros, valorTotal, precoLitro }) {
  if (precoLitro === undefined || precoLitro === null) {
    const calculado = Number(valorTotal) / Number(litros);
    if (calculado > PRECO_LITRO_MAX) {
      throw validationError('valorTotal', 'O total é alto demais para essa quantidade de litros');
    }
    return calculado.toFixed(3);
  }
  // Em centavos inteiros, para o limite da tolerância não depender de ponto flutuante.
  const esperado = Math.round(Number(litros) * Number(precoLitro) * 100);
  if (Math.abs(esperado - Math.round(Number(valorTotal) * 100)) > TOLERANCIA_TOTAL_CENTAVOS) {
    throw validationError('precoLitro', 'O preço do litro não confere com os litros e o valor total');
  }
  return precoLitro;
}

export function abastecimentosRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id, db = pool) => {
    const [rows] = await db.query(`${ABASTECIMENTO_SELECT} WHERE a.Id = ? AND a.Ativo = 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Abastecimento não encontrado.');
    return rows[0];
  };

  // A viagem precisa existir e ser do mesmo veículo abastecido.
  const checkViagem = async (viagemId, veiculoId) => {
    const [rows] = await pool.query('SELECT VeiculoId FROM viagens WHERE Id = ? AND Ativo = 1', [viagemId]);
    if (rows.length === 0) {
      throw new AppError(400, 'INVALID_REFERENCE', 'Viagem não encontrada ou inativa.', [
        { campo: 'viagemId', mensagem: 'Viagem não encontrada ou inativa' },
      ]);
    }
    if (rows[0].VeiculoId !== veiculoId) {
      throw validationError('viagemId', 'A viagem informada é de outro veículo');
    }
  };

  // Uma despesa só pode estar ligada a um abastecimento.
  const checkMovimentacaoLivre = async (movimentacaoId, ignorarId) => {
    const [rows] = await pool.query('SELECT Id FROM abastecimentos WHERE MovimentacaoId = ? AND Ativo = 1', [
      movimentacaoId,
    ]);
    if (rows.some((row) => row.Id !== ignorarId)) {
      throw new AppError(409, 'DUPLICATE', 'Esta movimentação já está ligada a outro abastecimento.');
    }
  };

  // O hodômetro do veículo só avança.
  const atualizarHodometro = (db, veiculoId, hodometro) =>
    db.query('UPDATE veiculos SET HodometroAtual = GREATEST(HodometroAtual, ?) WHERE Id = ? AND Ativo = 1', [
      hodometro,
      veiculoId,
    ]);

  // GET /api/abastecimentos — lista paginada com filtros e totais do filtro.
  router.get(
    '/',
    requireAccess('abastecimentos', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['a.Ativo = 1'];
      const params = [];
      const add = (condition, value) => { where.push(condition); params.push(value); };

      if (q.veiculoId) add('a.VeiculoId = ?', q.veiculoId);
      if (q.viagemId) add('a.ViagemId = ?', q.viagemId);
      if (q.movimentacaoId) add('a.MovimentacaoId = ?', q.movimentacaoId);
      if (q.abastecidoDe) add('DATE(a.CriadoEm) >= ?', q.abastecidoDe);
      if (q.abastecidoAte) add('DATE(a.CriadoEm) <= ?', q.abastecidoAte);
      if (q.busca) {
        const term = likeTerm(q.busca);
        where.push('(a.Posto LIKE ? OR v.Placa LIKE ?)');
        params.push(term, term);
      }
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);
      const from = 'FROM abastecimentos a JOIN veiculos v ON v.Id = a.VeiculoId';

      const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total ${from} WHERE ${clause}`, params);
      const [rows] = await pool.query(
        `${ABASTECIMENTO_SELECT} WHERE ${clause} ORDER BY a.CriadoEm DESC LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      const [[sums]] = await pool.query(
        `SELECT COALESCE(SUM(a.Litros), 0) AS litros, COALESCE(SUM(a.ValorTotal), 0) AS valorTotal
         ${from} WHERE ${clause}`,
        params,
      );
      const totais = { litros: sums?.litros ?? '0.00', valorTotal: sums?.valorTotal ?? '0.00' };
      res.json({ ...pageResult(rows.map(mapAbastecimento), Number(total), q), totais });
    }),
  );

  router.get(
    '/:id',
    requireAccess('abastecimentos', 'read'),
    asyncHandler(async (req, res) => res.json(mapAbastecimento(await fetchRow(idParam(req))))),
  );

  // POST /api/abastecimentos
  // Registra o abastecimento, avança o hodômetro do veículo e, sem movimentacaoId,
  // lança a despesa (categoria Combustível, já paga) na mesma transação.
  router.post(
    '/',
    requireAccess('abastecimentos', 'write'),
    asyncHandler(async (req, res) => {
      const { formaPagamentoId, ...body } = createBody.parse(req.body);

      const [veiculos] = await pool.query('SELECT Placa FROM veiculos WHERE Id = ? AND Ativo = 1', [body.veiculoId]);
      if (veiculos.length === 0) {
        throw new AppError(400, 'INVALID_REFERENCE', 'Veículo não encontrado ou inativo.', [
          { campo: 'veiculoId', mensagem: 'Veículo não encontrado ou inativo' },
        ]);
      }
      if (body.viagemId) await checkViagem(body.viagemId, body.veiculoId);
      const precoLitro = resolvePrecoLitro(body);

      let categoriaId = null;
      if (body.movimentacaoId) {
        await assertActive(pool, 'movimentacoes', body.movimentacaoId, 'movimentacaoId', 'Movimentação');
        await checkMovimentacaoLivre(body.movimentacaoId);
      } else {
        await assertActive(pool, 'formas_pagamento', formaPagamentoId, 'formaPagamentoId', 'Forma de pagamento');
        const [categorias] = await pool.query(
          "SELECT Id FROM categorias WHERE Titulo = 'Combustível' AND EhSistema = 1 AND Ativo = 1 LIMIT 1",
        );
        if (categorias.length === 0) {
          throw new AppError(500, 'SETUP_ERROR', 'A categoria de sistema "Combustível" não foi encontrada.');
        }
        categoriaId = categorias[0].Id;
      }

      const id = randomUUID();
      await withTransaction(pool, async (db) => {
        let movimentacaoId = body.movimentacaoId;
        if (!movimentacaoId) {
          movimentacaoId = randomUUID();
          const descricao = `Abastecimento ${veiculos[0].Placa}${body.posto ? ` - ${body.posto}` : ''}`.slice(0, 200);
          await db.query(
            `INSERT INTO movimentacoes
               (Id, Valor, Descricao, CategoriaId, FormaPagamentoId, ViagemId, VeiculoId,
                DataPagamento, Status, LitrosCombustivel, CriadoPorUsuarioId)
             VALUES (?, ?, ?, ?, ?, ?, ?, CURDATE(), 'Pago', ?, ?)`,
            [
              movimentacaoId,
              body.valorTotal,
              descricao,
              categoriaId,
              formaPagamentoId,
              body.viagemId ?? null,
              body.veiculoId,
              body.litros,
              req.user.id,
            ],
          );
        }
        await db.query(
          `INSERT INTO abastecimentos
             (Id, VeiculoId, ViagemId, Litros, ValorTotal, PrecoLitro, Posto, Hodometro, MovimentacaoId)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            id,
            body.veiculoId,
            body.viagemId ?? null,
            body.litros,
            body.valorTotal,
            precoLitro,
            body.posto ?? null,
            body.hodometro,
            movimentacaoId,
          ],
        );
        await atualizarHodometro(db, body.veiculoId, body.hodometro);
      });
      res.status(201).json(mapAbastecimento(await fetchRow(id)));
    }),
  );

  // PATCH /api/abastecimentos/:id — alterar litros ou total também corrige a despesa ligada.
  router.patch(
    '/:id',
    requireAccess('abastecimentos', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      if (Object.keys(changes).length === 0) {
        throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      }
      const current = await fetchRow(id);

      const merged = {
        veiculoId: changes.veiculoId ?? current.VeiculoId,
        viagemId: changes.viagemId !== undefined ? changes.viagemId : current.ViagemId,
        litros: changes.litros ?? current.Litros,
        valorTotal: changes.valorTotal ?? current.ValorTotal,
        hodometro: changes.hodometro ?? current.Hodometro,
      };
      const amountsChanged = changes.litros !== undefined || changes.valorTotal !== undefined;
      // Mudou litros/total sem novo preço: recalcula. Mudou só o preço: confere com o resto.
      changes.precoLitro = resolvePrecoLitro({
        ...merged,
        precoLitro: amountsChanged ? changes.precoLitro : (changes.precoLitro ?? current.PrecoLitro),
      });

      if (changes.veiculoId) {
        await assertActive(pool, 'veiculos', changes.veiculoId, 'veiculoId', 'Veículo');
      }
      if (merged.viagemId && (changes.viagemId || changes.veiculoId)) {
        await checkViagem(merged.viagemId, merged.veiculoId);
      }
      if (changes.movimentacaoId) {
        await assertActive(pool, 'movimentacoes', changes.movimentacaoId, 'movimentacaoId', 'Movimentação');
        await checkMovimentacaoLivre(changes.movimentacaoId, id);
      }

      const update = buildUpdate('abastecimentos', id, changes, COLUMNS, expected);
      await withTransaction(pool, async (db) => {
        await runUpdate(db, 'abastecimentos', update, id, expected, 'Abastecimento não encontrado.');
        if (amountsChanged) {
          await db.query('UPDATE movimentacoes SET Valor = ?, LitrosCombustivel = ? WHERE Id = ? AND Ativo = 1', [
            merged.valorTotal,
            merged.litros,
            changes.movimentacaoId ?? current.MovimentacaoId,
          ]);
        }
        if (changes.hodometro !== undefined || changes.veiculoId) {
          await atualizarHodometro(db, merged.veiculoId, merged.hodometro);
        }
      });
      res.json(mapAbastecimento(await fetchRow(id)));
    }),
  );

  // Exclusão lógica. A despesa em Movimentações não é mexida: quem cuida do financeiro decide.
  router.delete(
    '/:id',
    requireAccess('abastecimentos', 'write'),
    asyncHandler(async (req, res) => {
      await softDelete(pool, 'abastecimentos', idParam(req), 'Abastecimento não encontrado.');
      res.status(204).end();
    }),
  );

  return router;
}
