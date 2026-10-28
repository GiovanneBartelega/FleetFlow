import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { VIAGEM_SELECT, mapViagem } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import { dateTime, intNonNegative, pageQuery, syncVersion, uuid } from '../schemas.js';
import { pageResult, pagination } from '../sql.js';

const listQuery = pageQuery.extend({
  rotaId: uuid.optional(), veiculoId: uuid.optional(), motoristaId: uuid.optional(),
  status: z.enum(['Aberta', 'Encerrada']).optional(),
});
const abrirBody = z.object({
  rotaId: uuid,
  veiculoId: uuid,
  motoristaId: uuid.optional(),
  hodometroInicial: intNonNegative,
  dataAbertura: dateTime.optional(),
  observacao: z.string().trim().max(500).nullable().optional(),
});
const fecharBody = z.object({
  hodometroFinal: intNonNegative,
  dataFechamento: dateTime.optional(),
  observacao: z.string().trim().max(500).nullable().optional(),
  syncVersion: syncVersion.optional(),
});

const nowUtc = () => new Date().toISOString().slice(0, 19).replace('T', ' ');

export function viagensRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);
  const canManageAny = (user) => ['Administrador', 'GestorFrota'].includes(user.perfil);
  const fetchRow = async (id) => {
    const [rows] = await pool.query(`${VIAGEM_SELECT} WHERE t.Id = ? AND t.Ativo = 1`, [id]);
    if (!rows[0]) throw new AppError(404, 'NOT_FOUND', 'Viagem não encontrada.');
    return rows[0];
  };
  const assertActive = async (connection, table, id, label) => {
    const [rows] = await connection.query(`SELECT Id FROM ${table} WHERE Id = ? AND Ativo = 1`, [id]);
    if (!rows[0]) throw new AppError(400, 'INVALID_REFERENCE', `${label} não encontrada ou inativa.`);
  };

  router.get('/', requireAccess('viagens', 'read'), asyncHandler(async (req, res) => {
    const q = listQuery.parse(req.query); const where = ['t.Ativo = 1']; const params = [];
    const add = (condition, value) => { where.push(condition); params.push(value); };
    if (q.rotaId) add('t.RotaId = ?', q.rotaId);
    if (q.veiculoId) add('t.VeiculoId = ?', q.veiculoId);
    if (q.motoristaId) add('t.MotoristaId = ?', q.motoristaId);
    if (q.status) add('t.Status = ?', q.status);
    if (req.user.perfil === 'OperadorMotorista') add('t.MotoristaId = ?', req.user.id);
    const clause = where.join(' AND '); const { limit, offset } = pagination(q);
    const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total FROM viagens t WHERE ${clause}`, params);
    const [rows] = await pool.query(`${VIAGEM_SELECT} WHERE ${clause} ORDER BY t.DataAbertura DESC LIMIT ? OFFSET ?`, [...params, limit, offset]);
    res.json(pageResult(rows.map(mapViagem), Number(total), q));
  }));

  router.get('/:id', requireAccess('viagens', 'read'), asyncHandler(async (req, res) => {
    const row = await fetchRow(idParam(req));
    if (req.user.perfil === 'OperadorMotorista' && row.MotoristaId !== req.user.id) throw new AppError(403, 'FORBIDDEN', 'Você só pode consultar as próprias viagens.');
    res.json(mapViagem(row));
  }));

  // Abre uma viagem e reserva o veículo. O hodômetro inicial nunca pode andar para trás.
  router.post('/abrir', requireAccess('viagens', 'write'), asyncHandler(async (req, res) => {
    const body = abrirBody.parse(req.body);
    const motoristaId = req.user.perfil === 'OperadorMotorista' ? req.user.id : (body.motoristaId ?? req.user.id);
    if (req.user.perfil === 'OperadorMotorista' && body.motoristaId && body.motoristaId !== req.user.id) {
      throw new AppError(403, 'FORBIDDEN', 'O motorista só pode abrir viagem para si mesmo.');
    }
    const id = randomUUID(); const connection = await pool.getConnection();
    try {
      await connection.beginTransaction();
      await assertActive(connection, 'rotas', body.rotaId, 'Rota');
      await assertActive(connection, 'usuarios', motoristaId, 'Motorista');
      const [vehicles] = await connection.query('SELECT * FROM veiculos WHERE Id = ? AND Ativo = 1 FOR UPDATE', [body.veiculoId]);
      const vehicle = vehicles[0];
      if (!vehicle) throw new AppError(400, 'INVALID_REFERENCE', 'Veículo não encontrado ou inativo.');
      if (vehicle.Status !== 'Disponivel') throw new AppError(409, 'VEHICLE_UNAVAILABLE', 'O veículo não está disponível para abrir uma viagem.');
      if (body.hodometroInicial < Number(vehicle.HodometroAtual)) {
        throw new AppError(400, 'INVALID_ODOMETER', 'O hodômetro inicial não pode ser menor que o hodômetro atual do veículo.');
      }
      const [[open]] = await connection.query("SELECT COUNT(*) AS total FROM viagens WHERE VeiculoId = ? AND Ativo = 1 AND Status = 'Aberta' FOR UPDATE", [body.veiculoId]);
      if (Number(open.total) > 0) throw new AppError(409, 'OPEN_TRIP_EXISTS', 'O veículo já possui uma viagem em aberto.');
      await connection.query(
        `INSERT INTO viagens (Id, RotaId, VeiculoId, MotoristaId, HodometroInicial, DataAbertura, Status, Observacao)
         VALUES (?, ?, ?, ?, ?, ?, 'Aberta', ?)`,
        [id, body.rotaId, body.veiculoId, motoristaId, body.hodometroInicial, body.dataAbertura ?? nowUtc(), body.observacao ?? null],
      );
      await connection.query("UPDATE veiculos SET Status = 'EmViagem', HodometroAtual = ? WHERE Id = ?", [body.hodometroInicial, body.veiculoId]);
      await connection.commit();
    } catch (error) { await connection.rollback(); throw error; } finally { connection.release(); }
    res.status(201).json(mapViagem(await fetchRow(id)));
  }));

  // Fecha a viagem e atualiza o hodômetro do veículo na mesma transação.
  router.post('/:id/fechar', requireAccess('viagens', 'write'), asyncHandler(async (req, res) => {
    const id = idParam(req); const body = fecharBody.parse(req.body); const connection = await pool.getConnection();
    try {
      await connection.beginTransaction();
      const [trips] = await connection.query('SELECT * FROM viagens WHERE Id = ? AND Ativo = 1 FOR UPDATE', [id]);
      const trip = trips[0];
      if (!trip) throw new AppError(404, 'NOT_FOUND', 'Viagem não encontrada.');
      if (!canManageAny(req.user) && trip.MotoristaId !== req.user.id) throw new AppError(403, 'FORBIDDEN', 'Você só pode fechar as próprias viagens.');
      if (trip.Status !== 'Aberta') throw new AppError(409, 'TRIP_ALREADY_CLOSED', 'Esta viagem já foi encerrada.');
      const [vehicles] = await connection.query('SELECT * FROM veiculos WHERE Id = ? AND Ativo = 1 FOR UPDATE', [trip.VeiculoId]);
      const vehicle = vehicles[0];
      if (!vehicle) throw new AppError(409, 'VEHICLE_UNAVAILABLE', 'O veículo desta viagem não está mais ativo.');
      if (body.hodometroFinal < Number(trip.HodometroInicial) || body.hodometroFinal < Number(vehicle.HodometroAtual)) {
        throw new AppError(400, 'INVALID_ODOMETER', 'O hodômetro final não pode ser menor que o hodômetro inicial ou o atual do veículo.');
      }
      const closeAt = body.dataFechamento ?? nowUtc();
      if (closeAt < trip.DataAbertura) throw new AppError(400, 'INVALID_DATE', 'O fechamento não pode ser anterior à abertura da viagem.');
      let sql = "UPDATE viagens SET HodometroFinal = ?, DataFechamento = ?, Status = 'Encerrada'";
      const params = [body.hodometroFinal, closeAt];
      if (body.observacao !== undefined) { sql += ', Observacao = ?'; params.push(body.observacao); }
      sql += ' WHERE Id = ? AND Ativo = 1 AND Status = \'Aberta\''; params.push(id);
      if (body.syncVersion !== undefined) { sql += ' AND SyncVersion = ?'; params.push(body.syncVersion); }
      const [updated] = await connection.query(sql, params);
      if (updated.affectedRows === 0) throw new AppError(409, 'VERSION_CONFLICT', 'A viagem foi alterada por outra pessoa. Recarregue e tente de novo.');
      await connection.query("UPDATE veiculos SET HodometroAtual = ?, Status = 'Disponivel' WHERE Id = ?", [body.hodometroFinal, trip.VeiculoId]);
      await connection.commit();
    } catch (error) { await connection.rollback(); throw error; } finally { connection.release(); }
    res.json(mapViagem(await fetchRow(id)));
  }));
  return router;
}
