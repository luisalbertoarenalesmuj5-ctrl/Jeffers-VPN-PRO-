package com.example.vpn

import com.example.data.local.VpnProfileEntity
import com.example.data.model.PingTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

object VpsReachabilityTester {

    suspend fun testProfile(profile: VpnProfileEntity, timeoutMs: Int = 5000): PingTestResult =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                val startNs = System.nanoTime()
                socket.connect(InetSocketAddress(profile.host, profile.port), timeoutMs)
                val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

                socket.soTimeout = 3000
                val isSsh = profile.port == 22 || profile.protocol == com.example.data.model.TunnelProtocol.SSH_DIRECT.name
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = socket.getOutputStream()

                val banner = if (isSsh) {
                    val serverId = try { reader.readLine() } catch (_: Exception) { null }
                    out.write("${BhttpPayloadBuilder.DEFAULT_SSH_CLIENT_ID}\r\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                    serverId ?: "Puerto SSH 22 abierto (${profile.username})"
                } else {
                    val payload = BhttpPayloadBuilder.compile(profile)
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                    try { reader.readLine() } catch (_: Exception) { null }
                }

                PingTestResult(
                    profileId = profile.id,
                    isReachable = true,
                    latencyMs = rttMs,
                    httpBanner = banner ?: "TCP abierto (sin banner inmediato)",
                    errorDetail = null
                )
            } catch (e: Exception) {
                PingTestResult(
                    profileId = profile.id,
                    isReachable = false,
                    latencyMs = null,
                    httpBanner = null,
                    errorDetail = e.localizedMessage ?: e.javaClass.simpleName
                )
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
}
