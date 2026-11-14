import 'dotenv/config';
import { createApp } from './app.js';
import { loadConfig } from './config.js';
import { createPool } from './db.js';
import { createGoogleVerifier } from './google.js';

const config = loadConfig();
const pool = createPool(config.db);
const app = createApp({
  pool,
  config,
  verifyGoogleToken: createGoogleVerifier(config.google.clientIds),
});

if (config.devLogin) {
  console.warn('ATENÇÃO: DEV_LOGIN está ativo (POST /api/auth/dev-login). Use só em ambiente local.');
}
if (config.google.clientIds.length === 0) {
  console.warn('ATENÇÃO: GOOGLE_CLIENT_IDS vazio; o login com Google vai responder erro.');
}

const server = app.listen(config.port, () => {
  console.log(`FleetFlow API ouvindo na porta ${config.port} (${config.nodeEnv})`);
});

async function shutdown(signal) {
  console.log(`${signal} recebido, encerrando...`);
  server.close(async () => {
    await pool.end();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
