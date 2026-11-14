import { Router } from 'express';
import { z } from 'zod';
import { asyncHandler } from '../errors.js';
import { alertasDoVeiculo } from '../frotaRegras.js';
import { requireAccess } from '../permissions.js';

const query = z.object({ diasAntecedencia: z.coerce.number().int().min(0).max(365).default(30) });

export function alertasRouter({ pool }) {
  const router = Router();
  router.get('/', requireAccess('alertas', 'read'), asyncHandler(async (req, res) => {
    const { diasAntecedencia } = query.parse(req.query);
    const [vehicles] = await pool.query(
      `SELECT v.*, MAX(o.DataConclusao) AS UltimaManutencaoConcluida
         FROM veiculos v
         LEFT JOIN ordens_servico o ON o.VeiculoId = v.Id AND o.Ativo = 1 AND o.DataConclusao IS NOT NULL
        WHERE v.Ativo = 1 AND v.Status <> 'Inativo'
        GROUP BY v.Id`,
    );
    const data = vehicles.flatMap((vehicle) => alertasDoVeiculo(vehicle, { diasAntecedencia }));
    const ordem = { critico: 0, atencao: 1 };
    data.sort((a, b) => ordem[a.nivel] - ordem[b.nivel] || String(a.vencimento || '').localeCompare(String(b.vencimento || '')));
    res.json({ data, total: data.length, diasAntecedencia });
  }));
  return router;
}
