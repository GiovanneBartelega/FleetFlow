import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { CHECKLIST_VIAGEM_SELECT, mapChecklistViagem } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { syncVersion, uuid } from '../schemas.js';
import { assertActive, buildUpdate, runUpdate } from '../sql.js';

const listQuery = z.object({
  viagemId: uuid,
  conforme: z.enum(['true', 'false']).optional(),
});

const fields = {
  conforme: z.boolean(),
  observacao: z.string().trim().max(200).nullable(),
  fotoUrl: z.string().trim().url('URL inválida').max(2000).nullable(),
};

// Regra ck_checklist_observacao: item não conforme exige observação.
function checkObservacao(value, ctx) {
  if (value.conforme === false && !value.observacao) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['observacao'],
      message: 'Informe a observação quando o item não estiver conforme',
    });
  }
}

const createBody = z
  .object({
    viagemId: uuid,
    itemChecklistId: uuid,
    conforme: fields.conforme,
    observacao: fields.observacao.optional(),
    fotoUrl: fields.fotoUrl.optional(),
  })
  .superRefine(checkObservacao);

const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const COLUMNS = { conforme: 'Conforme', observacao: 'Observacao', fotoUrl: 'FotoUrl' };

function validationError(issues) {
  return new AppError(
    400,
    'VALIDATION_ERROR',
    'Dados inválidos.',
    issues.map((issue) => ({ campo: issue.path.join('.'), mensagem: issue.message })),
  );
}

// Respostas do checklist de cada viagem (um registro por viagem + item).
export function checklistsViagemRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query(`${CHECKLIST_VIAGEM_SELECT} WHERE c.Id = ? AND c.Ativo = 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Resposta de checklist não encontrada.');
    return rows[0];
  };

  const assertViagemAberta = async (viagemId) => {
    const [rows] = await pool.query('SELECT Status FROM viagens WHERE Id = ? AND Ativo = 1', [viagemId]);
    if (rows.length === 0) {
      throw new AppError(400, 'INVALID_REFERENCE', 'Viagem não encontrada ou inativa.', [
        { campo: 'viagemId', mensagem: 'Viagem não encontrada ou inativa' },
      ]);
    }
    if (rows[0].Status !== 'EmAndamento') {
      throw new AppError(409, 'TRIP_CLOSED', 'O checklist só pode ser alterado em viagens em andamento.');
    }
  };

  // GET /api/checklists-viagem?viagemId=... — respostas de uma viagem.
  router.get(
    '/',
    requireAccess('checklist', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['c.Ativo = 1', 'c.ViagemId = ?'];
      const params = [q.viagemId];
      if (q.conforme) { where.push('c.Conforme = ?'); params.push(q.conforme === 'true' ? 1 : 0); }
      const [rows] = await pool.query(
        `${CHECKLIST_VIAGEM_SELECT} WHERE ${where.join(' AND ')} ORDER BY i.Nome`,
        params,
      );
      res.json({ data: rows.map(mapChecklistViagem) });
    }),
  );

  router.get(
    '/:id',
    requireAccess('checklist', 'read'),
    asyncHandler(async (req, res) => res.json(mapChecklistViagem(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('checklist', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      await assertViagemAberta(body.viagemId);
      await assertActive(pool, 'itens_checklist', body.itemChecklistId, 'itemChecklistId', 'Item de checklist');

      // uq_checklist_viagem_item vale mesmo para linhas inativas.
      const [dup] = await pool.query(
        'SELECT Id FROM checklists_viagem WHERE ViagemId = ? AND ItemChecklistId = ?',
        [body.viagemId, body.itemChecklistId],
      );
      if (dup.length > 0) {
        throw new AppError(409, 'DUPLICATE', 'Este item já foi respondido para a viagem. Atualize a resposta existente.');
      }

      const id = randomUUID();
      await pool.query(
        `INSERT INTO checklists_viagem (Id, ViagemId, ItemChecklistId, Conforme, Observacao, FotoUrl)
         VALUES (?, ?, ?, ?, ?, ?)`,
        [
          id,
          body.viagemId,
          body.itemChecklistId,
          body.conforme ? 1 : 0,
          body.observacao || null,
          body.fotoUrl ?? null,
        ],
      );
      res.status(201).json(mapChecklistViagem(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('checklist', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      const current = await fetchRow(id);
      await assertViagemAberta(current.ViagemId);

      const issues = [];
      checkObservacao(
        {
          conforme: changes.conforme ?? Boolean(current.Conforme),
          observacao: changes.observacao !== undefined ? changes.observacao : current.Observacao,
        },
        { addIssue: (issue) => issues.push(issue) },
      );
      if (issues.length > 0) throw validationError(issues);

      if (changes.conforme !== undefined) changes.conforme = changes.conforme ? 1 : 0;
      if (changes.observacao === '') changes.observacao = null;
      const update = buildUpdate('checklists_viagem', id, changes, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'checklists_viagem', update, id, expected, 'Resposta de checklist não encontrada.');
      res.json(mapChecklistViagem(await fetchRow(id)));
    }),
  );

  // Remoção definitiva: o UNIQUE (viagem + item) impediria responder de novo após exclusão lógica.
  router.delete(
    '/:id',
    requireAccess('checklist', 'write'),
    asyncHandler(async (req, res) => {
      const current = await fetchRow(idParam(req));
      await assertViagemAberta(current.ViagemId);
      await pool.query('DELETE FROM checklists_viagem WHERE Id = ?', [current.Id]);
      res.status(204).end();
    }),
  );

  return router;
}
