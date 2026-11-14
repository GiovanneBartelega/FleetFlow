import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { mapRota } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { intPositive, pageQuery, search, syncVersion, uuid } from '../schemas.js';
import { buildUpdate, likeTerm, pageResult, pagination, runUpdate, softDelete } from '../sql.js';

const fields = {
  nome: z.string().trim().min(1).max(100),
  origem: z.string().trim().min(1).max(120),
  destino: z.string().trim().min(1).max(120),
  distanciaEstimadaKm: intPositive,
  descricao: z.string().trim().max(500).nullable(),
};
const createBody = z.object({ ...fields, descricao: fields.descricao.optional() });
const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });
const listQuery = pageQuery.extend({ busca: search.optional() });
const COLUMNS = {
  nome: 'Nome', origem: 'Origem', destino: 'Destino',
  distanciaEstimadaKm: 'DistanciaEstimadaKm', descricao: 'Descricao',
};

export function rotasRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);
  const fetchRow = async (id) => {
    const [rows] = await pool.query('SELECT * FROM rotas WHERE Id = ? AND Ativo = 1', [id]);
    if (!rows[0]) throw new AppError(404, 'NOT_FOUND', 'Rota não encontrada.');
    return rows[0];
  };

  router.get('/', requireAccess('rotas', 'read'), asyncHandler(async (req, res) => {
    const q = listQuery.parse(req.query);
    const where = ['Ativo = 1']; const params = [];
    if (q.busca) {
      const term = likeTerm(q.busca);
      where.push('(Nome LIKE ? OR Origem LIKE ? OR Destino LIKE ?)');
      params.push(term, term, term);
    }
    const clause = where.join(' AND '); const { limit, offset } = pagination(q);
    const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total FROM rotas WHERE ${clause}`, params);
    const [rows] = await pool.query(`SELECT * FROM rotas WHERE ${clause} ORDER BY Nome LIMIT ? OFFSET ?`, [...params, limit, offset]);
    res.json(pageResult(rows.map(mapRota), Number(total), q));
  }));

  router.get('/:id', requireAccess('rotas', 'read'), asyncHandler(async (req, res) => {
    res.json(mapRota(await fetchRow(idParam(req))));
  }));

  router.post('/', requireAccess('rotas', 'write'), asyncHandler(async (req, res) => {
    const body = createBody.parse(req.body); const id = randomUUID();
    await pool.query(
      'INSERT INTO rotas (Id, Nome, Origem, Destino, DistanciaEstimadaKm, Descricao) VALUES (?, ?, ?, ?, ?, ?)',
      [id, body.nome, body.origem, body.destino, body.distanciaEstimadaKm, body.descricao ?? null],
    );
    res.status(201).json(mapRota(await fetchRow(id)));
  }));

  router.patch('/:id', requireAccess('rotas', 'write'), asyncHandler(async (req, res) => {
    const id = idParam(req); const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
    await fetchRow(id);
    const update = buildUpdate('rotas', id, changes, COLUMNS, expected);
    if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
    await runUpdate(pool, 'rotas', update, id, expected, 'Rota não encontrada.');
    res.json(mapRota(await fetchRow(id)));
  }));

  router.delete('/:id', requireAccess('rotas', 'write'), asyncHandler(async (req, res) => {
    const id = idParam(req); await fetchRow(id);
    const [[used]] = await pool.query('SELECT COUNT(*) AS total FROM viagens WHERE RotaId = ? AND Ativo = 1', [id]);
    if (Number(used.total) > 0) throw new AppError(409, 'IN_USE', 'A rota possui viagens e não pode ser removida.');
    await softDelete(pool, 'rotas', id, 'Rota não encontrada.');
    res.status(204).end();
  }));

  return router;
}
