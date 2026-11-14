package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimentacaoDao {

    // Mais recente primeiro.
    @Query("SELECT * FROM movimentacoes ORDER BY criadoEm DESC")
    fun observarTodas(): Flow<List<MovimentacaoEntity>>

    @Query("SELECT COUNT(*) FROM movimentacoes")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(m: MovimentacaoEntity)

    @Query("DELETE FROM movimentacoes")
    suspend fun limparTudo()
}