package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.CategoriasRepo
import com.fleetflow.mobile.data.FormasPagamentoRepo
import com.fleetflow.mobile.data.Movimentacao
import com.fleetflow.mobile.data.MovimentacoesRepo
import com.fleetflow.mobile.ui.theme.*
import androidx.activity.compose.BackHandler
@Composable
fun MovimentacaoScreen(
    onVoltarClick: () -> Unit
) {

    BackHandler {
        onVoltarClick()
    }

    // restante do código...
    var tipo by remember { mutableStateOf("ENTRADA") }
    var valor by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var categoriaSelecionada by remember { mutableStateOf("") }
    var formaPagamentoSelecionada by remember { mutableStateOf("") }
    var dataVencimento by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("PENDENTE") }
    var dataPagamento by remember { mutableStateOf("") }

    var mensagemErro by remember { mutableStateOf("") }
    var mensagemSucesso by remember { mutableStateOf("") }

    val categorias = CategoriasRepo.lista.filter {
        it.ativo && it.tipo == tipo
    }

    val formasPagamento = FormasPagamentoRepo.lista.filter {
        it.ativo
    }

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

                IconButton(
                    onClick = {
                        onVoltarClick()
                    }
                ) {
                    Text(
                        text = "←",
                        color = Branco,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "MOVIMENTAÇÃO FINANCEIRA",
                    color = Branco,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {

                Text(
                    "Tipo de movimentação",
                    color = TextoSecundario,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SeletorTipoMovimentacao(
                        texto = "Entrada",
                        selecionado = tipo == "ENTRADA",
                        onClick = {
                            tipo = "ENTRADA"
                            categoriaSelecionada = ""
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    SeletorTipoMovimentacao(
                        texto = "Saída",
                        selecionado = tipo == "SAIDA",
                        onClick = {
                            tipo = "SAIDA"
                            categoriaSelecionada = ""
                        }
                    )
                }
            }

            item {

                OutlinedTextField(
                    value = valor,
                    onValueChange = {
                        valor = it
                        mensagemErro = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor") },
                    placeholder = { Text("Ex.: 1500,00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Petroleo,
                        unfocusedBorderColor = Divisor
                    )
                )
            }

            item {

                OutlinedTextField(
                    value = descricao,
                    onValueChange = {
                        descricao = it
                        mensagemErro = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descrição") },
                    placeholder = { Text("Descrição da movimentação") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Petroleo,
                        unfocusedBorderColor = Divisor
                    )
                )
            }

            item {

                CampoSelecao(
                    titulo = "Categoria",
                    valor = categoriaSelecionada.ifBlank {
                        "Selecione uma categoria"
                    },
                    opcoes = categorias.map { it.nome },
                    onSelecionar = {
                        categoriaSelecionada = it
                    }
                )
            }

            item {

                CampoSelecao(
                    titulo = "Forma de pagamento",
                    valor = formaPagamentoSelecionada.ifBlank {
                        "Selecione uma forma de pagamento"
                    },
                    opcoes = formasPagamento.map { it.nome },
                    onSelecionar = {
                        formaPagamentoSelecionada = it
                    }
                )
            }

            item {

                OutlinedTextField(
                    value = dataVencimento,
                    onValueChange = {
                        dataVencimento = it
                        mensagemErro = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Data de vencimento") },
                    placeholder = { Text("DD/MM/AAAA") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Petroleo,
                        unfocusedBorderColor = Divisor
                    )
                )
            }

            item {

                Text(
                    "Status",
                    color = TextoSecundario,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    SeletorTipoMovimentacao(
                        texto = if (tipo == "ENTRADA") {
                            "Recebido"
                        } else {
                            "Pago"
                        },
                        selecionado = status == "PAGO",
                        onClick = {
                            status = "PAGO"
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    SeletorTipoMovimentacao(
                        texto = "Pendente",
                        selecionado = status == "PENDENTE",
                        onClick = {
                            status = "PENDENTE"
                            dataPagamento = ""
                        }
                    )
                }
            }

            if (status == "PAGO") {

                item {

                    OutlinedTextField(
                        value = dataPagamento,
                        onValueChange = {
                            dataPagamento = it
                            mensagemErro = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (tipo == "ENTRADA")
                                    "Data do recebimento"
                                else
                                    "Data do pagamento"
                            )
                        },
                        placeholder = { Text("DD/MM/AAAA") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Petroleo,
                            unfocusedBorderColor = Divisor
                        )
                    )
                }
            }

            item {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuperficieCard)
                        .padding(16.dp)
                ) {

                    Text(
                        "Comprovante",
                        color = TextoSecundario,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        OutlinedButton(
                            onClick = {
                                // Câmera será conectada posteriormente
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Câmera")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                // Galeria será conectada posteriormente
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Galeria")
                        }
                    }
                }
            }

            if (mensagemErro.isNotBlank()) {

                item {

                    Text(
                        mensagemErro,
                        color = Vermelho,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (mensagemSucesso.isNotBlank()) {

                item {

                    Text(
                        mensagemSucesso,
                        color = VerdeEsmeralda,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {

                Button(
                    onClick = {

                        val valorNumerico = valor
                            .replace(",", ".")
                            .toDoubleOrNull()

                        when {
                            valorNumerico == null -> {
                                mensagemErro = "Informe um valor válido."
                                mensagemSucesso = ""
                            }

                            valorNumerico <= 0 -> {
                                mensagemErro = "O valor deve ser maior que zero."
                                mensagemSucesso = ""
                            }

                            descricao.isBlank() -> {
                                mensagemErro = "Informe uma descrição."
                                mensagemSucesso = ""
                            }

                            categoriaSelecionada.isBlank() -> {
                                mensagemErro = "Selecione uma categoria."
                                mensagemSucesso = ""
                            }

                            formaPagamentoSelecionada.isBlank() -> {
                                mensagemErro = "Selecione uma forma de pagamento."
                                mensagemSucesso = ""
                            }

                            dataVencimento.isBlank() -> {
                                mensagemErro = "Informe a data de vencimento."
                                mensagemSucesso = ""
                            }

                            status == "PAGO" && dataPagamento.isBlank() -> {
                                mensagemErro = if (tipo == "ENTRADA") {
                                    "Informe a data do recebimento."
                                } else {
                                    "Informe a data do pagamento."
                                }
                                mensagemSucesso = ""
                            }

                            else -> {

                                val categoria = categorias.firstOrNull {
                                    it.nome == categoriaSelecionada
                                }

                                val formaPagamento = formasPagamento.firstOrNull {
                                    it.nome == formaPagamentoSelecionada
                                }

                                if (categoria == null || formaPagamento == null) {

                                    mensagemErro = "Categoria ou forma de pagamento inválida."
                                    mensagemSucesso = ""

                                } else {

                                    val novoId =
                                        (MovimentacoesRepo.lista.maxOfOrNull { it.id } ?: 0) + 1

                                    MovimentacoesRepo.lista.add(
                                        Movimentacao(
                                            id = novoId,
                                            tipo = tipo,
                                            valor = valorNumerico,
                                            descricao = descricao.trim(),
                                            categoriaId = categoria.id,
                                            formaPagamentoId = formaPagamento.id,
                                            dataVencimento = dataVencimento.trim(),
                                            status = status,
                                            dataPagamento = if (status == "PAGO") {
                                                dataPagamento.trim()
                                            } else {
                                                null
                                            },
                                            comprovante = null
                                        )
                                    )

                                    mensagemErro = ""
                                    mensagemSucesso = "Movimentação salva com sucesso!"

                                    valor = ""
                                    descricao = ""
                                    categoriaSelecionada = ""
                                    formaPagamentoSelecionada = ""
                                    dataVencimento = ""
                                    status = "PENDENTE"
                                    dataPagamento = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Petroleo
                    )
                ) {
                    Text(
                        "Salvar movimentação",
                        color = Branco,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SeletorTipoMovimentacao(
    texto: String,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selecionado) Petroleo else CinzaSuave
            )
            .clickable { onClick() }
            .padding(
                horizontal = 16.dp,
                vertical = 10.dp
            )
    ) {
        Text(
            texto,
            color = if (selecionado) Branco else TextoSecundario,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CampoSelecao(
    titulo: String,
    valor: String,
    opcoes: List<String>,
    onSelecionar: (String) -> Unit
) {
    var aberto by remember { mutableStateOf(false) }

    Column {

        Text(
            titulo,
            color = TextoSecundario,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SuperficieCard)
                .clickable {
                    aberto = !aberto
                }
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    valor,
                    color = if (valor.startsWith("Selecione")) {
                        PlaceholderCor
                    } else {
                        TextoPrincipal
                    },
                    fontSize = 14.sp
                )

                Text(
                    if (aberto) "▲" else "▼",
                    color = TextoSecundario,
                    fontSize = 12.sp
                )
            }
        }

        if (aberto) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuperficieCard)
            ) {

                opcoes.forEach { opcao ->

                    Text(
                        opcao,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelecionar(opcao)
                                aberto = false
                            }
                            .padding(14.dp),
                        color = TextoPrincipal,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
