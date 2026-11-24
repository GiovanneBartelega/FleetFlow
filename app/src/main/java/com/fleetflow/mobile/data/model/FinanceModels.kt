package com.fleetflow.mobile.data.model

data class DataDto<T>(
    val data: List<T>
)

data class CategoriaDto(
    val id: String,
    val titulo: String,
    val tipo: String,
    val ehSistema: Boolean = false
)

data class FormaDto(
    val id: String,
    val nome: String,
    val ehSistema: Boolean = false
)

data class MovDto(
    val id: String,
    val descricao: String,
    val valor: String,
    val categoria: CategoriaDto,
    val formaPagamento: FormaDto,
    val status: String,
    val dataVencimento: String?,
    val dataPagamento: String?,
    val syncVersion: Int,
    val viagemId: String? = null
)

data class TotaisDto(
    val entradas: String,
    val saidas: String
)

data class MovPageDto(
    val data: List<MovDto>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
    val totais: TotaisDto
)

data class SaldoDto(
    val saldoAtual: String,
    val saldoPrevisto: String,
    val receitasPagas: String,
    val despesasPagas: String,
    val aReceber: String,
    val aPagar: String
)

data class KpisDto(
    val inicio: String, val fim: String, val receitas: Double, val despesas: Double,
    val resultado: Double, val margemPercentual: Double?
)

data class FluxoMesDto(
    val mes: String, val receitas: Double, val despesas: Double,
    val resultado: Double, val saldoAcumulado: Double
)

data class FluxoCaixaDto(val inicio: String, val fim: String, val meses: List<FluxoMesDto>)

data class DespesaCategoriaDto(
    val categoriaId: String, val categoria: String, val valor: Double, val percentual: Double
)

data class DespesasCategoriaDto(val inicio: String, val fim: String, val total: Double, val categorias: List<DespesaCategoriaDto>)

data class RankingVeiculoDto(
    val veiculoId: String, val placa: String, val receitas: Double,
    val despesas: Double, val resultado: Double
)

data class RankingsDto(
    val inicio: String, val fim: String, val maisRentaveis: List<RankingVeiculoDto>,
    val maioresDespesas: List<RankingVeiculoDto>
)

data class PrevisaoMesDto(
    val mes: String, val entradas: Double, val saidas: Double,
    val resultado: Double, val saldoProjetado: Double
)

data class PrevisaoCaixaDto(val inicio: String, val meses: Int, val saldoInicial: Double, val previsao: List<PrevisaoMesDto>)

data class AlertaSaldoDto(val tipo: String, val mes: String, val saldoProjetado: Double, val mensagem: String)
data class AlertasSaldoDto(val inicio: String, val meses: Int, val alertas: List<AlertaSaldoDto>)

data class MovRequestDto(
    val valor: String,
    val descricao: String,
    val categoriaId: String,
    val formaPagamentoId: String,
    val status: String,
    val dataVencimento: String?,
    val dataPagamento: String?,
    val syncVersion: Int? = null,
    val viagemId: String? = null
)

data class DividaRequestDto(
    val descricao: String,
    val categoriaId: String,
    val formaPagamentoId: String,
    val quantidadeParcelas: Int,
    val valorParcela: String,
    val dataVencimentoPrimeira: String,
    val valorQuitacaoAntecipada: String? = null
)
