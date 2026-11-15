package com.example.opendash.google

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Receives the Navigation SDK's turn-by-turn feed (about once a second while guiding).
 * The SDK binds to this service and posts [NavInfo] bundles; we republish them as a
 * flow that the dash loop reads to fill the Tripper's own turn widget.
 */
class GoogleNavInfoService : Service() {
    private val turnByTurn = TurnByTurnManager.createInstance()

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == TurnByTurnManager.MSG_NAV_INFO) {
                runCatching { turnByTurn.readNavInfoFromBundle(msg.data) }
                    .getOrNull()?.let { _navInfo.value = it }
            } else super.handleMessage(msg)
        }
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        _navInfo.value = null
        return super.onUnbind(intent)
    }

    companion object {
        private val _navInfo = MutableStateFlow<NavInfo?>(null)
        val navInfo = _navInfo.asStateFlow()
        fun clear() { _navInfo.value = null }
    }
}
