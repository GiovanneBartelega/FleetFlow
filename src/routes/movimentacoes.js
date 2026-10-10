import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { MOVIMENTACAO_SELECT, mapMovimentacao } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { dateOnly, money, pageQuery, search, syncVersion, uuid } from '../schemas.js';
import {
  assertActive,
  buildUpdate,
  likeTerm,
  pageResult,
  pagination,
  runUpdate,
  softDelete,
} from '../sql.js';

const STATUS = ['Pendente', 'Pago'];

const listQuery = pageQuery.extend({
  status: z.enum(STATUS).optional(),
  tipo: z.enum(['Entrada', 'Saida']).optional(),
  categoriaId: uuid.optional(),
  formaPagamentoId: uuid.optional(),
  dividaId: uuid.optional(),
  veiculoId: uuid.optional(),
  viagemId: uuid.optional(),
  vencimentoDe: dateOnly.optional(),
  vencimentoAte: dateOnly.optional(),
  pagamentoDe: dateOnly.optional(),
  pagamentoAte: dateOnly.optional(),
  busca: search.optional(),
});

const fields = {
  valor: money,
  descricao: z.string().trim().min(1).max(200),
  categoriaId: uuid,
  formaPagamentoId: uuid,
  // As tabelas viagens e veiculos ainda não existem neste banco: ids não verificados.
  viagemId: uuid.nullable(),
  dividaId: uuid.nullable(),
  veiculoId: uuid.nullable(),
  numeroParcela: z.number().int().positive().nullable(),
  dataVencimento: dateOnly.nullable(),
  dataPagamento: dateOnly.nullable(),
  status: z.enum(STATUS),
  litrosCombustivel: money.nullable(),
};

const createBody = z
  .object({
    ...fields,
    viagemId: fields.viagemId.optional(),
    dividaId: fields.dividaId.optional(),
    veiculoId: fields.veiculoId.optional(),
    numeroParcela: fields.numeroParcela.optional(),
    dataVencimento: fields.dataVencimento.optional(),
    dataPagamento: fields.dataPagamento.optional(),
    status: fields.status.default('Pendente'),
    litrosCombustivel: fields.litrosCombustivel.optional(),
  })
  .superRefine(checkPaymentConsistency);

const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const payBody = z.object({
  dataPagamento: dateOnly,
  formaPagamentoId: uuid.optional(),
  syncVersion: syncVersion.optional(),
});

const COLUMNS = {
  valor: 'Valor',
  descricao: 'Descricao',
  categoriaId: 'CategoriaId',
  formaPagamentoId: 'FormaPagamentoId',
  viagemId: 'ViagemId',
  dividaId: 'DividaId',
  veiculoId: 'VeiculoId',
  numeroParcela: 'NumeroParcela',
  dataVencimento: 'DataVencimento',
  dataPagamento: 'DataPagamento',
  status: 'Status',
  litrosCombustivel: 'LitrosCombustivel',
};

// Pago exige data de pagamento (regra ck_movimentacoes_pago); Pendente não tem.
function checkPaymentConsistency(value, ctx) {
  if (value.status === 'Pago' && !value.dataPagamento) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['dataPagamento'],
      message: 'Informe a data de pagamento para movimentações pagas',
    });
  }
  if (value.status === 'Pendente' && value.dataPagamento) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['dataPagamento'],
      message: 'Movimentações pendentes não têm data de pagamento',
    });
  }
}

function listFilters(q) {
  const where = ['m.Ativo = 1'];
  const params = [];
  const add = (condition, value) => { where.push(condition); params.push(value); };

  if (q.status) add('m.Status = ?', q.status);
  if (q.tipo) add('c.Tipo = ?', q.tipo);
  if (q.categoriaId) add('m.CategoriaId = ?', q.categoriaId);
  if (q.formaPagamentoId) add('m.FormaPagamentoId = ?', q.formaPagamentoId);
  if (q.dividaId) add('m.DividaId = ?', q.dividaId);
  if (q.veiculoId) add('m.VeiculoId = ?', q.veiculoId);
  if (q.viagemId) add('m.ViagemId = ?', q.viagemId);
  if (q.vencimentoDe) add('m.DataVencimento >= ?', q.vencimentoDe);
  if (q.vencimentoAte) add('m.DataVencimento <= ?', q.vencimentoAte);
  if (q.pagamentoDe) add('m.DataPagamento >= ?', q.pagamentoDe);
  if (q.pagamentoAte) add('m.DataPagamento <= ?', q.pagamentoAte);
  if (q.busca) add('m.Descricao LIKE ?', likeTerm(q.busca));

  return { clause: where.join(' AND '), params };
}

export function movimentacoesRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query(`${MOVIMENTACAO_SELECT} WHERE m.Id = ? AND m.Ativo = 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Movimentação não encontrada.');
    return rows[0];
  };

  const checkReferences = async (ref) => {
    if (ref.categoriaId) await assertActive(pool, 'categorias', ref.categoriaId, 'categoriaId', 'Categoria');
    if (ref.formaPagamentoId) {
      await assertActive(pool, 'formas_pagamento', ref.formaPagamentoId, 'formaPagamentoId', 'Forma de pagamento');
    }
    if (ref.dividaId) await assertActive(pool, 'dividas', ref.dividaId, 'dividaId', 'Dívida');
  };

  // GET /api/movimentacoes — lista paginada com filtros e totais do filtro.
  router.get(
    '/',
    requireAccess('movimentacoes', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const { clause, params } = listFilters(q);
      const { limit, offset } = pagination(q);
      const from = `FROM movimentacoes m
        JOIN categorias c ON c.Id = m.CategoriaId
        JOIN formas_pagamento f ON f.Id = m.FormaPagamentoId`;

      const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total ${from} WHERE ${clause}`, params);
      const [rows] = await pool.query(
        `${MOVIMENTACAO_SELECT} WHERE ${clause}
         ORDER BY COALESCE(m.DataVencimento, m.DataPagamento, DATE(m.CriadoEm)) DESC, m.CriadoEm DESC
         LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      const [sums] = await pool.query(
        `SELECT c.Tipo AS tipo, SUM(m.Valor) AS total ${from} WHERE ${clause} GROUP BY c.Tipo`,
        params,
      );

      const totais = { entradas: '0.00', saidas: '0.00' };
      for (const row of sums) {
        if (row.tipo === 'Entrada') totais.entradas = row.total;
        if (row.tipo === 'Saida') totais.saidas = row.total;
      }
      res.json({ ...pageResult(rows.map(mapMovimentacao), Number(total), q), totais });
    }),
  );

  router.get(
    '/:id',
    requireAccess('movimentacoes', 'read'),
    asyncHandler(async (req, res) => res.json(mapMovimentacao(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('movimentacoes', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      await checkReferences(body);

      const id = randomUUID();
      await pool.query(
        `INSERT INTO movimentacoes
           (Id, Valor, Descricao, CategoriaId, FormaPagamentoId, ViagemId, DividaId, VeiculoId,
            NumeroParcela, DataVencimento, DataPagamento, Status, LitrosCombustivel, CriadoPorUsuarioId)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          id,
          body.valor,
          body.descricao,
          body.categoriaId,
          body.formaPagamentoId,
          body.viagemId ?? null,
          body.dividaId ?? null,
          body.veiculoId ?? null,
          body.numeroParcela ?? null,
          body.dataVencimento ?? null,
          body.dataPagamento ?? null,
          body.status,
          body.litrosCombustivel ?? null,
          req.user.id,
        ],
      );
      res.status(201).json(mapMovimentacao(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('movimentacoes', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      const current = await fetchRow(id);

      // Voltar para Pendente sem informar a data limpa a data de pagamento.
      if (changes.status === 'Pendente' && changes.dataPagamento === undefined) {
        changes.dataPagamento = null;
      }
      // Valida o resultado final (registro atual + alterações).
      const merged = {
        status: changes.status ?? current.Status,
        dataPagamento: changes.dataPagamento !== undefined ? changes.dataPagamento : current.DataPagamento,
      };
      const issues = [];
      checkPaymentConsistency(merged, { addIssue: (issue) => issues.push(issue) });
      if (issues.length > 0) {
        throw new AppError(
          400,
          'VALIDATION_ERROR',
          'Dados inválidos.',
          issues.map((issue) => ({ campo: issue.path.join('.'), mensagem: issue.message })),
        );
      }

      await checkReferences(changes);
      const update = buildUpdate('movimentacoes', id, changes, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'movimentacoes', update, id, expected, 'Movimentação não encontrada.');
      res.json(mapMovimentacao(await fetchRow(id)));
    }),
  );

  // POST /api/movimentacoes/:id/pagar  { dataPagamento, formaPagamentoId? }
  router.post(
    '/:id/pagar',
    requireAccess('movimentacoes', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...body } = payBody.parse(req.body);
      const current = await fetchRow(id);
      if (current.Status === 'Pago') {
        throw new AppError(409, 'ALREADY_PAID', 'Esta movimentação já está paga.');
      }
      await checkReferences(body);

      const update = buildUpdate(
        'movimentacoes',
        id,
        { status: 'Pago', dataPagamento: body.dataPagamento, formaPagamentoId: body.formaPagamentoId },
        COLUMNS,
        expected,
      );
      await runUpdate(pool, 'movimentacoes', update, id, expected, 'Movimentação não encontrada.');
      res.json(mapMovimentacao(await fetchRow(id)));
    }),
  );

  router.delete(
    '/:id',
    requireAccess('movimentacoes', 'write'),
    asyncHandler(async (req, res) => {
      await softDelete(pool, 'movimentacoes', idParam(req), 'Movimentação não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
