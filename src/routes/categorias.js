import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { mapCategoria } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { search, syncVersion, uuid } from '../schemas.js';
import { buildUpdate, likeTerm, runUpdate, softDelete } from '../sql.js';

const TIPOS = ['Entrada', 'Saida'];

const listQuery = z.object({
  tipo: z.enum(TIPOS).optional(),
  busca: search.optional(),
});

const createBody = z.object({
  titulo: z.string().trim().min(1).max(60),
  tipo: z.enum(TIPOS),
});

const updateBody = z
  .object({
    titulo: z.string().trim().min(1).max(60),
    tipo: z.enum(TIPOS),
    syncVersion,
  })
  .partial();

const COLUMNS = { titulo: 'Titulo', tipo: 'Tipo' };

export function categoriasRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query('SELECT * FROM categorias WHERE Id = ? AND Ativo = 1', [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Categoria não encontrada.');
    return rows[0];
  };

  const isInUse = async (id) => {
    const [[row]] = await pool.query(
      `SELECT
         (SELECT COUNT(*) FROM dividas WHERE CategoriaId = ? AND Ativo = 1) +
         (SELECT COUNT(*) FROM movimentacoes WHERE CategoriaId = ? AND Ativo = 1) AS total`,
      [id, id],
    );
    return Number(row.total) > 0;
  };

  // Categorias de sistema (EhSistema) não podem ser alteradas por ninguém.
  const assertEditable = (row) => {
    if (row.EhSistema) {
      throw new AppError(403, 'SYSTEM_RECORD', 'Categorias do sistema não podem ser alteradas ou removidas.');
    }
  };

  router.get(
    '/',
    requireAccess('categorias', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['Ativo = 1'];
      const params = [];
      if (q.tipo) { where.push('Tipo = ?'); params.push(q.tipo); }
      if (q.busca) { where.push('Titulo LIKE ?'); params.push(likeTerm(q.busca)); }

      const [rows] = await pool.query(
        `SELECT * FROM categorias WHERE ${where.join(' AND ')} ORDER BY Tipo, Titulo`,
        params,
      );
      res.json({ data: rows.map(mapCategoria) });
    }),
  );

  router.get(
    '/:id',
    requireAccess('categorias', 'read'),
    asyncHandler(async (req, res) => res.json(mapCategoria(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('categorias', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      const id = randomUUID();
      await pool.query(
        'INSERT INTO categorias (Id, Titulo, Tipo, EhSistema) VALUES (?, ?, ?, 0)',
        [id, body.titulo, body.tipo],
      );
      res.status(201).json(mapCategoria(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('categorias', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...fields } = updateBody.parse(req.body);

      const current = await fetchRow(id);
      assertEditable(current);
      if (fields.tipo !== undefined && fields.tipo !== current.Tipo && (await isInUse(id))) {
        throw new AppError(409, 'IN_USE', 'Não é possível mudar o tipo de uma categoria já utilizada.');
      }

      const update = buildUpdate('categorias', id, fields, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'categorias', update, id, expected, 'Categoria não encontrada.');
      res.json(mapCategoria(await fetchRow(id)));
    }),
  );

  router.delete(
    '/:id',
    requireAccess('categorias', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      assertEditable(await fetchRow(id));
      if (await isInUse(id)) {
        throw new AppError(409, 'IN_USE', 'A categoria está em uso por dívidas ou movimentações.');
      }
      await softDelete(pool, 'categorias', id, 'Categoria não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
