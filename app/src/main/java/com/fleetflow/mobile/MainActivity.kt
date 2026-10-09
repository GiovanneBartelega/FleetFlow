package com.fleetflow.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.fleetflow.mobile.data.PerfilUsuario
import com.fleetflow.mobile.ui.screens.AcessoNegadoScreen
import com.fleetflow.mobile.ui.screens.AguardandoScreen
import com.fleetflow.mobile.ui.screens.CategoriasScreen
import com.fleetflow.mobile.ui.screens.FinanciamentoScreen
import com.fleetflow.mobile.ui.screens.FormasPagamentoScreen
import com.fleetflow.mobile.ui.screens.HomeScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
import com.fleetflow.mobile.ui.screens.MovimentacaoScreen
import com.fleetflow.mobile.ui.screens.PerfilScreen
import com.fleetflow.mobile.ui.screens.UsuariosScreen
import com.fleetflow.mobile.ui.screens.WelcomeScreen
import com.fleetflow.mobile.ui.theme.FleetFlowTheme

sealed class Tela {
    object Boasvindas : Tela()
    object Login : Tela()
    object Aguardando : Tela()
    object Home : Tela()
    object Perfil : Tela()
    object Usuarios : Tela()
    object AcessoNegado : Tela()
    object Categorias : Tela()
    object FormasPagamento : Tela()
    object Movimentacao : Tela()
    object Financiamento : Tela()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FleetFlowTheme {

                var telaAtual by remember {
                    mutableStateOf<Tela>(Tela.Boasvindas)
                }

                var perfilAtual by remember {
                    mutableStateOf<PerfilUsuario?>(null)
                }

                when (telaAtual) {

                    is Tela.Boasvindas -> {
                        WelcomeScreen(
                            onComecarClick = {
                                telaAtual = Tela.Login
                            }
                        )
                    }

                    is Tela.Login -> {
                        LoginScreen(
                            onLoginSuccess = { perfil ->
                                perfilAtual = perfil
                                telaAtual = Tela.Home
                            }
                        )
                    }

                    is Tela.Aguardando -> {
                        AguardandoScreen(
                            onSairClick = {
                                perfilAtual = null
                                telaAtual = Tela.Login
                            }
                        )
                    }

                    is Tela.Home -> {
                        val perfil = perfilAtual

                        if (perfil == null) {
                            telaAtual = Tela.Login
                        } else {
                            HomeScreen(
                                perfil = perfil,

                                onPerfilClick = {
                                    telaAtual = Tela.Perfil
                                },

                                onUsuariosClick = {
                                    telaAtual =
                                        if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                            Tela.Usuarios
                                        } else {
                                            Tela.AcessoNegado
                                        }
                                },

                                onCategoriasClick = {
                                    telaAtual =
                                        if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                            Tela.Categorias
                                        } else {
                                            Tela.AcessoNegado
                                        }
                                },

                                onFormasPagamentoClick = {
                                    telaAtual =
                                        if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                            Tela.FormasPagamento
                                        } else {
                                            Tela.AcessoNegado
                                        }
                                },

                                onMovimentacaoClick = {
                                    telaAtual =
                                        if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                            Tela.Movimentacao
                                        } else {
                                            Tela.AcessoNegado
                                        }
                                },

                                onFinanciamentoClick = {
                                    telaAtual =
                                        if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                            Tela.Financiamento
                                        } else {
                                            Tela.AcessoNegado
                                        }
                                }
                            )
                        }
                    }

                    is Tela.Perfil -> {
                        val perfil = perfilAtual

                        if (perfil == null) {
                            telaAtual = Tela.Login
                        } else {
                            PerfilScreen(
                                perfil = perfil,
                                onSairClick = {
                                    perfilAtual = null
                                    telaAtual = Tela.Login
                                }
                            )
                        }
                    }

                    is Tela.Usuarios -> {
                        if (perfilAtual == PerfilUsuario.ADMINISTRADOR) {
                            UsuariosScreen(
                                onVoltarClick = {
                                    telaAtual = Tela.Home
                                }
                            )
                        } else {
                            telaAtual = Tela.AcessoNegado
                        }
                    }

                    is Tela.AcessoNegado -> {
                        AcessoNegadoScreen(
                            onVoltarClick = {
                                telaAtual =
                                    if (perfilAtual != null) {
                                        Tela.Home
                                    } else {
                                        Tela.Login
                                    }
                            }
                        )
                    }

                    is Tela.Categorias -> {
                        if (perfilAtual == PerfilUsuario.ADMINISTRADOR) {
                            CategoriasScreen(
                                onVoltarClick = {
                                    telaAtual = Tela.Home
                                }
                            )
                        } else {
                            telaAtual = Tela.AcessoNegado
                        }
                    }

                    is Tela.FormasPagamento -> {
                        if (perfilAtual == PerfilUsuario.ADMINISTRADOR) {
                            FormasPagamentoScreen(
                                onVoltarClick = {
                                    telaAtual = Tela.Home
                                }
                            )
                        } else {
                            telaAtual = Tela.AcessoNegado
                        }
                    }

                    is Tela.Movimentacao -> {
                        if (perfilAtual == PerfilUsuario.ADMINISTRADOR) {
                            MovimentacaoScreen(
                                onVoltarClick = {
                                    telaAtual = Tela.Home
                                }
                            )
                        } else {
                            telaAtual = Tela.AcessoNegado
                        }
                    }

                    is Tela.Financiamento -> {
                        if (perfilAtual == PerfilUsuario.ADMINISTRADOR) {
                            FinanciamentoScreen(
                                onVoltarClick = {
                                    telaAtual = Tela.Home
                                },
                                formatarComoData = { data ->
                                    data
                                }
                            )
                        } else {
                            telaAtual = Tela.AcessoNegado
                        }
                    }
                }
            }
        }
    }
}