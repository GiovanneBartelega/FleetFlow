package com.fleetflow.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fleetflow.mobile.ui.theme.FleetFlowTheme

// ---------------------------------------------------------------
// 1) CAMPO DE VALOR (R$)
// O estado guarda só os DÍGITOS ("123450"). A tela mostra formatado
// ("R$ 1.234,50"). Assim nunca sobra vírgula ou ponto no lugar errado.
// ---------------------------------------------------------------
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    OutlinedTextField(
        value = formatMoney(value),
        onValueChange = { digitado ->
            // Mantém só números, no máximo 12 dígitos.
            onValueChange(digitado.filter { it.isDigit() }.take(12))
        },
        label = { Text(label) },
        leadingIcon = { Text("R$", style = MaterialTheme.typography.bodyMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
        supportingText = { if (isError && errorText != null) Text(errorText) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

// Transforma "123450" em "1.234,50". Trata os dois últimos dígitos como centavos.
fun formatMoney(digits: String): String {
    if (digits.isEmpty()) return ""
    val cents = digits.toLong()
    val reais = (cents / 100).toString()
        .reversed().chunked(3).joinToString(".").reversed() // ponto de milhar
    return "%s,%02d".format(reais, cents % 100)
}

// ---------------------------------------------------------------
// 2) SELETOR (dropdown) — escolher categoria, forma de pagamento, etc.
// ---------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------
// 3) ESTADO VAZIO — quando uma lista não tem nada ainda.
// ---------------------------------------------------------------
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------
// PREVIEW — vê os três componentes sem rodar o app inteiro.
// Clica em "Split" ou "Design" no topo direito do editor.
// ---------------------------------------------------------------
@Preview(showBackground = true)
@Composable
private fun ComponentsPreview() {
    FleetFlowTheme {
        var valor by remember { mutableStateOf("") }
        var categoria by remember { mutableStateOf<String?>(null) }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoneyField(value = valor, onValueChange = { valor = it }, label = "Valor")
            AppDropdown(
                label = "Categoria",
                options = listOf("Combustível", "Pedágio", "Frete", "Manutenção"),
                selected = categoria,
                onSelected = { categoria = it },
            )
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "Nada por aqui",
                message = "Nenhum lançamento ainda. Toque em adicionar.",
                actionLabel = "Adicionar",
                onAction = {},
            )
        }
    }
}
