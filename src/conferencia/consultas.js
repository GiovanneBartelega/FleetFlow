import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const DIR_SQL = fileURLToPath(new URL('./sql/', import.meta.url));

// Os .sql manuais usam :inicio, :fim e :regime (legíveis, ao contrário de ? posicionais).
// Aqui cada marcador vira "?" e os valores entram na ordem em que aparecem; nada é concatenado.
// Linhas que são só comentário saem antes, para um ":regime" citado em comentário não virar parâmetro.
export function prepararSql(texto, valores) {
  const semComentarios = texto
    .split('\n')
    .filter((linha) => !/^\s*--/.test(linha))
    .join('\n');
  const params = [];
  const sql = semComentarios.replace(/:(inicio|fim|regime)\b/g, (_, nome) => {
    params.push(valores[nome]);
    return '?';
  });
  return { sql, params };
}

// `arquivo` é relativo a src/conferencia/sql (ex.: 'km_rodados.sql' ou 'integridade/x.sql').
export async function consultar(db, arquivo, valores = {}) {
  const texto = await readFile(DIR_SQL + arquivo, 'utf8');
  const { sql, params } = prepararSql(texto, valores);
  const [rows] = await db.query(sql, params);
  return rows;
}

export const numero = (valor) => (valor === null || valor === undefined ? null : Number(valor));
