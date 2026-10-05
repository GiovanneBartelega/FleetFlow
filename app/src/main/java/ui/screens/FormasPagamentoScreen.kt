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
import com.fleetflow.mobile.data.FormaPagamento
import com.fleetflow.mobile.data.FormasPagamentoRepo
import com.fleetflow.mobile.ui.theme.*

@Composable
fun FormasPagamentoScreen(onVoltarClick: () -> Unit) {
    var nomeNovo by remember { mutableStateOf("") }

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

            Column(
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
                            val novoId = (FormasPagamentoRepo.lista.maxOfOrNull { it.id } ?: 0) + 1
                            FormasPagamentoRepo.lista.add(FormaPagamento(novoId, nomeNovo.trim(), true))
                            nomeNovo = ""
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
                items(FormasPagamentoRepo.lista, key = { it.id }) { forma ->
                    CardFormaPagamento(forma)
                }
            }
        }
    }
}

@Composable
private fun CardFormaPagamento(forma: FormaPagamento) {
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

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (forma.ativo) VerdeSuave else CinzaSuave)
                .clickable { forma.ativo = !forma.ativo }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                if (forma.ativo) "Ativo" else "Inativo",
                color = if (forma.ativo) VerdeEsmeralda else CinzaBadge,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}