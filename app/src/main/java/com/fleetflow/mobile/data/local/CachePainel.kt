package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// Cache local dos painéis (especificação, Módulo 5, "Desempenho": os resultados ficam em
// cache local com carimbo de hora). Uma linha por painel e período: a resposta da API como
// veio, em JSON, e a hora em que ela chegou. Assim a home abre na hora, mesmo sem internet,
// e cada tela de BI nova só precisa de uma chave, sem tabela nova.
@Entity(tableName = "cache_paineis")
data class CachePainelEntity(
    @PrimaryKey val chave: String,   // id da conta + "|" + painel e filtros (RegrasCache.chave)
    val json: String,
    val salvoEm: Long,               // quando a API respondeu (hora do aparelho)
)

@Dao
abstract class CachePainelDao {

    @Query("SELECT * FROM cache_paineis WHERE chave = :chave")
    abstract suspend fun buscar(chave: String): CachePainelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvar(linha: CachePainelEntity)

    // Períodos que ninguém abre há muito tempo saem do aparelho.
    @Query("DELETE FROM cache_paineis WHERE salvoEm < :antes")
    abstract suspend fun apagarAntigos(antes: Long)

    // Linhas de outra conta: a sessão expirou e outra pessoa entrou no mesmo aparelho.
    @Query("DELETE FROM cache_paineis WHERE substr(chave, 1, length(:prefixo)) <> :prefixo")
    abstract suspend fun apagarDeOutrasContas(prefixo: String)

    // Sair da conta apaga os números financeiros do aparelho (especificação, "Segurança").
    @Query("DELETE FROM cache_paineis")
    abstract suspend fun limpar()
}
