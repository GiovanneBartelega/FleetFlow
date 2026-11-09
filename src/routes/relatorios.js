import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { requireAccess } from '../permissions.js';
import { AGRUPAMENTOS, REGIMES } from '../queries/relatorios.sql.js';
import { custoKm, rentabilidade } from '../services/relatorios.js';

const data = (campo) =>
  z
    .string({ required_error: `Informe ${campo} (AAAA-MM-DD)`, invalid_type_error: `${campo} inválida` })
    .regex(/^\d{4}-\d{2}-\d{2}$/, `Use o formato AAAA-MM-DD em ${campo}`)
    .refine((valor) => {
      const d = new Date(`${valor}T00:00:00Z`);
      return !Number.isNaN(d.getTime()) && d.toISOString().startsWith(valor);
    }, `${campo} inválida`);

const id = (campo) => z.string().uuid(`${campo} deve ser um UUID válido`).optional();

const query = z
  .object({
    inicio: data('a data inicial (inicio)'),
    fim: data('a data final (fim)'),
    agrupar: z
      .enum(AGRUPAMENTOS, { errorMap: () => ({ message: `agrupar deve ser: ${AGRUPAMENTOS.join(', ')}` }) })
      .default('veiculo'),
    regime: z
      .enum(REGIMES, { errorMap: () => ({ message: `regime deve ser: ${REGIMES.join(', ')}` }) })
      .default('caixa'),
    veiculoId: id('veiculoId'),
    rotaId: id('rotaId'),
    motoristaId: id('motoristaId'),
  })
  .refine((q) => q.fim >= q.inicio, { path: ['fim'], message: 'A data final não pode ser anterior à inicial' });

// Erros 400 em português com o mesmo formato do restante da API.
function parseFiltros(raw) {
  const result = query.safeParse(raw);
  if (result.success) return result.data;
  const details = result.error.issues.map((i) => ({ campo: i.path.join('.'), mensagem: i.message }));
  throw new AppError(400, 'VALIDATION_ERROR', 'Parâmetros inválidos.', details);
}

// Relatórios são somente leitura. Acesso: Administrador, Financeiro e GestorFrota
// (módulo "relatorios" em permissions.js); OperadorMotorista recebe 403.
export function relatoriosRouter({ pool }) {
  const router = Router();

  // GET /api/relatorios/rentabilidade?inicio=&fim=&agrupar=&regime=&veiculoId=&rotaId=&motoristaId=
  router.get(
    '/rentabilidade',
    requireAccess('relatorios', 'read'),
    asyncHandler(async (req, res) => res.json(await rentabilidade(pool, parseFiltros(req.query)))),
  );

  // GET /api/relatorios/custo-km (mesmos parâmetros)
  router.get(
    '/custo-km',
    requireAccess('relatorios', 'read'),
    asyncHandler(async (req, res) => res.json(await custoKm(pool, parseFiltros(req.query)))),
  );

  return router;
}
