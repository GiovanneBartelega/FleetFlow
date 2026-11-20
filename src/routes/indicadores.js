import { Router } from 'express';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { requireAccess } from '../permissions.js';
import { dateOnly } from '../schemas.js';
import { buscarAlertasSaldo, buscarDespesasPorCategoria, buscarFluxoCaixa, buscarKpis, buscarPrevisaoCaixa, buscarRankings } from '../services/indicadores.js';

const periodoBase = z.object({ inicio: dateOnly, fim: dateOnly });
const comPeriodoValido = (schema) => schema.refine((valor) => valor.fim >= valor.inicio, { path: ['fim'], message: 'A data final não pode ser anterior à inicial.' });
const periodo = comPeriodoValido(periodoBase);
const ranking = comPeriodoValido(periodoBase.extend({ limite: z.coerce.number().int().min(1).max(20).default(5) }));
const previsao = z.object({ inicio: dateOnly.optional(), meses: z.coerce.number().int().min(1).max(12).default(12) });

function validar(schema, query) {
  const resultado = schema.safeParse(query);
  if (resultado.success) return resultado.data;
  throw new AppError(400, 'VALIDATION_ERROR', 'Parâmetros inválidos.', resultado.error.issues.map((item) => ({ campo: item.path.join('.'), mensagem: item.message })));
}

function hoje() { return new Date().toISOString().slice(0, 10); }

export function indicadoresRouter({ pool }) {
  const router = Router();
  router.get('/kpis', requireAccess('indicadores', 'read'), asyncHandler(async (req, res) => res.json(await buscarKpis(pool, validar(periodo, req.query)))));
  router.get('/fluxo-caixa', requireAccess('indicadores', 'read'), asyncHandler(async (req, res) => res.json(await buscarFluxoCaixa(pool, validar(periodo, req.query)))));
  router.get('/despesas-por-categoria', requireAccess('indicadores', 'read'), asyncHandler(async (req, res) => res.json(await buscarDespesasPorCategoria(pool, validar(periodo, req.query)))));
  router.get('/rankings', requireAccess('indicadores', 'read'), asyncHandler(async (req, res) => res.json(await buscarRankings(pool, validar(ranking, req.query)))));
  router.get('/previsao-caixa', requireAccess('indicadores', 'read'), asyncHandler(async (req, res) => {
    const filtros = validar(previsao, req.query);
    res.json(await buscarPrevisaoCaixa(pool, { ...filtros, inicio: filtros.inicio ?? hoje() }));
  }));
  router.get('/alertas-saldo', requireAccess('indicadores', 'read'), asyncHandler(async (req, res) => {
    const filtros = validar(previsao, req.query);
    res.json(await buscarAlertasSaldo(pool, { ...filtros, inicio: filtros.inicio ?? hoje() }));
  }));
  return router;
}
