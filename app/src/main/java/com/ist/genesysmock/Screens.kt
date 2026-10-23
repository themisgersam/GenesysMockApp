package com.ist.genesysmock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    initialConfig: GenesysConfig,
    error: String?,
    onLogin: (String, String, String, GenesysConfig) -> Unit,
    onDismissError: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(value = false) }
    var deploymentId by remember { mutableStateOf(initialConfig.deploymentId) }
    var domain by remember { mutableStateOf(initialConfig.domain) }

    val valid = name.isNotBlank() && mobile.isNotBlank() && email.contains("@") &&
        deploymentId.isNotBlank() && domain.isNotBlank()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.statusBarsPadding().imePadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Welcome", style = MaterialTheme.typography.headlineMedium)
            Text("Mock login — details are sent to Genesys as custom attributes.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                mobile, { mobile = it }, label = { Text("Mobile number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "Hide Genesys settings" else "Genesys settings")
            }
            if (showSettings) {
                OutlinedTextField(deploymentId, { deploymentId = it }, label = { Text("Deployment ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(domain, { domain = it }, label = { Text("Domain (e.g. mypurecloud.de)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
            Button(
                onClick = { onLogin(name.trim(), mobile.trim(), email.trim(), GenesysConfig(deploymentId.trim(), domain.trim())) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Login") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onLogout: () -> Unit,
    onDismissError: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var showDebug by remember { mutableStateOf(value = false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Support chat")
                        Text(state.connection, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    TextButton(onClick = { showDebug = !showDebug }) { Text(if (showDebug) "Hide debug" else "Debug") }
                    TextButton(onClick = onLogout) { Text("Logout") }
                },
            )
        },
        modifier = Modifier.imePadding(),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (showDebug) AttributesDebugPanel(state)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.messages, key = { (id) -> id }) { (_, fromUser, text) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                    }
                }
            }
            state.error?.let { err ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, placeholder = { Text("Type a message") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSend(input); input = "" }, enabled = input.isNotBlank()) { Text("Send") }
            }
        }
    }
}

@Composable
private fun AttributesDebugPanel(state: UiState) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            Text("customAttributesStore", style = MaterialTheme.typography.labelLarge)
            AttrLines(state.storeAttributes)
            Spacer(Modifier.height(8.dp))
            Text("Sent with last message", style = MaterialTheme.typography.labelLarge)
            AttrLines(state.lastSentAttributes)
        }
    }
}

@Composable
private fun AttrLines(map: Map<String, String>) {
    if (map.isEmpty()) Text("(none)", style = MaterialTheme.typography.bodySmall)
    map.forEach { (k, v) -> Text("$k = $v", style = MaterialTheme.typography.bodySmall) }
}
