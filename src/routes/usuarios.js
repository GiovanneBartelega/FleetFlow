import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { USER_SELECT, mapPerfil, mapUsuario } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { categoriaCnh, dateOnly, pageQuery, search, syncVersion, uuid } from '../schemas.js';
import {
  assertActive,
  buildUpdate,
  likeTerm,
  pageResult,
  pagination,
  runUpdate,
  softDelete,
} from '../sql.js';

const STATUS = ['AguardandoAprovacao', 'Ativo', 'Bloqueado'];

const listQuery = pageQuery.extend({
  status: z.enum(STATUS).optional(),
  perfilId: uuid.optional(),
  busca: search.optional(),
});

const createBody = z.object({
  nome: z.string().trim().min(1).max(120),
  email: z.string().trim().toLowerCase().email().max(180),
  perfilId: uuid,
  status: z.enum(STATUS).default('Ativo'),
  categoriaCnh: categoriaCnh.nullable().optional(),
  validadeCnh: dateOnly.nullable().optional(),
});

const updateBody = z
  .object({
    nome: z.string().trim().min(1).max(120),
    perfilId: uuid,
    status: z.enum(STATUS),
    categoriaCnh: categoriaCnh.nullable(),
    validadeCnh: dateOnly.nullable(),
    syncVersion,
  })
  .partial();

const COLUMNS = {
  nome: 'Nome',
  perfilId: 'PerfilId',
  status: 'Status',
  categoriaCnh: 'CategoriaCnh',
  validadeCnh: 'ValidadeCnh',
};

export function usuariosRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchUsuario = async (id) => {
    const [rows] = await pool.query(`${USER_SELECT} WHERE u.Id = ? AND u.Ativo = 1 LIMIT 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Usuário não encontrado.');
    return mapUsuario(rows[0]);
  };

  router.get(
    '/',
    requireAccess('usuarios', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['u.Ativo = 1'];
      const params = [];
      if (q.status) { where.push('u.Status = ?'); params.push(q.status); }
      if (q.perfilId) { where.push('u.PerfilId = ?'); params.push(q.perfilId); }
      if (q.busca) {
        where.push('(u.Nome LIKE ? OR u.Email LIKE ?)');
        params.push(likeTerm(q.busca), likeTerm(q.busca));
      }
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);

      const [[{ total }]] = await pool.query(
        `SELECT COUNT(*) AS total FROM usuarios u WHERE ${clause}`,
        params,
      );
      const [rows] = await pool.query(
        `${USER_SELECT} WHERE ${clause} ORDER BY u.Nome LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      res.json(pageResult(rows.map(mapUsuario), Number(total), q));
    }),
  );

  router.get(
    '/:id',
    requireAccess('usuarios', 'read'),
    asyncHandler(async (req, res) => res.json(await fetchUsuario(idParam(req)))),
  );

  // Pré-cadastro: o administrador cadastra o e-mail e o perfil antes do primeiro
  // login. O GoogleSubjectId provisório ("pre-...") é trocado no login.
  router.post(
    '/',
    requireAccess('usuarios', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      await assertActive(pool, 'perfis', body.perfilId, 'perfilId', 'Perfil');

      const id = randomUUID();
      await pool.query(
        `INSERT INTO usuarios
           (Id, Nome, Email, GoogleSubjectId, PerfilId, Status, CategoriaCnh, ValidadeCnh)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          id,
          body.nome,
          body.email,
          `pre-${randomUUID()}`,
          body.perfilId,
          body.status,
          body.categoriaCnh ?? null,
          body.validadeCnh ?? null,
        ],
      );
      res.status(201).json(await fetchUsuario(id));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('usuarios', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const body = updateBody.parse(req.body);
      const { syncVersion: expected, ...fields } = body;

      // Evita o administrador se trancar para fora do sistema.
      if (id === req.user.id && (fields.perfilId !== undefined || fields.status !== undefined)) {
        throw new AppError(403, 'SELF_MODIFICATION', 'Você não pode alterar o próprio perfil ou status.');
      }
      if (fields.perfilId) await assertActive(pool, 'perfis', fields.perfilId, 'perfilId', 'Perfil');

      const update = buildUpdate('usuarios', id, fields, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'usuarios', update, id, expected, 'Usuário não encontrado.');
      res.json(await fetchUsuario(id));
    }),
  );

  // Desativa a conta (soft delete). O histórico financeiro é preservado.
  router.delete(
    '/:id',
    requireAccess('usuarios', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      if (id === req.user.id) {
        throw new AppError(403, 'SELF_MODIFICATION', 'Você não pode desativar a própria conta.');
      }
      await softDelete(pool, 'usuarios', id, 'Usuário não encontrado.');
      res.status(204).end();
    }),
  );

  return router;
}

export function perfisRouter({ pool }) {
  const router = Router();

  router.get(
    '/',
    requireAccess('usuarios', 'read'),
    asyncHandler(async (req, res) => {
      const [rows] = await pool.query('SELECT * FROM perfis WHERE Ativo = 1 ORDER BY Nome');
      res.json({ data: rows.map(mapPerfil) });
    }),
  );

  return router;
}
