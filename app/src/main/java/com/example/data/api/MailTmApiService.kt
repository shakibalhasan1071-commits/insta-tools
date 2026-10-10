package com.example.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Response

data class DomainResponse(val id: String, val domain: String)
data class AccountRequest(val address: String, val password: String)
data class AccountResponse(val id: String, val address: String)

interface MailTmApiService {
    @GET("/domains")
    suspend fun getDomains(): Response<List<DomainResponse>>

    @POST("/accounts")
    suspend fun createAccount(@Body request: AccountRequest): Response<AccountResponse>
}
