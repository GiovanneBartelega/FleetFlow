import jwt from 'jsonwebtoken';
import { ZodError } from 'zod';
import { AppError, asyncHandler } from './errors.js';
import { USER_SELECT } from './mappers.js';
import { toBool } from './sql.js';

// Códigos que o app usa para decidir qual tela mostrar.
export function accountProblem(user) {
  if (!user || !toBool(user.Ativo) || !toBool(user.PerfilAtivo)) {
    return new AppError(403, 'ACCOUNT_DISABLED', 'Esta conta está desativada.');
  }
  if (user.Status === 'AguardandoAprovacao') {
    return new AppError(403, 'ACCOUNT_PENDING', 'Sua conta está aguardando aprovação.');
  }
  if (user.Status === 'Bloqueado') {
    return new AppError(403, 'ACCOUNT_BLOCKED', 'Sua conta está bloqueada.');
  }
  return null;
}

export function toRequestUser(row) {
  return {
    id: row.Id,
    nome: row.Nome,
    email: row.Email,
    perfil: row.PerfilNome,
    perfilId: row.PerfilId,
    status: row.Status,
    fotoUrl: row.FotoUrl,
    categoriaCnh: row.CategoriaCnh,
    validadeCnh: row.ValidadeCnh,
  };
}

// Valida o JWT e relê o usuário no banco a cada requisição. Assim, bloquear a
// conta ou trocar o perfil vale imediatamente, sem esperar o token expirar.
export function authenticate({ pool, config }) {
  return asyncHandler(async (req, res, next) => {
    const [scheme, token] = (req.get('authorization') || '').split(' ');
    if (scheme?.toLowerCase() !== 'bearer' || !token) {
      throw new AppError(401, 'UNAUTHENTICATED', 'Token de acesso ausente.');
    }

    let payload;
    try {
      payload = jwt.verify(token, config.jwt.secret, { algorithms: ['HS256'] });
    } catch {
      throw new AppError(401, 'INVALID_TOKEN', 'Token inválido ou expirado.');
    }

    const [rows] = await pool.query(`${USER_SELECT} WHERE u.Id = ? LIMIT 1`, [payload.sub]);
    const row = rows[0];
    if (!row) throw new AppError(401, 'INVALID_TOKEN', 'Usuário do token não existe mais.');

    const problem = accountProblem(row);
    if (problem) throw problem;

    req.user = toRequestUser(row);
    next();
  });
}

export function notFound(req, res, next) {
  next(new AppError(404, 'ROUTE_NOT_FOUND', 'Rota não encontrada.'));
}

// Mensagens amigáveis para chaves únicas do schema.
const DUPLICATE_MESSAGES = {
  uq_categorias_titulo_ativas: 'Já existe uma categoria ativa com esse título.',
  uq_formas_pagamento_nome: 'Já existe uma forma de pagamento com esse nome.',
  uq_usuarios_email: 'Já existe um usuário com esse e-mail.',
  uq_usuarios_google: 'Esta conta Google já está vinculada a outro usuário.',
  uq_perfis_nome: 'Já existe um perfil com esse nome.',
};

const DB_DOWN_CODES = new Set([
  'ECONNREFUSED',
  'ETIMEDOUT',
  'ENOTFOUND',
  'PROTOCOL_CONNECTION_LOST',
  'ER_CON_COUNT_ERROR',
  'ER_ACCESS_DENIED_ERROR',
  'ER_BAD_DB_ERROR',
]);

export function errorHandler(logger = console) {
  // eslint-disable-next-line no-unused-vars
  return (err, req, res, next) => {
    if (res.headersSent) return next(err);

    const send = (status, code, message, details) =>
      res.status(status).json({ error: { code, message, ...(details ? { details } : {}) } });

    if (err instanceof AppError) return send(err.status, err.code, err.message, err.details);

    if (err instanceof ZodError) {
      const details = err.issues.map((issue) => ({
        campo: issue.path.join('.'),
        mensagem: issue.message,
      }));
      return send(400, 'VALIDATION_ERROR', 'Dados inválidos.', details);
    }

    if (err.type === 'entity.parse.failed') return send(400, 'INVALID_JSON', 'JSON inválido.');
    if (err.type === 'entity.too.large') return send(413, 'PAYLOAD_TOO_LARGE', 'Corpo grande demais.');

    switch (err.code) {
      case 'ER_DUP_ENTRY': {
        const key = Object.keys(DUPLICATE_MESSAGES).find((name) =>
          String(err.sqlMessage || err.message).includes(name),
        );
        return send(409, 'DUPLICATE', key ? DUPLICATE_MESSAGES[key] : 'Registro duplicado.');
      }
      case 'ER_NO_REFERENCED_ROW_2':
        return send(400, 'INVALID_REFERENCE', 'Referência para um registro que não existe.');
      case 'ER_ROW_IS_REFERENCED_2':
        return send(409, 'IN_USE', 'O registro está em uso e não pode ser removido.');
      case 'ER_CHECK_CONSTRAINT_VIOLATED':
        return send(400, 'CONSTRAINT_VIOLATED', 'Os dados violam uma regra do banco de dados.');
      default:
    }

    if (DB_DOWN_CODES.has(err.code)) {
      logger.error('Banco indisponível:', err.code);
      return send(503, 'DB_UNAVAILABLE', 'Banco de dados indisponível no momento.');
    }

    logger.error(err);
    return send(500, 'INTERNAL_ERROR', 'Erro interno do servidor.');
  };
}
