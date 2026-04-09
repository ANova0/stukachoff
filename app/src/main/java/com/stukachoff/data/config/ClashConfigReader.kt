package com.stukachoff.data.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClashConfigReader @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun read(port: Int): ClashReadResult? = withContext(Dispatchers.IO) {
        runCatching {
            ClashReadResult(
                rawConfig   = get("http://127.0.0.1:$port/configs"),
                proxies     = getProxies(port),
                connections = getConnections(port)
            )
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().body?.string()
    }.getOrNull()

    private fun getProxies(port: Int): List<ProxyEntry> {
        val json = get("http://127.0.0.1:$port/proxies") ?: return emptyList()
        return runCatching {
            val root = JSONObject(json).getJSONObject("proxies")
            root.keys().asSequence().mapNotNull { key ->
                runCatching {
                    val p = root.getJSONObject(key)
                    ProxyEntry(key, p.optString("type"), p.optString("server"), p.optInt("port"))
                }.getOrNull()
            }.toList()
        }.getOrDefault(emptyList())
    }

    private fun getConnections(port: Int): List<ConnectionEntry> {
        val json = get("http://127.0.0.1:$port/connections") ?: return emptyList()
        return runCatching {
            val arr = JSONObject(json).getJSONArray("connections")
            (0 until minOf(arr.length(), 20)).mapNotNull { i ->
                runCatching {
                    val conn = arr.getJSONObject(i)
                    val meta = conn.getJSONObject("metadata")
                    ConnectionEntry(
                        host        = meta.optString("host"),
                        destIp      = meta.optString("destinationIP"),
                        destPort    = meta.optInt("destinationPort"),
                        processPath = meta.optString("processPath"),
                        upload      = conn.optLong("upload"),
                        download    = conn.optLong("download")
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}

data class ClashReadResult(
    val rawConfig: String?,
    val proxies: List<ProxyEntry>,
    val connections: List<ConnectionEntry>
)
data class ProxyEntry(val name: String, val type: String, val server: String, val port: Int)
data class ConnectionEntry(val host: String, val destIp: String, val destPort: Int,
                           val processPath: String, val upload: Long, val download: Long)
