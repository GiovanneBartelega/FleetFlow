package com.fleetflow.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class FrotaDao {

    @Query("SELECT * FROM veiculos ORDER BY marca, modelo, placa")
    abstract suspend fun listarVeiculos(): List<VeiculoEntity>

    // Mais recente primeiro, como na lista da API.
    @Query("SELECT * FROM ordens_servico ORDER BY dataManutencao DESC, salvoEm DESC")
    abstract suspend fun listarOrdens(): List<OrdemServicoEntity>

    // Quando a lista mostrada foi gravada pela última vez (null = nunca).
    @Query("SELECT MAX(salvoEm) FROM veiculos")
    abstract suspend fun ultimaGravacao(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserirVeiculos(itens: List<VeiculoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserirOrdens(itens: List<OrdemServicoEntity>)

    @Query("DELETE FROM veiculos")
    abstract suspend fun limparVeiculos()

    @Query("DELETE FROM ordens_servico")
    abstract suspend fun limparOrdens()

    // Troca a cópia inteira de uma vez: ou grava tudo, ou nada. Assim um veículo
    // excluído no servidor também some daqui.
    @Transaction
    open suspend fun substituirVeiculos(itens: List<VeiculoEntity>) {
        limparVeiculos()
        inserirVeiculos(itens)
    }

    @Transaction
    open suspend fun substituirOrdens(itens: List<OrdemServicoEntity>) {
        limparOrdens()
        inserirOrdens(itens)
    }

    @Transaction
    open suspend fun limparTudo() {
        limparOrdens()
        limparVeiculos()
    }
}
