package com.secondmemory.android.network

import com.secondmemory.android.data.AskResult
import com.secondmemory.android.data.AskSource
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.data.ResultJson
import com.secondmemory.android.data.SessionResult
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class BackendClient(baseUrl: String) {
    private val base = URI(baseUrl.trimEnd('/') + "/")

    fun health(): String {
        val json = request("GET", "api/v1/health")
        if (json.optString("status") != "UP") throw BackendException("Backend is not ready")
        return "Connected to ${json.optString("transcriptionProvider")} transcription"
    }

    fun createSession(userId: String, startedAt: String): String {
        val body = JSONObject().apply {
            put("userId", userId)
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

    fun process(sessionId: String): SessionResult {
        val json = request("POST", "api/v1/memory/sessions/$sessionId/process", JSONObject())
        val transcription = json.getJSONObject("transcription")
        val extraction = json.getJSONObject("extraction")
        val array = extraction.optJSONArray("memories")
        val memories = if (array == null) emptyList() else List(array.length()) {
            ResultJson.memoryFromJson(array.getJSONObject(it))
        }
        return SessionResult(
            sessionId = sessionId,
            transcript = transcription.optString("transcriptText"),
            summary = extraction.optString("summary"),
            memories = memories
        )
    }

    fun ask(userId: String, question: String): AskResult {
        val body = JSONObject().apply { put("userId", userId); put("question", question); put("topK", 5) }
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
        }
    }

    private fun readResponse(connection: HttpURLConnection): JSONObject {
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(raw).optString("message") }.getOrNull()
                throw BackendException(message?.takeIf { it.isNotBlank() } ?: "Backend returned HTTP $code")
            }
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}

class BackendException(message: String) : Exception(message)
