import swaggerUi from 'swagger-ui-express';
import { comprovantesRouter } from './routes/comprovantes.js';
import { readFileSync } from 'node:fs';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { asyncHandler } from './errors.js';
import { authenticate, errorHandler, notFound } from './middleware.js';
import { authRouter } from './routes/auth.js';
import { categoriasRouter } from './routes/categorias.js';
import { dividasRouter } from './routes/dividas.js';
import { formasPagamentoRouter } from './routes/formasPagamento.js';
import { movimentacoesRouter } from './routes/movimentacoes.js';
import { perfisRouter, usuariosRouter } from './routes/usuarios.js';
import { rotasRouter } from './routes/rotas.js';
import { viagensRouter } from './routes/viagens.js';
import { veiculosRouter } from './routes/veiculos.js';
import { ordensServicoRouter } from './routes/ordensServico.js';
import { checklistsViagemRouter } from './routes/checklistsViagem.js';
import { itensChecklistRouter } from './routes/itensChecklist.js';

// `pool` e `verifyGoogleToken` são injetados para a API poder ser testada
// sem MySQL e sem chamar o Google.
export function createApp({ pool, config, verifyGoogleToken, logger = console }) {
  const app = express();

  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', config.trustProxy);

  app.use(helmet({contentSecurityPolicy:{directives:{'script-src':["'self'"], 'img-src':["'self'",'data:'], 'style-src':["'self'","'unsafe-inline'"]}}}));
  if (config.corsOrigins.length > 0) app.use(cors({ origin: config.corsOrigins }));
  app.use(express.json({ limit: '100kb' }));

  app.get(
    '/health',
    asyncHandler(async (req, res) => {
      await pool.query('SELECT 1');
      res.json({ status: 'ok' });
    }),
  );

  const auth = authenticate({ pool, config });
  const deps = { pool, config };

  app.use('/api/auth', authRouter({ ...deps, verifyGoogleToken, authenticate: auth }));
  app.use('/api/perfis', auth, perfisRouter(deps));
  app.use('/api/usuarios', auth, usuariosRouter(deps));
  app.use('/api/categorias', auth, categoriasRouter(deps));
  app.use('/api/formas-pagamento', auth, formasPagamentoRouter(deps));
  app.use('/api/dividas', auth, dividasRouter(deps));
  app.use('/api/movimentacoes', auth, comprovantesRouter(deps));
  app.use('/api/movimentacoes', auth, movimentacoesRouter(deps));
  app.use('/api/rotas', auth, rotasRouter(deps));
  app.use('/api/viagens', auth, viagensRouter(deps));
  app.use('/api/veiculos', auth, veiculosRouter(deps));
  app.use('/api/ordens-servico', auth, ordensServicoRouter(deps));
  app.get('/openapi.json', (req,res)=>res.json(JSON.parse(readFileSync(new URL('../docs/openapi.json',import.meta.url)))));
app.use('/api/itens-checklist', auth, itensChecklistRouter(deps));
app.use('/api/checklists-viagem', auth, checklistsViagemRouter(deps));

  const spec=JSON.parse(readFileSync(new URL('../docs/openapi.json',import.meta.url)));
  app.use('/docs', swaggerUi.serve, swaggerUi.setup(spec,{swaggerOptions:{persistAuthorization:false}}));
  app.use(notFound);
  app.use(errorHandler(logger));
  return app;
}
