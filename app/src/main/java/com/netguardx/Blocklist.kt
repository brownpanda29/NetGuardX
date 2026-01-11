package com.netguardx

object Blocklist {

    fun isBlocked(domain: String, blocklist: Set<String>): Boolean {
        val parts = domain.split(".")
        for (i in parts.indices) {
            val subdomain = parts.subList(i, parts.size).joinToString(".")
            if (blocklist.contains(subdomain)) {
                return true
            }
        }
        return false
    }
}