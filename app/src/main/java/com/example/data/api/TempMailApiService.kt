package com.example.data.api

import retrofit2.Response
import retrofit2.http.GET

data class TempMailResponse(
    val address: String,
    val token: String
)

interface TempMailApiService {
    @GET("/v2/new")
    suspend fun createEmail(): Response<TempMailResponse>
}
