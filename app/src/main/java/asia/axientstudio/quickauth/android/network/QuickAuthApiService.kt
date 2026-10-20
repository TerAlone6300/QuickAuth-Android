package asia.axientstudio.quickauth.android.network

import retrofit2.http.Body
import retrofit2.http.POST

interface QuickAuthApiService {
    @POST("check")
    suspend fun checkUser(@Body request: CheckUserRequest): CheckUserResponse

    @POST("auth")
    suspend fun auth(@Body request: AuthRequest): AuthResponse

    @POST("refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse

    @POST("session")
    suspend fun getSession(@Body request: SessionRequest): SessionResponse

    @POST("sync")
    suspend fun sync(@Body request: SyncRequest): SyncResponse
}

data class CheckUserRequest(val user: String)
data class CheckUserResponse(val exists: Boolean = false)

data class AuthRequest(val user: String, val pass: String, val action: String, val env: String)
data class AuthResponse(
    val success: Boolean,
    val at: String? = null,
    val rt: String? = null,
    val exp: Long? = null,
    val message: String? = null
)

data class RefreshRequest(val rt: String?, val env: String? = null)
data class RefreshResponse(
    val success: Boolean,
    val at: String? = null,
    val rt: String? = null,
    val exp: Long? = null,
    val message: String? = null
)

data class SessionRequest(val at: String?)
data class SessionResponse(val success: Boolean, val st: String? = null, val message: String? = null)

// Values are nullable: a null value in a request deletes that entry on the server (used to purge old
// unencrypted entries). Gson must be built with serializeNulls() for that to be sent (see SyncManager).
data class SyncRequest(val st: String?, val data: Map<String, String?>?)
data class SyncResponse(val success: Boolean, val data: Map<String, String?>? = null, val message: String? = null)

/** Body of the server's error responses (HTTP 4xx/5xx), e.g. {"success": false, "message": "..."}. */
data class ApiError(val message: String? = null)
