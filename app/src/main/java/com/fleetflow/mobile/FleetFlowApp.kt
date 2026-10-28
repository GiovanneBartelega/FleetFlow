package com.fleetflow.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.fleetflow.mobile.data.auth.TokenManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.data.model.UserResponseDto
import com.fleetflow.mobile.ui.screens.AcessoNegadoScreen
import com.fleetflow.mobile.ui.screens.*
import com.fleetflow.mobile.ui.screens.HomeScreen
import com.fleetflow.mobile.ui.screens.PerfilScreen
import com.fleetflow.mobile.ui.screens.UsuariosScreen
import kotlinx.coroutines.launch

enum class Profile { ADMIN, FINANCEIRO, GESTOR, MOTORISTA }

// A API de Dados fornece perfil.nome em português; aqui ele vira o enum da navegação.
// Texto desconhecido vira null, e null não entra no app.
fun profileFromRole(role: String): Profile? = when (role) {
    "Administrador" -> Profile.ADMIN
    "Financeiro" -> Profile.FINANCEIRO
    "GestorFrota" -> Profile.GESTOR
    "OperadorMotorista" -> Profile.MOTORISTA
    else -> null
}

fun nomeDoPerfil(p: Profile): String = when (p) {
    Profile.ADMIN -> "Administrador"
    Profile.FINANCEIRO -> "Financeiro"
    Profile.GESTOR -> "Gestor de Frota"
    Profile.MOTORISTA -> "Motorista"
}

// Mesmo nome legível, a partir do texto que a API devolve ("GestorFrota" vira
// "Gestor de Frota"). Texto desconhecido aparece como veio.
fun nomeDoPerfil(nomeNaApi: String): String =
    profileFromRole(nomeNaApi)?.let { nomeDoPerfil(it) } ?: nomeNaApi

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Filled.Home),
    FINANCAS("Finanças", Icons.Filled.AccountBalanceWallet),
    FROTA("Frota", Icons.Filled.LocalShipping),
    VIAGENS("Viagens", Icons.Filled.Route),
    VEICULOS("Veículos", Icons.Filled.DirectionsCar),
    RELATORIOS("Relatórios", Icons.Filled.Description),
    PERFIL("Perfil", Icons.Filled.Person),
}

// A REGRA DE PERMISSÃO: quais abas cada perfil enxerga (mapa de telas da especificação).
// No máximo 5 abas: é o limite da barra de navegação do Material 3. Rotas não é aba;
// abre por um botão no topo da Frota.
fun tabsFor(profile: Profile): List<Screen> = when (profile) {
    Profile.ADMIN      -> listOf(Screen.HOME, Screen.FINANCAS, Screen.FROTA, Screen.VIAGENS, Screen.PERFIL)
    Profile.FINANCEIRO -> listOf(Screen.HOME, Screen.FINANCAS, Screen.RELATORIOS, Screen.PERFIL)
    Profile.GESTOR     -> listOf(Screen.HOME, Screen.FROTA, Screen.VIAGENS, Screen.RELATORIOS, Screen.PERFIL)
    Profile.MOTORISTA  -> listOf(Screen.VIAGENS, Screen.VEICULOS, Screen.PERFIL)
}

// Quem cadastra e altera veículos, ordens de serviço e rotas. A API dá escrita em
// veículos e manutenção só para Administrador e Gestor de Frota.
fun podeEditarFrota(p: Profile) = p == Profile.ADMIN || p == Profile.GESTOR

// Gestão de usuários: só Administrador, como diz a especificação.
// A API também restringe gestão de usuários ao Administrador.
fun podeGerenciarUsuarios(p: Profile) = p == Profile.ADMIN

@Composable
fun FleetFlowApp(usuario: UserResponseDto, onLogout: () -> Unit) {
    val context=LocalContext.current
    var permissoes by remember(usuario.id) {mutableStateOf<Map<String,String?>>(emptyMap())}
    LaunchedEffect(usuario.id) {permissoes=TokenManager(context).getPermissoes()}
    val perfil = profileFromRole(usuario.perfil.nome)
    if (perfil == null) {
        // Perfil que o app não conhece: por segurança, não entra.
        AcessoNegadoScreen(onVoltarClick = onLogout)
        return
    }

    val tabs = tabsFor(perfil)
    var current by remember(perfil) { mutableStateOf(tabs.first()) }  // motorista cai em Viagens
    var cadastro by remember { mutableStateOf<String?>(null) }
    var mostrandoUsuarios by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = mostrandoUsuarios || cadastro != null) { mostrandoUsuarios = false; cadastro = null }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { screen ->
                    NavigationBarItem(
                        selected = current == screen && !mostrandoUsuarios,
                        onClick = { current = screen; mostrandoUsuarios = false; cadastro = null },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // As telas do grupo têm Scaffold próprio. Sem esta linha, o espaço da barra
                // de status seria somado duas vezes e sobraria uma faixa vazia no topo.
                .consumeWindowInsets(padding)
        ) {
            if (cadastro != null) {
                when(cadastro) {
                    "categorias" -> CategoriasScreen(onVoltarClick = {cadastro=null}, somenteLeitura = perfil == Profile.GESTOR)
                    "formas" -> FormasPagamentoScreen(onVoltarClick = {cadastro=null}, somenteLeitura = perfil == Profile.GESTOR)
                    "dividas" -> FinanciamentoScreen(onVoltarClick = {cadastro=null})
                    "rotas" -> Column(Modifier.fillMaxSize()) {
                        // A tela de Rotas foi feita para ser aba e não tem botão de voltar.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { cadastro = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar para a Frota")
                            }
                            Text("Frota", style = MaterialTheme.typography.titleMedium)
                        }
                        Box(Modifier.weight(1f)) {
                            RotasScreen(somenteLeitura = !podeEditarFrota(perfil))
                        }
                    }
                }
            } else if (mostrandoUsuarios) {
                if (podeGerenciarUsuarios(perfil)) {
                    UsuariosScreen(permissoes=permissoes, onVoltarClick = { mostrandoUsuarios = false })
                } else {
                    AcessoNegadoScreen(onVoltarClick = { mostrandoUsuarios = false })
                }
            } else {
                when (current) {
                    Screen.HOME ->
                        if (perfil == Profile.GESTOR) {
                            HomeLogisticaPlaceholder(onCategorias={cadastro="categorias"},onFormas={cadastro="formas"})
                        } else {
                            HomeScreen(
                                usuario = usuario,
                                podeUsuarios = podeGerenciarUsuarios(perfil),
                                onMovimentacoes = {current=Screen.FINANCAS},
                                onPerfilClick = { current = Screen.PERFIL },
                                onUsuariosClick = { mostrandoUsuarios = true },
                            )
                        }
                    Screen.FINANCAS -> FinancasScreen(
                        onCategorias = {cadastro="categorias"}, onFormas={cadastro="formas"}, onDividas={cadastro="dividas"},
                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    Screen.PERFIL -> PerfilScreen(user = usuario, onSairClick = onLogout)
                    Screen.FROTA -> FrotaScreen(
                        podeEditar = podeEditarFrota(perfil),
                        onRotasClick = { cadastro = "rotas" },
                    )
                    // Motorista: vê a frota só para consulta e sem as ordens de serviço,
                    // porque a API não libera manutenção para esse perfil.
                    Screen.VEICULOS -> FrotaScreen(
                        podeEditar = false,
                        mostrarOrdens = false,
                    )
                    Screen.VIAGENS -> ViagensScreen(
                        somenteLeitura = perfil == Profile.FINANCEIRO
                    )
                    else -> GenericPlaceholder(current.label)
                }
            }
        }
    }
}

// A Home do grupo é financeira (receitas x despesas). O Gestor de Frota não vê caixa,
// então ganha a sua própria Home quando o módulo de frota existir.
@Composable
private fun HomeLogisticaPlaceholder(onCategorias:()->Unit,onFormas:()->Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.TextButton(onClick=onCategorias) {Text("Categorias")}
        androidx.compose.material3.TextButton(onClick=onFormas) {Text("Formas de pagamento")}
        Text("Início — Frota", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Veículos em viagem, custo por km e alertas de frota aparecem aqui.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GenericPlaceholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}