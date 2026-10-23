package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

data class CoreDevice(val id: Long, val label: String, val current: Boolean, val lastActive: Int)
data class CoreBlockedUser(val id: Long, val label: String)
data class CoreSecurityState(
    val devices: List<CoreDevice> = emptyList(),
    val blocked: List<CoreBlockedUser> = emptyList(),
    val loading: Boolean = false,
    val privacy: Map<String, String> = emptyMap(),
    val notice: String? = null
)

/**
 * Phase 4.2: intentionally narrow security controls.
 * Never expose device IPs, session tokens, phonebook records or passwords.
 * Revoking an OTHER device requires a separate confirmation in Compose UI.
 */
class TdlibCoreSecurity(private val client: () -> Client?) {
    private val lock = Any()
    private var epoch = 0L
    private val _state = MutableStateFlow(CoreSecurityState())
    val state = _state.asStateFlow()

    fun refresh() {
        val active = client() ?: return
        val generation: Long
        synchronized(lock) {
            generation = ++epoch
            _state.value = _state.value.copy(loading = true, notice = null)
        }
        active.send(TdApi.GetActiveSessions()) { result ->
            synchronized(lock) {
                if (generation != epoch) return@synchronized
                _state.value = _state.value.copy(
                    devices = (result as? TdApi.Sessions)?.sessions.orEmpty()
                        .filterNotNull().map {
                            CoreDevice(
                                id = it.id,
                                label = listOf(it.deviceModel, it.applicationName)
                                    .filter { value -> !value.isNullOrBlank() }.joinToString(" · ")
                                    .take(100).ifBlank { "Telegram device" },
                                current = it.isCurrent,
                                lastActive = it.lastActiveDate
                            )
                        },
                    loading = false,
                    notice = if (result is TdApi.Error)
                        "Sessions unavailable (Telegram code ${result.code})." else null
                )
            }
        }
        refreshPrivacy()
        active.send(TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), 0, 100)) { result ->
            if (result is TdApi.MessageSenders) {
                val ids = result.senders.orEmpty().mapNotNull {
                    (it as? TdApi.MessageSenderUser)?.userId
                }.take(100)
                synchronized(lock) {
                    if (generation != epoch) return@synchronized
                    _state.value = _state.value.copy(
                        blocked = ids.map { CoreBlockedUser(it, "Telegram user") }
                    )
                }
                ids.forEach { id ->
                    active.send(TdApi.GetUser(id)) { user ->
                        if (user is TdApi.User) synchronized(lock) {
                            if (generation != epoch) return@synchronized
                            val label = (user.firstName + " " + user.lastName).trim()
                                .ifBlank { "Telegram user" }.take(90)
                            _state.value = _state.value.copy(
                                blocked = _state.value.blocked.map {
                                    if (it.id == id) it.copy(label = label) else it
                                }
                            )
                        }
                    }
                }
            } else if (result is TdApi.Error) synchronized(lock) {
                if (generation == epoch) _state.value = _state.value.copy(
                    notice = "Blocked users unavailable (Telegram code ${result.code})."
                )
            }
        }
    }

    private fun privacyKind(key: String): TdApi.UserPrivacySetting? = when (key) {
        "Last seen" -> TdApi.UserPrivacySettingShowStatus()
        "Phone number" -> TdApi.UserPrivacySettingShowPhoneNumber()
        "Voice/video calls" -> TdApi.UserPrivacySettingAllowCalls()
        else -> null
    }

    private fun defaultRuleLabel(rules: Array<TdApi.UserPrivacySettingRule>): String {
        val base = rules.firstOrNull {
            it is TdApi.UserPrivacySettingRuleAllowAll ||
                it is TdApi.UserPrivacySettingRuleAllowContacts ||
                it is TdApi.UserPrivacySettingRuleRestrictAll
        }
        val label = when (base) {
            is TdApi.UserPrivacySettingRuleAllowAll -> "Everyone"
            is TdApi.UserPrivacySettingRuleAllowContacts -> "My contacts"
            is TdApi.UserPrivacySettingRuleRestrictAll -> "Nobody"
            else -> "Custom"
        }
        return label + if (rules.any { rule ->
            rule !is TdApi.UserPrivacySettingRuleAllowAll &&
                rule !is TdApi.UserPrivacySettingRuleAllowContacts &&
                rule !is TdApi.UserPrivacySettingRuleRestrictAll
        }) " (+exceptions)" else ""
    }

    /** Read three common Telegram privacy defaults, never assume local values. */
    fun refreshPrivacy() {
        val active = client() ?: return
        val generation = synchronized(lock) { epoch }
        listOf("Last seen", "Phone number", "Voice/video calls").forEach { key ->
            val setting = privacyKind(key) ?: return@forEach
            active.send(TdApi.GetUserPrivacySettingRules(setting)) { response ->
                synchronized(lock) {
                    if (generation != epoch) return@synchronized
                    if (response is TdApi.UserPrivacySettingRules) {
                        _state.value = _state.value.copy(
                            privacy = _state.value.privacy +
                                (key to defaultRuleLabel(response.rules.orEmpty()))
                        )
                    }
                }
            }
        }
    }

    /**
     * Re-fetch fresh privacy rules just before a write; retain per-user/chat
     * exceptions. No change happens without explicit user confirmation.
     */
    fun changePrivacy(key: String, audience: String) {
        val setting = privacyKind(key) ?: return
        val base: TdApi.UserPrivacySettingRule = when (audience) {
            "Everyone" -> TdApi.UserPrivacySettingRuleAllowAll()
            "My contacts" -> TdApi.UserPrivacySettingRuleAllowContacts()
            "Nobody" -> TdApi.UserPrivacySettingRuleRestrictAll()
            else -> return
        }
        val active = client() ?: return
        active.send(TdApi.GetUserPrivacySettingRules(setting)) { response ->
            if (response !is TdApi.UserPrivacySettingRules) {
                synchronized(lock) { _state.value = _state.value.copy(
                    notice = "Could not fetch fresh Telegram privacy rules."
                ) }
                return@send
            }
            // Preserve all explicit allow/restrict user and chat exceptions.
            val exceptions = response.rules.orEmpty().filterNot {
                it is TdApi.UserPrivacySettingRuleAllowAll ||
                    it is TdApi.UserPrivacySettingRuleAllowContacts ||
                    it is TdApi.UserPrivacySettingRuleRestrictAll
            }
            val next = TdApi.UserPrivacySettingRules((exceptions + base).toTypedArray())
            active.send(TdApi.SetUserPrivacySettingRules(setting, next)) { result ->
                synchronized(lock) {
                    _state.value = _state.value.copy(
                        notice = if (result is TdApi.Error)
                            "Privacy change denied (Telegram code ${result.code})."
                        else "Privacy preferences saved by Telegram."
                    )
                }
                if (result !is TdApi.Error) refreshPrivacy()
            }
        }
    }

    fun unblock(userId: Long) {
        if (userId !in _state.value.blocked.map { it.id }) return
        client()?.send(TdApi.SetMessageSenderBlockList(
            TdApi.MessageSenderUser(userId), null
        )) { result ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    notice = if (result is TdApi.Error)
                        "Unblock failed (Telegram code ${result.code})."
                    else "Unblocked on Telegram."
                )
            }
            if (result !is TdApi.Error) refresh()
        }
    }

    fun block(userId: Long) {
        if (userId <= 0) return
        client()?.send(TdApi.SetMessageSenderBlockList(
            TdApi.MessageSenderUser(userId), TdApi.BlockListMain()
        )) { result ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    notice = if (result is TdApi.Error)
                        "Block failed (Telegram code ${result.code})."
                    else "User blocked on Telegram."
                )
            }
            if (result !is TdApi.Error) refresh()
        }
    }

    fun terminateOtherDevice(id: Long) {
        val device = _state.value.devices.firstOrNull { it.id == id } ?: return
        if (device.current) return // Cannot kill current session from this control
        client()?.send(TdApi.TerminateSession(id)) { result ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    notice = if (result is TdApi.Error)
                        "Could not revoke device (Telegram code ${result.code})."
                    else "Other Telegram device signed out."
                )
            }
            if (result !is TdApi.Error) refresh()
        }
    }

    fun clear() {
        synchronized(lock) {
            epoch++
            _state.value = CoreSecurityState()
        }
    }
}
