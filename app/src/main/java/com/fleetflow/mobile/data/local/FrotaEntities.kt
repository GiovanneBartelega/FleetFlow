package com.fleetflow.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fleetflow.mobile.data.model.OrdemServicoDto
import com.fleetflow.mobile.data.model.VeiculoDto
import com.fleetflow.mobile.data.model.VeiculoResumoDto

// Cópia local da frota. A API continua sendo a fonte da verdade: estas tabelas guardam
// a última lista que veio dela, para o app mostrar a frota sem internet. É o começo da
// gravação local; a fila de sincronização (envio do que foi feito offline) vem depois.

@Entity(tableName = "veiculos")
data class VeiculoEntity(
    @PrimaryKey val id: String,
    val placa: String,
    val marca: String,
    val modelo: String,
    val anoFabricacao: Int,
    val capacidadeCargaToneladas: String,
    val status: String,
    val hodometroAtual: Int,
    val intervaloManutencaoKm: Int?,
    val intervaloManutencaoMeses: Int?,
    val hodometroUltimaManutencao: Int?,
    val vencimentoLicenciamento: String?,
    val vencimentoSeguro: String?,
    val consumoMedioReferenciaKmL: String?,
    val syncVersion: Int,
    val salvoEm: Long,          // quando a cópia foi gravada, em milissegundos
)

@Entity(tableName = "ordens_servico")
data class OrdemServicoEntity(
    @PrimaryKey val id: String,
    val veiculoId: String,
    val veiculoPlaca: String,
    val veiculoMarca: String,
    val veiculoModelo: String,
    val dataManutencao: String,
    val descricao: String,
    val tipo: String,
    val valorTotal: String,
    val hodometroNaManutencao: Int?,
    val dataConclusao: String?,
    val concluida: Boolean,
    val movimentacaoId: String,
    val syncVersion: Int,
    val salvoEm: Long,
)

fun VeiculoDto.paraEntity(salvoEm: Long) = VeiculoEntity(
    id = id,
    placa = placa,
    marca = marca,
    modelo = modelo,
    anoFabricacao = anoFabricacao,
    capacidadeCargaToneladas = capacidadeCargaToneladas,
    status = status,
    hodometroAtual = hodometroAtual,
    intervaloManutencaoKm = intervaloManutencaoKm,
    intervaloManutencaoMeses = intervaloManutencaoMeses,
    hodometroUltimaManutencao = hodometroUltimaManutencao,
    vencimentoLicenciamento = vencimentoLicenciamento,
    vencimentoSeguro = vencimentoSeguro,
    consumoMedioReferenciaKmL = consumoMedioReferenciaKmL,
    syncVersion = syncVersion,
    salvoEm = salvoEm,
)

fun VeiculoEntity.paraDto() = VeiculoDto(
    id = id,
    placa = placa,
    marca = marca,
    modelo = modelo,
    anoFabricacao = anoFabricacao,
    capacidadeCargaToneladas = capacidadeCargaToneladas,
    status = status,
    hodometroAtual = hodometroAtual,
    intervaloManutencaoKm = intervaloManutencaoKm,
    intervaloManutencaoMeses = intervaloManutencaoMeses,
    hodometroUltimaManutencao = hodometroUltimaManutencao,
    vencimentoLicenciamento = vencimentoLicenciamento,
    vencimentoSeguro = vencimentoSeguro,
    consumoMedioReferenciaKmL = consumoMedioReferenciaKmL,
    syncVersion = syncVersion,
)

fun OrdemServicoDto.paraEntity(salvoEm: Long) = OrdemServicoEntity(
    id = id,
    veiculoId = veiculo.id,
    veiculoPlaca = veiculo.placa,
    veiculoMarca = veiculo.marca,
    veiculoModelo = veiculo.modelo,
    dataManutencao = dataManutencao,
    descricao = descricao,
    tipo = tipo,
    valorTotal = valorTotal,
    hodometroNaManutencao = hodometroNaManutencao,
    dataConclusao = dataConclusao,
    concluida = concluida,
    movimentacaoId = movimentacaoId,
    syncVersion = syncVersion,
    salvoEm = salvoEm,
)

fun OrdemServicoEntity.paraDto() = OrdemServicoDto(
    id = id,
    veiculo = VeiculoResumoDto(veiculoId, veiculoPlaca, veiculoMarca, veiculoModelo),
    dataManutencao = dataManutencao,
    descricao = descricao,
    tipo = tipo,
    valorTotal = valorTotal,
    hodometroNaManutencao = hodometroNaManutencao,
    dataConclusao = dataConclusao,
    concluida = concluida,
    movimentacaoId = movimentacaoId,
    syncVersion = syncVersion,
)
