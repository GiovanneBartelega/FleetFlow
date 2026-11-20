package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DashboardHistoricoDao {

    @Query("""
        SELECT * FROM dashboard_historico
        WHERE periodo = :periodo
        ORDER BY inicio ASC
    """)
    suspend fun buscarPorPeriodo(
        periodo: String
    ): List<DashboardHistoricoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodos(
        dados: List<DashboardHistoricoEntity>
    )

    @Query("""
        DELETE FROM dashboard_historico
        WHERE periodo = :periodo
    """)
    suspend fun limparPeriodo(periodo: String)

    @Query("DELETE FROM dashboard_historico")
    suspend fun limparTudo()
}