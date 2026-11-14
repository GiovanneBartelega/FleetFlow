package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Avisos da resolução de conflito (especificação, "Conflitos"): quando o app descarta uma
// alteração sozinho (viagem apagada no servidor, ou o servidor tinha a alteração mais
// recente), a pessoa precisa ficar sabendo. O aviso fica gravado até ela dispensar, porque
// o envio às vezes roda com o app fechado.
@Entity(tableName = "avisos_sync")
data class AvisoSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registroId: String,      // id local da viagem (ViagemEntity.id)
    val titulo: String,          // placa e trajeto, para a pessoa reconhecer a viagem
    val mensagem: String,
    val criadoEm: Long,
)

@Dao
abstract class AvisoSyncDao {

    @Query("SELECT * FROM avisos_sync ORDER BY criadoEm DESC, id DESC")
    abstract fun observar(): Flow<List<AvisoSyncEntity>>

    @Query("SELECT COUNT(*) FROM avisos_sync")
    abstract fun observarTotal(): Flow<Int>

    @Insert
    abstract suspend fun inserir(aviso: AvisoSyncEntity): Long

    @Query("DELETE FROM avisos_sync WHERE id = :id")
    abstract suspend fun apagar(id: Long)

    @Query("DELETE FROM avisos_sync")
    abstract suspend fun limpar()
}
