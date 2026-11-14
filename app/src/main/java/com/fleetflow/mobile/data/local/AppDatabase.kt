package com.fleetflow.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MovimentacaoEntity::class, VeiculoEntity::class, OrdemServicoEntity::class,
        RotaEntity::class, ViagemEntity::class, ItemChecklistEntity::class, OperacaoSyncEntity::class,
        AvisoSyncEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movimentacaoDao(): MovimentacaoDao

    abstract fun frotaDao(): FrotaDao

    abstract fun viagemDao(): ViagemDao

    abstract fun filaSyncDao(): FilaSyncDao

    abstract fun avisoSyncDao(): AvisoSyncDao

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

        // A 3 cria as tabelas das viagens (entrega de 28/10) e a fila de sincronização
        // (entrega de 04/11), de novo sem apagar nada. Mesmo cuidado: cada coluna igual à
        // entidade, sem DEFAULT, porque as entidades não declaram valor padrão.
        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rotas` (" +
                        "`id` TEXT NOT NULL, `nome` TEXT NOT NULL, `origem` TEXT NOT NULL, " +
                        "`destino` TEXT NOT NULL, `distanciaEstimadaKm` INTEGER NOT NULL, " +
                        "`salvoEm` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `viagens` (" +
                        "`id` TEXT NOT NULL, `idServidor` TEXT, `rotaId` TEXT NOT NULL, " +
                        "`rotaNome` TEXT NOT NULL, `rotaOrigem` TEXT NOT NULL, `rotaDestino` TEXT NOT NULL, " +
                        "`veiculoId` TEXT NOT NULL, `veiculoPlaca` TEXT NOT NULL, " +
                        "`veiculoMarca` TEXT NOT NULL, `veiculoModelo` TEXT NOT NULL, " +
                        "`motoristaId` TEXT NOT NULL, `motoristaNome` TEXT NOT NULL, " +
                        "`hodometroInicial` INTEGER NOT NULL, `hodometroFinal` INTEGER, " +
                        "`dataAbertura` TEXT NOT NULL, `dataFechamento` TEXT, `status` TEXT NOT NULL, " +
                        "`observacao` TEXT, `checklistRespondido` INTEGER NOT NULL, " +
                        "`checklistLiberado` INTEGER NOT NULL, " +
                        "`syncVersion` INTEGER NOT NULL, `situacaoSync` TEXT NOT NULL, `erroSync` TEXT, " +
                        "`salvoEm` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `itens_checklist` (" +
                        "`id` TEXT NOT NULL, `nome` TEXT NOT NULL, `obrigatorio` INTEGER NOT NULL, " +
                        "`salvoEm` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fila_sync` (" +
                        "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id` TEXT NOT NULL, " +
                        "`entidade` TEXT NOT NULL, `registroId` TEXT NOT NULL, `verbo` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, `tentativas` INTEGER NOT NULL, `ultimoErro` TEXT, " +
                        "`situacao` TEXT NOT NULL, `criadoEm` INTEGER NOT NULL)"
                )
            }
        }

        // A 5 cria a tabela de avisos da resolução de conflito (entrega de 11/11).
        // A versão 4 só existiu no ZIP do Douglas de 07–08/11 e, no fim da migração dela, o
        // banco ficava com as mesmas tabelas da 3. Por isso as duas sobem para a 5 com o mesmo
        // SQL, e quem instalou aquela versão não precisa desinstalar o app.
        private const val CRIAR_AVISOS_SYNC =
            "CREATE TABLE IF NOT EXISTS `avisos_sync` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `registroId` TEXT NOT NULL, " +
                "`titulo` TEXT NOT NULL, `mensagem` TEXT NOT NULL, `criadoEm` INTEGER NOT NULL)"

        val MIGRACAO_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CRIAR_AVISOS_SYNC)
            }
        }

        val MIGRACAO_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CRIAR_AVISOS_SYNC)
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fleetflow.db"
                )
                    .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_5, MIGRACAO_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
