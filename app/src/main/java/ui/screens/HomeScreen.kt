package com.fleetflow.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetflow.mobile.data.MockDataService
import com.fleetflow.mobile.data.PerfilUsuario
import com.fleetflow.mobile.ui.theme.*

@Composable
fun HomeScreen(
    perfil: PerfilUsuario,
    onPerfilClick: () -> Unit,
    onUsuariosClick: () -> Unit,
    onCategoriasClick: () -> Unit,
    onFormasPagamentoClick: () -> Unit
) {
    Scaffold(containerColor = BackgroundTela) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // CABEÇALHO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Petroleo)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    "FleetFlow",
                    color = Branco,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AmbarDourado)
                        .clickable { onPerfilClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (perfil == PerfilUsuario.ADMINISTRADOR) {
                            "JP"
                        } else {
                            "MT"
                        },
                        color = Petroleo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                // SAUDAÇÃO
                Text(
                    text = if (perfil == PerfilUsuario.ADMINISTRADOR) {
                        "Ola, Joao Pedro"
                    } else {
                        "Ola, Motorista"
                    },
                    color = TextoPrincipal,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (perfil == PerfilUsuario.ADMINISTRADOR) {
                        "Administrador - FleetFlow"
                    } else {
                        "Motorista - FleetFlow"
                    },
                    color = TextoSecundario,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(
                        top = 4.dp,
                        bottom = 20.dp
                    )
                )

                // =====================================================
                // VISÃO DO ADMINISTRADOR
                // =====================================================

                if (perfil == PerfilUsuario.ADMINISTRADOR) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardKpi(
                            "SALDO ATUAL",
                            "R$ 32.850",
                            VerdeEsmeralda,
                            "+8,4%",
                            Modifier.weight(1f)
                        )

                        CardKpi(
                            "A RECEBER",
                            "R$ 12.400",
                            null,
                            null,
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardKpi(
                            "A PAGAR",
                            "R$ 8.150",
                            null,
                            null,
                            Modifier.weight(1f)
                        )

                        CardKpi(
                            "EM VIAGEM",
                            "3",
                            null,
                            null,
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // INSIGHT
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FundoInsight)
                            .border(
                                1.dp,
                                AmbarDourado,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {

                        Text(
                            "INSIGHT",
                            color = AmbarDourado,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Suas despesas com combustivel subiram 12% em relacao ao mes passado.",
                            color = TextoPrincipal,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // GRÁFICO
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuperficieCard)
                            .padding(16.dp)
                    ) {

                        Text(
                            "RECEITAS X DESPESAS",
                            color = TextoSecundario,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            LegendaCor(
                                VerdeEsmeralda,
                                "Receita"
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            LegendaCor(
                                Vermelho,
                                "Despesa"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        GraficoReceitasDespesas(
                            dados = MockDataService.obterMovimentacoesMensais()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // MOVIMENTAÇÕES
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmbarDourado
                        )
                    ) {
                        Text(
                            "Ver todas as movimentacoes",
                            color = Petroleo,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // USUÁRIOS
                    Button(
                        onClick = onUsuariosClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Petroleo
                        )
                    ) {
                        Text(
                            "Gestao de Usuarios",
                            color = Branco,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // CATEGORIAS E FORMAS DE PAGAMENTO
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Button(
                            onClick = onCategoriasClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmbarDourado
                            )
                        ) {
                            Text(
                                "Categorias",
                                color = Petroleo,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = onFormasPagamentoClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmbarDourado
                            )
                        ) {
                            Text(
                                "Formas Pgto.",
                                color = Petroleo,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // =====================================================
                // VISÃO DO MOTORISTA
                // =====================================================

                if (perfil == PerfilUsuario.MOTORISTA) {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SuperficieCard
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {

                            Text(
                                "Área do Motorista",
                                color = Petroleo,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Você está conectado com acesso restrito ao perfil de motorista.",
                                color = TextoSecundario,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            CardKpi(
                                "EM VIAGEM",
                                "3",
                                null,
                                null,
                                Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Os módulos financeiros estão disponíveis apenas para usuários administradores.",
                        color = TextoSecundario,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CardKpi(
    titulo: String,
    valor: String,
    corVariacao: androidx.compose.ui.graphics.Color?,
    variacao: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SuperficieCard)
            .padding(14.dp)
    ) {

        Text(
            titulo,
            color = TextoSecundario,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            valor,
            color = TextoPrincipal,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (variacao != null && corVariacao != null) {

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                variacao,
                color = corVariacao,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LegendaCor(
    cor: androidx.compose.ui.graphics.Color,
    texto: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            texto,
            color = TextoSecundario,
            fontSize = 12.sp
        )
    }
}