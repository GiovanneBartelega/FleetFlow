package com.fleetflow.mobile.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fleetflow.mobile.data.local.AppDatabase
import com.fleetflow.mobile.data.local.CachePainelDao
import com.fleetflow.mobile.data.local.CachePainelEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.reflect.Type
import kotlin.coroutines.cancellation.CancellationException

// Cache local dos painéis da home e de BI (entrega de 18/11). Guarda a resposta de cada
// painel no Room com a hora em que chegou; quem usa é a classe Painel, logo abaixo.
object PainelRepository {

    private lateinit var dao: CachePainelDao
    private val gson = Gson()

    // Conta dona dos números: cada linha leva o id dela na frente da chave. Se a sessão
    // expirar e outra pessoa entrar no mesmo aparelho, ela não vê os números da anterior,
    // porque a API confere a permissão a cada leitura e o cache não.
    @Volatile
    private var conta: String? = null

    // Gravar e limpar não se cruzam: nada gravado fica para trás depois de sair da conta.
    private val trava = Mutex()

    data class Salvo<T>(val valor: T, val salvoEm: Long)

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AppDatabase.get(context.applicationContext).cachePainelDao()
    }

    // Só para o teste unitário: troca o Room por uma versão em memória.
    internal fun iniciarComDao(daoDeTeste: CachePainelDao) {
        dao = daoDeTeste
        conta = null
    }

    // No login e ao abrir o app com a sessão salva.
    fun usarConta(usuarioId: String) {
        conta = usuarioId
    }

    suspend fun <T> ler(chave: String, tipo: Type): Salvo<T>? {
        val dona = conta ?: return null
        if (!::dao.isInitialized) return null
        return withContext(Dispatchers.IO) {
            val linha = dao.buscar(linhaDe(dona, chave)) ?: return@withContext null
            // JSON que não abre (gravado por outra versão do app, por exemplo) é ignorado.
            val valor: T = runCatching { gson.fromJson<T>(linha.json, tipo) }.getOrNull()
                ?: return@withContext null
            Salvo(valor, linha.salvoEm)
        }
    }

    suspend fun salvar(chave: String, valor: Any, salvoEm: Long) {
        if (!::dao.isInitialized) return
        val json = withContext(Dispatchers.Default) { gson.toJson(valor) }
        trava.withLock {
            val dona = conta ?: return
            dao.salvar(CachePainelEntity(linhaDe(dona, chave), json, salvoEm))
            // Faxina: períodos que ninguém abre há 30 dias e o que sobrou de outra conta.
            dao.apagarAntigos(salvoEm - RegrasCache.DESCARTE_MS)
            dao.apagarDeOutrasContas(prefixo(dona))
        }
    }

    // Ao sair da conta: os números financeiros não ficam no aparelho (especificação,
    // "Segurança": banco local limpo ao sair da conta).
    suspend fun limpar() {
        trava.withLock {
            conta = null
            if (::dao.isInitialized) dao.limpar()
        }
    }

    private fun prefixo(conta: String) = "$conta|"
    private fun linhaDe(conta: String, chave: String) = prefixo(conta) + chave
}

// O que a tela mostra para um painel.
sealed interface EstadoPainel<out T> {
    // Nada salvo e a API ainda não respondeu: skeleton, nunca tela em branco nem zeros.
    data object Carregando : EstadoPainel<Nothing>

    // Há números para mostrar, da API agora ou do cache.
    data class Dados<T>(
        val valor: T,
        val atualizadoEm: Long,        // quando esses números vieram da API
        val atualizando: Boolean,      // buscando a versão nova por trás
        val falha: FalhaPainel?,       // a última busca falhou: mostra o aviso com Tentar de novo
    ) : EstadoPainel<T>

    // A busca falhou e não há nada salvo.
    data class Erro(val falha: FalhaPainel) : EstadoPainel<Nothing>
}

data class FalhaPainel(val semConexao: Boolean, val mensagem: String) {
    companion object {
        fun de(erro: Throwable): FalhaPainel = when (erro) {
            // A mensagem do OkHttp é técnica ("Failed to connect to /10.0.2.2:3000").
            is IOException -> FalhaPainel(semConexao = true, mensagem = "Sem conexão com o servidor.")
            else -> FalhaPainel(
                semConexao = false,
                mensagem = erro.message?.takeIf { it.isNotBlank() } ?: "Não foi possível atualizar.",
            )
        }
    }
}

// Um painel da tela (os cards da home, um gráfico, um ranking): mostra na hora o que está
// salvo no aparelho e, se passou da validade, busca a versão nova na API sem apagar a
// antiga da tela. Sem internet, fica o salvo com o aviso; sem salvo, o erro.
//
// `buscar` chama a API e lança exceção se ela falhar (IOException quando não há conexão).
// Na tela, use rememberPainel (ui/components/EstadosPainel.kt).
class Painel<T : Any>(
    private val chave: String,
    private val tipo: Type,
    private val buscar: suspend () -> T,
    private val agora: () -> Long = System::currentTimeMillis,
) {
    var estado by mutableStateOf<EstadoPainel<T>>(EstadoPainel.Carregando)
        private set

    // Abrir a tela e tocar em Atualizar ao mesmo tempo não dispara duas buscas.
    private val mutex = Mutex()

    suspend fun carregar(forcar: Boolean = false) {
        mutex.withLock {
            val salvo = PainelRepository.ler<T>(chave, tipo)
            if (salvo != null && estado !is EstadoPainel.Dados) {
                estado = EstadoPainel.Dados(salvo.valor, salvo.salvoEm, atualizando = false, falha = null)
            }
            if (!RegrasCache.precisaBuscar(salvo?.salvoEm, agora(), forcar)) return@withLock

            estado = when (val atual = estado) {
                is EstadoPainel.Dados -> atual.copy(atualizando = true)
                else -> EstadoPainel.Carregando
            }
            try {
                val novo = buscar()
                val chegouEm = agora()
                PainelRepository.salvar(chave, novo, chegouEm)
                estado = EstadoPainel.Dados(novo, chegouEm, atualizando = false, falha = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val falha = FalhaPainel.de(e)
                estado = when (val atual = estado) {
                    is EstadoPainel.Dados -> atual.copy(atualizando = false, falha = falha)
                    else -> EstadoPainel.Erro(falha)
                }
            }
        }
    }
}
