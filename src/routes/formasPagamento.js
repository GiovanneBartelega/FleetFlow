import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { mapFormaPagamento } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { syncVersion, uuid } from '../schemas.js';
import { buildUpdate, runUpdate, softDelete } from '../sql.js';

const createBody = z.object({ nome: z.string().trim().min(1).max(40) });
const updateBody = z.object({ nome: z.string().trim().min(1).max(40), syncVersion }).partial();
const COLUMNS = { nome: 'Nome' };

export function formasPagamentoRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query('SELECT * FROM formas_pagamento WHERE Id = ? AND Ativo = 1', [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Forma de pagamento não encontrada.');
    return rows[0];
  };

  const isInUse = async (id) => {
    const [[row]] = await pool.query(
      'SELECT COUNT(*) AS total FROM movimentacoes WHERE FormaPagamentoId = ? AND Ativo = 1',
      [id],
    );
    return Number(row.total) > 0;
  };

  const assertEditable = (row) => {
    if (row.EhSistema) {
      throw new AppError(403, 'SYSTEM_RECORD', 'Formas de pagamento do sistema não podem ser alteradas ou removidas.');
    }
  };

  router.get(
    '/',
    requireAccess('formasPagamento', 'read'),
    asyncHandler(async (req, res) => {
      const [rows] = await pool.query('SELECT * FROM formas_pagamento WHERE Ativo = 1 ORDER BY Nome');
      res.json({ data: rows.map(mapFormaPagamento) });
    }),
  );

  router.get(
    '/:id',
    requireAccess('formasPagamento', 'read'),
    asyncHandler(async (req, res) => res.json(mapFormaPagamento(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('formasPagamento', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      const id = randomUUID();
      await pool.query('INSERT INTO formas_pagamento (Id, Nome, EhSistema) VALUES (?, ?, 0)', [id, body.nome]);
      res.status(201).json(mapFormaPagamento(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('formasPagamento', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...fields } = updateBody.parse(req.body);

      assertEditable(await fetchRow(id));
      const update = buildUpdate('formas_pagamento', id, fields, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'formas_pagamento', update, id, expected, 'Forma de pagamento não encontrada.');
      res.json(mapFormaPagamento(await fetchRow(id)));
    }),
  );

  router.delete(
    '/:id',
    requireAccess('formasPagamento', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      assertEditable(await fetchRow(id));
      if (await isInUse(id)) {
        throw new AppError(409, 'IN_USE', 'A forma de pagamento está em uso por movimentações.');
      }
      await softDelete(pool, 'formas_pagamento', id, 'Forma de pagamento não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
