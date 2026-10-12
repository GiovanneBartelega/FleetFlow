package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.model.FormaDto
import com.fleetflow.mobile.data.repository.FinanceRepository
import kotlinx.coroutines.launch
import com.fleetflow.mobile.ui.theme.*

@Composable
fun FormasPagamentoScreen(onVoltarClick: () -> Unit, somenteLeitura:Boolean=false) {
    var nomeNovo by remember { mutableStateOf("") }

    val scope=rememberCoroutineScope()
    var erro by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    suspend fun execute(action:suspend()->Unit) {busy=true;runCatching {action()}.onFailure {erro=it.message};busy=false}
    LaunchedEffect(Unit) {execute {FinanceRepository.catalogs()}}
    Scaffold(
        containerColor = BackgroundTela,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Petroleo)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVoltarClick) {
                    Text("←", color = Branco, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("FORMAS DE PAGAMENTO", color = Branco, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            erro?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!somenteLeitura)             Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuperficieCard)
                    .padding(16.dp)
            ) {
                Text("Nova forma de pagamento", color = TextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nomeNovo,
                    onValueChange = { nomeNovo = it },
                    placeholder = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Petroleo,
                        unfocusedBorderColor = Divisor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (nomeNovo.isNotBlank()) {
                            scope.launch {execute {FinanceRepository.createForma(nomeNovo.trim());nomeNovo=""}}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
                ) {
                    Text("Adicionar", color = Branco, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(FinanceRepository.formas, key = { it.id }) { forma ->
                    CardFormaPagamento(forma,somenteLeitura,{nome->scope.launch {execute {FinanceRepository.editForma(forma.id,nome)}}},{scope.launch {execute {FinanceRepository.deleteForma(forma.id)}}})
                }
            }
        }
    }
}

@Composable
private fun CardFormaPagamento(forma: FormaDto,somenteLeitura:Boolean,onEdit:(String)->Unit,onDelete:()->Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SuperficieCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AmbarDourado),
            contentAlignment = Alignment.Center
        ) {
            Text(forma.nome.take(1), color = Petroleo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(forma.nome, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))

        if(forma.ehSistema || somenteLeitura) Text(if(forma.ehSistema) "Sistema" else "Leitura",color=TextoSecundario)
        else {
            var editando by remember {mutableStateOf(false)}
            var nome by remember(forma.id) {mutableStateOf(forma.nome)}
            var excluir by remember {mutableStateOf(false)}
            Column {
                TextButton(onClick={editando=true}){Text("Editar")}
                TextButton(onClick={excluir=true}){Text("Excluir")}
            }
            if(editando)AlertDialog(onDismissRequest={editando=false},title={Text("Editar")},text={OutlinedTextField(value=nome,onValueChange={nome=it})},
                confirmButton={TextButton(onClick={editando=false;onEdit(nome.trim())}){Text("Salvar")}},dismissButton={TextButton(onClick={editando=false}){Text("Cancelar")}})
            if(excluir)AlertDialog(onDismissRequest={excluir=false},title={Text("Excluir cadastro?")},text={Text(forma.nome)},
                confirmButton={TextButton(onClick={excluir=false;onDelete()}){Text("Excluir")}},dismissButton={TextButton(onClick={excluir=false}){Text("Cancelar")}})
        }
    }
}