package com.ist.genesysmock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                if (state.loggedIn) {
                    ChatScreen(
                        state = state,
                        onSend = vm::send,
                        onLogout = vm::logout,
                        onDismissError = vm::dismissError,
                    )
                } else {
                    LoginScreen(
                        initialConfig = vm.config(),
                        error = state.error,
                        onLogin = vm::login,
                        onDismissError = vm::dismissError,
                    )
                }
            }
        }
    }
}
