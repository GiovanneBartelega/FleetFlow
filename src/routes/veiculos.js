import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { mapVeiculo } from '../mappers.js';
import { requireAccess } from '../permissions.js';
import {
  dateOnly,
  intNonNegative,
  intPositive,
  pageQuery,
  positiveDecimal,
  search,
  syncVersion,
  uuid,
} from '../schemas.js';
import { buildUpdate, likeTerm, pageResult, pagination, runUpdate, softDelete } from '../sql.js';

const STATUS = ['Disponivel', 'EmViagem', 'EmManutencao', 'Inativo'];

// Aceita AAA0A00 (Mercosul), AAA0000 e AAA-0000; guarda sem hífen e em maiúsculas.
const placa = z
  .string()
  .trim()
  .transform((value) => value.toUpperCase().replace(/-/g, ''))
  .refine((value) => /^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$/.test(value), 'Placa inválida (use AAA0A00 ou AAA-0000)');

const listQuery = pageQuery.extend({
  status: z.enum(STATUS).optional(),
  busca: search.optional(),
  licenciamentoAte: dateOnly.optional(),
  seguroAte: dateOnly.optional(),
});

const fields = {
  placa,
  marca: z.string().trim().min(1).max(60),
  modelo: z.string().trim().min(1).max(60),
  anoFabricacao: z.number().int().min(1950).max(2100),
  capacidadeCargaToneladas: positiveDecimal(6, 2),
  status: z.enum(STATUS),
  hodometroAtual: intNonNegative,
  intervaloManutencaoKm: intPositive.nullable(),
  intervaloManutencaoMeses: intPositive.nullable(),
  hodometroUltimaManutencao: intNonNegative.nullable(),
  vencimentoLicenciamento: dateOnly.nullable(),
  vencimentoSeguro: dateOnly.nullable(),
  consumoMedioReferenciaKmL: positiveDecimal(3, 2).nullable(),
};

const createBody = z.object({
  ...fields,
  status: fields.status.default('Disponivel'),
  hodometroAtual: fields.hodometroAtual.default(0),
  intervaloManutencaoKm: fields.intervaloManutencaoKm.optional(),
  intervaloManutencaoMeses: fields.intervaloManutencaoMeses.optional(),
  hodometroUltimaManutencao: fields.hodometroUltimaManutencao.optional(),
  vencimentoLicenciamento: fields.vencimentoLicenciamento.optional(),
  vencimentoSeguro: fields.vencimentoSeguro.optional(),
  consumoMedioReferenciaKmL: fields.consumoMedioReferenciaKmL.optional(),
});

const updateBody = z.object(fields).partial().extend({ syncVersion: syncVersion.optional() });

const COLUMNS = {
  placa: 'Placa',
  marca: 'Marca',
  modelo: 'Modelo',
  anoFabricacao: 'AnoFabricacao',
  capacidadeCargaToneladas: 'CapacidadeCargaToneladas',
  status: 'Status',
  hodometroAtual: 'HodometroAtual',
  intervaloManutencaoKm: 'IntervaloManutencaoKm',
  intervaloManutencaoMeses: 'IntervaloManutencaoMeses',
  hodometroUltimaManutencao: 'HodometroUltimaManutencao',
  vencimentoLicenciamento: 'VencimentoLicenciamento',
  vencimentoSeguro: 'VencimentoSeguro',
  consumoMedioReferenciaKmL: 'ConsumoMedioReferenciaKmL',
};

export function veiculosRouter({ pool }) {
  const router = Router();
  const idParam = (req) => uuid.parse(req.params.id);

  const fetchRow = async (id) => {
    const [rows] = await pool.query('SELECT * FROM veiculos WHERE Id = ? AND Ativo = 1', [id]);
    if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', 'Veículo não encontrado.');
    return rows[0];
  };

  // GET /api/veiculos — lista paginada. licenciamentoAte/seguroAte listam os
  // veículos com vencimento até a data informada (base para alertas).
  router.get(
    '/',
    requireAccess('veiculos', 'read'),
    asyncHandler(async (req, res) => {
      const q = listQuery.parse(req.query);
      const where = ['Ativo = 1'];
      const params = [];
      if (q.status) { where.push('Status = ?'); params.push(q.status); }
      if (q.licenciamentoAte) { where.push('VencimentoLicenciamento <= ?'); params.push(q.licenciamentoAte); }
      if (q.seguroAte) { where.push('VencimentoSeguro <= ?'); params.push(q.seguroAte); }
      if (q.busca) {
        const term = likeTerm(q.busca);
        where.push('(Placa LIKE ? OR Marca LIKE ? OR Modelo LIKE ?)');
        params.push(term, term, term);
      }
      const clause = where.join(' AND ');
      const { limit, offset } = pagination(q);

      const [[{ total }]] = await pool.query(`SELECT COUNT(*) AS total FROM veiculos WHERE ${clause}`, params);
      const [rows] = await pool.query(
        `SELECT * FROM veiculos WHERE ${clause} ORDER BY Placa LIMIT ? OFFSET ?`,
        [...params, limit, offset],
      );
      res.json(pageResult(rows.map(mapVeiculo), Number(total), q));
    }),
  );

  router.get(
    '/:id',
    requireAccess('veiculos', 'read'),
    asyncHandler(async (req, res) => res.json(mapVeiculo(await fetchRow(idParam(req))))),
  );

  router.post(
    '/',
    requireAccess('veiculos', 'write'),
    asyncHandler(async (req, res) => {
      const body = createBody.parse(req.body);
      const id = randomUUID();
      await pool.query(
        `INSERT INTO veiculos
           (Id, Placa, Marca, Modelo, AnoFabricacao, CapacidadeCargaToneladas, Status, HodometroAtual,
            IntervaloManutencaoKm, IntervaloManutencaoMeses, HodometroUltimaManutencao,
            VencimentoLicenciamento, VencimentoSeguro, ConsumoMedioReferenciaKmL)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          id,
          body.placa,
          body.marca,
          body.modelo,
          body.anoFabricacao,
          body.capacidadeCargaToneladas,
          body.status,
          body.hodometroAtual,
          body.intervaloManutencaoKm ?? null,
          body.intervaloManutencaoMeses ?? null,
          body.hodometroUltimaManutencao ?? null,
          body.vencimentoLicenciamento ?? null,
          body.vencimentoSeguro ?? null,
          body.consumoMedioReferenciaKmL ?? null,
        ],
      );
      res.status(201).json(mapVeiculo(await fetchRow(id)));
    }),
  );

  router.patch(
    '/:id',
    requireAccess('veiculos', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const { syncVersion: expected, ...changes } = updateBody.parse(req.body);
      await fetchRow(id);

      const update = buildUpdate('veiculos', id, changes, COLUMNS, expected);
      if (!update) throw new AppError(400, 'EMPTY_UPDATE', 'Nenhum campo para atualizar.');
      await runUpdate(pool, 'veiculos', update, id, expected, 'Veículo não encontrado.');
      res.json(mapVeiculo(await fetchRow(id)));
    }),
  );

  // Exclusão lógica. Veículo em viagem ou com ordem de serviço em aberto não sai.
  router.delete(
    '/:id',
    requireAccess('veiculos', 'write'),
    asyncHandler(async (req, res) => {
      const id = idParam(req);
      const current = await fetchRow(id);
      if (current.Status === 'EmViagem') {
        throw new AppError(409, 'IN_USE', 'O veículo está em viagem e não pode ser removido.');
      }
      const [[open]] = await pool.query(
        'SELECT COUNT(*) AS total FROM ordens_servico WHERE VeiculoId = ? AND Ativo = 1 AND DataConclusao IS NULL',
        [id],
      );
      if (Number(open.total) > 0) {
        throw new AppError(409, 'IN_USE', 'O veículo tem ordens de serviço em aberto.');
      }
      await softDelete(pool, 'veiculos', id, 'Veículo não encontrado.');
      res.status(204).end();
    }),
  );

  return router;
}
