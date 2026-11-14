package com.fleetflow.mobile.data.sync

import android.content.Context
import com.fleetflow.mobile.data.repository.ViagemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Disparador único da fila. A fila permanece no Room; quando há rede, o app chama
// este objeto pela tela, pela conectividade e pela inicialização da atividade.
object SyncScheduler {
    fun agendarPeriodico(context: Context) {
        ViagemRepository.init(context.applicationContext)
    }

    fun enviarAgora(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            ViagemRepository.init(context.applicationContext)
            ViagemRepository.enviarPendentes()
        }
    }
}
