package com.example.data.provider

import com.example.data.local.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MailTmProvider : TempMailProvider {
    override val providerName: String = "Mail.tm API"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Store tokens per email address
    private val tokenMap = ConcurrentHashMap<String, String>()
    private val passwordMap = ConcurrentHashMap<String, String>()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun generateAddress(preferredDomain: String?): String = withContext(Dispatchers.IO) {
        val domains = fetchDomains()
        val domainToUse = if (preferredDomain != null && domains.contains(preferredDomain)) {
            preferredDomain
        } else if (domains.isNotEmpty()) {
            domains.first()
        } else {
            "mailtm.com"
        }

        val randomChars = (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val email = "kmt_$randomChars@$domainToUse"
        val password = "KmtPass_${(100000..999999).random()}"

        // Register account on Mail.tm
        val registered = registerAccount(email, password)
        if (registered) {
            val token = acquireToken(email, password)
            if (!token.isNullOrBlank()) {
                tokenMap[email] = token
                passwordMap[email] = password
            }
        }

        return@withContext email
    }

    private fun fetchDomains(): List<String> {
        return try {
            val request = Request.Builder()
                .url("https://api.mail.tm/domains")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val memberArray = json.optJSONArray("hydra:member") ?: return emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until memberArray.length()) {
                    val domObj = memberArray.getJSONObject(i)
                    if (domObj.optBoolean("isActive", true)) {
                        list.add(domObj.getString("domain"))
                    }
                }
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun registerAccount(email: String, pass: String): Boolean {
        return try {
            val jsonPayload = JSONObject().apply {
                put("address", email)
                put("password", pass)
            }.toString()

            val request = Request.Builder()
                .url("https://api.mail.tm/accounts")
                .post(jsonPayload.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { resp ->
                resp.isSuccessful || resp.code == 422 // 422 = already exists
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun acquireToken(email: String, pass: String): String? {
        return try {
            val jsonPayload = JSONObject().apply {
                put("address", email)
                put("password", pass)
            }.toString()

            val request = Request.Builder()
                .url("https://api.mail.tm/token")
                .post(jsonPayload.toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                json.optString("token", null)
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchMessages(emailAddress: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        var token = tokenMap[emailAddress]

        if (token.isNullOrBlank()) {
            val storedPass = passwordMap[emailAddress] ?: "KmtPass_123456"
            registerAccount(emailAddress, storedPass)
            token = acquireToken(emailAddress, storedPass)
            if (!token.isNullOrBlank()) {
                tokenMap[emailAddress] = token
            }
        }

        if (token.isNullOrBlank()) {
            return@withContext emptyList()
        }

        val messagesList = mutableListOf<MessageEntity>()

        try {
            val request = Request.Builder()
                .url("https://api.mail.tm/messages")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val bodyStr = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: "{\"hydra:member\":[]}"
            }

            val json = JSONObject(bodyStr)
            val memberArray = json.optJSONArray("hydra:member") ?: return@withContext emptyList()

            for (i in 0 until memberArray.length()) {
                val item = memberArray.getJSONObject(i)
                val msgId = item.optString("id", "")
                if (msgId.isBlank()) continue

                val fromObj = item.optJSONObject("from")
                val senderName = fromObj?.optString("name", "")?.ifBlank { fromObj.optString("address", "Unknown") } ?: "Unknown"
                val senderEmail = fromObj?.optString("address", "unknown@mail.tm") ?: "unknown@mail.tm"
                val subject = item.optString("subject", "No Subject")
                val intro = item.optString("intro", "")
                val createdAtStr = item.optString("createdAt", "")

                val timeMs = try {
                    if (createdAtStr.isNotBlank()) isoFormat.parse(createdAtStr)?.time ?: System.currentTimeMillis()
                    else System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                // Fetch detail
                val detailReq = Request.Builder()
                    .url("https://api.mail.tm/messages/$msgId")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                var fullText = intro
                var fullHtml: String? = null

                try {
                    client.newCall(detailReq).execute().use { detailResp ->
                        if (detailResp.isSuccessful) {
                            val detailBody = detailResp.body?.string()
                            if (!detailBody.isNullOrBlank()) {
                                val detailJson = JSONObject(detailBody)
                                fullText = detailJson.optString("text", intro)
                                val htmlArray = detailJson.optJSONArray("html")
                                if (htmlArray != null && htmlArray.length() > 0) {
                                    fullHtml = htmlArray.getString(0)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // fallback to intro
                }

                messagesList.add(
                    MessageEntity(
                        id = "mailtm_$msgId",
                        emailAddress = emailAddress,
                        senderName = senderName,
                        senderEmail = senderEmail,
                        subject = subject,
                        bodyText = fullText,
                        bodyHtml = fullHtml,
                        snippet = intro.ifBlank { fullText.take(120) },
                        timestamp = timeMs,
                        isRead = false
                    )
                )
            }
        } catch (e: Exception) {
            // Failure propagates
        }

        return@withContext messagesList
    }
}
