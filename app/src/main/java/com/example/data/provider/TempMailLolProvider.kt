package com.example.data.provider

import com.example.data.local.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TempMailLolProvider : TempMailProvider {
    override val providerName: String = "Temp-Mail"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val secMail = SecMailProvider()

    override suspend fun getAvailableDomains(): List<String> = withContext(Dispatchers.IO) {
        secMail.getAvailableDomains()
    }

    override suspend fun generateAddress(preferredDomain: String?): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.tempmail.lol/v2/inbox/create")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        val address = json.optString("address", "")
                        if (address.isNotBlank()) {
                            return@withContext address
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to SecMail if tempmail.lol is unreachable
        }

        return@withContext secMail.generateAddress(preferredDomain)
    }

    override suspend fun fetchMessages(emailAddress: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        if (!emailAddress.contains("@")) return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url("https://api.tempmail.lol/v2/inbox?address=$emailAddress")
                .get()
                .build()

            val messagesList = mutableListOf<MessageEntity>()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        val emailsArray = json.optJSONArray("emails") ?: JSONArray()
                        for (i in 0 until emailsArray.length()) {
                            val emailObj = emailsArray.getJSONObject(i)
                            val sender = emailObj.optString("from", "Unknown")
                            val subject = emailObj.optString("subject", "No Subject")
                            val body = emailObj.optString("body", "")
                            val html = emailObj.optString("html", body)
                            val id = emailObj.optString("id", System.currentTimeMillis().toString())
                            val timestamp = emailObj.optLong("date", System.currentTimeMillis())

                            messagesList.add(
                                MessageEntity(
                                    id = "tempmail_$id",
                                    emailAddress = emailAddress,
                                    senderName = sender.substringBefore("<").trim().ifBlank { sender },
                                    senderEmail = if (sender.contains("<")) sender.substringAfter("<").substringBefore(">").trim() else sender,
                                    subject = subject,
                                    bodyText = body,
                                    bodyHtml = html,
                                    snippet = body.take(120).ifBlank { subject },
                                    timestamp = timestamp,
                                    isRead = false
                                )
                            )
                        }
                        return@withContext messagesList
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        return@withContext secMail.fetchMessages(emailAddress)
    }
}
