package com.netguardx

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class BlocklistManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val blocklist = mutableSetOf<String>()

    private val defaultDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net"
    )

    fun loadBlocklists() {
        blocklist.clear()
        blocklist.addAll(defaultDomains)

        // Load user domains and remote lists
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val blocklistsJson = prefs.getString("blocklists", "[]")
        val lines = blocklistsJson?.split("\n") ?: emptyList()
        for (line in lines) {
            if (line.isNotEmpty()) {
                val parts = line.split(";")
                if (parts.size == 3 && parts[1].toBoolean()) { // enabled
                    val name = parts[0]
                    val isUrl = parts[2].toBoolean()
                    if (isUrl) {
                        loadRemoteList(name)
                    } else {
                        blocklist.add(name)
                    }
                }
            }
        }
    }

    private fun loadRemoteList(url: String) {
        executor.execute {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        val trimmed = it.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            blocklist.add(trimmed)
                        }
                    }
                }
                reader.close()
                connection.disconnect()
                Log.d("Blocklist", "Loaded remote list: $url")
            } catch (e: Exception) {
                Log.e("Blocklist", "Error loading $url", e)
            }
        }
    }

    fun updateRemoteLists() {
        // Reload all enabled remote lists
        loadBlocklists()
    }

    fun isBlocked(domain: String): Boolean {
        if (domain == "googlevideo.com" || domain.contains("youtube.com")) return false
        return Blocklist.isBlocked(domain, blocklist)
    }
}