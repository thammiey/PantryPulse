package com.pantrypulse.app.data.remote

import com.pantrypulse.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal HTTP helper; the free APIs used here need no keys. */
object Http {
    private val userAgent = "PantryPulse-Android/${BuildConfig.VERSION_NAME}"

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun get(url: String): String = withContext(Dispatchers.IO) { request(url, "GET", null) }

    suspend fun postForm(url: String, form: Map<String, String>): String = withContext(Dispatchers.IO) {
        request(url, "POST", form.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" })
    }

    suspend fun postJson(url: String, json: String): String = withContext(Dispatchers.IO) {
        request(url, "POST", json, "application/json; charset=UTF-8")
    }

    private fun request(url: String, method: String, body: String?, contentType: String = "application/x-www-form-urlencoded; charset=UTF-8"): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Server returned HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
