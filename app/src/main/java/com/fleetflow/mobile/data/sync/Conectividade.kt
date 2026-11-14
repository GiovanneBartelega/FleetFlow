package com.fleetflow.mobile.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Se o aparelho tem rede agora. Antes o aviso da lista de viagens só sabia que estava sem
// conexão depois de uma carga falhar, e no modo avião mostrava "Enviando para o servidor".
// Quando a rede volta, a fila é enviada na hora (especificação: "ao recuperar a conexão").
object Conectividade {

    var online by mutableStateOf(true)
        private set

    private var iniciada = false

    fun iniciar(context: Context) {
        if (iniciada) return
        iniciada = true
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        online = temInternet(cm)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online = true
                    SyncScheduler.enviarAgora(appContext)
                }

                override fun onLost(network: Network) {
                    online = false
                }
            })
        }
    }

    private fun temInternet(cm: ConnectivityManager): Boolean {
        val rede = cm.activeNetwork ?: return false
        val recursos = cm.getNetworkCapabilities(rede) ?: return false
        return recursos.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
