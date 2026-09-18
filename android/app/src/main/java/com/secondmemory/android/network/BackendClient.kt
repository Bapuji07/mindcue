package com.secondmemory.android.network

import com.secondmemory.android.data.AskResult
import com.secondmemory.android.data.AskSource
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.data.ResultJson
import com.secondmemory.android.data.ConversationDetail
import com.secondmemory.android.data.ConversationSession
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class BackendClient(baseUrl: String, private val token: String? = null) {
    private val base = URI(baseUrl.trimEnd('/') + "/")

    fun health(): String {
        val json = request("GET", "api/v1/health")
        if (json.optString("status") != "UP") throw BackendException("Backend is not ready")
        return "Connected to ${json.optString("transcriptionProvider")} transcription"
    }

    fun login(username: String, password: String): LoginResult {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val json = request("POST", "api/v1/auth/login", body)
        return LoginResult(json.getString("token"), json.getString("userId"), json.getString("username"))
    }

    fun register(username: String, password: String): LoginResult {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val json = request("POST", "api/v1/auth/register", body)
        return LoginResult(json.getString("token"), json.getString("userId"), json.getString("username"))
    }

    fun createSession(startedAt: String): String {
        val body = JSONObject().apply {
            put("title", "Phone memory ${OffsetDateTime.now().toLocalDateTime()}")
            put("startedAt", startedAt)
            put("timezone", ZoneId.systemDefault().id)
            put("source", "ANDROID")
        }
        return request("POST", "api/v1/memory/sessions", body).getString("id")
    }

    fun upload(sessionId: String, audio: File) {
        val boundary = "SecondMemory-${UUID.randomUUID()}"
        val connection = connection("api/v1/memory/sessions/$sessionId/audio").apply {
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        connection.outputStream.buffered().use { output ->
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"file\"; filename=\"memory.m4a\"\r\n".toByteArray())
            output.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
            audio.inputStream().buffered().use { it.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        readResponse(connection)
    }

    /** Starts transcription + extraction in the background on the server and returns immediately. */
    fun startProcessing(sessionId: String) {
        request("POST", "api/v1/memory/sessions/$sessionId/process", JSONObject())
    }

    fun ask(question: String): AskResult {
        val body = JSONObject().apply { put("question", question); put("topK", 5) }
        val json = request("POST", "api/v1/memory/ask", body)
        val sourceJson = json.optJSONArray("sources")
        val sources = if (sourceJson == null) emptyList() else List(sourceJson.length()) { index ->
            val source = sourceJson.getJSONObject(index)
            val evidenceJson = source.optJSONArray("evidence")
            AskSource(
                source.optString("title"), source.optString("content"),
                if (evidenceJson == null) emptyList() else List(evidenceJson.length()) {
                    evidenceJson.getJSONObject(it).optString("text")
                }
            )
        }
        return AskResult(json.optString("answer"), sources)
    }

    fun listSessions(): List<ConversationSession> {
        val array = request("GET", "api/v1/memory/sessions?limit=100").getJSONArray("sessions")
        return List(array.length()) { sessionFromJson(array.getJSONObject(it)) }
    }

    fun sessionDetail(sessionId: String): ConversationDetail {
        val json = request("GET", "api/v1/memory/sessions/$sessionId/detail")
        val memoriesJson = json.getJSONArray("memories")
        val chunks = json.getJSONArray("transcriptChunks")
        val transcript = buildString {
            repeat(chunks.length()) { index ->
                val chunk = chunks.getJSONObject(index)
                if (isNotEmpty()) append("\n\n")
                chunk.optString("speakerLabel").takeIf { it.isNotBlank() }?.let { append(it).append(": ") }
                append(chunk.optString("text"))
            }
        }
        return ConversationDetail(
            session = sessionFromJson(json.getJSONObject("session")),
            memories = List(memoriesJson.length()) { ResultJson.memoryFromJson(memoriesJson.getJSONObject(it)) },
            transcript = transcript
        )
    }

    fun renameSession(sessionId: String, title: String): ConversationSession {
        val body = JSONObject().put("title", title)
        return sessionFromJson(request("PATCH", "api/v1/memory/sessions/$sessionId", body))
    }

    fun deleteSession(sessionId: String) {
        request("DELETE", "api/v1/memory/sessions/$sessionId")
    }

    fun updateMemory(memoryId: String, status: String? = null, active: Boolean? = null): MemoryItem {
        val body = JSONObject().apply {
            status?.let { put("resolutionStatus", it) }
            active?.let { put("active", it) }
        }
        return ResultJson.memoryFromJson(
            request("PATCH", "api/v1/memory/memories/$memoryId", body)
        )
    }

    private fun sessionFromJson(json: JSONObject) = ConversationSession(
        id = json.getString("id"),
        title = json.optString("title").ifBlank { "Untitled conversation" },
        status = json.optString("status"),
        startedAt = json.optString("startedAt"),
        durationSeconds = if (json.isNull("durationSeconds")) null else json.optInt("durationSeconds"),
        summary = if (json.isNull("summary")) null else json.optString("summary").takeIf { it.isNotBlank() },
        errorMessage = if (json.isNull("errorMessage")) null else json.optString("errorMessage").takeIf { it.isNotBlank() }
    )

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = connection(path).apply {
            requestMethod = method
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        return readResponse(connection)
    }

    private fun connection(path: String): HttpURLConnection {
        val target = base.resolve(path)
        require(target.scheme == base.scheme && target.host == base.host && target.port == base.port) {
            "Invalid backend path"
        }
        return (target.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 240_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private fun readResponse(connection: HttpURLConnection): JSONObject {
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(raw).optString("message") }.getOrNull()
                val friendly = message?.takeIf { it.isNotBlank() } ?: "Backend returned HTTP $code"
                if (code == 401) throw AuthException(friendly)
                throw BackendException(friendly)
            }
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}

data class LoginResult(val token: String, val userId: String, val username: String)

open class BackendException(message: String) : Exception(message)
class AuthException(message: String) : BackendException(message)
