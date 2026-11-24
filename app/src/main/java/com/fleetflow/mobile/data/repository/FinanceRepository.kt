package com.fleetflow.mobile.data.repository

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.*
import com.fleetflow.mobile.data.api.*
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.local.DashboardHistoricoEntity
import com.fleetflow.mobile.data.model.*
import com.fleetflow.mobile.data.MovimentacaoMensal
import com.fleetflow.mobile.ui.screens.*
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

object FinanceRepository {

    private var context: Context? = null
    private lateinit var api: FinanceApi

    val categorias = mutableStateListOf<CategoriaDto>()
    val formas = mutableStateListOf<FormaDto>()

    var saldo by mutableStateOf<SaldoDto?>(null)
        private set

    var totais by mutableStateOf<TotaisDto?>(null)
        private set

    var totalPages by mutableStateOf(1)
        private set

    // ============================================================
    // ESTADOS DO DASHBOARD
    // ============================================================

    var historicoDashboard by mutableStateOf<List<MovimentacaoMensal>>(
        emptyList()
    )
        private set

    var carregandoDashboard by mutableStateOf(false)
        private set

    var semConexaoDashboard by mutableStateOf(false)
        private set

    var salvoEmDashboard by mutableStateOf<Long?>(null)
        private set

    var erroDashboard by mutableStateOf<String?>(null)
        private set

    // Dados dos endpoints de indicadores da entrega de 18/11.
    var kpisDashboard by mutableStateOf<KpisDto?>(null)
        private set
    var despesasCategoriaDashboard by mutableStateOf<List<DespesaCategoriaDto>>(emptyList())
        private set
    var rankingsDashboard by mutableStateOf<RankingsDto?>(null)
        private set
    var previsaoDashboard by mutableStateOf<PrevisaoCaixaDto?>(null)
        private set
    var alertasSaldoDashboard by mutableStateOf<List<AlertaSaldoDto>>(emptyList())
        private set

    // Uma única resposta cacheada reúne todos os painéis da Home para que os
    // indicadores também continuem visíveis sem internet, não só o gráfico.
    data class DashboardCache(
        val kpis: KpisDto?,
        val despesas: List<DespesaCategoriaDto>,
        val rankings: RankingsDto?,
        val previsao: PrevisaoCaixaDto?,
        val alertas: List<AlertaSaldoDto>,
        val historico: List<MovimentacaoMensal>
    )

    private fun aplicarCacheDashboard(cache: DashboardCache) {
        kpisDashboard = cache.kpis
        despesasCategoriaDashboard = cache.despesas
        rankingsDashboard = cache.rankings
        previsaoDashboard = cache.previsao
        alertasSaldoDashboard = cache.alertas
        historicoDashboard = cache.historico
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        api = ApiClient.getRetrofit(ctx).create(FinanceApi::class.java)
    }

    private fun <T> checked(response: Response<T>): T {
        if (!response.isSuccessful) {
            val error = runCatching {
                Gson().fromJson(
                    response.errorBody()?.string(),
                    ErrorResponseDto::class.java
                )
            }.getOrNull()

            throw ApiException(
                error?.error?.code,
                error?.error?.message ?: "Erro ${response.code()}"
            )
        }

        return response.body() ?: throw Exception("Resposta vazia")
    }

    private fun checkEmpty(response: Response<*>) {
        if (!response.isSuccessful) {
            checked(response)
        }
    }

    // ============================================================
    // CATEGORIAS E FORMAS
    // ============================================================

    suspend fun catalogs() {
        categorias.clear()
        categorias.addAll(checked(api.categorias()).data)

        formas.clear()
        formas.addAll(checked(api.formas()).data)
    }

    // ============================================================
    // MOVIMENTAÇÕES
    // ============================================================

    suspend fun load(
        page: Int = 1,
        busca: String = "",
        status: String = "",
        tipo: String = ""
    ) {
        val q = mutableMapOf(
            "page" to page.toString(),
            "pageSize" to "20"
        )

        if (busca.isNotBlank()) q["busca"] = busca
        if (status.isNotBlank()) q["status"] = status
        if (tipo.isNotBlank()) q["tipo"] = tipo

        val result = checked(api.movimentacoes(q))

        MovimentacaoRepo.itens.clear()
        MovimentacaoRepo.itens.addAll(
            result.data.map { it.toModel() }
        )

        totalPages = result.totalPages
        totais = result.totais
        saldo = checked(api.saldo())
    }

    suspend fun movimentacoesDaViagem(
        viagemId: String
    ): List<Movimentacao> {

        val query = mapOf(
            "page" to "1",
            "pageSize" to "100",
            "viagemId" to viagemId
        )

        val result = checked(api.movimentacoes(query))

        return result.data.map { it.toModel() }
    }

    suspend fun refreshSaldo() {
        saldo = checked(api.saldo())
    }

    // Mantém a API usada pelos painéis já integrados em 11/11.
    suspend fun buscarSaldo(): SaldoDto = checked(api.saldo()).also { saldo = it }

    // ============================================================
    // DASHBOARD - HISTÓRICO FINANCEIRO
    // ============================================================

    private data class DashboardBucket(
        val inicio: LocalDate,
        val fim: LocalDate,
        val rotulo: String
    )

    private fun criarPeriodos(
        periodo: String,
        hoje: LocalDate
    ): List<DashboardBucket> {

        return when (periodo) {

            "7D" -> {
                val inicio = hoje.minusDays(6)

                (0..6).map { indice ->
                    val data = inicio.plusDays(indice.toLong())

                    DashboardBucket(
                        inicio = data,
                        fim = data,
                        rotulo = data.format(
                            DateTimeFormatter.ofPattern("dd/MM")
                        )
                    )
                }
            }

            "30D" -> {
                val inicio = hoje.minusDays(29)

                (0..4).map { indice ->
                    val dataInicio = inicio.plusDays(
                        indice * 7L
                    )

                    val dataFim = minOf(
                        dataInicio.plusDays(6),
                        hoje
                    )

                    DashboardBucket(
                        inicio = dataInicio,
                        fim = dataFim,
                        rotulo = dataInicio.format(
                            DateTimeFormatter.ofPattern("dd/MM")
                        )
                    )
                }
            }

            "6M" -> {
                val primeiroMes = YearMonth.from(
                    hoje.minusMonths(5)
                )

                (0..5).map { indice ->
                    val mes = primeiroMes.plusMonths(
                        indice.toLong()
                    )

                    val inicioMes = mes.atDay(1)

                    val fimMes = minOf(
                        mes.atEndOfMonth(),
                        hoje
                    )

                    DashboardBucket(
                        inicio = inicioMes,
                        fim = fimMes,
                        rotulo = mes.month.getDisplayName(
                            java.time.format.TextStyle.SHORT,
                            Locale("pt", "BR")
                        ).replaceFirstChar { it.uppercase() }
                    )
                }
            }

            else -> emptyList()
        }
    }

    private fun converterValor(valor: String): Double {
        return BigDecimal(valor).toDouble()
    }

    private fun converterData(data: String?): LocalDate? {
        if (data.isNullOrBlank()) return null

        return runCatching {
            LocalDate.parse(data.take(10))
        }.getOrNull()
    }

    private suspend fun buscarMovimentacoesDoPeriodo(
        inicio: LocalDate,
        fim: LocalDate
    ): List<MovDto> {

        val resultado = mutableListOf<MovDto>()

        var pagina = 1
        var totalPaginas = 1

        do {
            val query = mapOf(
                "page" to pagina.toString(),
                "pageSize" to "100",
                "status" to "Pago",
                "pagamentoDe" to inicio.toString(),
                "pagamentoAte" to fim.toString()
            )

            val resposta = checked(
                api.movimentacoes(query)
            )

            resultado.addAll(resposta.data)

            totalPaginas = resposta.totalPages.coerceAtLeast(1)
            pagina++

        } while (
            pagina <= totalPaginas &&
            pagina <= 100
        )

        return resultado
    }

    private fun montarHistorico(
        periodo: String,
        buckets: List<DashboardBucket>,
        movimentacoes: List<MovDto>
    ): List<DashboardHistoricoEntity> {

        val valores = buckets.map {
            it.inicio to Pair(0.0, 0.0)
        }.toMap().toMutableMap()

        movimentacoes.forEach { movimentacao ->

            val data = converterData(
                movimentacao.dataPagamento
            ) ?: return@forEach

            val bucket = buckets.firstOrNull {
                !data.isBefore(it.inicio) &&
                        !data.isAfter(it.fim)
            } ?: return@forEach

            val atual = valores[bucket.inicio] ?: Pair(0.0, 0.0)

            val valor = converterValor(movimentacao.valor)

            valores[bucket.inicio] = if (
                movimentacao.categoria.tipo == "Entrada"
            ) {
                Pair(
                    atual.first + valor,
                    atual.second
                )
            } else {
                Pair(
                    atual.first,
                    atual.second + valor
                )
            }
        }

        val agora = System.currentTimeMillis()

        return buckets.map { bucket ->

            val valor = valores[bucket.inicio] ?: Pair(0.0, 0.0)

            DashboardHistoricoEntity(
                chave = "$periodo-${bucket.inicio}",
                periodo = periodo,
                rotulo = bucket.rotulo,
                receita = valor.first,
                despesa = valor.second,
                inicio = bucket.inicio.toString(),
                fim = bucket.fim.toString(),
                salvoEm = agora
            )
        }
    }

    private fun entidadesParaGrafico(
        dados: List<DashboardHistoricoEntity>
    ): List<MovimentacaoMensal> {

        return dados.map {
            MovimentacaoMensal(
                it.rotulo,
                it.receita,
                it.despesa
            )
        }
    }

    suspend fun carregarHistoricoDashboard(
        periodo: String
    ) {
        val ctx = context ?: return

        if (periodo !in listOf("7D", "30D", "6M")) {
            return
        }

        carregandoDashboard = true
        erroDashboard = null

        try {

            val hoje = LocalDate.now()

            val buckets = criarPeriodos(
                periodo,
                hoje
            )

            val inicio = buckets.first().inicio
            val fim = buckets.last().fim

            val query = mapOf("inicio" to inicio.toString(), "fim" to fim.toString())
            val fluxo = checked(api.fluxoCaixa(query))
            kpisDashboard = checked(api.kpis(query))
            despesasCategoriaDashboard = checked(api.despesasPorCategoria(query)).categorias
            rankingsDashboard = checked(api.rankings(query + ("limite" to "3")))
            val previsaoQuery = mapOf("inicio" to inicio.toString(), "meses" to "12")
            previsaoDashboard = checked(api.previsaoCaixa(previsaoQuery))
            alertasSaldoDashboard = checked(api.alertasSaldo(previsaoQuery)).alertas

            // O gráfico recebe o fluxo calculado no servidor, em vez de somar páginas
            // de movimentações no celular. A chave inclui o mês para cada série ficar cacheada.
            val agora = System.currentTimeMillis()
            val dados = fluxo.meses.map { item ->
                DashboardHistoricoEntity(
                    chave = "$periodo-${item.mes}", periodo = periodo, rotulo = item.mes,
                    receita = item.receitas, despesa = item.despesas,
                    inicio = inicio.toString(), fim = fim.toString(), salvoEm = agora
                )
            }

            val dao = AppDatabase.get(ctx).dashboardHistoricoDao()

            withContext(Dispatchers.IO) {
                dao.limparPeriodo(periodo)
                dao.salvarTodos(dados)
            }

            historicoDashboard = entidadesParaGrafico(dados)
            salvoEmDashboard = System.currentTimeMillis()
            semConexaoDashboard = false
            PainelRepository.salvar(
                "home-indicadores-$periodo",
                DashboardCache(kpisDashboard, despesasCategoriaDashboard, rankingsDashboard, previsaoDashboard, alertasSaldoDashboard, historicoDashboard),
                salvoEmDashboard!!
            )

        } catch (e: Exception) {

            semConexaoDashboard = e is IOException

            erroDashboard = if (e is IOException) {
                "Sem conexão. Exibindo os últimos dados disponíveis."
            } else {
                e.message ?: "Não foi possível atualizar o gráfico."
            }

            // Primeiro recupera todos os painéis da Home; se este for um cache de
            // versão antiga, mantém a compatibilidade com o histórico do Room.
            val cacheCompleto = PainelRepository.ler<DashboardCache>(
                "home-indicadores-$periodo",
                DashboardCache::class.java
            )
            if (cacheCompleto != null) {
                aplicarCacheDashboard(cacheCompleto.valor)
                salvoEmDashboard = cacheCompleto.salvoEm
                return
            }

            // Recupera o histórico salvo no Room.
            runCatching {

                val dao = AppDatabase.get(ctx).dashboardHistoricoDao()

                val cache = withContext(Dispatchers.IO) {
                    dao.buscarPorPeriodo(periodo)
                }

                if (cache.isNotEmpty()) {
                    historicoDashboard = entidadesParaGrafico(cache)

                    salvoEmDashboard = cache.maxOfOrNull {
                        it.salvoEm
                    }
                } else {
                    historicoDashboard = emptyList()
                    salvoEmDashboard = null
                }
            }.onFailure {
                historicoDashboard = emptyList()
            }

        } finally {
            carregandoDashboard = false
        }
    }

    // ============================================================
    // CADASTRO E EDIÇÃO
    // ============================================================

    suspend fun save(m: Movimentacao): String {

        val cat = categorias.firstOrNull {
            it.titulo == m.categoria
        } ?: throw Exception("Selecione uma categoria")

        val forma = formas.firstOrNull {
            it.nome == m.formaPagamento
        } ?: throw Exception("Selecione uma forma")

        val body = MovRequestDto(
            valor = BigDecimal.valueOf(
                m.valorCentavos,
                2
            ).toPlainString(),

            descricao = m.descricao,
            categoriaId = cat.id,
            formaPagamentoId = forma.id,

            status = if (m.status == StatusMov.PAGO) {
                "Pago"
            } else {
                "Pendente"
            },

            dataVencimento = isoDate(m.dataVencimento),

            dataPagamento = if (m.status == StatusMov.PAGO) {
                isoDate(m.dataPagamento)
            } else {
                null
            },

            syncVersion = m.syncVersion,
            viagemId = m.viagemId
        )

        val row = checked(
            if (m.syncVersion == null) {
                api.criar(body)
            } else {
                api.editar(m.id, body)
            }
        )

        return row.id
    }

    suspend fun upload(id: String, uri: Uri) {

        val ctx = context
            ?: throw Exception("Aplicativo não iniciado")

        val bytes = withContext(Dispatchers.IO) {

            val bitmap = ImagemComprovante.carregar(
                ctx,
                uri,
                1600
            ) ?: throw Exception("Não foi possível abrir a imagem")

            ByteArrayOutputStream().use { stream ->

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    80,
                    stream
                )

                bitmap.recycle()

                stream.toByteArray()
            }
        }

        checked(
            api.comprovante(
                id,
                bytes.toRequestBody("image/jpeg".toMediaType())
            )
        )
    }

    suspend fun pay(m: Movimentacao) {
        checked(
            api.pagar(
                m.id,
                mapOf(
                    "dataPagamento" to
                            java.time.LocalDate.now().toString()
                )
            )
        )
    }

    suspend fun delete(m: Movimentacao) {
        checkEmpty(api.remover(m.id))
    }

    // ============================================================
    // CATEGORIAS
    // ============================================================

    suspend fun createCat(nome: String, tipo: String) {
        checked(
            api.criarCategoria(
                mapOf(
                    "titulo" to nome,
                    "tipo" to tipo
                )
            )
        )
        catalogs()
    }

    suspend fun editCat(id: String, nome: String) {
        checked(
            api.editarCategoria(
                id,
                mapOf("titulo" to nome)
            )
        )
        catalogs()
    }

    suspend fun deleteCat(id: String) {
        checkEmpty(api.removerCategoria(id))
        catalogs()
    }

    // ============================================================
    // FORMAS DE PAGAMENTO
    // ============================================================

    suspend fun createForma(nome: String) {
        checked(
            api.criarForma(
                mapOf("nome" to nome)
            )
        )
        catalogs()
    }

    suspend fun editForma(id: String, nome: String) {
        checked(
            api.editarForma(
                id,
                mapOf("nome" to nome)
            )
        )
        catalogs()
    }

    suspend fun deleteForma(id: String) {
        checkEmpty(api.removerForma(id))
        catalogs()
    }

    suspend fun createDivida(body: DividaRequestDto) {
        checked(api.divida(body))
    }

    // ============================================================
    // LIMPEZA DE ESTADO EM MEMÓRIA
    // ============================================================

    fun clear() {
        categorias.clear()
        formas.clear()
        saldo = null
        totais = null
        MovimentacaoRepo.itens.clear()

        historicoDashboard = emptyList()
        carregandoDashboard = false
        semConexaoDashboard = false
        salvoEmDashboard = null
        erroDashboard = null
        kpisDashboard = null
        despesasCategoriaDashboard = emptyList()
        rankingsDashboard = null
        previsaoDashboard = null
        alertasSaldoDashboard = emptyList()
    }

    // Ao sair da conta, o histórico financeiro também precisa sair do aparelho.
    suspend fun limparHistoricoDashboard() {
        val ctx = context ?: return
        withContext(Dispatchers.IO) {
            AppDatabase.get(ctx).dashboardHistoricoDao().limparTudo()
        }
        historicoDashboard = emptyList()
        salvoEmDashboard = null
    }
}

// ============================================================
// DATAS
// ============================================================

fun isoDate(text: String?): String? {

    if (text.isNullOrBlank()) return null

    val format = SimpleDateFormat(
        "dd/MM/yyyy",
        Locale.ROOT
    ).apply {
        isLenient = false
    }

    val position = java.text.ParsePosition(0)

    val date = format.parse(text, position)
        ?: throw IllegalArgumentException(
            "Data inválida. Use dd/MM/aaaa"
        )

    if (position.index != text.length) {
        throw IllegalArgumentException(
            "Data inválida. Use dd/MM/aaaa"
        )
    }

    return SimpleDateFormat(
        "yyyy-MM-dd",
        Locale.ROOT
    ).format(date)
}

private fun displayDate(iso: String?): String {
    return iso?.split("-")?.let {
        if (it.size == 3) {
            "${it[2]}/${it[1]}/${it[0]}"
        } else {
            ""
        }
    } ?: ""
}

// ============================================================
// CONVERSÃO DA MOVIMENTAÇÃO DA API PARA O MODELO DA TELA
// ============================================================

fun MovDto.toModel() = Movimentacao(
    id = id,
    descricao = descricao,

    valorCentavos = BigDecimal(valor)
        .movePointRight(2)
        .longValueExact(),

    tipo = if (categoria.tipo == "Entrada") {
        Tipo.ENTRADA
    } else {
        Tipo.SAIDA
    },

    categoria = categoria.titulo,
    formaPagamento = formaPagamento.nome,

    data = displayDate(dataPagamento ?: dataVencimento),

    status = if (status == "Pago") {
        StatusMov.PAGO
    } else {
        StatusMov.PENDENTE
    },

    dataVencimento = displayDate(dataVencimento),
    dataPagamento = displayDate(dataPagamento),
    syncVersion = syncVersion,
    viagemId = viagemId
)
