const num = (value, fallback) =>
  value === undefined || value === '' ? fallback : Number(value);

const list = (value) =>
  (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

export function loadConfig(env = process.env) {
  const nodeEnv = env.NODE_ENV || 'development';
  const jwtSecret = env.JWT_SECRET || '';

  if (jwtSecret.length < 32) {
    throw new Error('JWT_SECRET é obrigatório e precisa ter pelo menos 32 caracteres.');
  }

  return {
    nodeEnv,
    port: num(env.PORT, 3000),
    trustProxy: num(env.TRUST_PROXY, 0),
    db: {
      host: env.DB_HOST || '127.0.0.1',
      port: num(env.DB_PORT, 3306),
      user: env.DB_USER || 'root',
      password: env.DB_PASSWORD ?? '',
      database: env.DB_NAME || 'fleetflow',
      connectionLimit: num(env.DB_POOL_SIZE, 10),
    },
    jwt: {
      secret: jwtSecret,
      expiresIn: env.JWT_EXPIRES_IN || '8h',
    },
    google: {
      // Para o Android, o "audience" do ID token é o Web Client ID passado em
      // setServerClientId(...) no app. Aceita mais de um, separados por vírgula.
      clientIds: list(env.GOOGLE_CLIENT_IDS),
    },
    corsOrigins: list(env.CORS_ORIGINS),
    // Perfil atribuído a quem entra pela primeira vez (fica AguardandoAprovacao).
    defaultProfileName: env.DEFAULT_PROFILE_NAME || 'OperadorMotorista',
    // Login sem Google, só para testes locais. Nunca habilita em produção.
    devLogin: env.DEV_LOGIN === 'true' && nodeEnv !== 'production',
  };
}
