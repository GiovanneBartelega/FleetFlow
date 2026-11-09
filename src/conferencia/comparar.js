// Tolerâncias da conferência.
export const TOLERANCIA = {
  dinheiro: 0.01,
  razao: 0.001, // km/l, custo/km, preço do litro
  inteiro: 0, // km, quantidades
};

// Compara um valor do app com o do banco. null contra null é OK (ex.: divisão por zero
// nos dois lados); null de um lado só é divergência.
export function compararValores(metrica, chave, app, banco, tolerancia) {
  const a = app ?? null;
  const b = banco ?? null;
  let diferenca = null;
  let ok;
  if (a === null || b === null) {
    ok = a === b;
  } else {
    diferenca = Number((a - b).toFixed(6));
    ok = Math.abs(a - b) <= tolerancia + 1e-9;
  }
  return { metrica, chave, app: a, banco: b, diferenca, tolerancia, ok };
}

const fmt = (valor) => {
  if (valor === null || valor === undefined) return 'null';
  return Number.isInteger(valor) ? String(valor) : String(Number(valor.toFixed(4)));
};

// Tabela legível: metrica | chave | app | banco | diferenca | status
export function formatarTabela(linhas) {
  const cabecalho = ['métrica', 'chave', 'app', 'banco', 'diferença', 'status'];
  const corpo = linhas.map((l) => [
    l.metrica,
    l.chave ?? '-',
    fmt(l.app),
    fmt(l.banco),
    l.diferenca === null ? '-' : fmt(l.diferenca),
    l.ok ? 'OK' : 'DIVERGE',
  ]);
  const larguras = cabecalho.map((titulo, i) => Math.max(titulo.length, ...corpo.map((row) => row[i].length)));
  const linha = (cols) => cols.map((c, i) => c.padEnd(larguras[i])).join(' | ');
  return [linha(cabecalho), larguras.map((w) => '-'.repeat(w)).join('-+-'), ...corpo.map(linha)].join('\n');
}
