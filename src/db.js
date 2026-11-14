import mysql from 'mysql2/promise';

export function createPool(dbConfig) {
  const pool = mysql.createPool({
    ...dbConfig,
    waitForConnections: true,
    charset: 'utf8mb4',
    // DATE/DATETIME chegam como string ('2026-10-30', '2026-10-30 14:00:00'),
    // sem conversão de fuso pelo driver. DECIMAL continua como string (dinheiro).
    dateStrings: true,
    timezone: 'Z',
  });

  // O banco guarda DATETIME sem fuso. Fixando a sessão em UTC, tudo que
  // CURRENT_TIMESTAMP grava é UTC e a API devolve ISO com "Z".
  pool.pool.on('connection', (connection) => {
    connection.query("SET time_zone = '+00:00'");
  });

  return pool;
}
