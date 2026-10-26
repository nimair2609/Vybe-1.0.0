package com.example.data.remote

import android.util.Log
import com.example.data.model.*
import com.example.util.TimestampUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cross-device Supabase synchronization for VYBE Live Zone (`live_streams`, `live_stream_comments`)
 * and all Explore Zone spaces (`audience_pools`, `double_takes`, `time_capsules`,
 * `crossed_paths`, `business_profiles`).
 */
object SupabaseExploreAndLiveRemote {
  private const val TAG = "VybeExploreLiveRemote"

  private fun mapProfileOrFallback(dto: SupabaseProfileDto?, userId: String, usersById: Map<String, User>): User {
    if (dto != null) {
      val cleanUser = dto.username?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
        ?: "vybe_${userId.replace("-", "").take(8)}"
      val fullName = "${dto.firstName.orEmpty()} ${dto.lastName.orEmpty()}".trim()
      val cleanDisplay = dto.displayName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
        ?: fullName.ifBlank { cleanUser }
      return User(
        id = dto.id,
        username = cleanUser,
        displayName = cleanDisplay,
        avatarUrl = dto.avatarUrl.orEmpty(),
        isVerified = dto.isVerified ?: false,
        isVip = dto.isVip ?: false,
        isOnline = TimestampUtils.isRecentlyOnline(dto.lastSeenAt)
      )
    }
    return usersById[userId] ?: User(
      id = userId,
      username = "vybe_${userId.replace("-", "").take(6)}",
      displayName = "VYBE Member"
    )
  }

  // =========================================================================
  // 1. LIVE ZONE (`live_streams` & `live_stream_comments`)
  // =========================================================================

  suspend fun fetchActiveLiveStreams(
    usersById: Map<String, User>,
    authHeader: String?
  ): List<LiveStreamSession> = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getActiveLiveStreams(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getActiveLiveStreams(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) return@withContext emptyList()
      val nowMs = System.currentTimeMillis()
      resp.body()!!.mapNotNull { dto ->
        if (!dto.isLive) return@mapNotNull null
        // Filter out orphaned streams whose heartbeat/start is older than 2 hours
        val heartbeatMs = TimestampUtils.parseIsoMillis(dto.lastHeartbeatAt ?: dto.startedAt)
        if (heartbeatMs != null && nowMs - heartbeatMs > 2L * 3600_000L) {
          return@mapNotNull null
        }
        LiveStreamSession(
          id = dto.id,
          host = mapProfileOrFallback(dto.profiles, dto.hostId, usersById),
          title = dto.title.ifBlank { "Live on VYBE" },
          category = dto.category ?: "Live Vibe",
          visibility = dto.visibility ?: "PUBLIC",
          isLive = dto.isLive,
          viewerCount = dto.viewerCount.coerceAtLeast(1),
          thumbnailUrl = dto.thumbnailUrl,
          latestFrameBase64 = dto.latestFrameBase64,
          startedAtIso = dto.startedAt
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "fetchActiveLiveStreams error: ${e.message}")
      emptyList()
    }
  }

  suspend fun fetchLiveStreamById(
    streamId: String,
    usersById: Map<String, User>,
    authHeader: String?
  ): LiveStreamSession? = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getLiveStreamById(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$streamId"
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getLiveStreamById(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          idFilter = "eq.$streamId",
          select = "*"
        )
      }
      val dto = resp.body()?.firstOrNull() ?: return@withContext null
      LiveStreamSession(
        id = dto.id,
        host = mapProfileOrFallback(dto.profiles, dto.hostId, usersById),
        title = dto.title.ifBlank { "Live on VYBE" },
        category = dto.category ?: "Live Vibe",
        visibility = dto.visibility ?: "PUBLIC",
        isLive = dto.isLive,
        viewerCount = dto.viewerCount.coerceAtLeast(1),
        thumbnailUrl = dto.thumbnailUrl,
        latestFrameBase64 = dto.latestFrameBase64,
        startedAtIso = dto.startedAt
      )
    } catch (_: Exception) {
      null
    }
  }

  suspend fun startLiveStream(
    host: User,
    title: String,
    category: String,
    authHeader: String?
  ): Result<LiveStreamSession> = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext Result.failure(Exception("Please sign in to go live."))
    try {
      var resp = SupabaseNetworkClient.restApi.insertLiveStream(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        stream = SupabaseLiveStreamInsertDto(
          hostId = host.id,
          title = title.trim().ifBlank { "${host.displayName} is Live" },
          category = category.trim().ifBlank { "Live Vibe" },
          viewerCount = 1
        )
      )
      if (!resp.isSuccessful && resp.code() == 400) {
        resp = SupabaseNetworkClient.restApi.insertLiveStreamLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          stream = SupabaseLiveStreamLegacyInsertDto(
            hostId = host.id,
            title = title.trim().ifBlank { "${host.displayName} is Live" },
            viewerCount = 1
          )
        )
      }
      val dto = resp.body()?.firstOrNull()
        ?: return@withContext Result.failure(Exception("Failed to start live stream (${resp.code()})."))
      Result.success(
        LiveStreamSession(
          id = dto.id,
          host = host,
          title = dto.title,
          category = dto.category ?: category,
          isLive = true,
          viewerCount = 1,
          startedAtIso = dto.startedAt ?: TimestampUtils.nowIsoUtc()
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateLiveStreamState(
    streamId: String,
    isLive: Boolean? = null,
    viewerCount: Int? = null,
    latestFrameBase64: String? = null,
    endedAt: String? = null,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank() || streamId.isBlank()) return@withContext
    try {
      SupabaseNetworkClient.restApi.patchLiveStream(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$streamId",
        patch = SupabaseLiveStreamPatchDto(
          isLive = isLive,
          viewerCount = viewerCount,
          latestFrameBase64 = latestFrameBase64,
          lastHeartbeatAt = TimestampUtils.nowIsoUtc(),
          endedAt = endedAt
        )
      )
    } catch (_: Exception) {}
  }

  suspend fun fetchLiveStreamComments(
    streamId: String,
    authHeader: String?
  ): List<LiveStreamComment> = withContext(Dispatchers.IO) {
    try {
      val resp = SupabaseNetworkClient.restApi.getLiveStreamComments(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        streamIdFilter = "eq.$streamId"
      )
      resp.body().orEmpty().map { dto ->
        LiveStreamComment(
          id = dto.id ?: "lsc_${System.currentTimeMillis()}",
          streamId = dto.streamId,
          authorId = dto.authorId,
          authorName = dto.authorName?.takeIf { it.isNotBlank() } ?: "VYBE Member",
          content = dto.content,
          timestamp = TimestampUtils.formatRelative(dto.createdAt, "Just now"),
          createdAtIso = dto.createdAt
        )
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  suspend fun sendLiveStreamComment(
    streamId: String,
    authorId: String,
    authorName: String,
    content: String,
    authHeader: String?
  ): LiveStreamComment? = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext null
    try {
      val resp = SupabaseNetworkClient.restApi.insertLiveStreamComment(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        comment = SupabaseLiveStreamCommentInsertDto(
          streamId = streamId,
          authorId = authorId,
          authorName = authorName,
          content = content.trim()
        )
      )
      val dto = resp.body()?.firstOrNull() ?: return@withContext null
      LiveStreamComment(
        id = dto.id ?: "lsc_${System.currentTimeMillis()}",
        streamId = dto.streamId,
        authorId = dto.authorId,
        authorName = dto.authorName ?: authorName,
        content = dto.content,
        timestamp = "Just now",
        createdAtIso = dto.createdAt ?: TimestampUtils.nowIsoUtc()
      )
    } catch (_: Exception) {
      null
    }
  }

  // =========================================================================
  // 2. WHAT NEXT POLLS (`audience_pools`)
  // =========================================================================

  suspend fun fetchAudiencePools(
    myUserId: String,
    usersById: Map<String, User>,
    authHeader: String?
  ): List<SocialPrediction>? = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getAudiencePools(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getAudiencePools(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*,audience_pool_options(*)"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) return@withContext null

      val myVotes: Map<String, Int> = if (myUserId.isNotBlank() && !authHeader.isNullOrBlank()) {
        try {
          SupabaseNetworkClient.restApi.getMyAudiencePoolVotes(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            userIdFilter = "eq.$myUserId"
          ).body().orEmpty().associate { it.poolId to it.optionId }
        } catch (_: Exception) { emptyMap() }
      } else emptyMap()

      resp.body()!!.map { dto ->
        val rawOpts = dto.options.orEmpty().sortedBy { it.id ?: 0 }
        val sumVotes = maxOf(dto.totalVotes, rawOpts.sumOf { it.votesCount })
        val mappedOpts = rawOpts.mapIndexed { idx, o ->
          val oid = o.id ?: (idx + 1)
          val pct = if (sumVotes > 0) ((o.votesCount.toFloat() / sumVotes) * 100f).toInt() else 0
          PredictionOption(id = oid, text = o.optionText, votesCount = o.votesCount, percentage = pct)
        }
        val creator = mapProfileOrFallback(dto.profiles, dto.creatorId, usersById)
        val closesMs = TimestampUtils.parseIsoMillis(dto.closesAt)
        val nowMs = System.currentTimeMillis()
        val isClosed = closesMs != null && closesMs <= nowMs
        val remHours = if (closesMs != null && closesMs > nowMs) ((closesMs - nowMs) / 3600_000L).toInt().coerceAtLeast(1) else 0
        val closingLabel = when {
          isClosed -> "Closed"
          remHours <= 24 -> "Closes in ${remHours}h"
          else -> "Closes in ${remHours / 24}d"
        }
        SocialPrediction(
          id = dto.id,
          title = dto.title,
          category = dto.category ?: "Community",
          closingTimeText = closingLabel,
          durationHours = remHours.coerceAtLeast(1),
          totalVotes = sumVotes,
          options = mappedOpts,
          userSelectedOptionId = myVotes[dto.id],
          isResolved = isClosed,
          authorId = dto.creatorId,
          authorUsername = creator.username
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "fetchAudiencePools error: ${e.message}")
      null
    }
  }

  suspend fun createAudiencePool(
    creator: User,
    title: String,
    category: String,
    durationHours: Int,
    optionTexts: List<String>,
    authHeader: String?
  ): SocialPrediction? = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext null
    try {
      val closesIso = TimestampUtils.formatIsoUtc(System.currentTimeMillis() + durationHours.coerceAtLeast(1) * 3_600_000L)
      val poolResp = SupabaseNetworkClient.restApi.insertAudiencePool(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        pool = SupabaseAudiencePoolInsertDto(
          creatorId = creator.id,
          title = title.trim(),
          category = category.trim().ifBlank { "Community" },
          closesAt = closesIso
        )
      )
      val createdPool = poolResp.body()?.firstOrNull() ?: return@withContext null
      val optResp = SupabaseNetworkClient.restApi.insertAudiencePoolOptions(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        options = optionTexts.filter { it.isNotBlank() }.map {
          SupabaseAudiencePoolOptionInsertDto(poolId = createdPool.id, optionText = it.trim())
        }
      )
      val savedOpts = optResp.body().orEmpty().mapIndexed { idx, o ->
        PredictionOption(id = o.id ?: (idx + 1), text = o.optionText, votesCount = 0, percentage = 0)
      }
      SocialPrediction(
        id = createdPool.id,
        title = createdPool.title,
        category = createdPool.category ?: category,
        closingTimeText = if (durationHours <= 24) "Closes in ${durationHours}h" else "Closes in ${durationHours / 24}d",
        durationHours = durationHours,
        totalVotes = 0,
        options = savedOpts,
        authorId = creator.id,
        authorUsername = creator.username
      )
    } catch (_: Exception) {
      null
    }
  }

  suspend fun voteAudiencePool(poolId: String, userId: String, optionId: Int, authHeader: String?) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext
    try {
      SupabaseNetworkClient.restApi.upsertAudiencePoolVote(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        vote = SupabaseAudiencePoolVoteDto(poolId = poolId, userId = userId, optionId = optionId)
      )
    } catch (_: Exception) {}
  }

  suspend fun deleteAudiencePool(poolId: String, authHeader: String?) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext
    try {
      SupabaseNetworkClient.restApi.deleteAudiencePool(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$poolId"
      )
    } catch (_: Exception) {}
  }

  // =========================================================================
  // 3. DOUBLE TAKE (`double_takes` & `double_take_answers`)
  // =========================================================================

  suspend fun fetchDoubleTakes(
    myUserId: String,
    usersById: Map<String, User>,
    authHeader: String?
  ): List<DoubleTakePrompt>? = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getDoubleTakes(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getDoubleTakes(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) return@withContext null

      resp.body()!!.map { dto ->
        val amCreator = dto.creatorId == myUserId
        val peerId = if (amCreator) (dto.partnerId ?: dto.creatorId) else dto.creatorId
        val peerUser = usersById[peerId] ?: User(id = peerId, username = "vyber", displayName = "VYBE Community")
        val pubAnswers = dto.publicAnswers.orEmpty().map { a ->
          PublicAnswer(
            id = a.id ?: "pa_${a.userId}",
            user = mapProfileOrFallback(a.profiles, a.userId, usersById),
            answer = a.answerText,
            timestamp = TimestampUtils.formatRelative(a.createdAt, "Recently"),
            isConnected = a.isConnected
          )
        }
        val myPubAns = pubAnswers.firstOrNull { it.user.id == myUserId }
        val myAns = when {
          myPubAns != null -> myPubAns.answer
          amCreator -> dto.creatorAnswer.orEmpty()
          dto.partnerId == myUserId -> dto.partnerAnswer.orEmpty()
          else -> ""
        }
        val peerAns = if (amCreator) dto.partnerAnswer.orEmpty() else dto.creatorAnswer.orEmpty()
        val mySubmitted = myAns.isNotBlank() || (amCreator && dto.isCreatorSubmitted) || (dto.partnerId == myUserId && dto.isPartnerSubmitted)
        val peerSubmitted = if (amCreator) dto.isPartnerSubmitted else dto.isCreatorSubmitted

        DoubleTakePrompt(
          id = dto.id,
          promptQuestion = dto.promptQuestion,
          category = dto.category ?: "Perspective",
          optionA = dto.optionA ?: "Option A",
          optionB = dto.optionB ?: "Option B",
          creatorId = dto.creatorId,
          myAnswer = myAns,
          peerUser = peerUser,
          peerAnswer = peerAns,
          isMyAnswerSubmitted = mySubmitted,
          isPeerAnswerSubmitted = peerSubmitted,
          isRevealed = dto.isRevealed || (mySubmitted && peerSubmitted),
          isCreatorAccepted = dto.isCreatorAccepted,
          isChatUnlocked = dto.isChatUnlocked,
          isPublic = dto.isPublic ?: (dto.partnerId == null),
          publicAnswers = pubAnswers
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun createDoubleTake(
    creatorId: String,
    partnerId: String?,
    question: String,
    category: String,
    optionA: String,
    optionB: String,
    isPublic: Boolean,
    authHeader: String?
  ): String? = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext null
    try {
      var resp = SupabaseNetworkClient.restApi.insertDoubleTake(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        body = SupabaseDoubleTakeInsertDto(
          creatorId = creatorId,
          partnerId = if (isPublic) null else partnerId,
          promptQuestion = question.trim(),
          category = category,
          optionA = optionA,
          optionB = optionB,
          isPublic = isPublic
        )
      )
      if (!resp.isSuccessful && !partnerId.isNullOrBlank()) {
        resp = SupabaseNetworkClient.restApi.insertDoubleTakeLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          body = SupabaseDoubleTakeLegacyInsertDto(
            creatorId = creatorId,
            partnerId = partnerId,
            promptQuestion = question.trim(),
            category = category
          )
        )
      }
      resp.body()?.firstOrNull()?.id
    } catch (_: Exception) {
      null
    }
  }

  suspend fun submitDoubleTakeAnswer(
    prompt: DoubleTakePrompt,
    myUserId: String,
    answer: String,
    isPublicAnswer: Boolean,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank() || prompt.id.startsWith("dt_")) return@withContext
    try {
      if (isPublicAnswer || prompt.isPublic) {
        SupabaseNetworkClient.restApi.upsertDoubleTakeAnswer(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          answer = SupabaseDoubleTakeAnswerInsertDto(promptId = prompt.id, userId = myUserId, answerText = answer)
        )
      } else {
        val amCreator = prompt.creatorId == myUserId
        val patch = if (amCreator) {
          SupabaseDoubleTakePatchDto(
            creatorAnswer = answer,
            isCreatorSubmitted = true,
            isRevealed = prompt.isPeerAnswerSubmitted
          )
        } else {
          SupabaseDoubleTakePatchDto(
            partnerAnswer = answer,
            isPartnerSubmitted = true,
            isRevealed = true
          )
        }
        SupabaseNetworkClient.restApi.patchDoubleTake(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          idFilter = "eq.${prompt.id}",
          patch = patch
        )
      }
    } catch (_: Exception) {}
  }

  // =========================================================================
  // 4. TIME CAPSULES, CROSSED PATHS & VERIFIED SHOPS
  // =========================================================================

  suspend fun fetchTimeCapsules(
    usersById: Map<String, User>,
    authHeader: String?
  ): List<TimeCapsule>? = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getTimeCapsules(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getTimeCapsules(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) return@withContext null
      val nowMs = System.currentTimeMillis()
      resp.body()!!.map { dto ->
        val sender = mapProfileOrFallback(dto.profiles, dto.creatorId, usersById)
        val unlockMs = TimestampUtils.parseIsoMillis("${dto.unlockDate}T00:00:00Z") ?: (nowMs + 86_400_000L * 7)
        TimeCapsule(
          id = dto.id,
          title = dto.title,
          sender = sender,
          recipientSummary = dto.recipientSummary,
          sealedMessage = dto.sealedMessage,
          mediaAttachmentUrl = dto.mediaUrl,
          unlockDateFormatted = dto.unlockDate,
          unlockTimestampMs = unlockMs,
          isUnlocked = dto.isUnlocked || nowMs >= unlockMs,
          createdAtFormatted = TimestampUtils.formatRelative(dto.createdAt, "Recently")
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun createTimeCapsule(
    creatorId: String,
    title: String,
    recipient: String,
    message: String,
    unlockDateRaw: String,
    authHeader: String?
  ): String? = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext null
    try {
      val isoDate = if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(unlockDateRaw.trim())) {
        unlockDateRaw.trim()
      } else {
        val future = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        future.format(java.util.Date(System.currentTimeMillis() + 30L * 86_400_000L))
      }
      val resp = SupabaseNetworkClient.restApi.insertTimeCapsule(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        capsule = SupabaseTimeCapsuleInsertDto(
          creatorId = creatorId,
          title = title.trim(),
          recipientSummary = recipient.trim().ifBlank { "Community Vault" },
          sealedMessage = message.trim(),
          unlockDate = isoDate
        )
      )
      resp.body()?.firstOrNull()?.id
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchCrossedPaths(
    myUserId: String,
    usersById: Map<String, User>,
    authHeader: String?
  ): List<CrossedPathEncounter>? = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getCrossedPaths(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getCrossedPaths(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) return@withContext null

      val sentSet: Set<String> = if (myUserId.isNotBlank() && !authHeader.isNullOrBlank()) {
        try {
          SupabaseNetworkClient.restApi.getMyCrossedPathRequests(
            apiKey = SupabaseConfig.supabaseAnonKey,
            authHeader = authHeader,
            senderIdFilter = "eq.$myUserId"
          ).body().orEmpty().map { it.encounterId }.toSet()
        } catch (_: Exception) { emptySet() }
      } else emptySet()

      val nowMs = System.currentTimeMillis()
      resp.body()!!.mapNotNull { dto ->
        val expMs = TimestampUtils.parseIsoMillis(dto.expiresAt)
        if (expMs != null && expMs <= nowMs) return@mapNotNull null
        val remHours = if (expMs != null) ((expMs - nowMs) / 3_600_000L).toInt().coerceIn(1, 72) else 72
        val type = try { CrossedPathType.valueOf(dto.encounterType ?: "MISSED_CONNECTION") } catch (_: Exception) { CrossedPathType.MISSED_CONNECTION }
        val broad = dto.broadLocation?.takeIf { it.isNotBlank() } ?: dto.neighborhoodOrVenue.orEmpty()
        CrossedPathEncounter(
          id = dto.id,
          type = type,
          title = dto.title?.takeIf { it.isNotBlank() } ?: "Noticed near ${broad.ifBlank { "Community" }}",
          description = dto.description,
          broadLocation = broad.ifBlank { "Community Area" },
          venueOrEventName = dto.venueName ?: dto.neighborhoodOrVenue,
          encounterDate = TimestampUtils.formatRelative(dto.createdAt, dto.timeWindow ?: "Recently"),
          poster = mapProfileOrFallback(dto.profiles, dto.creatorId, usersById),
          isAnonymous = true,
          expiresHoursRemaining = remHours,
          hasSentRequest = sentSet.contains(dto.id),
          photoUrl = dto.photoUrl,
          mapQueryLocation = (dto.venueName ?: broad).ifBlank { "India" }
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun createCrossedPath(
    creatorId: String,
    type: CrossedPathType,
    title: String,
    venue: String,
    broadLocation: String,
    description: String,
    photoUrl: String?,
    authHeader: String?
  ): String? = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext null
    try {
      val area = venue.ifBlank { broadLocation.ifBlank { "Community Area" } }
      var resp = SupabaseNetworkClient.restApi.insertCrossedPath(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        encounter = SupabaseCrossedPathInsertDto(
          creatorId = creatorId,
          neighborhoodOrVenue = area,
          description = description,
          encounterType = type.name,
          title = title,
          broadLocation = broadLocation,
          venueName = venue.takeIf { it.isNotBlank() },
          photoUrl = photoUrl
        )
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.insertCrossedPathLegacy(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          encounter = SupabaseCrossedPathLegacyInsertDto(
            creatorId = creatorId,
            neighborhoodOrVenue = area,
            description = description,
            photoUrl = photoUrl
          )
        )
      }
      resp.body()?.firstOrNull()?.id
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchBusinessShops(
    usersById: Map<String, User>,
    authHeader: String?
  ): List<VybeShop>? = withContext(Dispatchers.IO) {
    try {
      var resp = SupabaseNetworkClient.restApi.getBusinessProfiles(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader
      )
      if (!resp.isSuccessful) {
        resp = SupabaseNetworkClient.restApi.getBusinessProfiles(
          apiKey = SupabaseConfig.supabaseAnonKey,
          authHeader = authHeader,
          select = "*"
        )
      }
      if (!resp.isSuccessful || resp.body() == null) return@withContext null
      resp.body()!!.map { dto ->
        val mappedReviews = dto.reviews.orEmpty().map { r ->
          ShopReview(
            id = r.id ?: "sr_${System.currentTimeMillis()}",
            author = mapProfileOrFallback(r.profiles, r.authorId, usersById),
            ratingStars = r.rating,
            reviewText = r.reviewText,
            timestamp = TimestampUtils.formatRelative(r.createdAt, "Recently")
          )
        }
        val avgRating = if (mappedReviews.isNotEmpty()) mappedReviews.map { it.ratingStars }.average().toFloat() else 5.0f
        val status = try { BusinessVerificationStatus.valueOf(dto.verificationStatus ?: "PENDING") } catch (_: Exception) { BusinessVerificationStatus.PENDING }
        VybeShop(
          id = dto.id,
          businessName = dto.businessName,
          category = dto.category,
          description = dto.description.orEmpty(),
          logoUrl = dto.logoUrl.orEmpty(),
          coverUrl = dto.coverImageUrl.orEmpty(),
          rating = avgRating,
          reviewsCount = mappedReviews.size,
          isVerifiedBusiness = status == BusinessVerificationStatus.APPROVED,
          locationAddress = dto.fullAddress ?: dto.city.orEmpty(),
          reviews = mappedReviews,
          followersCount = 0,
          ownerId = dto.ownerId,
          verificationStatus = status,
          isShopActive = true
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun deleteDoubleTake(promptId: String, authHeader: String?) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank() || promptId.startsWith("dt_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.deleteDoubleTake(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$promptId"
      )
    } catch (_: Exception) {}
  }

  suspend fun unlockTimeCapsule(capsuleId: String, authHeader: String?) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank() || capsuleId.startsWith("tc_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.patchTimeCapsule(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        idFilter = "eq.$capsuleId",
        patch = mapOf("is_unlocked" to true)
      )
    } catch (_: Exception) {}
  }

  suspend fun sendCrossedPathRequest(encounterId: String, senderId: String, authHeader: String?) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank() || encounterId.startsWith("cp_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.insertCrossedPathRequest(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        request = SupabaseCrossedPathRequestDto(encounterId = encounterId, senderId = senderId)
      )
    } catch (_: Exception) {}
  }

  suspend fun createBusinessShop(
    ownerId: String,
    businessName: String,
    category: String,
    gstNumber: String,
    description: String,
    city: String,
    fullAddress: String,
    authHeader: String?
  ): String? = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank()) return@withContext null
    try {
      val resp = SupabaseNetworkClient.restApi.insertBusinessProfile(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        profile = SupabaseBusinessProfileInsertDto(
          ownerId = ownerId,
          businessName = businessName.trim(),
          category = category.trim().ifBlank { "Retail" },
          gstNumber = gstNumber.trim(),
          description = description.trim(),
          city = city.trim().ifBlank { "India" },
          fullAddress = fullAddress.trim()
        )
      )
      resp.body()?.firstOrNull()?.id
    } catch (_: Exception) {
      null
    }
  }

  suspend fun addBusinessReview(
    shopId: String,
    authorId: String,
    rating: Int,
    reviewText: String,
    authHeader: String?
  ) = withContext(Dispatchers.IO) {
    if (authHeader.isNullOrBlank() || shopId.startsWith("shop_") || shopId.startsWith("ba_")) return@withContext
    try {
      SupabaseNetworkClient.restApi.insertBusinessReview(
        apiKey = SupabaseConfig.supabaseAnonKey,
        authHeader = authHeader,
        review = SupabaseBusinessReviewInsertDto(
          businessId = shopId,
          authorId = authorId,
          rating = rating.coerceIn(1, 5),
          reviewText = reviewText.trim()
        )
      )
    } catch (_: Exception) {}
  }
}

