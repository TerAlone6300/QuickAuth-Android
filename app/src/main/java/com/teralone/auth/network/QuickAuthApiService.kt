package com.teralone.auth.network

import retrofit2.http.Body
import retrofit2.http.POST

interface QuickAuthApiService {
    @POST("sync")
    suspend fun sync(@Body data: Map<String, String>): SyncResponse

    @POST("auth")
    suspend fun auth(@Body request: AuthRequest): AuthResponse
}

data class SyncResponse(val success: Boolean, val data: Map<String, String>?, val message: String?)
data class AuthRequest(val user: String, val pass: String, val action: String)
data class AuthResponse(val success: Boolean, val at: String?, val rt: String?, val exp: Long?, val message: String?)
