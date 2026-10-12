package com.fleetflow.mobile.data.model

data class DataDto<T>(val data:List<T>)
data class CategoriaDto(val id:String,val titulo:String,val tipo:String,val ehSistema:Boolean=false)
data class FormaDto(val id:String,val nome:String,val ehSistema:Boolean=false)
data class MovDto(val id:String,val descricao:String,val valor:String,val categoria:CategoriaDto,val formaPagamento:FormaDto,
    val status:String,val dataVencimento:String?,val dataPagamento:String?,val syncVersion:Int)
data class TotaisDto(val entradas:String,val saidas:String)
data class MovPageDto(val data:List<MovDto>,val page:Int,val totalPages:Int,val total:Int,val totais:TotaisDto)
data class SaldoDto(val saldoAtual:String,val saldoPrevisto:String,val receitasPagas:String,val despesasPagas:String,val aReceber:String,val aPagar:String)
data class MovRequestDto(val valor:String,val descricao:String,val categoriaId:String,val formaPagamentoId:String,
    val status:String,val dataVencimento:String?,val dataPagamento:String?,val syncVersion:Int?=null)
data class DividaRequestDto(val descricao:String,val categoriaId:String,val formaPagamentoId:String,val quantidadeParcelas:Int,
    val valorParcela:String,val dataVencimentoPrimeira:String,val valorQuitacaoAntecipada:String?=null)
