package com.netguardx

import android.util.Log
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class DnsHandler {

    private val upstreamDns = InetAddress.getByName("8.8.8.8")
    private val upstreamPort = 53

    fun handleDns(buffer: ByteBuffer, outputStream: FileOutputStream, blocklistManager: BlocklistManager) {
        try {
            val dnsPacket = ByteArray(buffer.remaining())
            buffer.get(dnsPacket)

            val domain = parseDomain(dnsPacket)
            Log.d("DNS", "Query for: $domain")

            if (blocklistManager.isBlocked(domain)) {
                Log.d("DNS", "Blocked: $domain")
                val nxdomain = createNxdomainResponse(dnsPacket)
                outputStream.write(nxdomain)
            } else {
                val response = forwardDns(dnsPacket)
                outputStream.write(response)
            }
        } catch (e: Exception) {
            Log.e("DNS", "Error handling DNS", e)
        }
    }

    private fun parseDomain(dnsPacket: ByteArray): String {
        val buffer = ByteBuffer.wrap(dnsPacket)
        buffer.position(12) // Skip header
        val labels = mutableListOf<String>()
        while (true) {
            val length = buffer.get().toInt()
            if (length == 0) break
            val label = ByteArray(length)
            buffer.get(label)
            labels.add(String(label))
        }
        return labels.joinToString(".")
    }

    private fun createNxdomainResponse(query: ByteArray): ByteArray {
        val response = ByteArray(query.size + 16) // Rough estimate
        System.arraycopy(query, 0, response, 0, query.size)
        // Set response bit, NXDOMAIN code
        response[2] = (response[2].toInt() or 0x80).toByte() // QR
        response[3] = (response[3].toInt() or 0x03).toByte() // NXDOMAIN
        // Add minimal answer section
        return response
    }

    private fun forwardDns(query: ByteArray): ByteArray {
        val socket = DatagramSocket()
        val packet = DatagramPacket(query, query.size, upstreamDns, upstreamPort)
        socket.send(packet)

        val response = ByteArray(4096)
        val responsePacket = DatagramPacket(response, response.size)
        socket.receive(responsePacket)
        socket.close()

        return response.copyOf(responsePacket.length)
    }
}