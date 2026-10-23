package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Telegram account/profile and Telegram-cloud contacts, not Android address
 * book scraping. All network requests run through the ONE authorized TDLib
 * client. Never persist phone numbers/bios to app preferences or logs.
 */
data class CorePerson(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String = "",
    val phone: String = "",
    val photoId: Int? = null,
    val lastSeenAt: Int = 0,
    val online: Boolean = false
) {
    val displayName: String get() =
        listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "Telegram user" }
}

data class CoreAccountState(
    val self: CorePerson? = null,
    val bio: String = "",
    val contacts: List<CorePerson> = emptyList(),
    val busy: Boolean = false,
    val contactsLoading: Boolean = false,
    val notice: String? = null
)

/** No device contacts permission, external phone lookup, or paid backend. */
class TdlibCoreAccount(
    private val client: () -> Client?,
    private val fileStore: TdlibFileStore
) {
    private val lock = Any()
    private var generation = 0L
    private var selfId: Long? = null
    private var contactIds = emptySet<Long>()
    private val users = mutableMapOf<Long, CorePerson>()
    private val _state = MutableStateFlow(CoreAccountState())
    val state = _state.asStateFlow()

    private fun model(user: TdApi.User): CorePerson {
        fileStore.ensure(user.profilePhoto?.small)
        return CorePerson(
            id = user.id,
            firstName = user.firstName.orEmpty(),
            lastName = user.lastName.orEmpty(),
            username = user.usernames?.activeUsernames?.firstOrNull().orEmpty(),
            phone = user.phoneNumber.orEmpty(),
            photoId = user.profilePhoto?.small?.id,
            lastSeenAt = (user.status as? TdApi.UserStatusOffline)?.wasOnline ?: 0,
            online = user.status is TdApi.UserStatusOnline
        )
    }

    private fun publishContacts() {
        _state.value = _state.value.copy(
            contacts = contactIds.mapNotNull(users::get)
                .sortedWith(compareByDescending<CorePerson> { it.online }
                    .thenByDescending { it.lastSeenAt }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        )
    }

    fun refresh() {
        val active = client() ?: run {
            _state.value = _state.value.copy(notice = "Telegram is offline.")
            return
        }
        val epoch: Long
        synchronized(lock) {
            // A screen/tab refresh must not cancel SetName -> SetBio while
            // the user is saving their Telegram profile.
            if (_state.value.busy) return
            epoch = ++generation
            _state.value = _state.value.copy(contactsLoading = true)
            // Preserve any visible profile-save notice until explicitly
            // dismissed; a refresh must not erase useful error feedback.
        }
        active.send(TdApi.GetMe()) { response ->
            if (response is TdApi.User) {
                synchronized(lock) {
                    if (epoch != generation) return@synchronized
                    selfId = response.id
                    val person = model(response)
                    users[person.id] = person
                    _state.value = _state.value.copy(self = person)
                }
                active.send(TdApi.GetUserFullInfo(response.id)) { info ->
                    synchronized(lock) {
                        if (epoch != generation) return@synchronized
                        if (info is TdApi.UserFullInfo) {
                            _state.value = _state.value.copy(bio = info.bio?.text.orEmpty())
                        }
                    }
                }
            } else if (response is TdApi.Error) {
                synchronized(lock) {
                    if (epoch == generation) _state.value = _state.value.copy(
                        notice = "Profile unavailable (Telegram code ${response.code})."
                    )
                }
            }
        }
        active.send(TdApi.GetContacts()) { response ->
            val ids = (response as? TdApi.Users)?.userIds?.toSet()
            synchronized(lock) {
                if (epoch != generation) return@synchronized
                if (ids == null) {
                    _state.value = _state.value.copy(
                        contactsLoading = false,
                        notice = "Contacts unavailable. Try Refresh."
                    )
                    return@synchronized
                }
                contactIds = ids
                publishContacts()
                _state.value = _state.value.copy(contactsLoading = ids.isNotEmpty())
            }
            var pending = ids?.size ?: 0
            ids?.forEach { id ->
                active.send(TdApi.GetUser(id)) { user ->
                    synchronized(lock) {
                        if (epoch != generation) return@synchronized
                        if (user is TdApi.User && id in contactIds) {
                            users[id] = model(user)
                            publishContacts()
                        }
                        // Even one missing/inaccessible contact cannot leave
                        // the spinner stuck forever.
                        pending--
                        if (pending <= 0) {
                            _state.value = _state.value.copy(contactsLoading = false)
                        }
                    }
                }
            }
        }
    }

    fun onUpdate(update: TdApi.Object) {
        synchronized(lock) {
            when (update) {
                is TdApi.UpdateUser -> {
                    val user = update.user
                    if (user.id != selfId && user.id !in contactIds) return
                    val person = model(user)
                    users[person.id] = person
                    if (user.id == selfId) _state.value = _state.value.copy(self = person)
                    else publishContacts()
                }
                is TdApi.UpdateUserFullInfo -> if (update.userId == selfId) {
                    _state.value = _state.value.copy(
                        bio = update.userFullInfo?.bio?.text.orEmpty()
                    )
                }
                else -> Unit
            }
        }
    }

    fun saveProfile(first: String, last: String, bio: String) {
        val firstSafe = first.trim().take(64)
        val lastSafe = last.trim().take(64)
        val bioSafe = bio.trim().take(70)
        if (firstSafe.isEmpty()) {
            _state.value = _state.value.copy(notice = "First name is required.")
            return
        }
        val active = client() ?: run {
            _state.value = _state.value.copy(notice = "Telegram is offline.")
            return
        }
        val epoch: Long
        synchronized(lock) {
            if (_state.value.busy) return
            _state.value = _state.value.copy(busy = true, notice = null)
            epoch = generation
        }
        // Telegram is authoritative. Refresh only after both requests succeed.
        active.send(TdApi.SetName(firstSafe, lastSafe)) { result ->
            synchronized(lock) { if (epoch != generation) return@send }
            if (result is TdApi.Error) {
                synchronized(lock) {
                    if (epoch == generation) _state.value = _state.value.copy(
                        busy = false,
                        notice = "Could not update name (Telegram code ${result.code})."
                    )
                }
                return@send
            }
            active.send(TdApi.SetBio(bioSafe)) { bioResult ->
                synchronized(lock) {
                    if (epoch != generation) return@synchronized
                    _state.value = _state.value.copy(
                        busy = false,
                        notice = if (bioResult is TdApi.Error)
                            "Name saved; bio failed (Telegram code ${bioResult.code})."
                        else "Telegram profile updated."
                    )
                }
                refresh()
            }
        }
    }

    /** Phase 4.1: only an explicit account action updates the public username. */
    fun changeUsername(raw: String) {
        val username = raw.trim().removePrefix("@")
        if (username.length > 32 ||
            (username.isNotEmpty() && !username.matches(Regex("[A-Za-z0-9_]+")))) {
            synchronized(lock) {
                _state.value = _state.value.copy(notice = "Username may contain only letters, numbers and underscores.")
            }
            return
        }
        val active = client() ?: return
        synchronized(lock) {
            if (_state.value.busy) return
            _state.value = _state.value.copy(busy = true, notice = null)
        }
        active.send(TdApi.SetUsername(username)) { reply ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    busy = false,
                    notice = if (reply is TdApi.Error)
                        "Telegram rejected username (code ${reply.code})."
                    else "Username saved to Telegram."
                )
            }
            if (reply !is TdApi.Error) refresh()
        }
    }

    /** The UI copies an explicitly selected JPEG/PNG to private cache. */
    fun uploadProfilePhoto(localPath: String) {
        val file = java.io.File(localPath)
        if (!file.isFile || file.length() !in 1..5_000_000) {
            synchronized(lock) { _state.value = _state.value.copy(
                notice = "Choose a photo smaller than 5 MB."
            ) }
            return
        }
        val active = client() ?: return
        synchronized(lock) {
            if (_state.value.busy) return
            _state.value = _state.value.copy(busy = true, notice = null)
        }
        active.send(
            TdApi.SetProfilePhoto(
                TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(file.absolutePath)), false
            )
        ) { reply ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    busy = false,
                    notice = if (reply is TdApi.Error)
                        "Telegram rejected photo (code ${reply.code})."
                    else "Telegram profile photo updated."
                )
            }
            file.delete()
            if (reply !is TdApi.Error) refresh()
        }
    }

    /** Resolve an exact public @username, then explicitly add on Telegram.
     *  We never share the current user's phone number by default.
     */
    fun addContactByUsername(raw: String) {
        val username = raw.trim().removePrefix("@")
        if (username.length !in 3..32 ||
            !username.matches(Regex("[A-Za-z0-9_]+"))) {
            synchronized(lock) {
                _state.value = _state.value.copy(notice = "Enter a valid Telegram @username.")
            }
            return
        }
        val active = client() ?: return
        synchronized(lock) {
            if (_state.value.busy) return
            _state.value = _state.value.copy(busy = true, notice = null)
        }
        active.send(TdApi.SearchPublicChat(username)) { response ->
            val type = (response as? TdApi.Chat)?.type as? TdApi.ChatTypePrivate
            if (type == null) {
                synchronized(lock) { _state.value = _state.value.copy(
                    busy = false, notice = "This username isn't a public Telegram user."
                ) }
                return@send
            }
            active.send(TdApi.GetUser(type.userId)) { user ->
                if (user !is TdApi.User) {
                    synchronized(lock) { _state.value = _state.value.copy(
                        busy = false, notice = "Telegram user is unavailable."
                    ) }
                    return@send
                }
                val contact = TdApi.ImportedContact().apply {
                    phoneNumber = ""
                    firstName = user.firstName.orEmpty()
                    lastName = user.lastName.orEmpty()
                }
                active.send(TdApi.AddContact(type.userId, contact, false)) { reply ->
                    synchronized(lock) {
                        _state.value = _state.value.copy(
                            busy = false,
                            notice = if (reply is TdApi.Error)
                                "Add contact failed (Telegram code ${reply.code})."
                            else "Contact added to Telegram."
                        )
                    }
                    if (reply !is TdApi.Error) refresh()
                }
            }
        }
    }

    /** Phase 4.1: remove only the selected Telegram-cloud contact. */
    fun removeContact(personId: Long) {
        val active = client() ?: return
        if (personId !in synchronized(lock) { contactIds }) return
        active.send(TdApi.RemoveContacts(longArrayOf(personId))) { reply ->
            synchronized(lock) {
                _state.value = _state.value.copy(
                    notice = if (reply is TdApi.Error) "Cannot remove contact (code ${reply.code})."
                    else "Telegram contact removed."
                )
            }
            if (reply !is TdApi.Error) refresh()
        }
    }

    /** Telegram Saved Messages is the authenticated user's own private chat. */
    fun openSavedMessages(onChat: (Long) -> Unit) {
        val id = synchronized(lock) { selfId } ?: return
        val active = client() ?: return
        active.send(TdApi.CreatePrivateChat(id, false)) { result ->
            when (result) {
                is TdApi.Chat -> onChat(result.id)
                is TdApi.Error -> synchronized(lock) {
                    _state.value = _state.value.copy(
                        notice = "Saved Messages unavailable (Telegram code ${result.code})."
                    )
                }
            }
        }
    }

    fun openContact(personId: Long, onChat: (Long) -> Unit) {
        if (personId !in synchronized(lock) { contactIds }) return
        val active = client() ?: return
        active.send(TdApi.CreatePrivateChat(personId, false)) { result ->
            when (result) {
                is TdApi.Chat -> onChat(result.id)
                is TdApi.Error -> synchronized(lock) {
                    _state.value = _state.value.copy(
                        notice = "Could not open chat (Telegram code ${result.code})."
                    )
                }
            }
        }
    }

    fun clearNotice() { synchronized(lock) {
        _state.value = _state.value.copy(notice = null)
    } }

    fun clear() {
        synchronized(lock) {
            generation++
            selfId = null
            contactIds = emptySet()
            users.clear()
            _state.value = CoreAccountState()
        }
    }
}
