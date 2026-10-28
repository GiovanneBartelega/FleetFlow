import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { mapItemChecklist } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { syncVersion, uuid } from '../schemas.js';
import { buildUpdate, runUpdate, softDelete } from '../sql.js';

const fields = {
  nome: z.string().trim().min(1).max(120),
  obrigatorio: z.boolean(),
};

const createBody = z.object({ ...fields, obrigatorio: fields.obrigatorio.default(true) });
const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const COLUMNS = { nome: 'Nome', obrigatorio: 'Obrigatorio' };

// Cat├ílogo de itens que comp├Áem o checklist de uma viagem.
export function itensChecklistRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query('SELECT * FROM itens_checklist WHERE Id = ? AND Ativo = 1', [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Item de checklist n├úo encontrado.');
    return rows[0];
  };

  router.get(
    '/',
    requireAccess('itensChecklist', 'read'),
    asyncHandler(async (req, res) => {
      const [rows] = await pool.query('SELECT * FROM itens_checklist WHERE Ativo = 1 ORDER BY Nome');
      res.json({ data: rows.map(mapItemChecklist) });
    }),
  );

  router.get(
    '/:id',
    requireAccess('itensChecklist', 'read'),
    asyncHandler(async (req, res) => res.json(mapItemChecklist(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('itensChecklist', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      const id = randomUUID();
      await pool.query('INSERT INTO itens_checklist (Id, Nome, Obrigatorio) VALUES (?, ?, ?)', [
        id,
        body.nome,
        body.obrigatorio ? 1 : 0,
      ]);
      res.status(201).json(mapItemChecklist(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('itensChecklist', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      await fetchRow(id);

      const update = buildUpdate('itens_checklist', id, changes, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'itens_checklist', update, id, expected, 'Item de checklist n├úo encontrado.');
      res.json(mapItemChecklist(await fetchRow(id)));
    }),
  );

  // Exclus├úo l├│gica: o hist├│rico de checklists j├í preenchidos continua apontando para o item.
  router.delete(
    '/:id',
    requireAccess('itensChecklist', 'write'),
    asyncHandler(async (req, res) => {
      await softDelete(pool, 'itens_checklist', idParam(req), 'Item de checklist n├úo encontrado.');
      res.status(204).end();
    }),
  );

  return router;
}
