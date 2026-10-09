package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.*
import com.fleetflow.mobile.ui.theme.*

@Composable
fun FinanciamentoScreen(
    onVoltarClick: () -> Unit,
    formatarComoData: (String) -> String
) {

    var descricao by remember { mutableStateOf("") }
    var valorParcela by remember { mutableStateOf("") }
    var quantidadeParcelas by remember { mutableStateOf("") }
    var vencimentoPrimeira by remember { mutableStateOf("") }
    var valorQuitacao by remember { mutableStateOf("") }
    var categoriaSelecionada by remember { mutableStateOf("") }
    var formaPagamentoSelecionada by remember { mutableStateOf("") }

    var mensagemErro by remember { mutableStateOf("") }
    var mensagemSucesso by remember { mutableStateOf("") }

    val categorias = CategoriasRepo.lista.filter { it.ativo && it.tipo == "SAIDA" }
    val formasPagamento = FormasPagamentoRepo.lista.filter { it.ativo }

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
                    Text("←", color = Branco, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("FINANCIAMENTO / DÍVIDA", color = Branco, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it; mensagemErro = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descrição") },
                placeholder = { Text("Ex.: Financiamento do caminhão B 1234 KLM") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Petroleo,
                    unfocusedBorderColor = Divisor
                )
            )

            CampoSelecao(
                titulo = "Categoria",
                valor = categoriaSelecionada.ifBlank { "Selecione uma categoria" },
                opcoes = categorias.map { it.nome },
                onSelecionar = { categoriaSelecionada = it }
            )

            CampoSelecao(
                titulo = "Forma de pagamento",
                valor = formaPagamentoSelecionada.ifBlank { "Selecione uma forma de pagamento" },
                opcoes = formasPagamento.map { it.nome },
                onSelecionar = { formaPagamentoSelecionada = it }
            )

            OutlinedTextField(
                value = valorParcela,
                onValueChange = { valorParcela = it; mensagemErro = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Valor da parcela") },
                placeholder = { Text("Ex.: 2500,00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Petroleo,
                    unfocusedBorderColor = Divisor
                )
            )

            OutlinedTextField(
                value = quantidadeParcelas,
                onValueChange = { quantidadeParcelas = it.filter { c -> c.isDigit() }; mensagemErro = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Quantidade de parcelas") },
                placeholder = { Text("Ex.: 24") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Petroleo,
                    unfocusedBorderColor = Divisor
                )
            )

            OutlinedTextField(
                value = vencimentoPrimeira,
                onValueChange = { vencimentoPrimeira = formatarComoData(it); mensagemErro = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Vencimento da 1ª parcela") },
                placeholder = { Text("DD/MM/AAAA") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Petroleo,
                    unfocusedBorderColor = Divisor
                )
            )

            OutlinedTextField(
                value = valorQuitacao,
                onValueChange = { valorQuitacao = it; mensagemErro = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Valor para quitação antecipada") },
                placeholder = { Text("Ex.: 48000,00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Petroleo,
                    unfocusedBorderColor = Divisor
                )
            )

            if (mensagemErro.isNotBlank()) {
                Text(mensagemErro, color = Vermelho, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (mensagemSucesso.isNotBlank()) {
                Text(mensagemSucesso, color = VerdeEsmeralda, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    val valorParcelaNumerico = valorParcela.replace(",", ".").toDoubleOrNull()
                    val quantidadeNumerica = quantidadeParcelas.toIntOrNull()
                    val valorQuitacaoNumerico = valorQuitacao.replace(",", ".").toDoubleOrNull()

                    when {
                        descricao.isBlank() -> {
                            mensagemErro = "Informe uma descrição."
                        }
                        categoriaSelecionada.isBlank() -> {
                            mensagemErro = "Selecione uma categoria."
                        }
                        formaPagamentoSelecionada.isBlank() -> {
                            mensagemErro = "Selecione uma forma de pagamento."
                        }
                        valorParcelaNumerico == null || valorParcelaNumerico <= 0 -> {
                            mensagemErro = "Informe um valor de parcela válido."
                        }
                        quantidadeNumerica == null || quantidadeNumerica <= 0 -> {
                            mensagemErro = "Informe uma quantidade de parcelas válida."
                        }
                        vencimentoPrimeira.isBlank() -> {
                            mensagemErro = "Informe o vencimento da 1ª parcela."
                        }
                        valorQuitacaoNumerico == null || valorQuitacaoNumerico <= 0 -> {
                            mensagemErro = "Informe um valor de quitação válido."
                        }
                        else -> {
                            val categoria = categorias.firstOrNull { it.nome == categoriaSelecionada }
                            val formaPagamento = formasPagamento.firstOrNull { it.nome == formaPagamentoSelecionada }

                            if (categoria == null || formaPagamento == null) {
                                mensagemErro = "Categoria ou forma de pagamento inválida."
                            } else {
                                // Monta todas as parcelas numa lista temporária antes de
                                // gravar qualquer coisa. Se algo aqui falhasse, nada seria
                                // inserido — é o equivalente, no front, da garantia de
                                // rollback que o backend aplica na transação do banco.
                                var proximoId = (MovimentacoesRepo.lista.maxOfOrNull { it.id } ?: 0) + 1
                                val novasParcelas = mutableListOf<Movimentacao>()

                                for (numeroParcela in 1..quantidadeNumerica) {
                                    val dataDaParcela = incrementarMes(vencimentoPrimeira.trim(), numeroParcela - 1)
                                    novasParcelas.add(
                                        Movimentacao(
                                            id = proximoId,
                                            tipo = "SAIDA",
                                            valor = valorParcelaNumerico,
                                            descricao = "${descricao.trim()} (parcela $numeroParcela/$quantidadeNumerica)",
                                            categoriaId = categoria.id,
                                            formaPagamentoId = formaPagamento.id,
                                            dataVencimento = dataDaParcela,
                                            status = "PENDENTE",
                                            dataPagamento = null,
                                            comprovante = null
                                        )
                                    )
                                    proximoId++
                                }

                                val novoIdFinanciamento = (FinanciamentosRepo.lista.maxOfOrNull { it.id } ?: 0) + 1
                                FinanciamentosRepo.lista.add(
                                    Financiamento(
                                        id = novoIdFinanciamento,
                                        descricao = descricao.trim(),
                                        valorParcela = valorParcelaNumerico,
                                        quantidadeParcelas = quantidadeNumerica,
                                        vencimentoPrimeiraParcela = vencimentoPrimeira.trim(),
                                        valorQuitacaoAntecipada = valorQuitacaoNumerico,
                                        categoriaId = categoria.id,
                                        formaPagamentoId = formaPagamento.id
                                    )
                                )

                                MovimentacoesRepo.lista.addAll(novasParcelas)

                                mensagemErro = ""
                                mensagemSucesso = "Financiamento salvo! $quantidadeNumerica parcelas geradas."

                                descricao = ""
                                valorParcela = ""
                                quantidadeParcelas = ""
                                vencimentoPrimeira = ""
                                valorQuitacao = ""
                                categoriaSelecionada = ""
                                formaPagamentoSelecionada = ""
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
            ) {
                Text("Salvar financiamento", color = Branco, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}