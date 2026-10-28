package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.data.repository.VybeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles Instagram-style interactive notification actions directly from the Android notification shade:
 * - Inline Direct Reply (`RemoteInput`) for Direct & Group Messages without opening the app.
 * - Instant "Mark as Read" dismissal and Supabase read-state synchronization.
 */
class VybeNotificationActionReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "VybeNotifReceiver"
    const val ACTION_DIRECT_REPLY = "com.aistudio.vybe.ACTION_DIRECT_REPLY"
    const val ACTION_MARK_READ = "com.aistudio.vybe.ACTION_MARK_READ"
    const val KEY_TEXT_REPLY = "key_vybe_direct_reply_text"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_PEER_TITLE = "peer_title"
    const val EXTRA_IS_GROUP = "is_group"
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent == null) return
    val appCtx = context.applicationContext
    val action = intent.action ?: return
    val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
    val notificationRecordId = intent.getStringExtra(EXTRA_NOTIFICATION_ID).orEmpty()
    val peerTitle = intent.getStringExtra(EXTRA_PEER_TITLE).orEmpty().ifBlank { "VYBE Chat" }
    val isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, false)

    when (action) {
      ACTION_DIRECT_REPLY -> {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
        if (replyText.isBlank() || conversationId.isBlank()) return

        val pendingResult = goAsync()
        scope.launch {
          try {
            VybeRepository.getInstance().initSession(appCtx)
            val sentOk = VybeRepository.get().sendQuickReplyFromNotification(
              context = appCtx,
              conversationId = conversationId,
              replyText = replyText
            )
            if (sentOk) {
              VybeNotificationHelper.appendSelfReplyToNotification(
                context = appCtx,
                conversationId = conversationId,
                conversationTitle = peerTitle,
                replyText = replyText,
                isGroup = isGroup
              )
            } else {
              VybeNotificationHelper.clearConversationNotifications(appCtx, conversationId)
            }
            if (notificationRecordId.isNotBlank()) {
              VybeRepository.get().markNotificationAsRead(notificationRecordId)
            }
          } catch (e: Exception) {
            Log.e(TAG, "Inline reply failed", e)
          } finally {
            pendingResult.finish()
          }
        }
      }

      ACTION_MARK_READ -> {
        val pendingResult = goAsync()
        scope.launch {
          try {
            VybeRepository.getInstance().initSession(appCtx)
            if (conversationId.isNotBlank()) {
              VybeRepository.get().markConversationAsRead(conversationId)
              VybeNotificationHelper.clearConversationNotifications(appCtx, conversationId)
            }
            if (notificationRecordId.isNotBlank()) {
              VybeRepository.get().markNotificationAsRead(notificationRecordId)
            }
          } catch (e: Exception) {
            Log.w(TAG, "Mark-as-read action warning: ${e.message}")
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }
}
