import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { DIVIDA_SELECT, mapDivida } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { dateOnly, money, pageQuery, search, syncVersion, uuid } from '../schemas.js';
import {
  assertActive,
  buildUpdate,
  likeTerm,
  moneyTimes,
  pageResult,
  pagination,
  runUpdate,
  softDelete,
} from '../sql.js';

const listQuery = pageQuery.extend({
  categoriaId: uuid.optional(),
  veiculoId: uuid.optional(),
  busca: search.optional(),
});

const createBody = z.object({
  descricao: z.string().trim().min(1).max(200),
  categoriaId: uuid,
  quantidadeParcelas: z.number().int().min(1).max(480),
  valorParcela: money,
  dataVencimentoPrimeira: dateOnly,
  valorQuitacaoAntecipada: money.nullable().optional(),
  // A tabela veiculos ainda não existe neste banco, então o id não é verificado.
  veiculoId: uuid.nullable().optional(),
});

const updateBody = createBody.partial().extend({ syncVersion: syncVersion.optional() });

const COLUMNS = {
  descricao: 'Descricao',
  categoriaId: 'CategoriaId',
  quantidadeParcelas: 'QuantidadeParcelas',
  valorParcela: 'ValorParcela',
  dataVencimentoPrimeira: 'DataVencimentoPrimeira',
  valorQuitacaoAntecipada: 'ValorQuitacaoAntecipada',
  veiculoId: 'VeiculoId',
};

const present = (row) => mapDivida(row, moneyTimes(row.QuantidadeParcelas, row.ValorParcela));

export function dividasRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query(`${DIVIDA_SELECT} WHERE d.Id = ? AND d.Ativo = 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Dívida não encontrada.');
    return rows[0];
  };

  router.get(
    '/',
    requireAccess('dividas', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['d.Ativo = 1'];
      const params = [];
      if (q.categoriaId) { where.push('d.CategoriaId = ?'); params.push(q.categoriaId); }
      if (q.veiculoId) { where.push('d.VeiculoId = ?'); params.push(q.veiculoId); }
      if (q.busca) { where.push('d.Descricao LIKE ?'); params.push(likeTerm(q.busca)); }
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);

      const [[{ total }]] = await pool.query(
        `SELECT COUNT(*) AS total FROM dividas d WHERE ${clause}`,
        params,
      );
      const [rows] = await pool.query(
        `${DIVIDA_SELECT} WHERE ${clause} ORDER BY d.DataVencimentoPrimeira DESC, d.CriadoEm DESC LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      res.json(pageResult(rows.map(present), Number(total), q));
    }),
  );

  router.get(
    '/:id',
    requireAccess('dividas', 'read'),
    asyncHandler(async (req, res) => res.json(present(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('dividas', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      await assertActive(pool, 'categorias', body.categoriaId, 'categoriaId', 'Categoria');

      const id = randomUUID();
      await pool.query(
        `INSERT INTO dividas
           (Id, Descricao, CategoriaId, QuantidadeParcelas, ValorParcela,
            DataVencimentoPrimeira, ValorQuitacaoAntecipada, VeiculoId)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          id,
          body.descricao,
          body.categoriaId,
          body.quantidadeParcelas,
          body.valorParcela,
          body.dataVencimentoPrimeira,
          body.valorQuitacaoAntecipada ?? null,
          body.veiculoId ?? null,
        ],
      );
      res.status(201).json(present(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('dividas', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...fields } = updateBody.parse(req.body);

      if (fields.categoriaId) {
        await assertActive(pool, 'categorias', fields.categoriaId, 'categoriaId', 'Categoria');
      }
      const update = buildUpdate('dividas', id, fields, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'dividas', update, id, expected, 'Dívida não encontrada.');
      res.json(present(await fetchRow(id)));
    }),
  );

  router.delete(
    '/:id',
    requireAccess('dividas', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      await fetchRow(id);
      const [[{ total }]] = await pool.query(
        'SELECT COUNT(*) AS total FROM movimentacoes WHERE DividaId = ? AND Ativo = 1',
        [id],
      );
      if (Number(total) > 0) {
        throw new AppError(409, 'IN_USE', 'A dívida possui movimentações vinculadas.');
      }
      await softDelete(pool, 'dividas', id, 'Dívida não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
