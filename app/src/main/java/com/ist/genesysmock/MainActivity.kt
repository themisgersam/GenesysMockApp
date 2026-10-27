package com.ist.genesysmock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var showLogin by rememberSaveable { mutableStateOf(false) }
                if (state.loggedIn) {
                    ChatScreen(
                        state = state,
                        onSend = vm::send,
                        onLogout = {
                            vm.logout()
                            showLogin = false // back to the IST home page
                        },
                        onDismissError = vm::dismissError,
                    )
                } else if (showLogin) {
                    BackHandler { showLogin = false }
                    LoginScreen(
                        initialConfig = vm.config(),
                        error = state.error,
                        onLogin = vm::login,
                        onDismissError = vm::dismissError,
                        onCancel = { showLogin = false },
                    )
                } else {
                    HomeScreen(onChat = { showLogin = true })
                }
            }
        }
    }
}
