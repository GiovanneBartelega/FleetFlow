package com.fleetflow.mobile.data

import androidx.compose.runtime.mutableStateListOf

data class Categoria(
    val id: Int,
    var nome: String,
    val tipo: String, // "ENTRADA" ou "SAIDA"
    var ativo: Boolean
)

data class FormaPagamento(
    val id: Int,
    var nome: String,
    var ativo: Boolean
)

object CategoriasRepo {
    val lista = mutableStateListOf(
        Categoria(1, "Fretes", "ENTRADA", true),
        Categoria(2, "Combustível", "SAIDA", true),
        Categoria(3, "Pedágio", "SAIDA", true),
        Categoria(4, "Salário Motorista", "SAIDA", true),
        Categoria(5, "Manutenção", "SAIDA", true)
    )
}

object FormasPagamentoRepo {
    val lista = mutableStateListOf(
        FormaPagamento(1, "PIX", true),
        FormaPagamento(2, "Boleto", true),
        FormaPagamento(3, "Cartão Itaú", true),
        FormaPagamento(4, "Cartão Sicoob", true)
    )
}
data class MovimentacaoMensal(
    val mesAbreviado: String,
    val receita: Double,
    val despesa: Double
)

object MockDataService {
    fun obterMovimentacoesMensais(): List<MovimentacaoMensal> = listOf(
        MovimentacaoMensal("Abr", 28500.0, 24100.0),
        MovimentacaoMensal("Mai", 31200.0, 26800.0),
        MovimentacaoMensal("Jun", 27900.0, 25300.0),
        MovimentacaoMensal("Jul", 33400.0, 27600.0),
        MovimentacaoMensal("Ago", 35100.0, 29200.0),
        MovimentacaoMensal("Set", 32800.0, 28450.0)
    )
}

data class Usuario(
    val id: Int,
    val nome: String,
    val email: String,
    val perfil: String,
    val status: String // "Aprovado" ou "Pendente"
)

fun MockDataService.obterUsuarios(): List<Usuario> = listOf(
    Usuario(1, "João Pedro", "joao@fleetflow.com", "Administrador", "Aprovado"),
    Usuario(2, "Maria Silva", "maria@fleetflow.com", "Gestor de Frota", "Aprovado"),
    Usuario(3, "Carlos Souza", "carlos@fleetflow.com", "Financeiro", "Pendente"),
    Usuario(4, "Ana Costa", "ana@fleetflow.com", "Motorista", "Pendente")
)

data class Movimentacao(
    val id: Int,
    val tipo: String, // "ENTRADA" ou "SAIDA"
    val valor: Double,
    val descricao: String,
    val categoriaId: Int,
    val formaPagamentoId: Int,
    val dataVencimento: String,
    var status: String, // "PENDENTE" ou "PAGO"
    var dataPagamento: String? = null,
    var comprovante: String? = null
)

object MovimentacoesRepo {
    val lista = mutableStateListOf<Movimentacao>()
}