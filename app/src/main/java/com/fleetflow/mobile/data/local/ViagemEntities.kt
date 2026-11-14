package com.fleetflow.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fleetflow.mobile.data.model.ItemChecklistDto
import com.fleetflow.mobile.data.model.RotaDto
import com.fleetflow.mobile.data.model.ViagemDto

// Situação de um registro em relação ao servidor. Pendente: tem operação na fila.
// Erro: a API recusou uma operação e o registro precisa de atenção.
// Conflito: o servidor está num estado que impede a operação agora (por exemplo, o veículo
// já está em outra viagem lá). Fica sinalizado e é tentado de novo a cada envio, sem contar
// tentativa, até o servidor liberar ou a pessoa descartar.
object SituacaoSync {
    const val SINCRONIZADA = "Sincronizada"
    const val PENDENTE = "Pendente"
    const val ERRO = "Erro"
    const val CONFLITO = "Conflito"
}

object StatusViagem {
    const val ABERTA = "Aberta"
    const val ENCERRADA = "Encerrada"
}

// Cópia das rotas para o motorista escolher a rota sem internet.
@Entity(tableName = "rotas")
data class RotaEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val origem: String,
    val destino: String,
    val distanciaEstimadaKm: Int,
    val salvoEm: Long,
)

// A viagem nasce aqui. `id` é gerado no aparelho e nunca muda; `idServidor` fica vazio
// até a abertura chegar à API, que cria o id dela.
@Entity(tableName = "viagens")
data class ViagemEntity(
    @PrimaryKey val id: String,
    val idServidor: String?,
    val rotaId: String,
    val rotaNome: String,
    val rotaOrigem: String,
    val rotaDestino: String,
    val veiculoId: String,
    val veiculoPlaca: String,
    val veiculoMarca: String,
    val veiculoModelo: String,
    val motoristaId: String,
    val motoristaNome: String,
    val hodometroInicial: Int,
    val hodometroFinal: Int?,
    val dataAbertura: String,          // ISO-8601, como a API devolve
    val dataFechamento: String?,
    val status: String,                // StatusViagem
    val observacao: String?,
    val checklistRespondido: Boolean,
    // false quando algum item obrigatório ficou não conforme: a API não fecha a viagem
    // assim, então o aparelho também não fecha.
    val checklistLiberado: Boolean,
    val syncVersion: Int,              // 0 enquanto a API não conhece a viagem
    val situacaoSync: String,          // SituacaoSync
    val erroSync: String?,
    val salvoEm: Long,
)

// Itens da conferência de saída, para o checklist funcionar sem internet.
@Entity(tableName = "itens_checklist")
data class ItemChecklistEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val obrigatorio: Boolean,
    val salvoEm: Long,
)

fun RotaDto.paraEntity(salvoEm: Long) = RotaEntity(
    id = id,
    nome = nome,
    origem = origem,
    destino = destino,
    distanciaEstimadaKm = distanciaEstimadaKm,
    salvoEm = salvoEm,
)

fun ItemChecklistDto.paraEntity(salvoEm: Long) = ItemChecklistEntity(
    id = id,
    nome = nome,
    obrigatorio = obrigatorio,
    salvoEm = salvoEm,
)

// Viagem que veio da API. `idLocal` é o id que ela já tem no aparelho, ou o próprio id
// do servidor quando ela é nova aqui.
fun ViagemDto.paraEntity(
    idLocal: String,
    checklistRespondido: Boolean,
    checklistLiberado: Boolean,
    salvoEm: Long,
) = ViagemEntity(
    id = idLocal,
    idServidor = id,
    rotaId = rota.id,
    rotaNome = rota.nome,
    rotaOrigem = rota.origem,
    rotaDestino = rota.destino,
    veiculoId = veiculo.id,
    veiculoPlaca = veiculo.placa,
    veiculoMarca = veiculo.marca,
    veiculoModelo = veiculo.modelo,
    motoristaId = motorista.id,
    motoristaNome = motorista.nome,
    hodometroInicial = hodometroInicial,
    hodometroFinal = hodometroFinal,
    dataAbertura = dataAbertura,
    dataFechamento = dataFechamento,
    status = status,
    observacao = observacao,
    checklistRespondido = checklistRespondido,
    checklistLiberado = checklistLiberado,
    syncVersion = syncVersion,
    situacaoSync = SituacaoSync.SINCRONIZADA,
    erroSync = null,
    salvoEm = salvoEm,
)
