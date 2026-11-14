import { OAuth2Client } from 'google-auth-library';
import { AppError } from './errors.js';

// Retorna uma função (idToken) => { sub, email, emailVerified, nome, foto }.
// É injetada em createApp(), o que permite trocar por um fake nos testes.
export function createGoogleVerifier(clientIds) {
  const client = new OAuth2Client();

  return async function verifyGoogleToken(idToken) {
    if (clientIds.length === 0) {
      throw new AppError(500, 'CONFIG_ERROR', 'GOOGLE_CLIENT_IDS não está configurado no servidor.');
    }
    try {
      const ticket = await client.verifyIdToken({ idToken, audience: clientIds });
      const payload = ticket.getPayload();
      return {
        sub: payload.sub,
        email: payload.email,
        emailVerified: payload.email_verified === true,
        nome: payload.name || payload.email,
        foto: payload.picture || null,
      };
    } catch {
      throw new AppError(401, 'INVALID_GOOGLE_TOKEN', 'Token do Google inválido ou expirado.');
    }
  };
}
