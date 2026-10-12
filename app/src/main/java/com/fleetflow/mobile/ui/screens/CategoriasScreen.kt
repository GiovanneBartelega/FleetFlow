package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.model.CategoriaDto
import com.fleetflow.mobile.data.repository.FinanceRepository
import kotlinx.coroutines.launch
import com.fleetflow.mobile.ui.theme.*

@Composable
fun CategoriasScreen(onVoltarClick: () -> Unit, somenteLeitura:Boolean=false) {
    var nomeNovo by remember { mutableStateOf("") }
    var tipoNovo by remember { mutableStateOf("SAIDA") }

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
                Text("CATEGORIAS", color = Branco, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                Text("Nova categoria", color = TextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nomeNovo,
                    onValueChange = { nomeNovo = it },
                    placeholder = { Text("Nome da categoria") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Petroleo,
                        unfocusedBorderColor = Divisor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row {
                    SeletorTipo("ENTRADA", tipoNovo == "ENTRADA") { tipoNovo = "ENTRADA" }
                    Spacer(modifier = Modifier.width(8.dp))
                    SeletorTipo("SAIDA", tipoNovo == "SAIDA") { tipoNovo = "SAIDA" }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (nomeNovo.isNotBlank()) {
                            scope.launch {execute {FinanceRepository.createCat(nomeNovo.trim(),if(tipoNovo=="ENTRADA")"Entrada" else "Saida");nomeNovo=""}}
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
                items(FinanceRepository.categorias, key = { it.id }) { categoria ->
                    CardCategoria(categoria,somenteLeitura,{nome->scope.launch {execute {FinanceRepository.editCat(categoria.id,nome)}}},{scope.launch {execute {FinanceRepository.deleteCat(categoria.id)}}})
                }
            }
        }
    }
}

@Composable
private fun SeletorTipo(label: String, selecionado: Boolean, onClick: () -> Unit) {
    val texto = if (label == "ENTRADA") "Entrada" else "Saída"
    val cor = if (selecionado) Petroleo else CinzaSuave
    val corTexto = if (selecionado) Branco else TextoSecundario

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(texto, color = corTexto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CardCategoria(categoria: CategoriaDto,somenteLeitura:Boolean,onEdit:(String)->Unit,onDelete:()->Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SuperficieCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(categoria.titulo, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val corTipo = if (categoria.tipo == "Entrada") VerdeEsmeralda else Vermelho
            Text(
                if (categoria.tipo == "Entrada") "Entrada" else "Saída",
                color = corTipo,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if(categoria.ehSistema || somenteLeitura) Text(if(categoria.ehSistema) "Sistema" else "Leitura",color=TextoSecundario)
        else {
            var editando by remember {mutableStateOf(false)}
            var nome by remember(categoria.id) {mutableStateOf(categoria.titulo)}
            var excluir by remember {mutableStateOf(false)}
            Column {
                TextButton(onClick={editando=true}){Text("Editar")}
                TextButton(onClick={excluir=true}){Text("Excluir")}
            }
            if(editando)AlertDialog(onDismissRequest={editando=false},title={Text("Editar")},text={OutlinedTextField(value=nome,onValueChange={nome=it})},
                confirmButton={TextButton(onClick={editando=false;onEdit(nome.trim())}){Text("Salvar")}},dismissButton={TextButton(onClick={editando=false}){Text("Cancelar")}})
            if(excluir)AlertDialog(onDismissRequest={excluir=false},title={Text("Excluir cadastro?")},text={Text(categoria.titulo)},
                confirmButton={TextButton(onClick={excluir=false;onDelete()}){Text("Excluir")}},dismissButton={TextButton(onClick={excluir=false}){Text("Cancelar")}})
        }
    }
}