package com.fleetflow.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_historico")
data class DashboardHistoricoEntity(
    @PrimaryKey
    val chave: String,

    val periodo: String,
    val rotulo: String,
    val receita: Double,
    val despesa: Double,
    val inicio: String,
    val fim: String,
    val salvoEm: Long
)