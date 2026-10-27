import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { mapRota } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { pageQuery, positiveDecimal, search, syncVersion, uuid } from '../schemas.js';
import { buildUpdate, likeTerm, pageResult, pagination, runUpdate, softDelete } from '../sql.js';

const uf = z
  .string()
  .trim()
  .length(2, 'Use a sigla da UF com 2 letras')
  .transform((value) => value.toUpperCase());

const listQuery = pageQuery.extend({
  ufOrigem: uf.optional(),
  ufDestino: uf.optional(),
  busca: search.optional(),
});

const fields = {
  nome: z.string().trim().min(1).max(120),
  cidadeOrigem: z.string().trim().min(1).max(100),
  ufOrigem: uf,
  cidadeDestino: z.string().trim().min(1).max(100),
  ufDestino: uf,
  distanciaEstimadaKm: positiveDecimal(7, 2),
};

const createBody = z.object(fields);
const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const COLUMNS = {
  nome: 'Nome',
  cidadeOrigem: 'CidadeOrigem',
  ufOrigem: 'UfOrigem',
  cidadeDestino: 'CidadeDestino',
  ufDestino: 'UfDestino',
  distanciaEstimadaKm: 'DistanciaEstimadaKm',
};

export function rotasRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query('SELECT * FROM rotas WHERE Id = ? AND Ativo = 1', [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Rota não encontrada.');
    return rows[0];
  };

  router.get(
    '/',
    requireAccess('rotas', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['Ativo = 1'];
      const params = [];
      if (q.ufOrigem) { where.push('UfOrigem = ?'); params.push(q.ufOrigem); }
      if (q.ufDestino) { where.push('UfDestino = ?'); params.push(q.ufDestino); }
      if (q.busca) {
        const term = likeTerm(q.busca);
        where.push('(Nome LIKE ? OR CidadeOrigem LIKE ? OR CidadeDestino LIKE ?)');
        params.push(term, term, term);
      }
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);

      const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total FROM rotas WHERE ${clause}`, params);
      const [rows] = await pool.query(
        `SELECT * FROM rotas WHERE ${clause} ORDER BY Nome LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      res.json(pageResult(rows.map(mapRota), Number(total), q));
    }),
  );

  router.get(
    '/:id',
    requireAccess('rotas', 'read'),
    asyncHandler(async (req, res) => res.json(mapRota(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('rotas', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      const id = randomUUID();
      await pool.query(
        `INSERT INTO rotas
           (Id, Nome, CidadeOrigem, UfOrigem, CidadeDestino, UfDestino, DistanciaEstimadaKm)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [id, body.nome, body.cidadeOrigem, body.ufOrigem, body.cidadeDestino, body.ufDestino, body.distanciaEstimadaKm],
      );
      res.status(201).json(mapRota(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('rotas', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      await fetchRow(id);

      const update = buildUpdate('rotas', id, changes, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'rotas', update, id, expected, 'Rota não encontrada.');
      res.json(mapRota(await fetchRow(id)));
    }),
  );

  // Exclusão lógica. Rota com viagem em andamento não sai.
  router.delete(
    '/:id',
    requireAccess('rotas', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      await fetchRow(id);
      const [[open]] = await pool.query(
        "SELECT COUNT(*) AS total FROM viagens WHERE RotaId = ? AND Ativo = 1 AND Status = 'EmAndamento'",
        [id],
      );
      if (Number(open.total) > 0) {
        throw new AppError(409, 'IN_USE', 'A rota tem viagens em andamento e não pode ser removida.');
      }
      await softDelete(pool, 'rotas', id, 'Rota não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
