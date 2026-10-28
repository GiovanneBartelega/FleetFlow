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

export const mapRota = (row) => ({
  ...base(row),
  nome: row.Nome,
  origem: row.Origem,
  destino: row.Destino,
  distanciaEstimadaKm: Number(row.DistanciaEstimadaKm),
  descricao: row.Descricao,
});

export const VIAGEM_SELECT = `
  SELECT t.Id, t.CriadoEm, t.AtualizadoEm, t.Ativo, t.SyncVersion,
         t.RotaId, r.Nome AS RotaNome, r.Origem AS RotaOrigem, r.Destino AS RotaDestino,
         t.VeiculoId, v.Placa AS VeiculoPlaca, v.Marca AS VeiculoMarca, v.Modelo AS VeiculoModelo,
         t.MotoristaId, u.Nome AS MotoristaNome,
         t.HodometroInicial, t.HodometroFinal, t.DataAbertura, t.DataFechamento, t.Status, t.Observacao
  FROM viagens t
  JOIN rotas r ON r.Id = t.RotaId
  JOIN veiculos v ON v.Id = t.VeiculoId
  JOIN usuarios u ON u.Id = t.MotoristaId`;

export const mapViagem = (row) => ({
  ...base(row),
  rota: { id: row.RotaId, nome: row.RotaNome, origem: row.RotaOrigem, destino: row.RotaDestino },
  veiculo: { id: row.VeiculoId, placa: row.VeiculoPlaca, marca: row.VeiculoMarca, modelo: row.VeiculoModelo },
  motorista: { id: row.MotoristaId, nome: row.MotoristaNome },
  hodometroInicial: Number(row.HodometroInicial),
  hodometroFinal: row.HodometroFinal == null ? null : Number(row.HodometroFinal),
  dataAbertura: toIso(row.DataAbertura),
  dataFechamento: toIso(row.DataFechamento),
  status: row.Status,
  observacao: row.Observacao,
});

export const mapVeiculo = (row) => ({
  ...base(row),
  placa: row.Placa,
  marca: row.Marca,
  modelo: row.Modelo,
  anoFabricacao: row.AnoFabricacao,
  capacidadeCargaToneladas: row.CapacidadeCargaToneladas,
  status: row.Status,
  hodometroAtual: row.HodometroAtual,
  intervaloManutencaoKm: row.IntervaloManutencaoKm,
  intervaloManutencaoMeses: row.IntervaloManutencaoMeses,
  hodometroUltimaManutencao: row.HodometroUltimaManutencao,
  vencimentoLicenciamento: row.VencimentoLicenciamento,
  vencimentoSeguro: row.VencimentoSeguro,
  consumoMedioReferenciaKmL: row.ConsumoMedioReferenciaKmL,
});

export const ORDEM_SERVICO_SELECT = `
  SELECT o.Id, o.CriadoEm, o.AtualizadoEm, o.Ativo, o.SyncVersion,
         o.VeiculoId, v.Placa AS VeiculoPlaca, v.Marca AS VeiculoMarca, v.Modelo AS VeiculoModelo,
         o.DataManutencao, o.Descricao, o.Tipo, o.ValorTotal,
         o.HodometroNaManutencao, o.DataConclusao, o.MovimentacaoId
  FROM ordens_servico o
  JOIN veiculos v ON v.Id = o.VeiculoId`;

export const mapOrdemServico = (row) => ({
  ...base(row),
  veiculo: { id: row.VeiculoId, placa: row.VeiculoPlaca, marca: row.VeiculoMarca, modelo: row.VeiculoModelo },
  dataManutencao: row.DataManutencao,
  descricao: row.Descricao,
  tipo: row.Tipo,
  valorTotal: row.ValorTotal,
  hodometroNaManutencao: row.HodometroNaManutencao,
  dataConclusao: row.DataConclusao,
  concluida: row.DataConclusao != null,
  movimentacaoId: row.MovimentacaoId,
});
export const mapItemChecklist = (row) => ({
  ...base(row),
  nome: row.Nome,
  obrigatorio: toBool(row.Obrigatorio),
});

export const CHECKLIST_VIAGEM_SELECT = `
  SELECT
    c.Id, c.CriadoEm, c.AtualizadoEm, c.Ativo, c.SyncVersion,
    c.ViagemId, c.ItemChecklistId,
    i.Nome AS ItemNome, i.Obrigatorio AS ItemObrigatorio,
    c.Conforme, c.Observacao, c.FotoUrl
  FROM checklists_viagem c
  JOIN itens_checklist i ON i.Id = c.ItemChecklistId`;

export const mapChecklistViagem = (row) => ({
  ...base(row),
  viagemId: row.ViagemId,
  itemChecklist: {
    id: row.ItemChecklistId,
    nome: row.ItemNome,
    obrigatorio: toBool(row.ItemObrigatorio),
  },
  conforme: toBool(row.Conforme),
  observacao: row.Observacao,
  fotoUrl: row.FotoUrl,
});