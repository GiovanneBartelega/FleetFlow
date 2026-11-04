package com.fleetflow.mobile.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fleetflow.mobile.data.repository.ResultadoEnvio
import com.fleetflow.mobile.data.repository.ViagemRepository

// Manda a fila de saída para a API. Roda em segundo plano, às vezes com o app fechado,
// então inicia o repositório por conta própria.
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ViagemRepository.init(applicationContext)
        return when (ViagemRepository.enviarPendentes()) {
            ResultadoEnvio.CONCLUIDO -> Result.success()
            // Sem rede ou erro 5xx: o WorkManager tenta de novo com espera crescente.
            ResultadoEnvio.TENTAR_DE_NOVO -> Result.retry()
        }
    }
}
