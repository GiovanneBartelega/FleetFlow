import { consultar } from './consultas.js';

// Cada verificação devolve as linhas problemáticas (não só a contagem).
//   severidade 'erro' -> dado inconsistente; faz o runner sair com código 2
//   severidade 'info' -> comportamento esperado a conhecer (não reprova)
const VERIFICACOES = [
  {
    id: 'abastecimento_valor_vs_movimentacao',
    titulo: 'abastecimentos.ValorTotal diferente da movimentação vinculada',
    arquivo: 'integridade/abastecimento_valor_vs_movimentacao.sql',
    severidade: 'erro',
  },
  {
    id: 'os_valor_vs_movimentacao',
    titulo: 'ordens_servico.ValorTotal diferente da movimentação vinculada',
    arquivo: 'integridade/os_valor_vs_movimentacao.sql',
    severidade: 'erro',
  },
  {
    id: 'viagens_km_inconsistente',
    titulo: 'Viagens concluídas com km inconsistente ou sem hodômetro final',
    arquivo: 'integridade/viagens_km_inconsistente.sql',
    severidade: 'erro',
  },
  {
    id: 'hodometro_veiculo_menor',
    titulo: 'HodometroAtual do veículo menor que o maior hodômetro de viagem/abastecimento',
    arquivo: 'integridade/hodometro_veiculo_menor.sql',
    severidade: 'erro',
  },
  {
    id: 'dividas_parcelas',
    titulo: 'Dívidas com parcelas duplicadas, faltando ou em quantidade diferente da prevista',
    arquivo: 'integridade/dividas_parcelas.sql',
    severidade: 'erro',
  },
  {
    id: 'saidas_sem_veiculo',
    titulo: 'Saídas sem veículo e sem viagem (entram em custosNaoAlocados)',
    arquivo: 'integridade/saidas_sem_veiculo.sql',
    severidade: 'info',
  },
  {
    id: 'movimentacoes_sem_vencimento',
    titulo: 'Movimentações sem DataVencimento (invisíveis no regime de competência)',
    arquivo: 'integridade/movimentacoes_sem_vencimento.sql',
    severidade: 'info',
  },
];

export async function verificarIntegridade(db, valores) {
  const resultado = [];
  for (const { arquivo, ...meta } of VERIFICACOES) {
    const linhas = await consultar(db, arquivo, valores);
    resultado.push({ ...meta, quantidade: linhas.length, linhas });
  }
  return resultado;
}
