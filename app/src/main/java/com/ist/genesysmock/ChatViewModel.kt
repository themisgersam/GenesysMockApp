package com.ist.genesysmock

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genesys.cloud.messenger.transport.core.Configuration
import com.genesys.cloud.messenger.transport.core.ErrorCode
import com.genesys.cloud.messenger.transport.core.Message
import com.genesys.cloud.messenger.transport.core.MessageEvent
import com.genesys.cloud.messenger.transport.core.MessagingClient
import com.genesys.cloud.messenger.transport.core.MessagingClient.State
import com.genesys.cloud.messenger.transport.core.MessengerTransportSDK
import com.genesys.cloud.messenger.transport.core.events.Event
import com.genesys.cloud.messenger.transport.util.DefaultVault
import com.genesys.cloud.messenger.transport.util.EncryptedVault
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenesysConfig(val deploymentId: String, val domain: String)

data class ChatLine(val id: String, val fromUser: Boolean, val text: String)

data class UiState(
    val loggedIn: Boolean = false,
    val connection: String = "Idle",
    val canSend: Boolean = false,
    /** True after the user sends a message, until an agent/bot reply arrives. */
    val awaitingReply: Boolean = false,
    val messages: List<ChatLine> = emptyList(),
    val error: String? = null,
    /** Debug: what the SDK's customAttributesStore currently holds. */
    val storeAttributes: Map<String, String> = emptyMap(),
    /** Debug: attributes passed with the last sendMessage call. */
    val lastSentAttributes: Map<String, String> = emptyMap(),
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("genesys_mock", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var client: MessagingClient? = null
    private var sdk: MessengerTransportSDK? = null
    private var attributes: Map<String, String> = emptyMap()

    // True once we know the active session is a brand-new one (no prior conversation).
    @Volatile private var freshSession = false
    @Volatile private var loggingOut = false

    fun config() = GenesysConfig(
        deploymentId = prefs.getString("deploymentId", null) ?: BuildConfig.DEFAULT_DEPLOYMENT_ID,
        domain = prefs.getString("domain", null) ?: BuildConfig.DEFAULT_DOMAIN,
    )

    fun login(name: String, mobile: String, email: String, config: GenesysConfig) {
        prefs.edit {
            putString("deploymentId", config.deploymentId)
            putString("domain", config.domain)
        }

        attributes = mapOf("name" to name, "mobileNumber" to mobile, "email" to email)
        freshSession = false
        loggingOut = false
        _state.value = UiState(loggedIn = true, connection = "Connecting…", canSend = false)

        viewModelScope.launch {
            try {
                // Vault needs an app context; set both types so whichever the SDK picks is initialised.
                val appContext = getApplication<Application>().applicationContext
                EncryptedVault.context = appContext
                DefaultVault.context = appContext
                val sdk = MessengerTransportSDK(
                    Configuration(
                        deploymentId = config.deploymentId,
                        domain = config.domain,
                        logging = true,
                        encryptedVault = true,
                    ),
                )
                // Forget any stored session token so this login always starts a brand-new conversation.
                sdk.forgetSession()
                this@ChatViewModel.sdk = sdk
                // Required before connect(); also validates the deployment ID / domain.
                sdk.fetchDeploymentConfig()
                if (loggingOut || !_state.value.loggedIn) return@launch // user logged out meanwhile

                val c = sdk.createMessagingClient()
                c.stateChangedListener = { onStateChanged(it.newState) }
                c.messageListener = { onMessage(it) }
                c.eventListener = { onEvent(it) }
                // Custom attributes are sent to Genesys with the first message / autostart event.
                c.customAttributesStore.add(attributes)
                client = c
                refreshAttributes()
                c.connect()
            } catch (t: Throwable) {
                Log.e(TAG, "login failed", t)
                client = null
                _state.value = UiState(loggedIn = false, error = t.message ?: "Failed to connect")
            }
        }
    }

    fun send(text: String) {
        val c = client ?: return
        if (text.isBlank()) return
        if (c.currentState !is State.Configured) {
            Log.w(TAG, "Cannot send message: MessagingClient state is ${c.currentState}")
            _state.update { it.copy(error = "Chat is not connected. Please wait until connected.") }
            return
        }
        try {
            // Re-add in case the store was cleared by a session close/error; also passed explicitly.
            c.customAttributesStore.add(attributes)
            c.sendMessage(text.trim(), attributes)
            _state.update { it.copy(lastSentAttributes = attributes, awaitingReply = true) }
            refreshAttributes()
        } catch (t: Throwable) {
            Log.e(TAG, "send failed", t)
            _state.update { it.copy(error = t.message ?: "Failed to send message") }
        }
    }

    /** Clears the conversation on Genesys, tears down the client and returns to the login screen. */
    fun logout() {
        val c = client
        loggingOut = true
        _state.update { it.copy(connection = "Logging out…") }
        if (c == null) {
            finishLogout()
            return
        }
        try {
            c.clearConversation() // finishLogout() runs on Event.ConversationCleared
        } catch (t: Throwable) {
            Log.w(TAG, "clearConversation not possible in state ${c.currentState}", t)
            finishLogout()
            return
        }
        // Safety net in case the cleared event never arrives.
        viewModelScope.launch {
            delay(5.seconds)
            if (loggingOut) finishLogout()
        }
    }

    private fun finishLogout() {
        val c = client
        client = null
        loggingOut = false
        freshSession = false
        try {
            c?.stateChangedListener = null
            c?.messageListener = null
            c?.eventListener = null
            c?.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect", t)
        }
        sdk?.forgetSession()
        sdk = null
        attributes = emptyMap()
        _state.value = UiState()
    }

    private fun onStateChanged(newState: State) {
        if (loggingOut) return
        val label = when (newState) {
            is State.Configured -> "Connected"
            is State.Connecting, is State.Connected -> "Connecting…"
            is State.Reconnecting -> "Reconnecting…"
            is State.ReadOnly -> "Conversation ended"
            is State.Error -> "Error: ${newState.message}"
            is State.Closed, is State.Closing -> "Disconnected"
            else -> "Idle"
        }
        val isConfigured = newState is State.Configured
        _state.update { it.copy(connection = label, canSend = isConfigured) }

        if ((newState is State.Configured) && !freshSession) {
            if (newState.newSession) {
                freshSession = true
            } else {
                // Should not happen since the token is reset on login; drop any restored history anyway.
                _state.update { it.copy(messages = emptyList()) }
                freshSession = true
            }
        }
        if (newState is State.Configured) {
            client?.customAttributesStore?.add(attributes)
            refreshAttributes()
        }
    }

    private fun onEvent(event: Event) {
        when (event) {
            is Event.ConversationCleared -> {
                if (loggingOut) {
                    finishLogout()
                } else {
                    // Cleared at login: connect again to get a brand-new session.
                    _state.update { it.copy(messages = emptyList()) }
                    try {
                        client?.connect()
                    } catch (t: Throwable) {
                        Log.e(TAG, "reconnect after clear failed", t)
                    }
                }
            }
            is Event.Error -> if (loggingOut && event.errorCode == ErrorCode.ClearConversationFailure) {
                // Deployment doesn't allow clearing; the session token is dropped locally instead.
                finishLogout()
            } else _state.update { it.copy(error = event.message ?: event.errorCode.toString()) }
            else -> Unit
        }
    }

    private fun onMessage(event: MessageEvent) {
        when (event) {
            is MessageEvent.MessageInserted -> upsert(event.message)
            is MessageEvent.MessageUpdated -> upsert(event.message)
            is MessageEvent.HistoryFetched ->
                if (!freshSession) Unit else event.messages.forEach { upsert(it) }
            else -> Unit
        }
    }

    private fun upsert(m: Message) {
        val text = m.text?.takeIf { it.isNotBlank() } ?: return
        val line = ChatLine(m.id, fromUser = m.direction == Message.Direction.Inbound, text = text)
        _state.update { s ->
            val i = s.messages.indexOfFirst { it.id == line.id }
            s.copy(
                messages = if (i >= 0) s.messages.toMutableList().also { it[i] = line } else s.messages + line,
                awaitingReply = if (line.fromUser) s.awaitingReply else false,
            )
        }
    }

    private fun MessengerTransportSDK.forgetSession() {
        try {
            vault.remove(vault.keys.tokenKey)
        } catch (t: Throwable) {
            Log.w(TAG, "could not reset session token", t)
        }
    }

    private fun refreshAttributes() {
        val stored = client?.customAttributesStore?.get() ?: emptyMap()
        _state.update { it.copy(storeAttributes = stored) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        try { client?.disconnect() } catch (_: Throwable) {}
    }

    private companion object { const val TAG = "GenesysMock" }
}
