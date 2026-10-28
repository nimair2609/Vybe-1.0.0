package com.example.data.music

import com.example.data.model.LicensedMusicTrack
import com.example.data.model.MusicCategory
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseNetworkClient
import com.example.data.remote.SupabaseRepository
import com.example.data.session.SessionManager
import com.squareup.moshi.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

enum class MusicCatalogStatus {
  OK,
  PROVIDER_NOT_CONFIGURED,
  LICENSE_APPROVAL_REQUIRED,
  UNAVAILABLE,
  NETWORK_ERROR;

  companion object {
    fun fromWire(raw: String?): MusicCatalogStatus {
      return when (raw?.trim()?.uppercase()) {
        "OK" -> OK
        "PROVIDER_NOT_CONFIGURED" -> PROVIDER_NOT_CONFIGURED
        "LICENSE_APPROVAL_REQUIRED" -> LICENSE_APPROVAL_REQUIRED
        "UNAVAILABLE" -> UNAVAILABLE
        "NETWORK_ERROR" -> NETWORK_ERROR
        else -> PROVIDER_NOT_CONFIGURED
      }
    }
  }
}

data class MusicCatalogWireDto(
  @param:Json(name = "status") val status: String? = null,
  @param:Json(name = "provider") val provider: String? = null,
  @param:Json(name = "licenseVerified") val licenseVerified: Boolean = false,
  @param:Json(name = "message") val message: String? = null,
  @param:Json(name = "availableCategories") val availableCategories: List<String>? = null,
  @param:Json(name = "tracks") val tracks: List<LicensedMusicTrack>? = null
)

data class MusicCatalogSearchQuery(
  val query: String = "",
  val category: MusicCategory = MusicCategory.TRENDING,
  val genre: String? = null,
  val mood: String? = null,
  val limit: Int = 25
)

data class MusicCatalogResult(
  val status: MusicCatalogStatus,
  val providerName: String,
  val licenseVerified: Boolean,
  val availableCategories: List<MusicCategory>,
  val tracks: List<LicensedMusicTrack>,
  val diagnosticMessage: String? = null
)

/**
 * Provider-Adapter interface for licensed music discovery and track resolution.
 * Implementations MUST communicate via secure server/Edge endpoints so private API keys
 * never exist in Android client code, and MUST NEVER scrape third-party streaming services
 * or fabricate fake tracks.
 */
interface MusicCatalogProvider {
  val adapterId: String
  suspend fun searchCatalog(searchQuery: MusicCatalogSearchQuery): MusicCatalogResult
  suspend fun getTrackById(trackId: String): LicensedMusicTrack?
}

/**
 * Default production adapter communicating with the VYBE Supabase Edge Function
 * (`/functions/v1/music-catalog`) with fallback to VYBE's server proxy (`/api/v1/music/catalog`).
 */
class SupabaseEdgeMusicProviderAdapter : MusicCatalogProvider {

  override val adapterId: String = "supabase_edge_licensed_proxy"

  private val wireAdapter by lazy {
    SupabaseNetworkClient.moshi.adapter(MusicCatalogWireDto::class.java)
  }

  override suspend fun searchCatalog(searchQuery: MusicCatalogSearchQuery): MusicCatalogResult =
    withContext(Dispatchers.IO) {
      executeCatalogCall(
        query = searchQuery.query.trim(),
        category = searchQuery.category.wireKey,
        genre = searchQuery.genre?.trim(),
        mood = searchQuery.mood?.trim() ?: if (searchQuery.category.isMoodBased) searchQuery.category.displayLabel else null,
        trackId = null,
        limit = searchQuery.limit
      )
    }

  override suspend fun getTrackById(trackId: String): LicensedMusicTrack? = withContext(Dispatchers.IO) {
    if (trackId.isBlank()) return@withContext null
    val res = executeCatalogCall(
      query = "",
      category = MusicCategory.TRENDING.wireKey,
      genre = null,
      mood = null,
      trackId = trackId.trim(),
      limit = 5
    )
    res.tracks.firstOrNull { it.id == trackId } ?: res.tracks.firstOrNull()
  }

  private suspend fun executeCatalogCall(
    query: String,
    category: String,
    genre: String?,
    mood: String?,
    trackId: String?,
    limit: Int
  ): MusicCatalogResult {
    if (!SupabaseConfig.isConfigured()) {
      return MusicCatalogResult(
        status = MusicCatalogStatus.PROVIDER_NOT_CONFIGURED,
        providerName = "unconfigured",
        licenseVerified = false,
        availableCategories = MusicCategory.entries,
        tracks = emptyList(),
        diagnosticMessage = "Supabase backend is not configured."
      )
    }

    val accessToken = try {
      SupabaseRepository.getInstance().resolveValidAccessToken(SessionManager.get()?.accessToken)
    } catch (_: Exception) {
      null
    }
    val authHeader = SupabaseConfig.buildAuthHeader(accessToken)

    val candidateEndpoints = listOf(
      "${SupabaseConfig.supabaseUrl.trimEnd('/')}/functions/v1/music-catalog",
      "${SupabaseConfig.vybeApiBaseUrl.trimEnd('/')}/api/v1/music/catalog"
    )

    var sawNetworkException: Exception? = null
    var sawEndpointMissing = false

    for (baseEndpoint in candidateEndpoints) {
      val parsedBase = baseEndpoint.toHttpUrlOrNull() ?: continue
      val urlBuilder = parsedBase.newBuilder()
        .addQueryParameter("category", category)
        .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
      if (query.isNotBlank()) urlBuilder.addQueryParameter("q", query)
      if (!genre.isNullOrBlank()) urlBuilder.addQueryParameter("genre", genre)
      if (!mood.isNullOrBlank()) urlBuilder.addQueryParameter("mood", mood)
      if (!trackId.isNullOrBlank()) urlBuilder.addQueryParameter("trackId", trackId)

      val reqBuilder = Request.Builder()
        .url(urlBuilder.build())
        .header("apikey", SupabaseConfig.supabaseAnonKey)
        .header("Accept", "application/json")
      if (!authHeader.isNullOrBlank()) {
        reqBuilder.header("Authorization", authHeader)
      }

      try {
        SupabaseNetworkClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
          val contentType = resp.header("Content-Type").orEmpty()
          val rawBody = resp.body?.string().orEmpty()

          // If Edge Function is not deployed (404) or Express fallback returned SPA HTML, try next endpoint
          if (resp.code == 404 || contentType.contains("text/html", ignoreCase = true)) {
            sawEndpointMissing = true
            return@use
          }

          if (!resp.isSuccessful || rawBody.isBlank()) {
            return MusicCatalogResult(
              status = MusicCatalogStatus.UNAVAILABLE,
              providerName = "server_proxy",
              licenseVerified = false,
              availableCategories = MusicCategory.entries,
              tracks = emptyList(),
              diagnosticMessage = "Music catalog service returned HTTP ${resp.code}."
            )
          }

          val dto = try {
            wireAdapter.fromJson(rawBody)
          } catch (_: Exception) {
            null
          } ?: run {
            sawEndpointMissing = true
            return@use
          }

          val status = MusicCatalogStatus.fromWire(dto.status)
          val categories = dto.availableCategories
            ?.mapNotNull { MusicCategory.fromWire(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: MusicCategory.entries

          // Safety guardrail: Never pass unlicensed tracks through to production playback
          val verifiedTracks = if (status == MusicCatalogStatus.OK && dto.licenseVerified) {
            dto.tracks.orEmpty().map { track ->
              track.copy(
                isSyncLicensed = track.isSyncLicensed && dto.licenseVerified,
                isAvailable = track.isAvailable && track.isSyncLicensed && !track.previewStreamUrl.isNullOrBlank()
              )
            }
          } else {
            emptyList()
          }

          return MusicCatalogResult(
            status = status,
            providerName = dto.provider?.ifBlank { null } ?: "licensed_proxy",
            licenseVerified = dto.licenseVerified,
            availableCategories = categories,
            tracks = verifiedTracks,
            diagnosticMessage = dto.message
          )
        }
      } catch (e: Exception) {
        sawNetworkException = e
      }
    }

    if (sawEndpointMissing && sawNetworkException == null) {
      return MusicCatalogResult(
        status = MusicCatalogStatus.PROVIDER_NOT_CONFIGURED,
        providerName = "unconfigured",
        licenseVerified = false,
        availableCategories = MusicCategory.entries,
        tracks = emptyList(),
        diagnosticMessage = "Deploy the `music-catalog` Supabase Edge Function and configure licensed music provider secrets (MUSIC_PROVIDER_API_KEY / JAMENDO_CLIENT_ID + MUSIC_LICENSE_APPROVED=true) to enable catalog streaming."
      )
    }

    return MusicCatalogResult(
      status = MusicCatalogStatus.NETWORK_ERROR,
      providerName = "offline",
      licenseVerified = false,
      availableCategories = MusicCategory.entries,
      tracks = emptyList(),
      diagnosticMessage = sawNetworkException?.message ?: "Unable to reach the licensed music catalog. Check your internet connection."
    )
  }
}

/**
 * Central repository mediating between UI/ViewModel and the swappable [MusicCatalogProvider] adapter.
 */
object MusicCatalogRepository {

  @Volatile
  private var activeProvider: MusicCatalogProvider = SupabaseEdgeMusicProviderAdapter()

  val currentProvider: MusicCatalogProvider
    get() = activeProvider

  fun setProviderAdapter(provider: MusicCatalogProvider) {
    activeProvider = provider
  }

  fun resetToDefaultProvider() {
    activeProvider = SupabaseEdgeMusicProviderAdapter()
  }

  suspend fun searchTracks(
    query: String = "",
    category: MusicCategory = MusicCategory.TRENDING,
    genre: String? = null,
    mood: String? = null
  ): MusicCatalogResult {
    return activeProvider.searchCatalog(
      MusicCatalogSearchQuery(
        query = query,
        category = category,
        genre = genre,
        mood = mood
      )
    )
  }

  suspend fun getTrackById(trackId: String): LicensedMusicTrack? {
    return activeProvider.getTrackById(trackId)
  }
}
