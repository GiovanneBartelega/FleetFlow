package com.fleetflow.mobile.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

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
        Categoria(5, "Manutenção", "SAIDA", true),
        Categoria(6, "Financiamento", "SAIDA", true),
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

data class Financiamento(
    val id: Int,
    val descricao: String,
    val valorParcela: Double,
    val quantidadeParcelas: Int,
    val vencimentoPrimeiraParcela: String,
    val valorQuitacaoAntecipada: Double,
    val categoriaId: Int,
    val formaPagamentoId: Int
)

object FinanciamentosRepo {
    val lista = mutableStateListOf<Financiamento>()
}

// Soma meses a uma data no formato DD/MM/AAAA.
// Não trata estouro de dia (ex.: 31/01 + 1 mês), suficiente para o protótipo.
fun incrementarMes(dataDDMMYYYY: String, mesesAIncrementar: Int): String {
    val partes = dataDDMMYYYY.trim().split("/")
    if (partes.size != 3) return dataDDMMYYYY

    val dia = partes[0].toIntOrNull() ?: return dataDDMMYYYY
    var mes = partes[1].toIntOrNull() ?: return dataDDMMYYYY
    var ano = partes[2].toIntOrNull() ?: return dataDDMMYYYY

    mes += mesesAIncrementar
    while (mes > 12) {
        mes -= 12
        ano += 1
    }

    val diaFormatado = dia.toString().padStart(2, '0')
    val mesFormatado = mes.toString().padStart(2, '0')
    return "$diaFormatado/$mesFormatado/$ano"
}

// Formata uma sequência de dígitos digitados como DD/MM/AAAA,
// mantendo o cursor sempre no final do texto. Usada nos campos de
// data para o usuário não precisar digitar "/" na mão.
fun formatarComoDataComCursor(valorAtual: TextFieldValue): TextFieldValue {
    val apenasDigitos = valorAtual.text.filter { it.isDigit() }.take(8)

    val textoFormatado = buildString {
        for (i in apenasDigitos.indices) {
            append(apenasDigitos[i])
            if (i == 1 || i == 3) append("/")
        }
    }

    return TextFieldValue(
        text = textoFormatado,
        selection = TextRange(textoFormatado.length)
    )
}
data class ResumoFinanceiro(
    val saldoAtual: Double,
    val totalEntradasPendentes: Double,
    val totalSaidasPendentes: Double,
    val saldoProjetado: Double
)

data class ProjecaoMensal(
    val mes: String,
    val entradas: Double,
    val saidas: Double,
    val saldoProjetado: Double
)

object FinanceiroService {

    // Saldo atual: considera somente movimentações pagas.
    fun calcularSaldoAtual(): Double {
        val entradasPagas = MovimentacoesRepo.lista
            .filter {
                it.tipo == "ENTRADA" && it.status == "PAGO"
            }
            .sumOf { it.valor }

        val saidasPagas = MovimentacoesRepo.lista
            .filter {
                it.tipo == "SAIDA" && it.status == "PAGO"
            }
            .sumOf { it.valor }

        return entradasPagas - saidasPagas
    }

    // Resumo do saldo atual e das movimentações pendentes.
    fun obterResumo(): ResumoFinanceiro {
        val saldoAtual = calcularSaldoAtual()

        val entradasPendentes = MovimentacoesRepo.lista
            .filter {
                it.tipo == "ENTRADA" && it.status == "PENDENTE"
            }
            .sumOf { it.valor }

        val saidasPendentes = MovimentacoesRepo.lista
            .filter {
                it.tipo == "SAIDA" && it.status == "PENDENTE"
            }
            .sumOf { it.valor }

        return ResumoFinanceiro(
            saldoAtual = saldoAtual,
            totalEntradasPendentes = entradasPendentes,
            totalSaidasPendentes = saidasPendentes,
            saldoProjetado = saldoAtual + entradasPendentes - saidasPendentes
        )
    }

    // Projeta o saldo acumulado por mês usando as datas de vencimento.
    fun obterProjecaoMensal(): List<ProjecaoMensal> {

        val saldoAtual = calcularSaldoAtual()

        val movimentacoesPendentes = MovimentacoesRepo.lista
            .filter { it.status == "PENDENTE" }

        val movimentacoesPorMes = movimentacoesPendentes
            .filter {
                val partes = it.dataVencimento.split("/")
                partes.size == 3 &&
                    partes[0].toIntOrNull() != null &&
                    partes[1].toIntOrNull() != null &&
                    partes[2].toIntOrNull() != null
            }
            .groupBy {
                val partes = it.dataVencimento.split("/")
                "${partes[1].padStart(2, '0')}/${partes[2]}"
            }
            .toSortedMap(
                compareBy<String> { chave ->
                    val partes = chave.split("/")
                    partes.getOrNull(1)?.toIntOrNull() ?: 0
                }.thenBy { chave ->
                    val partes = chave.split("/")
                    partes.getOrNull(0)?.toIntOrNull() ?: 0
                }
            )

        var saldoAcumulado = saldoAtual

        return movimentacoesPorMes.map { (mes, movimentacoes) ->

            val entradas = movimentacoes
                .filter { it.tipo == "ENTRADA" }
                .sumOf { it.valor }

            val saidas = movimentacoes
                .filter { it.tipo == "SAIDA" }
                .sumOf { it.valor }

            saldoAcumulado += entradas - saidas

            ProjecaoMensal(
                mes = mes,
                entradas = entradas,
                saidas = saidas,
                saldoProjetado = saldoAcumulado
            )
        }
    }
}