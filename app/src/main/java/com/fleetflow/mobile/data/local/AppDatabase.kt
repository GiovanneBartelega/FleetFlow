package com.fleetflow.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MovimentacaoEntity::class, VeiculoEntity::class, OrdemServicoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movimentacaoDao(): MovimentacaoDao

    abstract fun frotaDao(): FrotaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Versão 1 tinha só "movimentacoes". A 2 cria as tabelas da frota sem apagar o
        // que já existe no aparelho. O SQL tem de bater com as entidades: o Room confere
        // colunas, tipos e NOT NULL ao abrir o banco.
        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `veiculos` (" +
                        "`id` TEXT NOT NULL, `placa` TEXT NOT NULL, `marca` TEXT NOT NULL, " +
                        "`modelo` TEXT NOT NULL, `anoFabricacao` INTEGER NOT NULL, " +
                        "`capacidadeCargaToneladas` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`hodometroAtual` INTEGER NOT NULL, `intervaloManutencaoKm` INTEGER, " +
                        "`intervaloManutencaoMeses` INTEGER, `hodometroUltimaManutencao` INTEGER, " +
                        "`vencimentoLicenciamento` TEXT, `vencimentoSeguro` TEXT, " +
                        "`consumoMedioReferenciaKmL` TEXT, `syncVersion` INTEGER NOT NULL, " +
                        "`salvoEm` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ordens_servico` (" +
                        "`id` TEXT NOT NULL, `veiculoId` TEXT NOT NULL, `veiculoPlaca` TEXT NOT NULL, " +
                        "`veiculoMarca` TEXT NOT NULL, `veiculoModelo` TEXT NOT NULL, " +
                        "`dataManutencao` TEXT NOT NULL, `descricao` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                        "`valorTotal` TEXT NOT NULL, `hodometroNaManutencao` INTEGER, " +
                        "`dataConclusao` TEXT, `concluida` INTEGER NOT NULL, " +
                        "`movimentacaoId` TEXT NOT NULL, `syncVersion` INTEGER NOT NULL, " +
                        "`salvoEm` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fleetflow.db"
                )
                    .addMigrations(MIGRACAO_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
