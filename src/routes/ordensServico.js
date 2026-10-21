import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { ORDEM_SERVICO_SELECT, mapOrdemServico } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { dateOnly, intNonNegative, money, pageQuery, search, syncVersion, uuid } from '../schemas.js';
import {
  assertActive,
  buildUpdate,
  likeTerm,
  pageResult,
  pagination,
  runUpdate,
  softDelete,
} from '../sql.js';

const TIPOS = ['Preventiva', 'Corretiva'];

const listQuery = pageQuery.extend({
  veiculoId: uuid.optional(),
  tipo: z.enum(TIPOS).optional(),
  concluida: z.enum(['true', 'false']).optional(),
  manutencaoDe: dateOnly.optional(),
  manutencaoAte: dateOnly.optional(),
  movimentacaoId: uuid.optional(),
  busca: search.optional(),
});

const fields = {
  veiculoId: uuid,
  dataManutencao: dateOnly,
  descricao: z.string().trim().min(1).max(200),
  tipo: z.enum(TIPOS),
  valorTotal: money,
  hodometroNaManutencao: intNonNegative.nullable(),
  dataConclusao: dateOnly.nullable(),
  // Toda ordem de serviço aponta para a movimentação financeira (despesa) que ela gerou.
  movimentacaoId: uuid,
};

// Regra ck_os_data_conclusao: a conclusão não pode ser anterior à manutenção.
// 'AAAA-MM-DD' compara corretamente como texto.
function checkConclusao(value, ctx) {
  if (value.dataConclusao && value.dataManutencao && value.dataConclusao < value.dataManutencao) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['dataConclusao'],
      message: 'A data de conclusão não pode ser anterior à data da manutenção',
    });
  }
}

const createBody = z
  .object({
    ...fields,
    hodometroNaManutencao: fields.hodometroNaManutencao.optional(),
    dataConclusao: fields.dataConclusao.optional(),
  })
  .superRefine(checkConclusao);

const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const concluirBody = z.object({
  dataConclusao: dateOnly,
  syncVersion: syncVersion.optional(),
});

const COLUMNS = {
  veiculoId: 'VeiculoId',
  dataManutencao: 'DataManutencao',
  descricao: 'Descricao',
  tipo: 'Tipo',
  valorTotal: 'ValorTotal',
  hodometroNaManutencao: 'HodometroNaManutencao',
  dataConclusao: 'DataConclusao',
  movimentacaoId: 'MovimentacaoId',
};

function validationError(issues) {
  return new AppError(
    400,
    'VALIDATION_ERROR',
    'Dados inválidos.',
    issues.map((issue) => ({ campo: issue.path.join('.'), mensagem: issue.message })),
  );
}

export function ordensServicoRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query(`${ORDEM_SERVICO_SELECT} WHERE o.Id = ? AND o.Ativo = 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Ordem de serviço não encontrada.');
    return rows[0];
  };

  const checkReferences = async (ref) => {
    if (ref.veiculoId) await assertActive(pool, 'veiculos', ref.veiculoId, 'veiculoId', 'Veículo');
    if (ref.movimentacaoId) {
      await assertActive(pool, 'movimentacoes', ref.movimentacaoId, 'movimentacaoId', 'Movimentação');
    }
  };

  // GET /api/ordens-servico — lista paginada com filtros.
  router.get(
    '/',
    requireAccess('manutencao', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['o.Ativo = 1'];
      const params = [];
      const add = (condition, value) => { where.push(condition); params.push(value); };

      if (q.veiculoId) add('o.VeiculoId = ?', q.veiculoId);
      if (q.tipo) add('o.Tipo = ?', q.tipo);
      if (q.movimentacaoId) add('o.MovimentacaoId = ?', q.movimentacaoId);
      if (q.concluida === 'true') where.push('o.DataConclusao IS NOT NULL');
      if (q.concluida === 'false') where.push('o.DataConclusao IS NULL');
      if (q.manutencaoDe) add('o.DataManutencao >= ?', q.manutencaoDe);
      if (q.manutencaoAte) add('o.DataManutencao <= ?', q.manutencaoAte);
      if (q.busca) {
        const term = likeTerm(q.busca);
        where.push('(o.Descricao LIKE ? OR v.Placa LIKE ?)');
        params.push(term, term);
      }
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);

      const [[{ total }]] = await pool.query(
        `SELECT COUNT(*) AS total FROM ordens_servico o JOIN veiculos v ON v.Id = o.VeiculoId WHERE ${clause}`,
        params,
      );
      const [rows] = await pool.query(
        `${ORDEM_SERVICO_SELECT} WHERE ${clause} ORDER BY o.DataManutencao DESC, o.CriadoEm DESC LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      res.json(pageResult(rows.map(mapOrdemServico), Number(total), q));
    }),
  );

  router.get(
    '/:id',
    requireAccess('manutencao', 'read'),
    asyncHandler(async (req, res) => res.json(mapOrdemServico(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('manutencao', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      await checkReferences(body);

      const id = randomUUID();
      await pool.query(
        `INSERT INTO ordens_servico
           (Id, VeiculoId, DataManutencao, Descricao, Tipo, ValorTotal,
            HodometroNaManutencao, DataConclusao, MovimentacaoId)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          id,
          body.veiculoId,
          body.dataManutencao,
          body.descricao,
          body.tipo,
          body.valorTotal,
          body.hodometroNaManutencao ?? null,
          body.dataConclusao ?? null,
          body.movimentacaoId,
        ],
      );
      res.status(201).json(mapOrdemServico(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('manutencao', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      const current = await fetchRow(id);

      // Valida o resultado final (registro atual + alterações).
      const issues = [];
      checkConclusao(
        {
          dataManutencao: changes.dataManutencao ?? current.DataManutencao,
          dataConclusao: changes.dataConclusao !== undefined ? changes.dataConclusao : current.DataConclusao,
        },
        { addIssue: (issue) => issues.push(issue) },
      );
      if (issues.length > 0) throw validationError(issues);

      await checkReferences(changes);
      const update = buildUpdate('ordens_servico', id, changes, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'ordens_servico', update, id, expected, 'Ordem de serviço não encontrada.');
      res.json(mapOrdemServico(await fetchRow(id)));
    }),
  );

  // POST /api/ordens-servico/:id/concluir  { dataConclusao }
  router.post(
    '/:id/concluir',
    requireAccess('manutencao', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, dataConclusao } = concluirBody.parse(req.body);
      const current = await fetchRow(id);
      if (current.DataConclusao) {
        throw new AppError(409, 'ALREADY_COMPLETED', 'Esta ordem de serviço já está concluída.');
      }

      const issues = [];
      checkConclusao(
        { dataManutencao: current.DataManutencao, dataConclusao },
        { addIssue: (issue) => issues.push(issue) },
      );
      if (issues.length > 0) throw validationError(issues);

      const update = buildUpdate('ordens_servico', id, { dataConclusao }, COLUMNS, expected);
      await runUpdate(pool, 'ordens_servico', update, id, expected, 'Ordem de serviço não encontrada.');
      res.json(mapOrdemServico(await fetchRow(id)));
    }),
  );

  router.delete(
    '/:id',
    requireAccess('manutencao', 'write'),
    asyncHandler(async (req, res) => {
      await softDelete(pool, 'ordens_servico', idParam(req), 'Ordem de serviço não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
