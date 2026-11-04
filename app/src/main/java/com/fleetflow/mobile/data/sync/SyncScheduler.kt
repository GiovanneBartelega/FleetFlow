package com.fleetflow.mobile.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Quando a fila é enviada (especificação): ao abrir o app, depois de cada gravação,
// quando a conexão volta e a cada 15 minutos. A condição de rede faz o WorkManager
// esperar a conexão voltar sozinho, mesmo com o app fechado.
object SyncScheduler {
    private const val ENVIO_AGORA = "fleetflow-fila-agora"
    private const val ENVIO_PERIODICO = "fleetflow-fila-15min"

    private fun comRede() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // APPEND_OR_REPLACE põe este envio depois do que já está rodando, para a operação
    // gravada agora não ficar esperando o envio de 15 minutos.
    fun enviarAgora(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(comRede())
            // O WorkManager não aceita menos de 10 s; a espera dobra a cada falha.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ENVIO_AGORA, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
    }

    fun agendarPeriodico(context: Context) {
        val pedido = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(comRede())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(ENVIO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }
}
