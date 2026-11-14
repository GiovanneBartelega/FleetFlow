import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { requireAccess } from '../permissions.js';
import { dateOnly, uuid } from '../schemas.js';
import { AGRUPAMENTOS, gerarRelatorio, REGIMES } from '../services/relatorios.js';

const filtrosSchema = z.object({
  inicio: dateOnly, fim: dateOnly,
  agrupar: z.enum(AGRUPAMENTOS).default('veiculo'),
  regime: z.enum(REGIMES).default('caixa'),
  veiculoId: uuid.optional(), rotaId: uuid.optional(), motoristaId: uuid.optional(),
}).refine((dados) => dados.fim >= dados.inicio, { path: ['fim'], message: 'A data final não pode ser anterior à inicial.' });

export function relatoriosRouter({ pool }) {
  const router = Router();
  const responder = async (req, res) => {
    const parse = filtrosSchema.safeParse(req.query);
    if (!parse.success) throw new AppError(400, 'VALIDATION_ERROR', 'Parâmetros inválidos.', parse.error.issues);
    res.json(await gerarRelatorio(pool, parse.data));
  };
  router.get('/rentabilidade', requireAccess('relatorios', 'read'), asyncHandler(responder));
  router.get('/custo-km', requireAccess('relatorios', 'read'), asyncHandler(responder));
  return router;
}
