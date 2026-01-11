package com.netguardx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private lateinit var dnsHandler: DnsHandler
    private lateinit var blocklistManager: BlocklistManager

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_APPS = "apps"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        dnsHandler = DnsHandler()
        blocklistManager = BlocklistManager(this)
        blocklistManager.loadBlocklists()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val apps = intent.getStringArrayListExtra(EXTRA_APPS) ?: emptyList()
                startVpn(apps)
            }
            ACTION_STOP -> {
                stopVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn(apps: List<String>) {
        if (running) return
        running = true
        isRunning = true

        val builder = Builder()
            .setSession("NetGuardX")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")

        for (app in apps) {
            builder.addAllowedApplication(app)
        }

        vpnInterface = builder.establish()
        startForeground(1, createNotification())

        Thread {
            runVpn()
        }.start()
    }

    private fun stopVpn() {
        running = false
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    private fun runVpn() {
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)

        val buffer = ByteBuffer.allocate(32767)

        while (running) {
            try {
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)
                    handlePacket(buffer, outputStream)
                    buffer.clear()
                }
            } catch (e: Exception) {
                Log.e("VPN", "Error reading packet", e)
            }
        }
    }

    private fun handlePacket(buffer: ByteBuffer, outputStream: FileOutputStream) {
        val ipVersion = buffer.get().toInt() shr 4
        if (ipVersion == 4) {
            val headerLength = (buffer.get().toInt() and 0x0F) * 4
            val protocol = buffer.get(headerLength - 9).toInt()
            if (protocol == 17) { // UDP
                val srcPort = buffer.getShort(headerLength).toInt() and 0xFFFF
                val dstPort = buffer.getShort(headerLength + 2).toInt() and 0xFFFF
                if (dstPort == 53) { // DNS
                    dnsHandler.handleDns(buffer, outputStream, blocklistManager)
                    return
                }
            }
        }
        // Forward other packets
        outputStream.write(buffer.array(), 0, buffer.limit())
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NetGuardX")
            .setContentText("VPN is running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}