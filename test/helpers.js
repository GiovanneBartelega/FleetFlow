import jwt from 'jsonwebtoken';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';

export const SECRET = 'x'.repeat(48);
export const config = loadConfig({ NODE_ENV: 'test', JWT_SECRET: SECRET });

export const PERFIS = ['Administrador', 'Financeiro', 'GestorFrota', 'OperadorMotorista'];

export function makeUser({ id, perfil, status = 'Ativo', ativo = 1, subject, email } = {}) {
  return {
    Id: id,
    CriadoEm: '2026-10-01 10:00:00',
    AtualizadoEm: '2026-10-01 10:00:00',
    Ativo: ativo,
    SyncVersion: 1,
    Nome: `Teste ${perfil}`,
    Email: email ?? `${id}@exemplo.com`,
    GoogleSubjectId: subject ?? `seed-${id}`,
    PerfilId: `perfil-${perfil}`,
    Status: status,
    FotoUrl: null,
    CategoriaCnh: null,
    ValidadeCnh: null,
    PerfilNome: perfil,
    PerfilAtivo: 1,
  };
}

// Pool falso: responde às consultas de usuário a partir de uma lista em memória
// e devolve resultados vazios para o resto. `handlers` permite sobrescrever.
export function makeFakePool({ users = [], handlers = [] } = {}) {
  const calls = [];
  return {
    users,
    calls,
    async query(sql, params = []) {
      const text = sql.replace(/\s+/g, ' ').trim();
      calls.push({ sql: text, params });

      for (const handler of handlers) {
        const result = handler(text, params);
        if (result !== undefined) return result;
      }
      if (/COUNT\(\*\) AS total/.test(text)) return [[{ total: 0 }]];
      if (/FROM usuarios u JOIN perfis p/.test(text)) {
        let found = users;
        if (/u\.Id = \?/.test(text)) found = users.filter((u) => u.Id === params[0]);
        else if (/u\.GoogleSubjectId = \?/.test(text)) found = users.filter((u) => u.GoogleSubjectId === params[0]);
        else if (/u\.Email = \?/.test(text)) found = users.filter((u) => u.Email === params[0]);
        return [found];
      }
      if (/^(INSERT|UPDATE)/i.test(text)) return [{ affectedRows: 1 }];
      return [[]];
    },
  };
}

export async function startServer({ pool, verifyGoogleToken, appConfig = config } = {}) {
  const app = createApp({
    pool,
    config: appConfig,
    verifyGoogleToken: verifyGoogleToken ?? (async () => { throw new Error('google não deveria ser chamado'); }),
    logger: { error() {} },
  });
  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const base = `http://127.0.0.1:${server.address().port}`;

  async function request(method, path, { token, body } = {}) {
    const response = await fetch(base + path, {
      method,
      headers: {
        ...(token ? { authorization: `Bearer ${token}` } : {}),
        ...(body !== undefined ? { 'content-type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    const text = await response.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { /* corpo vazio ou não JSON */ }
    return { status: response.status, body: json };
  }

  return { request, base, close: () => new Promise((resolve) => server.close(resolve)) };
}

export const tokenFor = (userId) => jwt.sign({ sub: userId }, SECRET, { algorithm: 'HS256', expiresIn: '1h' });
