package com.fleetflow.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.model.DividaRequestDto
import com.fleetflow.mobile.data.repository.FinanceRepository
import com.fleetflow.mobile.data.repository.isoDate
import com.fleetflow.mobile.ui.components.*
import com.fleetflow.mobile.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun FinanciamentoScreen(onVoltarClick:()->Unit,formatarComoData:(String)->String={it}) {
    BackHandler {onVoltarClick()}
    var descricao by remember {mutableStateOf("")}
    var valor by remember {mutableStateOf("")}
    var quantidade by remember {mutableStateOf("")}
    var vencimento by remember {mutableStateOf(hoje)}
    var categoria by remember {mutableStateOf<String?>(null)}
    var forma by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit) {runCatching {FinanceRepository.catalogs()}.onFailure {message=it.message?:"Erro"}}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Petroleo).padding(8.dp)) {
            TextButton(onClick=onVoltarClick){Text("Voltar",color=Branco)}
            Text("FINANCIAMENTO",color=Branco,modifier=Modifier.padding(12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value=descricao,onValueChange={descricao=it},label={Text("Descrição")},modifier=Modifier.fillMaxWidth())
            MoneyField(value=valor,onValueChange={valor=it},label="Valor da parcela")
            OutlinedTextField(value=quantidade,onValueChange={quantidade=it.filter(Char::isDigit)},label={Text("Quantidade de parcelas (1–480)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=vencimento,onValueChange={vencimento=it},label={Text("Primeiro vencimento dd/MM/aaaa")},modifier=Modifier.fillMaxWidth())
            AppDropdown("Categoria",FinanceRepository.categorias.filter {it.tipo=="Saida"}.map {it.titulo},categoria,{categoria=it})
            AppDropdown("Forma de pagamento",FORMAS,forma,{forma=it})
            if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.primary)
            Button(enabled=!busy,onClick={scope.launch {
                busy=true
                runCatching {
                    require(descricao.isNotBlank()) {"Informe a descrição"}
                    val count=quantidade.toIntOrNull()?:0;require(count in 1..480) {"Informe de 1 a 480 parcelas"}
                    val centavos=valor.toLongOrNull()?:0;require(centavos>0){"Informe um valor maior que zero"}
                    val cat=FinanceRepository.categorias.firstOrNull {it.titulo==categoria}?:error("Selecione uma categoria")
                    val method=FinanceRepository.formas.firstOrNull {it.nome==forma}?:error("Selecione uma forma de pagamento")
                    val date=isoDate(vencimento)?:error("Informe uma data")
                    FinanceRepository.createDivida(DividaRequestDto(descricao.trim(),cat.id,method.id,count,BigDecimal.valueOf(centavos,2).toPlainString(),date))
                }.onSuccess {message="Financiamento e parcelas salvos na API";descricao="";valor="";quantidade=""}
                 .onFailure {message=it.message?:"Falha ao salvar"}
                busy=false
            }},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Salvando…" else "Salvar financiamento")}
        }
    }
}
