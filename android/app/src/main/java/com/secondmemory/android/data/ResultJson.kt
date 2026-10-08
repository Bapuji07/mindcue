package com.secondmemory.android.data

import org.json.JSONArray
import org.json.JSONObject

object ResultJson {
    fun encode(result: SessionResult): String = JSONObject().apply {
        put("sessionId", result.sessionId)
        put("transcript", result.transcript)
        put("summary", result.summary)
        put("memories", JSONArray().apply { result.memories.forEach { put(memoryToJson(it)) } })
    }.toString()

    fun decode(raw: String): SessionResult {
        val json = JSONObject(raw)
        val memories = json.getJSONArray("memories")
        return SessionResult(
            json.getString("sessionId"), json.optString("transcript"), json.optString("summary"),
            List(memories.length()) { memoryFromJson(memories.getJSONObject(it)) }
        )
    }

    fun memoryFromJson(json: JSONObject) = MemoryItem(
        type = json.optString("type", "NOTE"),
        title = json.optString("title", "Memory"),
        content = json.optString("content"),
        resolutionStatus = json.optNullableString("resolutionStatus"),
        dueAt = json.optNullableString("dueAt"),
        importance = json.optNullableDouble("importance"),
        confidence = json.optNullableDouble("confidence"),
        id = json.optNullableString("id"),
        sessionId = json.optNullableString("sessionId"),
        owner = json.optNullableString("owner"),
        ownerIsSelf = json.optBoolean("ownerIsSelf", false)
    )

    private fun memoryToJson(memory: MemoryItem) = JSONObject().apply {
        put("type", memory.type); put("title", memory.title); put("content", memory.content)
        put("resolutionStatus", memory.resolutionStatus); put("dueAt", memory.dueAt)
        put("importance", memory.importance); put("confidence", memory.confidence)
        put("id", memory.id); put("sessionId", memory.sessionId)
        put("owner", memory.owner); put("ownerIsSelf", memory.ownerIsSelf)
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name) || !has(name)) null else optDouble(name).takeUnless { it.isNaN() }
}
