package com.fleetflow.mobile.ui.screens

import com.fleetflow.mobile.data.local.RotaEntity
import com.fleetflow.mobile.data.model.VeiculoDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Formatos usados pelas telas de viagem (lista, abrir, checklist e fechar).

private val FORMATO_DATA_HORA_VIAGEM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

// "ABC1D23" -> "ABC-1D23", como na Frota.
internal fun placaViagem(placa: String): String =
    if (placa.length == 7) "${placa.substring(0, 3)}-${placa.substring(3)}" else placa

// 120000 -> "120.000"
internal fun kmViagem(valor: Int): String =
    valor.toString().reversed().chunked(3).joinToString(".").reversed()

// ISO-8601 da API ou do aparelho -> "04/11/2026 13:05", no fuso do celular.
internal fun dataHoraViagem(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(FORMATO_DATA_HORA_VIAGEM)
    }.getOrDefault(iso)
}

internal fun rotuloVeiculoViagem(veiculo: VeiculoDto): String =
    "${placaViagem(veiculo.placa)} · ${veiculo.marca} ${veiculo.modelo}"

internal fun rotuloRotaViagem(rota: RotaEntity): String =
    "${rota.origem} → ${rota.destino} · ${rota.nome} (${kmViagem(rota.distanciaEstimadaKm)} km)"
