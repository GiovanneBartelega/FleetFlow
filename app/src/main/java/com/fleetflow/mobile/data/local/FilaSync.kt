package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Fila de saída (especificação, "Sincronização offline-first"): uma linha por operação
// que ainda não chegou à API. `seq` dá a ordem de envio; `id` é o id da operação, enviado
// no cabeçalho Idempotency-Key.
object EntidadeSync {
    const val VIAGEM = "viagem"
}

object VerboSync {
    const val ABRIR = "abrir"
    const val CHECKLIST = "checklist"
    const val FECHAR = "fechar"
}

@Entity(tableName = "fila_sync")
data class OperacaoSyncEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    val entidade: String,
    val registroId: String,        // id local do registro (ViagemEntity.id)
    val verbo: String,
    val payload: String,           // JSON do corpo da requisição
    val tentativas: Int = 0,
    val ultimoErro: String? = null,
    val situacao: String = SituacaoSync.PENDENTE,
    val criadoEm: Long,
)

@Dao
abstract class FilaSyncDao {

    @Query("SELECT * FROM fila_sync ORDER BY seq")
    abstract suspend fun listar(): List<OperacaoSyncEntity>

    // A tela de pendências observa a fila inteira: o que o envio em segundo plano muda
    // aparece sozinho.
    @Query("SELECT * FROM fila_sync ORDER BY seq")
    abstract fun observar(): Flow<List<OperacaoSyncEntity>>

    // Quantas operações ainda não subiram (pendentes, com erro ou em conflito).
    @Query("SELECT COUNT(*) FROM fila_sync")
    abstract fun observarTotal(): Flow<Int>

    @Query("SELECT * FROM fila_sync WHERE registroId = :registroId ORDER BY seq")
    abstract suspend fun listarDoRegistro(registroId: String): List<OperacaoSyncEntity>

    @Insert
    abstract suspend fun inserir(operacao: OperacaoSyncEntity): Long

    @Update
    abstract suspend fun atualizar(operacao: OperacaoSyncEntity)

    @Query("DELETE FROM fila_sync WHERE seq = :seq")
    abstract suspend fun apagar(seq: Long)

    @Query("DELETE FROM fila_sync WHERE registroId = :registroId")
    abstract suspend fun apagarDoRegistro(registroId: String)

    @Query("DELETE FROM fila_sync")
    abstract suspend fun limpar()
}
