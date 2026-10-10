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

// `pool` e `verifyGoogleToken` são injetados para a API poder ser testada
// sem MySQL e sem chamar o Google.
export function createApp({ pool, config, verifyGoogleToken, logger = console }) {
  const app = express();

  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', config.trustProxy);

  app.use(helmet());
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
  app.use('/api/movimentacoes', auth, movimentacoesRouter(deps));

  app.use(notFound);
  app.use(errorHandler(logger));
  return app;
}
