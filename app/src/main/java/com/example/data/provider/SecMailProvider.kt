package com.example.data.provider

import com.example.data.local.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SecMailProvider : TempMailProvider {
    override val providerName: String = "1SecMail API"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun generateAddress(preferredDomain: String?): String = withContext(Dispatchers.IO) {
        val activeDomains = fetchActiveDomains()
        val domainToUse = if (preferredDomain != null && activeDomains.contains(preferredDomain)) {
            preferredDomain
        } else if (activeDomains.isNotEmpty()) {
            activeDomains.random()
        } else {
            "1secmail.com"
        }

        val randomChars = (1..7).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        return@withContext "kmt_$randomChars@$domainToUse"
    }

    private fun fetchActiveDomains(): List<String> {
        return try {
            val request = Request.Builder()
                .url("https://www.1secmail.com/api/v1/?action=getDomainList")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val jsonArray = JSONArray(bodyStr)
                val domains = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    domains.add(jsonArray.getString(i))
                }
                domains
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchMessages(emailAddress: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        val parts = emailAddress.split("@")
        if (parts.size < 2) return@withContext emptyList()
        val login = parts[0]
        val domain = parts[1]

        val messagesList = mutableListOf<MessageEntity>()

        try {
            val listUrl = "https://www.1secmail.com/api/v1/?action=getMessages&login=$login&domain=$domain"
            val request = Request.Builder().url(listUrl).get().build()

            val rawMessagesJson = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string() ?: "[]"
            }

            val jsonArray = JSONArray(rawMessagesJson)
            for (i in 0 until jsonArray.length()) {
                val msgMeta = jsonArray.getJSONObject(i)
                val msgId = msgMeta.optInt("id", -1)
                if (msgId == -1) continue

                val detailUrl = "https://www.1secmail.com/api/v1/?action=readMessage&login=$login&domain=$domain&id=$msgId"
                val detailReq = Request.Builder().url(detailUrl).get().build()

                try {
                    client.newCall(detailReq).execute().use { detailResp ->
                        if (detailResp.isSuccessful) {
                            val detailBody = detailResp.body?.string()
                            if (!detailBody.isNullOrBlank()) {
                                val detailJson = JSONObject(detailBody)
                                val sender = detailJson.optString("from", "Unknown Sender")
                                val subject = detailJson.optString("subject", "No Subject")
                                val dateStr = detailJson.optString("date", "")
                                val htmlBody = detailJson.optString("htmlBody", detailJson.optString("body", ""))
                                val textBody = detailJson.optString("textBody", "").ifBlank {
                                    detailJson.optString("body", "").replace(Regex("<[^>]*>"), "")
                                }

                                val parsedTime = try {
                                    if (dateStr.isNotBlank()) dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                                    else System.currentTimeMillis()
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }

                                val snippet = textBody.take(120).ifBlank { subject }

                                messagesList.add(
                                    MessageEntity(
                                        id = "1secmail_$msgId",
                                        emailAddress = emailAddress,
                                        senderName = sender.substringBefore("<").trim().ifBlank { sender },
                                        senderEmail = if (sender.contains("<")) sender.substringAfter("<").substringBefore(">").trim() else sender,
                                        subject = subject,
                                        bodyText = textBody,
                                        bodyHtml = htmlBody,
                                        snippet = snippet,
                                        timestamp = parsedTime,
                                        isRead = false
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continue fetching remaining messages
                }
            }
        } catch (e: Exception) {
            // Failure propagates empty list to trigger failover if needed
        }

        return@withContext messagesList
    }
}
