package com.pixeltoonz.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseClient(private val baseUrl: String, private val anonKey: String) {
    data class ApiResult(val code: Int, val body: String) {
        val json: JSONObject get() = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
        val ok: Boolean get() = code in 200..299
        fun message(): String = json.optString("msg").ifBlank {
            json.optString("message").ifBlank { json.optString("error_description").ifBlank { json.optString("error").ifBlank { body.take(240) } } }
        }
    }

    val configured: Boolean get() = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    fun request(path: String, method: String = "GET", body: JSONObject? = null, token: String? = null, prefer: String? = null): ApiResult {
        if (!configured) return ApiResult(0, "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY in GitHub Actions secrets.")
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", anonKey)
        connection.setRequestProperty("Authorization", "Bearer ${token?.takeIf { it.isNotBlank() } ?: anonKey}")
        connection.setRequestProperty("Accept", "application/json")
        if (prefer != null) connection.setRequestProperty("Prefer", prefer)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            ApiResult(code, text)
        } finally { connection.disconnect() }
    }

    fun refresh(refreshToken: String) = request("/auth/v1/token?grant_type=refresh_token", "POST",\n        JSONObject().put("refresh_token", refreshToken))\n    fun signIn(email: String, password: String) = request("/auth/v1/token?grant_type=password", "POST",
        JSONObject().put("email", email).put("password", password))
    fun signUp(name: String, email: String, password: String) = request("/auth/v1/signup", "POST",
        JSONObject().put("email", email).put("password", password).put("data", JSONObject().put("full_name", name)))
    fun recover(email: String) = request("/auth/v1/recover", "POST", JSONObject().put("email", email))
    fun courses(token: String) = request("/rest/v1/courses?select=id,title,description,thumbnail_url,level,is_free,price,published&published=eq.true&order=created_at.desc", token = token)
    fun lessons(courseId: String, token: String) = request("/rest/v1/lessons?select=id,course_id,title,description,video_url,duration_minutes,sort_order&course_id=eq.${enc(courseId)}&order=sort_order.asc", token = token)
    fun announcements(token: String) = request("/rest/v1/announcements?select=id,title,body,created_at&published=eq.true&order=created_at.desc&limit=10", token = token)
    fun profile(token: String) = request("/auth/v1/user", token = token)
    fun enroll(courseId: String, token: String) = request("/rest/v1/enrollments", "POST",
        JSONObject().put("course_id", courseId), token, "resolution=merge-duplicates,return=minimal")
    fun markComplete(lessonId: String, token: String) = request("/rest/v1/lesson_progress", "POST",
        JSONObject().put("lesson_id", lessonId).put("completed", true), token, "resolution=merge-duplicates,return=minimal")
    fun myEnrollments(token: String) = request("/rest/v1/enrollments?select=course_id,created_at", token = token)
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    companion object {
        fun array(body: String): JSONArray = runCatching { JSONArray(body) }.getOrElse { JSONArray() }
    }
}
