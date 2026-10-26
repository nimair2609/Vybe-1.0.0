package com.example.data.remote

import android.util.Log
import com.example.data.model.LivePulseStatus
import com.example.data.model.UnloadMessage
import com.example.data.model.UnloadRoom
import com.example.data.model.UnloadRoomMode
import com.example.util.TimestampUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * Modular remote data access for Persistent Live Rooms, Presence Heartbeats,
 * Message Read Receipts, and Cross-Device Voice/Video Call Signaling.
 */
object SupabaseRoomsAndCallsRemote {

  private const val TAG = "SupabaseRoomsCalls"
  private const val ROOM_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
  private val secureRandom = SecureRandom()

  /**
   * Generates a unique, non-guessable VYBE Room ID such as `VYB-7K4M-9P2X`.
   */
  fun generateUniqueRoomCode(): String {
    val partA = (1..4).map { ROOM_CODE_ALPHABET[secureRandom.nextInt(ROOM_CODE_ALPHABET.length)] }.joinToString("")
    val partB = (1..4).map { ROOM_CODE_ALPHABET[secureRandom.nextInt(ROOM_CODE_ALPHABET.length)] }.joinToString("")
    return "VYB-$partA-$partB"
  }

  fun normalizeRoomLookupInput(raw: String): String {
    val trimmed = raw.trim().uppercase()
    if (trimmed.contains("-") && trimmed.length > 20) {
      // Looks like a UUID
      return trimmed.lowercase()
    }
    val alnum = trimmed.filter { it.isLetterOrDigit() }
    return if (alnum.startsWith("VYB") && alnum.length == 11) {
      "VYB-${alnum.substring(3, 7)}-${alnum.substring(7, 11)}"
    } else if (alnum.length == 8) {
      "VYB-${alnum.substring(0, 4)}-${alnum.substring(4, 8)}"
    } else {
      trimmed
    }
  }

  // =========================================================================
  // 1. PRESENCE HEARTBEAT & READ RECEIPTS
  // =========================================================================

  suspend fun updatePresence(
    userId: String,
    livePulse: LivePulseStatus,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || userId.isBlank() || authHeader.isNullOrBlank()) return@withContext
    try {
      SupabaseNetworkClient.restApi.patchPresence(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$userId",
        patch = SupabasePresencePatchDto(
          lastSeenAt = TimestampUtils.nowIsoUtc(),
          livePulse = livePulse.name
        )
      )
    } catch (e: Exception) {
      Log.d(TAG, "Presence update skipped (migration 0005 optional): ${e.message}")
    }
  }

  suspend fun markMessagesRead(
    userId: String,
    messageIds: List<String>,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || userId.isBlank() || messageIds.isEmpty() || authHeader.isNullOrBlank()) {
      return@withContext
    }
    val validUuidIds = messageIds.filter { !it.startsWith("c_") && !it.startsWith("m_") }
    if (validUuidIds.isEmpty()) return@withContext
    try {
      val nowIso = TimestampUtils.nowIsoUtc()
      val payload = validUuidIds.map { mid ->
        SupabaseMessageReceiptDto(messageId = mid, userId = userId, readAt = nowIso)
      }
      SupabaseNetworkClient.restApi.upsertMessageReceipts(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        receipts = payload
      )
    } catch (_: Exception) {}
  }

  // =========================================================================
  // 2. PERSISTENT LIVE ROOMS (CREATE, LIST, SEARCH BY ROOM ID, JOIN, CHAT)
  // =========================================================================

  suspend fun fetchActiveRooms(
    currentUserId: String?,
    authHeader: String?
  ): Result<List<UnloadRoom>> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured()) return@withContext Result.success(emptyList())
    try {
      var resp = SupabaseNetworkClient.restApi.getRooms(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getRooms(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) {
        return@withContext Result.failure(Exception("Unable to fetch rooms (${resp.code()})."))
      }
      val nowMs = System.currentTimeMillis()
      val rooms = resp.body()!!.mapNotNull { dto ->
        val expMs = TimestampUtils.parseIsoMillis(dto.expiresAt)
        if (expMs != null && expMs <= nowMs) return@mapNotNull null
        if (dto.isActive == false) return@mapNotNull null
        mapRoomDto(dto, currentUserId, authHeader)
      }
      Result.success(rooms)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun findRoomByCodeOrId(
    rawInput: String,
    currentUserId: String?,
    authHeader: String?
  ): Result<UnloadRoom> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured()) {
      return@withContext Result.failure(Exception("Backend not configured."))
    }
    val normalized = normalizeRoomLookupInput(rawInput)
    if (normalized.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Please enter a valid Room ID."))
    }

    try {
      // 1. Lookup by unique room_code (e.g. VYB-7K4M-9P2X)
      if (normalized.startsWith("VYB-")) {
        val codeResp = SupabaseNetworkClient.restApi.getRoomByCode(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          roomCodeFilter = "eq.$normalized"
        )
        if (codeResp.isSuccessful && !codeResp.body().isNullOrEmpty()) {
          val dto = codeResp.body()!!.first()
          return@withContext validateAndMapLookupRoom(dto, currentUserId, authHeader)
        }
      }

      // 2. Lookup by UUID room ID if valid UUID format
      if (normalized.count { it == '-' } == 4) {
        val idResp = SupabaseNetworkClient.restApi.getRoomById(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          idFilter = "eq.${normalized.lowercase()}"
        )
        if (idResp.isSuccessful && !idResp.body().isNullOrEmpty()) {
          val dto = idResp.body()!!.first()
          return@withContext validateAndMapLookupRoom(dto, currentUserId, authHeader)
        }
      }

      // 3. Fallback scan on active rooms (supports unmigrated DBs where room_code is derived from UUID)
      val allResp = SupabaseNetworkClient.restApi.getRooms(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        limit = 80
      )
      if (allResp.isSuccessful && !allResp.body().isNullOrEmpty()) {
        val matched = allResp.body()!!.firstOrNull { dto ->
          val derivedCode = dto.roomCode?.uppercase()
            ?: "VYB-${dto.id.filter { it.isLetterOrDigit() }.take(8).uppercase().chunked(4).joinToString("-")}"
          derivedCode.equals(normalized, ignoreCase = true) || dto.id.equals(normalized, ignoreCase = true)
        }
        if (matched != null) {
          return@withContext validateAndMapLookupRoom(matched, currentUserId, authHeader)
        }
      }

      Result.failure(Exception("No active room found for ID '$normalized'. Check the code and try again."))
    } catch (e: Exception) {
      Result.failure(Exception(e.message ?: "Unable to look up Room ID."))
    }
  }

  private suspend fun validateAndMapLookupRoom(
    dto: SupabaseRoomDto,
    currentUserId: String?,
    authHeader: String?
  ): Result<UnloadRoom> {
    if (dto.isActive == false) {
      return Result.failure(Exception("This room has already ended."))
    }
    val expMs = TimestampUtils.parseIsoMillis(dto.expiresAt)
    if (expMs != null && expMs <= System.currentTimeMillis()) {
      return Result.failure(Exception("This room has expired."))
    }
    val mapped = mapRoomDto(dto, currentUserId, authHeader)
    if (mapped.activeListenersCount >= mapped.capacity && mapped.hostId != currentUserId) {
      return Result.failure(Exception("This room is currently full (${mapped.capacity}/${mapped.capacity})."))
    }
    return Result.success(mapped)
  }

  suspend fun createRoom(
    hostId: String,
    hostName: String,
    title: String,
    description: String,
    topicTag: String,
    mode: UnloadRoomMode,
    durationMinutes: Int,
    capacity: Int,
    scheduledTime: String,
    joinerMessage: String,
    authHeader: String?
  ): Result<UnloadRoom> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank()) {
      return@withContext Result.failure(Exception("Please sign in to create a live room."))
    }
    val roomCode = generateUniqueRoomCode()
    val expiresIso = TimestampUtils.formatIsoUtc(
      System.currentTimeMillis() + (durationMinutes.coerceIn(15, 1440) * 60_000L).coerceAtLeast(3_600_000L)
    )
    try {
      var resp = SupabaseNetworkClient.restApi.insertRoom(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        room = SupabaseRoomInsertDto(
          roomCode = roomCode,
          hostId = hostId,
          title = title.trim(),
          description = description.trim(),
          category = topicTag.trim(),
          mode = mode.name,
          durationMinutes = durationMinutes,
          maxParticipants = capacity,
          isPrivate = false,
          isActive = true,
          joinerMessage = joinerMessage.trim(),
          scheduledTime = scheduledTime,
          expiresAt = expiresIso
        )
      )
      if (!resp.isSuccessful && resp.code() == 400) {
        resp = SupabaseNetworkClient.restApi.insertRoomLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          room = SupabaseRoomLegacyInsertDto(
            hostId = hostId,
            title = title.trim(),
            description = description.trim(),
            category = topicTag.trim(),
            maxParticipants = capacity,
            isPrivate = false,
            isActive = true
          )
        )
      }
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        val created = resp.body()!!.first()
        // Register host in room_members
        try {
          SupabaseNetworkClient.restApi.upsertRoomMember(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            member = SupabaseRoomMemberDto(roomId = created.id, userId = hostId, role = "HOST")
          )
        } catch (_: Exception) {}

        val derivedCode = created.roomCode?.takeIf { it.isNotBlank() }
          ?: "VYB-${created.id.filter { it.isLetterOrDigit() }.take(8).uppercase().chunked(4).joinToString("-")}"

        Result.success(
          UnloadRoom(
            id = created.id,
            roomCode = derivedCode,
            title = created.title,
            description = created.description ?: description,
            topicTag = created.category ?: topicTag,
            mode = mode,
            activeListenersCount = 1,
            isHostOnline = true,
            isActive = true,
            expiresAtIso = created.expiresAt ?: expiresIso,
            createdAtIso = created.createdAt ?: TimestampUtils.nowIsoUtc(),
            hostId = hostId,
            hostName = hostName,
            durationMinutes = durationMinutes,
            capacity = capacity,
            scheduledTime = scheduledTime,
            joinerMessage = joinerMessage
          )
        )
      } else {
        Result.failure(Exception("Failed to create room (${resp.code()})."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun joinRoomMember(
    roomId: String,
    userId: String,
    role: String = "LISTENER",
    authHeader: String?
  ): Boolean = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank() || roomId.startsWith("ul_")) return@withContext true
    try {
      val resp = SupabaseNetworkClient.restApi.upsertRoomMember(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        member = SupabaseRoomMemberDto(roomId = roomId, userId = userId, role = role)
      )
      resp.isSuccessful
    } catch (_: Exception) {
      false
    }
  }

  suspend fun leaveRoomMember(
    roomId: String,
    userId: String,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank() || roomId.startsWith("ul_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.deleteRoomMember(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        roomIdFilter = "eq.$roomId",
        userIdFilter = "eq.$userId"
      )
    } catch (_: Exception) {}
  }

  suspend fun updateRoomOnSupabase(
    roomId: String,
    title: String,
    description: String,
    topicTag: String,
    mode: UnloadRoomMode,
    durationMinutes: Int,
    capacity: Int,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank() || roomId.startsWith("ul_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.patchRoom(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$roomId",
        patch = SupabaseRoomPatchDto(
          title = title,
          description = description,
          category = topicTag,
          mode = mode.name,
          durationMinutes = durationMinutes,
          maxParticipants = capacity
        )
      )
    } catch (_: Exception) {}
  }

  suspend fun deleteRoomOnSupabase(
    roomId: String,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank() || roomId.startsWith("ul_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.deleteRoom(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$roomId"
      )
    } catch (_: Exception) {}
  }

  suspend fun fetchRoomMessages(
    roomId: String,
    currentUserId: String?,
    authHeader: String?
  ): List<UnloadMessage> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || roomId.startsWith("ul_") || roomId.startsWith("room_")) {
      return@withContext emptyList()
    }
    try {
      var resp = SupabaseNetworkClient.restApi.getRoomMessages(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        roomIdFilter = "eq.$roomId"
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getRoomMessages(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          roomIdFilter = "eq.$roomId",
          select = "*"
        )
      }
      if (resp.isSuccessful && resp.body() != null) {
        return@withContext resp.body()!!.map { m ->
          val anon = m.isAnonymous == true
          val displaySender = when {
            anon -> "Anonymous Vyber"
            !m.senderName.isNullOrBlank() -> m.senderName
            !m.profiles?.displayName.isNullOrBlank() -> m.profiles!!.displayName!!
            !m.profiles?.username.isNullOrBlank() -> m.profiles!!.username!!
            else -> "VYBE Member"
          }
          UnloadMessage(
            id = m.id ?: "rm_${System.currentTimeMillis()}",
            senderName = displaySender,
            isAnonymous = anon,
            text = m.content,
            timestamp = TimestampUtils.formatRelative(m.createdAt, fallback = "Just now"),
            createdAtIso = m.createdAt,
            isMe = m.senderId == currentUserId
          )
        }
      }
    } catch (_: Exception) {}
    emptyList()
  }

  suspend fun sendRoomMessage(
    roomId: String,
    senderId: String,
    senderName: String,
    content: String,
    isAnonymous: Boolean,
    authHeader: String?
  ): Result<UnloadMessage> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank() || roomId.startsWith("ul_") || roomId.startsWith("room_")) {
      return@withContext Result.failure(Exception("Local community space"))
    }
    try {
      var resp = SupabaseNetworkClient.restApi.insertRoomMessage(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        message = SupabaseRoomMessageInsertDto(
          roomId = roomId,
          senderId = senderId,
          content = content,
          senderName = if (isAnonymous) "Anonymous Vyber" else senderName,
          isAnonymous = isAnonymous
        )
      )
      if (!resp.isSuccessful && resp.code() == 400) {
        resp = SupabaseNetworkClient.restApi.insertRoomMessageLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          message = SupabaseRoomMessageLegacyInsertDto(
            roomId = roomId,
            senderId = senderId,
            content = content
          )
        )
      }
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        val dto = resp.body()!!.first()
        Result.success(
          UnloadMessage(
            id = dto.id ?: "rm_${System.currentTimeMillis()}",
            senderName = if (isAnonymous) "Anonymous Vyber" else senderName,
            isAnonymous = isAnonymous,
            text = dto.content,
            timestamp = TimestampUtils.formatRelative(dto.createdAt, fallback = "Just now"),
            createdAtIso = dto.createdAt ?: TimestampUtils.nowIsoUtc(),
            isMe = true
          )
        )
      } else {
        Result.failure(Exception("Failed to send room message"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private suspend fun mapRoomDto(
    dto: SupabaseRoomDto,
    currentUserId: String?,
    authHeader: String?
  ): UnloadRoom {
    val memberCount = try {
      val mResp = SupabaseNetworkClient.restApi.getRoomMembers(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        roomIdFilter = "eq.${dto.id}"
      )
      if (mResp.isSuccessful) (mResp.body()?.size ?: 1).coerceAtLeast(1) else 1
    } catch (_: Exception) { 1 }

    val messages = fetchRoomMessages(dto.id, currentUserId, authHeader)
    val hostName = dto.profiles?.displayName?.takeIf { it.isNotBlank() && !it.equals("null", true) }
      ?: dto.profiles?.username?.takeIf { it.isNotBlank() && !it.equals("null", true) }
      ?: "VYBE Host"
    val parsedMode = try {
      UnloadRoomMode.valueOf(dto.mode ?: "VOICE_AND_TEXT")
    } catch (_: Exception) {
      UnloadRoomMode.VOICE_AND_TEXT
    }
    val code = dto.roomCode?.takeIf { it.isNotBlank() }
      ?: "VYB-${dto.id.filter { it.isLetterOrDigit() }.take(8).uppercase().chunked(4).joinToString("-")}"

    return UnloadRoom(
      id = dto.id,
      roomCode = code,
      title = dto.title,
      description = dto.description ?: "",
      topicTag = dto.category ?: "Community Space",
      mode = parsedMode,
      activeListenersCount = memberCount,
      isHostOnline = dto.isActive != false,
      isActive = dto.isActive != false,
      isPrivateRoom = dto.isPrivate == true,
      expiresAtIso = dto.expiresAt,
      createdAtIso = dto.createdAt,
      recentMessages = messages,
      hostId = dto.hostId,
      hostName = hostName,
      durationMinutes = dto.durationMinutes ?: 45,
      capacity = dto.maxParticipants ?: 25,
      scheduledTime = dto.scheduledTime ?: "Live Now",
      joinerMessage = dto.joinerMessage ?: "Welcome to this listening space. Share your thoughts or simply listen."
    )
  }

  // =========================================================================
  // 3. CALL SESSION & SIGNALING CRUD
  // =========================================================================

  suspend fun createCallSession(
    callerId: String,
    calleeId: String,
    isVideo: Boolean,
    offerSdp: String,
    authHeader: String?
  ): Result<SupabaseCallSessionDto> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || authHeader.isNullOrBlank()) {
      return@withContext Result.failure(Exception("Authentication required to place a call."))
    }
    try {
      val resp = SupabaseNetworkClient.restApi.insertCallSession(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        session = SupabaseCallSessionInsertDto(
          callerId = callerId,
          calleeId = calleeId,
          isVideo = isVideo,
          status = "RINGING",
          offerSdp = offerSdp
        )
      )
      if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
        Result.success(resp.body()!!.first())
      } else {
        Result.failure(Exception("Call signaling table not ready (${resp.code()}). Run migration 0005 in Supabase SQL Editor."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun fetchIncomingCalls(
    myUserId: String,
    authHeader: String?
  ): List<SupabaseCallSessionDto> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || myUserId.isBlank() || authHeader.isNullOrBlank()) return@withContext emptyList()
    try {
      val resp = SupabaseNetworkClient.restApi.getIncomingCallSessions(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        calleeIdFilter = "eq.$myUserId"
      )
      if (resp.isSuccessful) {
        val now = System.currentTimeMillis()
        // Only surface recent ringing calls (< 50 seconds old)
        return@withContext resp.body().orEmpty().filter { dto ->
          val created = TimestampUtils.parseIsoMillis(dto.createdAt)
          created == null || (now - created) in -60_000L..50_000L
        }
      }
    } catch (_: Exception) {}
    emptyList()
  }

  suspend fun fetchCallSessionById(
    callId: String,
    authHeader: String?
  ): SupabaseCallSessionDto? = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || callId.isBlank() || authHeader.isNullOrBlank()) return@withContext null
    try {
      val resp = SupabaseNetworkClient.restApi.getCallSessionById(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$callId"
      )
      if (resp.isSuccessful) return@withContext resp.body()?.firstOrNull()
    } catch (_: Exception) {}
    null
  }

  suspend fun patchCallSession(
    callId: String,
    status: String? = null,
    answerSdp: String? = null,
    startedAt: String? = null,
    endedAt: String? = null,
    authHeader: String?
  ): Boolean = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || callId.isBlank() || authHeader.isNullOrBlank()) return@withContext false
    try {
      val resp = SupabaseNetworkClient.restApi.patchCallSession(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$callId",
        patch = SupabaseCallSessionPatchDto(
          status = status,
          answerSdp = answerSdp,
          startedAt = startedAt,
          endedAt = endedAt
        )
      )
      resp.isSuccessful
    } catch (_: Exception) {
      false
    }
  }

  suspend fun postCallSignal(
    callId: String,
    senderId: String,
    signalType: String,
    payload: Map<String, String>,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || callId.isBlank() || authHeader.isNullOrBlank()) return@withContext
    try {
      SupabaseNetworkClient.restApi.insertCallSignal(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        signal = SupabaseCallSignalInsertDto(
          callId = callId,
          senderId = senderId,
          signalType = signalType,
          payload = payload
        )
      )
    } catch (_: Exception) {}
  }

  suspend fun fetchCallSignals(
    callId: String,
    authHeader: String?
  ): List<SupabaseCallSignalDto> = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured() || callId.isBlank() || authHeader.isNullOrBlank()) return@withContext emptyList()
    try {
      val resp = SupabaseNetworkClient.restApi.getCallSignals(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        callIdFilter = "eq.$callId"
      )
      if (resp.isSuccessful) return@withContext resp.body().orEmpty()
    } catch (_: Exception) {}
    emptyList()
  }
}
