import { AppError } from './errors.js';

export const toBool = (value) => value === 1 || value === true || value === '1';

// 'YYYY-MM-DD HH:MM:SS' (UTC, vindo do banco) -> 'YYYY-MM-DDTHH:MM:SSZ'
export const toIso = (value) => (value ? `${String(value).replace(' ', 'T')}Z` : null);

// Escapa % e _ para uso em LIKE.
export const likeTerm = (term) => `%${term.replace(/[\\%_]/g, '\\$&')}%`;

export function pagination({ page, pageSize }) {
  return { limit: pageSize, offset: (page - 1) * pageSize };
}

export function pageResult(data, total, { page, pageSize }) {
  return { data, page, pageSize, total, totalPages: Math.max(1, Math.ceil(total / pageSize)) };
}

// Monta "UPDATE ... SET col = ?, ... WHERE Id = ? AND Ativo = 1 [AND SyncVersion = ?]".
// `columns` mapeia campo da API -> coluna. Retorna null se não há nada a atualizar.
// SyncVersion e AtualizadoEm são mantidos pelo trigger BEFORE UPDATE.
export function buildUpdate(table, id, input, columns, expectedSyncVersion) {
  const sets = [];
  const params = [];

  for (const [field, column] of Object.entries(columns)) {
    if (input[field] !== undefined) {
      sets.push(`${column} = ?`);
      params.push(input[field]);
    }
  }
  if (sets.length === 0) return null;

  let sql = `UPDATE ${table} SET ${sets.join(', ')} WHERE Id = ? AND Ativo = 1`;
  params.push(id);
  if (expectedSyncVersion !== undefined) {
    sql += ' AND SyncVersion = ?';
    params.push(expectedSyncVersion);
  }
  return { sql, params };
}

// Executa o UPDATE. Se nenhuma linha casou, distingue "não existe" de
// "SyncVersion desatualizado" (conflito de edição).
export async function runUpdate(pool, table, update, id, expectedSyncVersion, notFoundMessage) {
  const [result] = await pool.query(update.sql, update.params);
  if (result.affectedRows > 0) return;

  const [rows] = await pool.query(`SELECT Id FROM ${table} WHERE Id = ? AND Ativo = 1`, [id]);
  if (rows.length === 0) throw new AppError(404, 'NOT_FOUND', notFoundMessage);
  if (expectedSyncVersion !== undefined) {
    throw new AppError(
      409,
      'VERSION_CONFLICT',
      'Este registro foi alterado por outra pessoa. Recarregue e tente de novo.',
    );
  }
  throw new AppError(409, 'NOT_UPDATED', 'Não foi possível atualizar o registro.');
}

export async function softDelete(pool, table, id, notFoundMessage) {
  const [result] = await pool.query(`UPDATE ${table} SET Ativo = 0 WHERE Id = ? AND Ativo = 1`, [id]);
  if (result.affectedRows === 0) throw new AppError(404, 'NOT_FOUND', notFoundMessage);
}

// Garante que a referência existe e está ativa; dá um erro 400 amigável
// antes de o banco responder com violação de FK.
export async function assertActive(pool, table, id, field, label) {
  const [rows] = await pool.query(`SELECT Id FROM ${table} WHERE Id = ? AND Ativo = 1`, [id]);
  if (rows.length === 0) {
    throw new AppError(400, 'INVALID_REFERENCE', `${label} não encontrada ou inativa.`, [
      { campo: field, mensagem: `${label} não encontrada ou inativa` },
    ]);
  }
}

// Soma exata em centavos para DECIMAL(14,2) vindo como string.
export function moneyTimes(quantity, amount) {
  const cents = BigInt(String(amount).replace('.', ''));
  const total = cents * BigInt(quantity);
  const digits = total.toString().padStart(3, '0');
  return `${digits.slice(0, -2)}.${digits.slice(-2)}`;
}
