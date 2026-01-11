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
                // Need to send back the full packet: IP + UDP + DNS response
                // For simplicity, since we intercepted, we need to construct the response packet
                // But this is complex; for now, just drop or send minimal
                // Actually, to properly respond, we need to modify the original packet and send back
                // But since it's TUN, we write the modified packet back
                // For NXDOMAIN, we can modify the dnsPacket and write back the full packet
                // But to keep simple, perhaps forward as is but with NXDOMAIN
                // Wait, better to create the response DNS and wrap it back
                // But for simplicity, since the code is approximate, let's assume we write the NXDOMAIN as UDP payload
                // But actually, the outputStream expects the full IP packet
                // So, we need to reconstruct the packet with NXDOMAIN DNS
                // This is getting complicated; perhaps just drop the packet for blocked
                // But to fix, let's modify the dnsPacket to NXDOMAIN and write back the full buffer with modified DNS
                // Since buffer is slice, we can modify the original buffer
                // Wait, better to create a new response
                // For now, to fix the build, perhaps just log and drop
                // But the error is annotations, perhaps not this.

                // Actually, to properly do it, we need to change the DNS response in the packet
                // But since it's query, we need to turn it into response
                // Let's modify dnsPacket to response
                dnsPacket[2] = (dnsPacket[2].toInt() or 0x80).toByte() // Set QR
                dnsPacket[3] = (dnsPacket[3].toInt() or 0x03).toByte() // NXDOMAIN
                // Write back the modified packet
                // But since buffer is the DNS part, we can set buffer.position(0), put the modified dnsPacket
                buffer.position(0)
                buffer.put(dnsPacket)
                buffer.flip()
                // Then the caller will write the full packet, but since we modified the slice, it should work
                // Wait, buffer is slice, so modifying it modifies the original
                // Yes
            } else {
                val response = forwardDns(dnsPacket)
                buffer.position(0)
                buffer.put(response)
                buffer.flip()
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
        val response = query.copyOf()
        response[2] = (response[2].toInt() or 0x80).toByte() // Set QR
        response[3] = (response[3].toInt() or 0x03).toByte() // NXDOMAIN
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