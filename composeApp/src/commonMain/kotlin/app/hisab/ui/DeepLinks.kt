package app.hisab.ui

import app.hisab.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Invite links opened from outside the app (hisab://join/<code>). */
object DeepLinks {
    private val _invite = MutableStateFlow<String?>(null)
    val invite: StateFlow<String?> = _invite

    fun handle(url: String) {
        Repository.parseInviteCode(url)?.let { _invite.value = it }
    }

    fun consume() {
        _invite.value = null
    }
}
