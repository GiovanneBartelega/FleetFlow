package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ViagemDao {

    // A tela observa o Room: o que o trabalho em segundo plano grava aparece sozinho.
    @Query("SELECT * FROM viagens ORDER BY dataAbertura DESC")
    abstract fun observarViagens(): Flow<List<ViagemEntity>>

    @Query("SELECT * FROM viagens")
    abstract suspend fun listarViagens(): List<ViagemEntity>

    @Query("SELECT * FROM viagens WHERE id = :id")
    abstract suspend fun buscar(id: String): ViagemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarViagem(viagem: ViagemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarViagens(itens: List<ViagemEntity>)

    @Query("DELETE FROM viagens WHERE id IN (:ids)")
    abstract suspend fun apagarViagens(ids: List<String>)

    // Atualizações pontuais do envio em segundo plano: mudam só estas colunas, para não
    // desfazer o que a tela gravou no mesmo registro enquanto o envio rodava.
    @Query("UPDATE viagens SET idServidor = :idServidor, syncVersion = :syncVersion WHERE id = :id")
    abstract suspend fun definirIdServidor(id: String, idServidor: String, syncVersion: Int)

    @Query("DELETE FROM viagens WHERE idServidor = :idServidor AND id != :id")
    abstract suspend fun apagarCopias(id: String, idServidor: String)

    // A abertura chegou à API. Se a lista da API trouxe a mesma viagem antes deste momento,
    // ela entrou no aparelho como um registro novo; essa cópia sai para não aparecer duas vezes.
    @Transaction
    open suspend fun adotarIdServidor(id: String, idServidor: String, syncVersion: Int) {
        apagarCopias(id, idServidor)
        definirIdServidor(id, idServidor, syncVersion)
    }

    @Query("UPDATE viagens SET situacaoSync = :situacao, erroSync = :erro WHERE id = :id")
    abstract suspend fun definirSituacao(id: String, situacao: String, erro: String?)

    @Query("UPDATE viagens SET syncVersion = :syncVersion WHERE id = :id")
    abstract suspend fun definirSyncVersion(id: String, syncVersion: Int)

    @Query("DELETE FROM viagens")
    abstract suspend fun limparViagens()

    @Query("SELECT * FROM rotas ORDER BY nome")
    abstract fun observarRotas(): Flow<List<RotaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserirRotas(itens: List<RotaEntity>)

    @Query("DELETE FROM rotas")
    abstract suspend fun limparRotas()

    @Query("SELECT * FROM itens_checklist ORDER BY nome")
    abstract fun observarItensChecklist(): Flow<List<ItemChecklistEntity>>

    @Query("SELECT * FROM itens_checklist ORDER BY nome")
    abstract suspend fun listarItensChecklist(): List<ItemChecklistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserirItensChecklist(itens: List<ItemChecklistEntity>)

    @Query("DELETE FROM itens_checklist")
    abstract suspend fun limparItensChecklist()

    // Troca a cópia inteira de uma vez, como na frota.
    @Transaction
    open suspend fun substituirRotas(itens: List<RotaEntity>) {
        limparRotas()
        inserirRotas(itens)
    }

    @Transaction
    open suspend fun substituirItensChecklist(itens: List<ItemChecklistEntity>) {
        limparItensChecklist()
        inserirItensChecklist(itens)
    }

    @Transaction
    open suspend fun limparTudo() {
        limparViagens()
        limparRotas()
        limparItensChecklist()
    }
}
