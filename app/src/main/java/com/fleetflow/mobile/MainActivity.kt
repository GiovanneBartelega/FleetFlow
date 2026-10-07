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
import com.fleetflow.mobile.ui.screens.FormasPagamentoScreen
import com.fleetflow.mobile.ui.screens.HomeScreen
import com.fleetflow.mobile.ui.screens.LoginScreen
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

                var perfilUsuario by remember {
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
                                perfilUsuario = perfil
                                telaAtual = Tela.Home
                            }
                        )
                    }

                    is Tela.Aguardando -> {
                        AguardandoScreen(
                            onSairClick = {
                                perfilUsuario = null
                                telaAtual = Tela.Login
                            }
                        )
                    }

                    is Tela.Home -> {

                        val perfil = perfilUsuario

                        if (perfil != null) {

                            HomeScreen(
                                perfil = perfil,

                                onPerfilClick = {
                                    telaAtual = Tela.Perfil
                                },

                                onUsuariosClick = {
                                    if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                        telaAtual = Tela.Usuarios
                                    } else {
                                        telaAtual = Tela.AcessoNegado
                                    }
                                },

                                onCategoriasClick = {
                                    if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                        telaAtual = Tela.Categorias
                                    } else {
                                        telaAtual = Tela.AcessoNegado
                                    }
                                },

                                onFormasPagamentoClick = {
                                    if (perfil == PerfilUsuario.ADMINISTRADOR) {
                                        telaAtual = Tela.FormasPagamento
                                    } else {
                                        telaAtual = Tela.AcessoNegado
                                    }
                                }
                            )

                        } else {
                            telaAtual = Tela.Login
                        }
                    }

                    is Tela.Perfil -> {

                        if (perfilUsuario != null) {

                            PerfilScreen(
                                perfil = perfilUsuario!!,
                                onSairClick = {
                                    perfilUsuario = null
                                    telaAtual = Tela.Login
                                }
                            )

                        } else {

                            telaAtual = Tela.Login
                        }
                    }

                    is Tela.Usuarios -> {

                        if (perfilUsuario == PerfilUsuario.ADMINISTRADOR) {

                            UsuariosScreen(
                                onVoltarClick = {
                                    telaAtual = Tela.Home
                                }
                            )

                        } else {

                            telaAtual = Tela.AcessoNegado
                        }
                    }

                    is Tela.Categorias -> {

                        if (perfilUsuario == PerfilUsuario.ADMINISTRADOR) {

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

                        if (perfilUsuario == PerfilUsuario.ADMINISTRADOR) {

                            FormasPagamentoScreen(
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
                                telaAtual = Tela.Home
                            }
                        )
                    }
                }
            }
        }
    }
}