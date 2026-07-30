package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ApiDomain(
    @Json(name = "domain") val domain: String
)

@JsonClass(generateAdapter = true)
data class ApiMessage(
    @Json(name = "id") val id: String,
    @Json(name = "from") val from: String,
    @Json(name = "subject") val subject: String,
    @Json(name = "date") val date: String,
    @Json(name = "body") val body: String? = null,
    @Json(name = "textBody") val textBody: String? = null,
    @Json(name = "htmlBody") val htmlBody: String? = null
)

interface KmtMailApi {

    @GET("domains")
    suspend fun getDomains(): List<String>

    @GET("messages/{email}")
    suspend fun getMessages(
        @Path("email") email: String
    ): List<ApiMessage>
}
