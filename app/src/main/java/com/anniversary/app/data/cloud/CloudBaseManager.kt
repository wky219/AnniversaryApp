package com.anniversary.app.data.cloud

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Manages CloudBase HTTP API client.
 * Uses OkHttp to call CloudBase REST API directly.
 */
object CloudBaseManager {

    private const val ENV_ID = "wky-1-d7g2yyq8jf31226d6"
    private const val BASE_URL = "https://$ENV_ID.api.tcloudbasegateway.com"

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var client: OkHttpClient? = null

    private var accessToken: String? = null

    fun getClient(): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
                .also { client = it }
        }
    }

    fun updateAccessToken(token: String?) {
        accessToken = token
    }

    fun getAccessToken(): String? = accessToken

    /**
     * Send a POST request to CloudBase Auth API.
     * Returns parsed response as Map, or null on failure.
     */
    fun post(path: String, body: Map<String, Any>): Map<String, Any>? {
        return try {
            val jsonBody = gson.toJson(body)
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    accessToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .addHeader("Content-Type", "application/json")
                .build()

            val response = getClient().newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                gson.fromJson<Map<String, Any>>(responseBody, type)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
