package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.repository.VybeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VybeNotificationActionReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "VybeNotifActionReceiver"
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val ACTION_DIRECT_REPLY = "com.aistudio.vybe.ACTION_DIRECT_REPLY"
    const val ACTION_MARK_READ = "com.aistudio.vybe.ACTION_MARK_READ"
    const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
    const val EXTRA_PEER_TITLE = "extra_peer_title"
    const val EXTRA_IS_GROUP = "extra_is_group"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
    val notifId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)

    when (action) {
      ACTION_DIRECT_REPLY -> {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
        val peerTitle = intent.getStringExtra(EXTRA_PEER_TITLE) ?: "VYBE Chat"
        val isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, false)
        if (!replyText.isNullOrBlank() && !conversationId.isNullOrBlank()) {
          VybeNotificationHelper.appendSelfReplyToNotification(
            context = context,
            conversationId = conversationId,
            conversationTitle = peerTitle,
            replyText = replyText,
            isGroup = isGroup
          )
          scope.launch {
            try {
              val repo = VybeRepository.get()
              repo.sendDirectMessage(conversationId, replyText)
              if (!notifId.isNullOrBlank()) {
                repo.markNotificationAsRead(notifId)
              }
            } catch (e: Exception) {
              Log.w(TAG, "Direct reply error: ${e.message}")
            }
          }
        }
      }
      ACTION_MARK_READ -> {
        if (!conversationId.isNullOrBlank()) {
          VybeNotificationHelper.clearConversationNotifications(context, conversationId)
          scope.launch {
            try {
              val repo = VybeRepository.get()
              repo.markConversationAsRead(conversationId)
              if (!notifId.isNullOrBlank()) {
                repo.markNotificationAsRead(notifId)
              }
            } catch (_: Exception) {}
          }
        } else if (!notifId.isNullOrBlank()) {
          scope.launch {
            try {
              VybeRepository.get().markNotificationAsRead(notifId)
            } catch (_: Exception) {}
          }
        }
      }
    }
  }
}
