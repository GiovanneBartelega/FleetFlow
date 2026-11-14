package com.fleetflow.mobile.data.model

data class VeiculoDto(
    val id: String,
    val placa: String,
    val marca: String,
    val modelo: String,
    val anoFabricacao: Int,
    val capacidadeCargaToneladas: String,
    val status: String,
    val hodometroAtual: Int,
    val intervaloManutencaoKm: Int? = null,
    val intervaloManutencaoMeses: Int? = null,
    val hodometroUltimaManutencao: Int? = null,
    val vencimentoLicenciamento: String? = null,
    val vencimentoSeguro: String? = null,
    val consumoMedioReferenciaKmL: String? = null,
    val syncVersion: Int,
)

data class VeiculosPageDto(val data: List<VeiculoDto>, val page: Int, val totalPages: Int, val total: Int)

data class VeiculoRequestDto(
    val placa: String,
    val marca: String,
    val modelo: String,
    val anoFabricacao: Int,
    val capacidadeCargaToneladas: Double,
    val status: String = "Disponivel",
    val hodometroAtual: Int = 0,
    val intervaloManutencaoKm: Int? = null,
    val intervaloManutencaoMeses: Int? = null,
    val hodometroUltimaManutencao: Int? = null,
    val vencimentoLicenciamento: String? = null,
    val vencimentoSeguro: String? = null,
    val consumoMedioReferenciaKmL: Double? = null,
    val syncVersion: Int? = null,
)

data class VeiculoResumoDto(val id: String, val placa: String, val marca: String, val modelo: String)

data class OrdemServicoDto(
    val id: String,
    val veiculo: VeiculoResumoDto,
    val dataManutencao: String,
    val descricao: String,
    val tipo: String,
    val valorTotal: String,
    val hodometroNaManutencao: Int? = null,
    val dataConclusao: String? = null,
    val concluida: Boolean,
    val movimentacaoId: String,
    val syncVersion: Int,
)

data class OrdensPageDto(val data: List<OrdemServicoDto>, val page: Int, val totalPages: Int, val total: Int)

data class OrdemServicoRequestDto(
    val veiculoId: String,
    val dataManutencao: String,
    val descricao: String,
    val tipo: String,
    val valorTotal: String,
    val hodometroNaManutencao: Int? = null,
    val dataConclusao: String? = null,
    val movimentacaoId: String? = null,
    val formaPagamentoId: String? = null,
)

data class ConcluirOrdemRequestDto(
    val dataConclusao: String,
    val syncVersion: Int? = null,
)
