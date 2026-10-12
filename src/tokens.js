import { randomBytes, createHash } from 'node:crypto';
export const hashToken=token=>createHash('sha256').update(token).digest('hex');
export async function newRefresh(pool,userId) {
  const token=randomBytes(48).toString('hex');
  await pool.query('INSERT INTO refresh_tokens(TokenHash,UsuarioId,ExpiraEm) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 DAY))',[hashToken(token),userId]);
  return token;
}
