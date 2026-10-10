import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import jwt from 'jsonwebtoken';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { AppError, asyncHandler } from '../errors.js';
import { USER_SELECT, mapUsuario } from '../mappers.js';
import { accountProblem } from '../middleware.js';
import { permissionsFor } from '../permissions.js';

const googleBody = z.object({ idToken: z.string().min(20) });
const devBody = z.object({ email: z.string().trim().email() });

// Subjects provisórios (usuários pré-cadastrados) são trocados pelo ID real
// do Google no primeiro login.
const isPlaceholderSubject = (subject) => /^(seed|pre)-/.test(subject);

export function authRouter({ pool, config, verifyGoogleToken, authenticate }) {
  const router = Router();

  if (config.nodeEnv !== 'test') {
    router.use(rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: true, legacyHeaders: false }));
  }

  const findBy = async (column, value) => {
    const [rows] = await pool.query(`${USER_SELECT} WHERE u.${column} = ? LIMIT 1`, [value]);
    return rows[0] ?? null;
  };

  const issueToken = (row) => ({
    token: jwt.sign({ sub: row.Id }, config.jwt.secret, {
      algorithm: 'HS256',
      expiresIn: config.jwt.expiresIn,
    }),
    tokenType: 'Bearer',
    expiresIn: config.jwt.expiresIn,
    usuario: mapUsuario(row),
    permissoes: permissionsFor(row.PerfilNome),
  });

  // POST /api/auth/google  { idToken }
  router.post(
    '/google',
    asyncHandler(async (req, res) => {
      const { idToken } = googleBody.parse(req.body);
      const google = await verifyGoogleToken(idToken);

      if (!google.emailVerified || !google.email) {
        throw new AppError(403, 'EMAIL_NOT_VERIFIED', 'O e-mail da conta Google não é verificado.');
      }
      const email = google.email.toLowerCase();

      let row = await findBy('GoogleSubjectId', google.sub);

      if (!row) {
        const byEmail = await findBy('Email', email);
        if (byEmail) {
          if (!isPlaceholderSubject(byEmail.GoogleSubjectId)) {
            throw new AppError(409, 'EMAIL_IN_USE', 'Este e-mail já está vinculado a outra conta Google.');
          }
          await pool.query('UPDATE usuarios SET GoogleSubjectId = ? WHERE Id = ?', [google.sub, byEmail.Id]);
          row = await findBy('Id', byEmail.Id);
        }
      }

      if (!row) {
        const [perfis] = await pool.query('SELECT Id FROM perfis WHERE Nome = ? AND Ativo = 1', [
          config.defaultProfileName,
        ]);
        if (perfis.length === 0) {
          throw new AppError(500, 'CONFIG_ERROR', `Perfil padrão "${config.defaultProfileName}" não existe.`);
        }
        const id = randomUUID();
        await pool.query(
          `INSERT INTO usuarios (Id, Nome, Email, GoogleSubjectId, PerfilId, Status, FotoUrl)
           VALUES (?, ?, ?, ?, ?, 'AguardandoAprovacao', ?)`,
          [id, google.nome.slice(0, 120), email.slice(0, 180), google.sub, perfis[0].Id, google.foto],
        );
        row = await findBy('Id', id);
      } else if (google.foto && google.foto !== row.FotoUrl) {
        await pool.query('UPDATE usuarios SET FotoUrl = ? WHERE Id = ?', [google.foto, row.Id]);
        row.FotoUrl = google.foto;
      }

      // Conta pendente, bloqueada ou desativada: nenhum token é emitido.
      const problem = accountProblem(row);
      if (problem) throw problem;

      res.json(issueToken(row));
    }),
  );

  // Login sem Google para testar permissões em ambiente local.
  // Só existe com DEV_LOGIN=true e NODE_ENV diferente de production.
  if (config.devLogin) {
    router.post(
      '/dev-login',
      asyncHandler(async (req, res) => {
        const { email } = devBody.parse(req.body);
        const row = await findBy('Email', email.toLowerCase());
        if (!row) throw new AppError(404, 'USER_NOT_FOUND', 'Usuário não encontrado.');
        const problem = accountProblem(row);
        if (problem) throw problem;
        res.json(issueToken(row));
      }),
    );
  }

  // GET /api/auth/me — dados do usuário e matriz de permissões do perfil,
  // para o app montar a navegação dinâmica.
  router.get(
    '/me',
    authenticate,
    asyncHandler(async (req, res) => {
      const row = await findBy('Id', req.user.id);
      res.json({ usuario: mapUsuario(row), permissoes: permissionsFor(req.user.perfil) });
    }),
  );

  return router;
}
