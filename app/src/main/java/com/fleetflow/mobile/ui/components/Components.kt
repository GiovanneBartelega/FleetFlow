package com.fleetflow.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fleetflow.mobile.ui.theme.FleetFlowTheme

// ---------------------------------------------------------------
// 1) CAMPO DE VALOR (R$)
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
    val texto = formatMoney(value)

    OutlinedTextField(
        value = TextFieldValue(
            texto,
            selection = TextRange(texto.length)
        ),
        onValueChange = { digitado ->
            onValueChange(
                digitado.text
                    .filter { it.isDigit() }
                    .trimStart('0')
                    .take(12)
            )
        },
        label = { Text(label) },
        leadingIcon = {
            Text(
                "R$",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        isError = isError,
        supportingText = {
            if (isError && errorText != null) {
                Text(errorText)
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

// Transforma "123450" em "1.234,50".
fun formatMoney(digits: String): String {
    if (digits.isEmpty()) return ""

    val cents = digits.toLong()
    val reais = (cents / 100)
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()

    return "%s,%02d".format(reais, cents % 100)
}

// ---------------------------------------------------------------
// 2) SELETOR COM PESQUISA
// ---------------------------------------------------------------

@Composable
fun AppDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var pesquisa by remember { mutableStateOf("") }

    val filtradas = remember(options, pesquisa) {
        if (pesquisa.isBlank()) {
            options
        } else {
            options.filter {
                it.contains(pesquisa.trim(), ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Selecione $label") },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ArrowDropUp
                    } else {
                        Icons.Filled.ArrowDropDown
                    },
                    contentDescription = "Abrir opções"
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Recebe o toque em toda a área do campo.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    pesquisa = ""
                    expanded = true
                }
        )
    }

    if (expanded) {
        Dialog(
            onDismissRequest = {
                expanded = false
                pesquisa = ""
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 600.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Selecionar $label",
                        style = MaterialTheme.typography.titleLarge,
                    )

                    OutlinedTextField(
                        value = pesquisa,
                        onValueChange = { pesquisa = it },
                        label = { Text("Pesquisar $label") },
                        placeholder = {
                            Text("Digite para pesquisar...")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (filtradas.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (options.isEmpty()) {
                                    "Nenhuma opção disponível."
                                } else {
                                    "Nenhum resultado encontrado."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filtradas) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelected(option)
                                            expanded = false
                                            pesquisa = ""
                                        }
                                        .padding(
                                            horizontal = 16.dp,
                                            vertical = 14.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (option == selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                expanded = false
                                pesquisa = ""
                            },
                        ) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// 3) ESTADO VAZIO
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
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            null,
            Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(16.dp))

        Text(
            title,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(4.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))

            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

// ---------------------------------------------------------------
// PREVIEW
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
            MoneyField(
                value = valor,
                onValueChange = { valor = it },
                label = "Valor"
            )

            AppDropdown(
                label = "Categoria",
                options = listOf(
                    "Combustível",
                    "Pedágio",
                    "Frete",
                    "Manutenção"
                ),
                selected = categoria,
                onSelected = { categoria = it },
            )

            EmptyState(
                icon = Icons.Filled.Inbox,
                title = "Nada por aqui",
                message = "Nenhum lançamento ainda. Toque em adicionar.",
                actionLabel = "Adicionar",
                onAction = {}
            )
        }
    }
}