package com.anniversary.app.data.cloud

import android.util.Log
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

    private const val TAG = "CloudBaseManager"
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
     * Send a POST request to CloudBase API.
     * Returns parsed response as Map if body exists, or empty map on success with no body (e.g. 201 Created).
     * Returns null on failure.
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

            if (response.isSuccessful) {
                if (!responseBody.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    gson.fromJson<Map<String, Any>>(responseBody, type) ?: emptyMap()
                } else {
                    // 201 Created with empty body is still a success
                    emptyMap()
                }
            } else {
                Log.e(TAG, "POST $path failed: code=${response.code}, body=$responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path exception: ${e.message}", e)
            null
        }
    }

    /**
     * Send a GET request to CloudBase API.
     * Returns parsed response as Map, or null on failure.
     */
    fun get(path: String): Map<String, Any>? {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .get()
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
                Log.e(TAG, "GET $path failed: code=${response.code}, body=$responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path exception: ${e.message}", e)
            null
        }
    }

    /**
     * Send a DELETE request to CloudBase API.
     * Returns true if successful.
     */
    fun delete(path: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .delete()
                .apply {
                    accessToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .addHeader("Content-Type", "application/json")
                .build()

            val response = getClient().newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "DELETE $path failed: code=${response.code}, body=${response.body?.string()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "DELETE $path exception: ${e.message}", e)
            false
        }
    }

    // ==================== Cloud Functions API ====================

    /**
     * Call a CloudBase cloud function.
     * Returns parsed response Map, or null on failure.
     */
    fun callFunction(functionName: String, params: Map<String, Any>): Map<String, Any>? {
        return post("/v1/functions/$functionName", params)
    }

    // ==================== Legacy Database REST API (requires PostgreSQL/MySQL) ====================

    /**
     * Insert a record into CloudBase MySQL table with username for multi-user isolation.
     */
    fun insertRecord(table: String, data: Map<String, Any>): Map<String, Any>? {
        return post("/v1/rdb/rest/$table", data)
    }

    /**
     * Query records from CloudBase MySQL table filtered by username.
     * The REST API returns a JSON array directly.
     */
    fun queryRecordsByUser(table: String, username: String): List<Map<String, Any>> {
        return try {
            val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/v1/rdb/rest/$table?username=eq.$encodedUsername")
                .get()
                .apply {
                    accessToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .addHeader("Content-Type", "application/json")
                .build()

            val response = getClient().newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                gson.fromJson<List<Map<String, Any>>>(responseBody, type) ?: emptyList()
            } else {
                Log.e(TAG, "GET /v1/rdb/rest/$table by user failed: code=${response.code}, body=$responseBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET /v1/rdb/rest/$table by user exception: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Delete all records for a specific user from CloudBase MySQL table.
     */
    fun deleteRecordsByUser(table: String, username: String): Boolean {
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        return delete("/v1/rdb/rest/$table?username=eq.$encodedUsername")
    }
}
