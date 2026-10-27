import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { VIAGEM_SELECT, mapViagem } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { intNonNegative, pageQuery, syncVersion, uuid } from '../schemas.js';
import { assertActive, pageResult, pagination, softDelete } from '../sql.js';

const STATUS = ['EmAndamento', 'Concluida', 'Cancelada'];

const observacao = z.string().trim().max(400);

const listQuery = pageQuery.extend({
  status: z.enum(STATUS).optional(),
  veiculoId: uuid.optional(),
  rotaId: uuid.optional(),
  motoristaId: uuid.optional(),
});

const createBody = z.object({
  veiculoId: uuid,
  rotaId: uuid,
  motoristaId: uuid,
  // Se omitido, usa o hodômetro atual do veículo.
  hodometroInicial: intNonNegative.optional(),
  observacao: observacao.nullable().optional(),
});

const updateBody = z.object({
  observacao: observacao.nullable(),
  syncVersion: syncVersion.optional(),
});

const concluirBody = z.object({
  hodometroFinal: intNonNegative,
  observacao: observacao.nullable().optional(),
  syncVersion: syncVersion.optional(),
});

const cancelarBody = z.object({
  observacao: observacao.nullable().optional(),
  syncVersion: syncVersion.optional(),
});

export function viagensRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query(`${VIAGEM_SELECT} WHERE vi.Id = ? AND vi.Ativo = 1`, [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Viagem não encontrada.');
    return rows[0];
  };

  const assertEmAndamento = (row) => {
    if (row.Status !== 'EmAndamento') {
      throw new AppError(409, 'TRIP_CLOSED', 'Esta viagem já foi encerrada.');
    }
  };

  // Fecha a viagem e libera o veículo. O UPDATE só casa se a viagem ainda está
  // em andamento (e, se informado, na SyncVersion esperada), então duas
  // requisições simultâneas não encerram a mesma viagem duas vezes.
  const encerrar = async (id, expected, sets, params, veiculoSql, veiculoParams) => {
    let sql = `UPDATE viagens SET ${sets.join(', ')}, DataFim = CURRENT_TIMESTAMP
               WHERE Id = ? AND Ativo = 1 AND Status = 'EmAndamento'`;
    const values = [...params, id];
    if (expected !== undefined) {
      sql += ' AND SyncVersion = ?';
      values.push(expected);
    }
    const [result] = await pool.query(sql, values);
    if (result.affectedRows === 0) {
      assertEmAndamento(await fetchRow(id));
      throw new AppError(
        409,
        'VERSION_CONFLICT',
        'Este registro foi alterado por outra pessoa. Recarregue e tente de novo.',
      );
    }
    await pool.query(veiculoSql, veiculoParams);
  };

  // GET /api/viagens — lista paginada, mais recentes primeiro.
  router.get(
    '/',
    requireAccess('viagens', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['vi.Ativo = 1'];
      const params = [];
      const add = (condition, value) => { where.push(condition); params.push(value); };

      if (q.status) add('vi.Status = ?', q.status);
      if (q.veiculoId) add('vi.VeiculoId = ?', q.veiculoId);
      if (q.rotaId) add('vi.RotaId = ?', q.rotaId);
      if (q.motoristaId) add('vi.MotoristaId = ?', q.motoristaId);
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);

      const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total FROM viagens vi WHERE ${clause}`, params);
      const [rows] = await pool.query(
        `${VIAGEM_SELECT} WHERE ${clause} ORDER BY vi.DataInicio DESC LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      res.json(pageResult(rows.map(mapViagem), Number(total), q));
    }),
  );

  router.get(
    '/:id',
    requireAccess('viagens', 'read'),
    asyncHandler(async (req, res) => res.json(mapViagem(await fetchRow(idParam(req))))),
  );

  // POST /api/viagens — inicia a viagem e marca o veículo como EmViagem.
  router.post(
    '/',
    requireAccess('viagens', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      await assertActive(pool, 'rotas', body.rotaId, 'rotaId', 'Rota');

      const [motoristas] = await pool.query(
        "SELECT Id FROM usuarios WHERE Id = ? AND Ativo = 1 AND Status = 'Ativo'",
        [body.motoristaId],
      );
      if (motoristas.length === 0) {
        throw new AppError(400, 'INVALID_REFERENCE', 'Motorista não encontrado ou inativo.', [
          { campo: 'motoristaId', mensagem: 'Motorista não encontrado ou inativo' },
        ]);
      }

      const [veiculos] = await pool.query('SELECT Status, HodometroAtual FROM veiculos WHERE Id = ? AND Ativo = 1', [
        body.veiculoId,
      ]);
      if (veiculos.length === 0) {
        throw new AppError(400, 'INVALID_REFERENCE', 'Veículo não encontrado ou inativo.', [
          { campo: 'veiculoId', mensagem: 'Veículo não encontrado ou inativo' },
        ]);
      }
      const hodometroInicial = body.hodometroInicial ?? veiculos[0].HodometroAtual;

      // Reserva o veículo de forma atômica: só um pedido consegue passar de Disponivel para EmViagem.
      const [claim] = await pool.query(
        "UPDATE veiculos SET Status = 'EmViagem' WHERE Id = ? AND Ativo = 1 AND Status = 'Disponivel'",
        [body.veiculoId],
      );
      if (claim.affectedRows === 0) {
        throw new AppError(409, 'VEHICLE_UNAVAILABLE', 'O veículo não está disponível para viagem.');
      }

      const id = randomUUID();
      try {
        await pool.query(
          `INSERT INTO viagens (Id, VeiculoId, RotaId, MotoristaId, HodometroInicial, Observacao)
           VALUES (?, ?, ?, ?, ?, ?)`,
          [id, body.veiculoId, body.rotaId, body.motoristaId, hodometroInicial, body.observacao ?? null],
        );
      } catch (error) {
        await pool.query("UPDATE veiculos SET Status = 'Disponivel' WHERE Id = ? AND Status = 'EmViagem'", [
          body.veiculoId,
        ]);
        throw error;
      }
      res.status(201).json(mapViagem(await fetchRow(id)));
    }),
  );

  // PATCH /api/viagens/:id — só a observação é editável; o fluxo muda por /concluir e /cancelar.
  router.patch(
    '/:id',
    requireAccess('viagens', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, observacao: texto } = updateBody.parse(req.body);
      assertEmAndamento(await fetchRow(id));

      let sql = "UPDATE viagens SET Observacao = ? WHERE Id = ? AND Ativo = 1 AND Status = 'EmAndamento'";
      const params = [texto, id];
      if (expected !== undefined) {
        sql += ' AND SyncVersion = ?';
        params.push(expected);
      }
      const [result] = await pool.query(sql, params);
      if (result.affectedRows === 0) {
        throw new AppError(
          409,
          'VERSION_CONFLICT',
          'Este registro foi alterado por outra pessoa. Recarregue e tente de novo.',
        );
      }
      res.json(mapViagem(await fetchRow(id)));
    }),
  );

  // POST /api/viagens/:id/concluir  { hodometroFinal }
  // Calcula KmRodados, grava DataFim, libera o veículo e atualiza o hodômetro dele.
  router.post(
    '/:id/concluir',
    requireAccess('viagens', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, hodometroFinal, observacao: texto } = concluirBody.parse(req.body);
      const current = await fetchRow(id);
      assertEmAndamento(current);

      if (hodometroFinal < current.HodometroInicial) {
        throw new AppError(400, 'VALIDATION_ERROR', 'Dados inválidos.', [
          { campo: 'hodometroFinal', mensagem: 'O hodômetro final não pode ser menor que o inicial' },
        ]);
      }

      const sets = ["Status = 'Concluida'", 'HodometroFinal = ?', 'KmRodados = ?'];
      const params = [hodometroFinal, hodometroFinal - current.HodometroInicial];
      if (texto !== undefined) {
        sets.push('Observacao = ?');
        params.push(texto);
      }
      await encerrar(
        id,
        expected,
        sets,
        params,
        `UPDATE veiculos SET Status = 'Disponivel', HodometroAtual = GREATEST(HodometroAtual, ?)
         WHERE Id = ? AND Status = 'EmViagem'`,
        [hodometroFinal, current.VeiculoId],
      );
      res.json(mapViagem(await fetchRow(id)));
    }),
  );

  // POST /api/viagens/:id/cancelar — encerra sem quilometragem e libera o veículo.
  router.post(
    '/:id/cancelar',
    requireAccess('viagens', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, observacao: texto } = cancelarBody.parse(req.body);
      const current = await fetchRow(id);
      assertEmAndamento(current);

      const sets = ["Status = 'Cancelada'"];
      const params = [];
      if (texto !== undefined) {
        sets.push('Observacao = ?');
        params.push(texto);
      }
      await encerrar(
        id,
        expected,
        sets,
        params,
        "UPDATE veiculos SET Status = 'Disponivel' WHERE Id = ? AND Status = 'EmViagem'",
        [current.VeiculoId],
      );
      res.json(mapViagem(await fetchRow(id)));
    }),
  );

  // Exclusão lógica. Viagem em andamento precisa ser concluída ou cancelada antes.
  router.delete(
    '/:id',
    requireAccess('viagens', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const current = await fetchRow(id);
      if (current.Status === 'EmAndamento') {
        throw new AppError(409, 'IN_USE', 'Conclua ou cancele a viagem antes de removê-la.');
      }
      await softDelete(pool, 'viagens', id, 'Viagem não encontrada.');
      res.status(204).end();
    }),
  );

  return router;
}
