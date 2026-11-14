package com.fleetflow.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "movimentacoes")
data class MovimentacaoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val descricao: String,
    val valorCentavos: Long,
    val tipo: String,            // "ENTRADA" / "SAIDA"
    val categoria: String,
    val formaPagamento: String,
    val data: String,
    val status: String,          // "PENDENTE" / "PAGO"
    val criadoEm: Long,          // Data de Inclusão, em milissegundos. A lista é ordenada por ela.
)