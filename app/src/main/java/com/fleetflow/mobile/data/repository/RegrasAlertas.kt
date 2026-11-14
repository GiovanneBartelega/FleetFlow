package com.fleetflow.mobile.data.repository

import com.fleetflow.mobile.data.model.VeiculoDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GravidadeAlerta { VENCIDO, ATENCAO }

data class AlertaFrota(
    val veiculoId: String,
    val placa: String,
    val titulo: String,
    val detalhe: String,
    val gravidade: GravidadeAlerta,
)

// Alertas de frota calculados só com os dados do veículo. Funções puras, sem Android.
object RegrasAlertas {

    // Avisa o documento com até 30 dias para vencer.
    const val DIAS_AVISO_DOCUMENTO = 30

    // Avisa a manutenção quando faltam até 500 km.
    const val KM_AVISO_MANUTENCAO = 500

    // Dias entre hoje e a data (negativo = já passou). Null se vazia ou inválida.
    fun diasAte(data: String?, hoje: LocalDate): Long? {
        if (data.isNullOrBlank()) return null
        return try {
            ChronoUnit.DAYS.between(hoje, LocalDate.parse(data.take(10)))
        } catch (e: Exception) {
            null
        }
    }

    private fun alertaDocumento(v: VeiculoDto, nome: String, data: String?, hoje: LocalDate): AlertaFrota? {
        val dias = diasAte(data, hoje) ?: return null
        return when {
            dias < 0 -> AlertaFrota(v.id, v.placa, "$nome vencido", "Venceu há ${-dias} dia(s)", GravidadeAlerta.VENCIDO)
            dias <= DIAS_AVISO_DOCUMENTO -> AlertaFrota(v.id, v.placa, "$nome vence em breve", "Vence em $dias dia(s)", GravidadeAlerta.ATENCAO)
            else -> null
        }
    }

    // Km que faltam para a próxima manutenção (negativo = atrasada). Null se faltam dados.
    fun kmParaManutencao(v: VeiculoDto): Int? {
        val intervalo = v.intervaloManutencaoKm ?: return null
        val ultima = v.hodometroUltimaManutencao ?: return null
        return intervalo - (v.hodometroAtual - ultima)
    }

    private fun alertaManutencao(v: VeiculoDto): AlertaFrota? {
        val faltam = kmParaManutencao(v) ?: return null
        return when {
            faltam < 0 -> AlertaFrota(v.id, v.placa, "Manutenção atrasada", "Passou ${-faltam} km do intervalo", GravidadeAlerta.VENCIDO)
            faltam <= KM_AVISO_MANUTENCAO -> AlertaFrota(v.id, v.placa, "Manutenção próxima", "Faltam $faltam km", GravidadeAlerta.ATENCAO)
            else -> null
        }
    }

    // Todos os alertas da frota, os vencidos primeiro.
    fun gerar(veiculos: List<VeiculoDto>, hoje: LocalDate = LocalDate.now()): List<AlertaFrota> =
        veiculos.flatMap { v ->
            listOfNotNull(
                alertaDocumento(v, "Licenciamento", v.vencimentoLicenciamento, hoje),
                alertaDocumento(v, "Seguro", v.vencimentoSeguro, hoje),
                alertaManutencao(v),
            )
        }.sortedBy { it.gravidade }
}
