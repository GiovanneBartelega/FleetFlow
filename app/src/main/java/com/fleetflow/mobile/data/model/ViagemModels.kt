package com.fleetflow.mobile.data.model

// Formato do JSON das rotas /api/rotas, /api/viagens, /api/itens-checklist e
// /api/checklists-viagem (API Node, branch fleetflow-integrado-28-10).

data class RotaDto(
    val id: String,
    val nome: String,
    val origem: String,
    val destino: String,
    val distanciaEstimadaKm: Int,
    val descricao: String? = null,
    val syncVersion: Int = 0,
    val ativo: Boolean = true,
)

data class RotasPageDto(val data: List<RotaDto>, val page: Int, val totalPages: Int, val total: Int)

// POST /api/rotas e PATCH /api/rotas/{id}. No PATCH, syncVersion faz a API recusar
// (VERSION_CONFLICT) se outra pessoa mudou a rota depois que ela foi carregada.
data class RotaRequestDto(
    val nome: String,
    val origem: String,
    val destino: String,
    val distanciaEstimadaKm: Int,
    val descricao: String? = null,
    val syncVersion: Int? = null,
)

data class RotaResumoDto(val id: String, val nome: String, val origem: String, val destino: String)

data class MotoristaResumoDto(val id: String, val nome: String)

// Status na API: "Aberta" ou "Encerrada". atualizadoEm é o que decide um conflito de
// sincronização: vence a alteração mais recente (especificação, "Conflitos").
data class ViagemDto(
    val id: String,
    val rota: RotaResumoDto,
    val veiculo: VeiculoResumoDto,
    val motorista: MotoristaResumoDto,
    val hodometroInicial: Int,
    val hodometroFinal: Int? = null,
    val dataAbertura: String,
    val dataFechamento: String? = null,
    val status: String,
    val observacao: String? = null,
    val syncVersion: Int,
    val ativo: Boolean = true,
    val criadoEm: String? = null,
    val atualizadoEm: String? = null,
)

data class ViagensPageDto(val data: List<ViagemDto>, val page: Int, val totalPages: Int, val total: Int)

// motoristaId vazio: a API usa o próprio usuário. Datas em ISO-8601 com fuso ("2026-11-04T13:00:00Z").
data class AbrirViagemRequestDto(
    val rotaId: String,
    val veiculoId: String,
    val motoristaId: String? = null,
    val hodometroInicial: Int,
    val dataAbertura: String? = null,
    val observacao: String? = null,
)

data class FecharViagemRequestDto(
    val hodometroFinal: Int,
    val dataFechamento: String? = null,
    val observacao: String? = null,
    val syncVersion: Int? = null,
)

data class ItemChecklistDto(val id: String, val nome: String, val obrigatorio: Boolean)

data class ItensChecklistDto(val data: List<ItemChecklistDto>)

data class RespostaChecklistRequestDto(
    val viagemId: String,
    val itemChecklistId: String,
    val conforme: Boolean,
    val observacao: String? = null,
)

data class RespostaChecklistDto(
    val id: String,
    val viagemId: String,
    val conforme: Boolean,
    val observacao: String? = null,
)

// GET /api/checklists-viagem?viagemId=: as respostas que a API já tem para a viagem.
data class RespostaChecklistServidorDto(
    val id: String,
    val item: ItemChecklistDto,
    val conforme: Boolean,
    val observacao: String? = null,
)

data class RespostasChecklistDto(val data: List<RespostaChecklistServidorDto>)

// PATCH /api/checklists-viagem/{id}: troca uma resposta já gravada na API.
data class AtualizarRespostaChecklistDto(val conforme: Boolean, val observacao: String? = null)

// O que vai para a fila quando o checklist é respondido no aparelho.
data class RespostaChecklistItem(val itemChecklistId: String, val conforme: Boolean, val observacao: String? = null)

data class ChecklistPayload(val respostas: List<RespostaChecklistItem>)
