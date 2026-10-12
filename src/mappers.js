import { toBool, toIso } from './sql.js';

const base = (row) => ({
  id: row.Id,
  ativo: toBool(row.Ativo),
  syncVersion: Number(row.SyncVersion),
  criadoEm: toIso(row.CriadoEm),
  atualizadoEm: toIso(row.AtualizadoEm),
});

export const USER_SELECT = `
  SELECT u.Id, u.CriadoEm, u.AtualizadoEm, u.Ativo, u.SyncVersion,
         u.Nome, u.Email, u.GoogleSubjectId, u.PerfilId, u.Status, u.FotoUrl,
         u.CategoriaCnh, u.ValidadeCnh,
         p.Nome AS PerfilNome, p.Ativo AS PerfilAtivo
  FROM usuarios u
  JOIN perfis p ON p.Id = u.PerfilId`;

export const mapUsuario = (row) => ({
  ...base(row),
  nome: row.Nome,
  email: row.Email,
  perfil: { id: row.PerfilId, nome: row.PerfilNome },
  status: row.Status,
  fotoUrl: row.FotoUrl,
  categoriaCnh: row.CategoriaCnh,
  validadeCnh: row.ValidadeCnh,
});

export const mapPerfil = (row) => ({ ...base(row), nome: row.Nome });

export const mapCategoria = (row) => ({
  ...base(row),
  titulo: row.Titulo,
  tipo: row.Tipo,
  ehSistema: toBool(row.EhSistema),
});

export const mapFormaPagamento = (row) => ({
  ...base(row),
  nome: row.Nome,
  ehSistema: toBool(row.EhSistema),
});

export const DIVIDA_SELECT = `
  SELECT d.Id, d.CriadoEm, d.AtualizadoEm, d.Ativo, d.SyncVersion,
         d.Descricao, d.CategoriaId, c.Titulo AS CategoriaTitulo, c.Tipo AS CategoriaTipo,
         d.QuantidadeParcelas, d.ValorParcela, d.DataVencimentoPrimeira,
         d.ValorQuitacaoAntecipada, d.VeiculoId
  FROM dividas d
  JOIN categorias c ON c.Id = d.CategoriaId`;

export const mapDivida = (row, valorTotal) => ({
  ...base(row),
  descricao: row.Descricao,
  categoria: { id: row.CategoriaId, titulo: row.CategoriaTitulo, tipo: row.CategoriaTipo },
  quantidadeParcelas: row.QuantidadeParcelas,
  valorParcela: row.ValorParcela,
  valorTotal,
  dataVencimentoPrimeira: row.DataVencimentoPrimeira,
  valorQuitacaoAntecipada: row.ValorQuitacaoAntecipada,
  veiculoId: row.VeiculoId,
});

export const MOVIMENTACAO_SELECT = `
  SELECT m.Id, m.CriadoEm, m.AtualizadoEm, m.Ativo, m.SyncVersion,
         m.Valor, m.Descricao,
         m.CategoriaId, c.Titulo AS CategoriaTitulo, c.Tipo AS CategoriaTipo,
         m.FormaPagamentoId, f.Nome AS FormaPagamentoNome,
         m.ViagemId, m.DividaId, m.VeiculoId, m.NumeroParcela,
         m.DataVencimento, m.DataPagamento, m.Status,
         m.LitrosCombustivel, m.CriadoPorUsuarioId
  FROM movimentacoes m
  JOIN categorias c ON c.Id = m.CategoriaId
  JOIN formas_pagamento f ON f.Id = m.FormaPagamentoId`;

export const mapMovimentacao = (row) => ({
  ...base(row),
  valor: row.Valor,
  descricao: row.Descricao,
  categoria: { id: row.CategoriaId, titulo: row.CategoriaTitulo, tipo: row.CategoriaTipo },
  formaPagamento: { id: row.FormaPagamentoId, nome: row.FormaPagamentoNome },
  viagemId: row.ViagemId,
  dividaId: row.DividaId,
  veiculoId: row.VeiculoId,
  numeroParcela: row.NumeroParcela,
  dataVencimento: row.DataVencimento,
  dataPagamento: row.DataPagamento,
  status: row.Status,
  litrosCombustivel: row.LitrosCombustivel,
  criadoPorUsuarioId: row.CriadoPorUsuarioId,
});
