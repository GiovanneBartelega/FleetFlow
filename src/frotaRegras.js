import { AppError } from './errors.js';

export function kmRodados(hodometroInicial, hodometroFinal) {
  const inicial = Number(hodometroInicial);
  const final = Number(hodometroFinal);
  if (!Number.isInteger(inicial) || !Number.isInteger(final) || inicial < 0 || final < inicial) {
    throw new AppError(400, 'INVALID_ODOMETER', 'O hodômetro final deve ser maior ou igual ao inicial.');
  }
  return final - inicial;
}

export function addMonths(dateText, months) {
  const date = new Date(`${dateText}T00:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + Number(months));
  return date.toISOString().slice(0, 10);
}

function daysBetween(from, to) {
  return Math.floor((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000);
}

export function alertasDoVeiculo(vehicle, { hoje, diasAntecedencia = 30 } = {}) {
  const referenceDate = hoje || new Date().toISOString().slice(0, 10);
  const alerts = [];
  const add = (tipo, nivel, titulo, mensagem, vencimento = null) => alerts.push({
    tipo, nivel, titulo, mensagem, vencimento, veiculo: { id: vehicle.Id, placa: vehicle.Placa },
  });

  const atual = Number(vehicle.HodometroAtual);
  const ultima = vehicle.HodometroUltimaManutencao == null ? null : Number(vehicle.HodometroUltimaManutencao);
  const intervaloKm = vehicle.IntervaloManutencaoKm == null ? null : Number(vehicle.IntervaloManutencaoKm);
  if (intervaloKm && ultima != null) {
    const percorrido = atual - ultima;
    const restante = intervaloKm - percorrido;
    if (restante <= 0) add('MANUTENCAO_KM_VENCIDA', 'critico', 'Manutenção preventiva vencida', `A manutenção por quilometragem venceu há ${Math.abs(restante)} km.`);
    else if (restante <= Math.max(500, Math.ceil(intervaloKm * 0.1))) add('MANUTENCAO_KM_PROXIMA', 'atencao', 'Manutenção preventiva próxima', `Faltam ${restante} km para a manutenção preventiva.`);
  }

  const intervaloMeses = vehicle.IntervaloManutencaoMeses == null ? null : Number(vehicle.IntervaloManutencaoMeses);
  if (intervaloMeses && vehicle.UltimaManutencaoConcluida) {
    const vencimento = addMonths(String(vehicle.UltimaManutencaoConcluida).slice(0, 10), intervaloMeses);
    const dias = daysBetween(referenceDate, vencimento);
    if (dias < 0) add('MANUTENCAO_TEMPO_VENCIDA', 'critico', 'Manutenção preventiva vencida', `A manutenção por tempo venceu há ${Math.abs(dias)} dia(s).`, vencimento);
    else if (dias <= diasAntecedencia) add('MANUTENCAO_TEMPO_PROXIMA', 'atencao', 'Manutenção preventiva próxima', `A manutenção por tempo vence em ${dias} dia(s).`, vencimento);
  }

  for (const [field, tipo, titulo] of [
    ['VencimentoLicenciamento', 'LICENCIAMENTO', 'Licenciamento'],
    ['VencimentoSeguro', 'SEGURO', 'Seguro'],
  ]) {
    if (!vehicle[field]) continue;
    const vencimento = String(vehicle[field]).slice(0, 10); const dias = daysBetween(referenceDate, vencimento);
    if (dias < 0) add(`${tipo}_VENCIDO`, 'critico', `${titulo} vencido`, `${titulo} venceu há ${Math.abs(dias)} dia(s).`, vencimento);
    else if (dias <= diasAntecedencia) add(`${tipo}_PROXIMO`, 'atencao', `${titulo} próximo`, `${titulo} vence em ${dias} dia(s).`, vencimento);
  }
  return alerts;
}
