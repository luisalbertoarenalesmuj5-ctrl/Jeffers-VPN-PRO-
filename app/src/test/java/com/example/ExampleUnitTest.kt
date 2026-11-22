package com.example

import com.example.data.model.PlanTier
import com.example.ui.Formatters
import com.example.ui.screens.LiveSeriesChannel
import com.example.ui.screens.discoverFirstReachableChannel
import com.example.ui.screens.isSportsChannel
import com.example.ui.screens.parseM3uChannels
import com.example.ui.screens.probeStreamHttp
import com.example.vpn.LicenseCodeEngine
import com.example.vpn.LicenseValidationResult
import com.example.vpn.TunPacketRelay
import com.example.vpn.VpsAnalyzerEngine
import com.example.vpn.VpsHealthGrade
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class ExampleUnitTest {

    @Test
    fun `formatters output expected human readable values`() {
        assertEquals("00:01:05", Formatters.formatDuration(65L))
        assertEquals("512 B", Formatters.formatBytes(512L))
        assertEquals("1.5 KB", Formatters.formatBytes(1536L))
        assertEquals("2.00 MB/s", Formatters.formatSpeed(2L * 1024L * 1024L))
        val masked = Formatters.maskPassword("7K1MlZfKz5XiiyT8VYQtTDqeFmuxl5KL")
        assertTrue(masked.startsWith("7K1M"))
        assertTrue(masked.endsWith("l5KL"))
        assertTrue(masked.contains("•"))
    }

    private fun onesComplementSum(bytes: ByteArray, offset: Int, length: Int, seed: Long = 0L): Int {
        var sum = seed
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (bytes[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    @Test
    fun `buildIpv4UdpPacket constructs valid IPv4 header and UDP payload`() {
        val srcIp = byteArrayOf(1, 1, 1, 1)
        val dstIp = byteArrayOf(10, 8, 0, 2)
        val payload = byteArrayOf(0x12, 0x34, 0x01, 0x00)
        val packet = TunPacketRelay.buildIpv4UdpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 53,
            dstPort = 54321,
            payload = payload
        )

        assertEquals(20 + 8 + payload.size, packet.size)
        assertEquals(0x45.toByte(), packet[0])
        assertEquals(17.toByte(), packet[9])
        assertEquals(0x12.toByte(), packet[28])
        assertEquals(0, onesComplementSum(packet, 0, 20))
        val udpSeed = (1L shl 8 or 1L) + (1L shl 8 or 1L) + (10L shl 8 or 8L) + (0L shl 8 or 2L) + 17L + (8 + payload.size)
        assertEquals(0, onesComplementSum(packet, 20, 8 + payload.size, udpSeed))
    }

    @Test
    fun `buildIpv4TcpPacket constructs valid IPv4 header and TCP payload`() {
        val srcIp = byteArrayOf(192.toByte(), 6, 154.toByte(), 212.toByte())
        val dstIp = byteArrayOf(10, 8, 0, 2)
        val payload = "OK".toByteArray()
        val packet = TunPacketRelay.buildIpv4TcpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 8080,
            dstPort = 50000,
            seqNum = 1001L,
            ackNum = 2002L,
            flags = 0x18,
            payload = payload
        )

        assertEquals(20 + 20 + payload.size, packet.size)
        assertEquals(0x45.toByte(), packet[0])
        assertEquals(6.toByte(), packet[9])
        assertEquals(0x18.toByte(), packet[33])
        assertEquals('O'.code.toByte(), packet[40])
        assertEquals('K'.code.toByte(), packet[41])
        assertEquals(0, onesComplementSum(packet, 0, 20))
        val tcpSeed = ((192L shl 8) or 6L) + ((154L shl 8) or 212L) + ((10L shl 8) or 8L) + 2L + 6L + (20 + payload.size)
        assertEquals(0, onesComplementSum(packet, 20, 20 + payload.size, tcpSeed))
    }

    @Test
    fun `buildIpv4IcmpPortUnreachablePacket builds valid IPv4 ICMP Type 3 Code 3 packet`() {
        val originalUdp = TunPacketRelay.buildIpv4UdpPacket(
            srcIp = byteArrayOf(10, 8, 0, 2),
            dstIp = byteArrayOf(142.toByte(), 250.toByte(), 190.toByte(), 78),
            srcPort = 51000,
            dstPort = 443,
            payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        )
        val icmp = TunPacketRelay.buildIpv4IcmpPortUnreachablePacket(originalUdp, 20)
        assertNotNull(icmp)
        icmp!!
        assertEquals(20 + 8 + 28, icmp.size)
        assertEquals(1.toByte(), icmp[9]) // Protocol = ICMP
        assertEquals(3.toByte(), icmp[20]) // Type 3
        assertEquals(3.toByte(), icmp[21]) // Code 3
        assertEquals(0, onesComplementSum(icmp, 0, 20))
        assertEquals(0, onesComplementSum(icmp, 20, 8 + 28))
    }

    @Test
    fun `app version matches 1_0_9_10`() {
        assertEquals("1.0.9.10", BuildConfig.VERSION_NAME)
        assertEquals(10, BuildConfig.VERSION_CODE)
    }

    @Test
    fun `admin password verification succeeds for default ultra-hard password and rejects wrong input`() {
        val ultraDefault = "Jv#8Qx!4Lp@9Zr%2Km&7Tn*5W^3B"
        val hash = LicenseCodeEngine.hashAdminPassword(ultraDefault)
        assertTrue(
            "Expected DEFAULT_ADMIN_HASH to be $hash",
            LicenseCodeEngine.verifyAdminPassword(ultraDefault, null)
        )
        assertFalse(LicenseCodeEngine.verifyAdminPassword("Jff!9X#vP4@mQ7%kL2&zW8*R", null))
        assertFalse(LicenseCodeEngine.verifyAdminPassword("Jeffers#Admin2026!Pro", null))
        assertFalse(LicenseCodeEngine.verifyAdminPassword("123456", null))

        assertTrue(LicenseCodeEngine.isUltraStrongAdminPassword(ultraDefault))
        assertTrue(LicenseCodeEngine.isUltraStrongAdminPassword("Ultra#Pass2026!Ok"))
        assertFalse(LicenseCodeEngine.isUltraStrongAdminPassword("Short#1A"))
        assertFalse(LicenseCodeEngine.isUltraStrongAdminPassword("alllowercase123!"))

        val customHash = LicenseCodeEngine.hashAdminPassword("NewUltra!Pass99#Xy")
        assertTrue(LicenseCodeEngine.verifyAdminPassword("NewUltra!Pass99#Xy", customHash))
        assertFalse(LicenseCodeEngine.verifyAdminPassword(ultraDefault, customHash))
    }

    @Test
    fun `license code generation and validation enforce plan tier, device ID, and expiration`() {
        val futureExpiry = LicenseCodeEngine.computeExpirationForDays(30)
        for (tier in PlanTier.entries) {
            val code = LicenseCodeEngine.generateCode(tier, futureExpiry, null)
            val result = LicenseCodeEngine.validateCode(code, "JFF-TEST01")
            assertTrue(result is LicenseValidationResult.Valid)
            assertEquals(tier, (result as LicenseValidationResult.Valid).decoded.tier)
        }

        val boundCode = LicenseCodeEngine.generateCode(PlanTier.VIP, futureExpiry, "JFF-998877")
        assertTrue(LicenseCodeEngine.validateCode(boundCode, "JFF-998877") is LicenseValidationResult.Valid)
        assertTrue(LicenseCodeEngine.validateCode(boundCode, "JFF-112233") is LicenseValidationResult.Invalid)

        val pastExpiry = System.currentTimeMillis() - 3L * 86_400_000L
        val expiredCode = LicenseCodeEngine.generateCode(PlanTier.PREMIUM, pastExpiry, null)
        assertTrue(LicenseCodeEngine.validateCode(expiredCode, "JFF-998877") is LicenseValidationResult.Invalid)
    }

    @Test
    fun `vps analyzer measures local socket latency, open port, and bhttp response`() = runTest {
        assertEquals(0, VpsAnalyzerEngine.computeHealthScore(null, null, 100, 0, false, false))
        assertTrue(VpsAnalyzerEngine.computeHealthScore(45, 5, 0, 2, true, true) >= 90)

        val server = ServerSocket(0)
        val localPort = server.localPort
        val worker = thread(start = true) {
            repeat(5) {
                try {
                    val client = server.accept()
                    client.soTimeout = 1000
                    val buf = ByteArray(1024)
                    val read = try { client.getInputStream().read(buf) } catch (_: Exception) { 0 }
                    if (read > 0) {
                        client.getOutputStream().write(
                            "HTTP/1.1 200 Connection established\r\nServer: nginx\r\n\r\n".toByteArray()
                        )
                        client.getOutputStream().flush()
                    }
                    client.close()
                } catch (_: Exception) {
                }
            }
        }

        try {
            val report = VpsAnalyzerEngine.analyze(
                rawHost = "127.0.0.1",
                targetPort = localPort,
                vpsName = "VPSpremiumPRO",
                password = "secret",
                sniHost = "",
                bhttpPayload = "GET / HTTP/1.1[crlf]Host: 127.0.0.1[crlf][crlf]"
            )
            assertEquals(0, report.packetLossPercent)
            assertNotNull(report.avgLatencyMs)
            assertTrue(report.portProbes.any { it.port == localPort && it.isOpen })
            assertEquals("HTTP/1.1 200 Connection established", report.bhttpStatusLine)
            assertEquals(VpsHealthGrade.OPTIMAL, report.grade)
        } finally {
            try { server.close() } catch (_: Exception) {}
            worker.join(1000)
        }
    }

    @Test
    fun `isSportsChannel accurately identifies football and sports streams`() {
        val espnChannel = LiveSeriesChannel("1", "ESPN Deportes HD", "Deportes", "https://cdn.example.com/espn.m3u8")
        val cookingChannel = LiveSeriesChannel("2", "Cocina 24h", "Estilo", "https://cdn.example.com/cook.m3u8")
        assertTrue(isSportsChannel(espnChannel))
        assertFalse(isSportsChannel(cookingChannel))
    }

    @Test
    fun `parseM3uChannels parses EXTINF metadata and prioritizes HTTPS HLS streams`() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="http://example.com/a.png" group-title="Novelas",Canal HTTP
            http://legacy.stream.net/live.ts
            #EXTINF:-1 tvg-logo="https://cdn.example.com/logo.png" group-title="Series",Pluto Series HD
            https://stitcher.example.com/hls/series/master.m3u8
            #EXTINF:-1 group-title="Series",Pluto Series HD Duplicate
            https://stitcher.example.com/hls/series/master.m3u8
        """.trimIndent()

        val channels = parseM3uChannels(sampleM3u)
        assertEquals(2, channels.size)
        assertEquals("Pluto Series HD", channels[0].name)
        assertEquals("Series", channels[0].group)
        assertEquals("https://stitcher.example.com/hls/series/master.m3u8", channels[0].streamUrl)
        assertEquals("Canal HTTP", channels[1].name)
    }

    @Test
    fun `probeStreamHttp filters closed ports and HTML error pages while accepting HLS responses`() = runTest {
        val closedServer = ServerSocket(0)
        val closedPort = closedServer.localPort
        closedServer.close()
        assertFalse(probeStreamHttp("http://127.0.0.1:$closedPort/live.m3u8", timeoutMs = 1000))

        val server = ServerSocket(0)
        val port = server.localPort
        val worker = thread(start = true) {
            repeat(2) {
                try {
                    val client = server.accept()
                    val req = client.getInputStream().bufferedReader().readLine().orEmpty()
                    val response = if (req.contains("/html")) {
                        "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 13\r\n\r\n<html></html>"
                    } else {
                        "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: 7\r\n\r\n#EXTM3U\n"
                    }
                    client.getOutputStream().write(response.toByteArray())
                    client.getOutputStream().flush()
                    client.close()
                } catch (_: Exception) {
                }
            }
        }

        try {
            assertFalse(probeStreamHttp("http://127.0.0.1:$port/html", timeoutMs = 1500))
            assertTrue(probeStreamHttp("http://127.0.0.1:$port/master.m3u8", timeoutMs = 1500))
        } finally {
            try { server.close() } catch (_: Exception) {}
            worker.join(1000)
        }
    }

    @Test
    fun `discoverFirstReachableChannel skips dead URLs and selects first online channel`() = runTest {
        val channels = listOf(
            LiveSeriesChannel("dead1", "Dead 1", "Sports", "https://dead1.invalid/live.m3u8"),
            LiveSeriesChannel("dead2", "Dead 2", "Sports", "https://dead2.invalid/live.m3u8"),
            LiveSeriesChannel("ok1", "Live HD", "Sports", "https://live.example/live.m3u8")
        )
        val discovery = discoverFirstReachableChannel(
            channels = channels,
            probeFn = { url -> url.contains("live.example") }
        )
        assertEquals("ok1", discovery.reachableChannel?.id)
        assertEquals(
            setOf("https://dead1.invalid/live.m3u8", "https://dead2.invalid/live.m3u8"),
            discovery.unreachableUrls
        )
    }

    @Test
    fun `country regions expose expected local mobile carriers and regional SNIs`() {
        assertTrue(com.example.data.model.CountryRegion.GUATEMALA.carriers.containsAll(
            listOf(
                com.example.data.model.CarrierType.TIGO,
                com.example.data.model.CarrierType.CLARO,
                com.example.data.model.CarrierType.MOVISTAR,
                com.example.data.model.CarrierType.TUENTI,
                com.example.data.model.CarrierType.GUATEL,
                com.example.data.model.CarrierType.CABLE_COLOR,
                com.example.data.model.CarrierType.MULTINET,
                com.example.data.model.CarrierType.STARLINK
            )
        ))
        assertTrue(com.example.data.model.CountryRegion.GUATEMALA.carriers.size > 2)
        assertTrue(com.example.data.model.CountryRegion.MEXICO.carriers.containsAll(
            listOf(
                com.example.data.model.CarrierType.TELCEL,
                com.example.data.model.CarrierType.ATT,
                com.example.data.model.CarrierType.MOVISTAR,
                com.example.data.model.CarrierType.BAIT,
                com.example.data.model.CarrierType.OXXO_CEL,
                com.example.data.model.CarrierType.PILLOFON,
                com.example.data.model.CarrierType.TELMEX,
                com.example.data.model.CarrierType.TOTALPLAY
            )
        ))
        assertTrue(com.example.data.model.CountryRegion.CHILE.carriers.containsAll(
            listOf(
                com.example.data.model.CarrierType.ENTEL,
                com.example.data.model.CarrierType.WOM,
                com.example.data.model.CarrierType.VTR,
                com.example.data.model.CarrierType.MUNDO,
                com.example.data.model.CarrierType.GTD
            )
        ))
        assertTrue(com.example.data.model.CountryRegion.PERU.carriers.containsAll(
            listOf(
                com.example.data.model.CarrierType.CLARO,
                com.example.data.model.CarrierType.MOVISTAR,
                com.example.data.model.CarrierType.ENTEL,
                com.example.data.model.CarrierType.BITEL,
                com.example.data.model.CarrierType.WIN
            )
        ))
        assertTrue(com.example.data.model.CountryRegion.COLOMBIA.carriers.containsAll(
            listOf(
                com.example.data.model.CarrierType.WOM,
                com.example.data.model.CarrierType.MOVIL_EXITO,
                com.example.data.model.CarrierType.KALLEY_MOVIL,
                com.example.data.model.CarrierType.FLASH_MOBILE
            )
        ))
        assertTrue(com.example.data.model.CountryRegion.ARGENTINA.carriers.containsAll(
            listOf(
                com.example.data.model.CarrierType.PERSONAL,
                com.example.data.model.CarrierType.IMOWI,
                com.example.data.model.CarrierType.TELECENTRO
            )
        ))
        assertTrue(com.example.data.model.CountryRegion.HONDURAS.carriers.contains(com.example.data.model.CarrierType.CABLE_COLOR))
        assertTrue(com.example.data.model.CountryRegion.EL_SALVADOR.carriers.contains(com.example.data.model.CarrierType.CABLE_COLOR))
        assertTrue(com.example.data.model.CountryRegion.NICARAGUA.carriers.contains(com.example.data.model.CarrierType.IBW))
        assertTrue(com.example.data.model.CountryRegion.COSTA_RICA.carriers.contains(com.example.data.model.CarrierType.TELECABLE))
        assertTrue(com.example.data.model.CountryRegion.BOLIVIA.carriers.contains(com.example.data.model.CarrierType.COTAS))
        assertTrue(com.example.data.model.CountryRegion.BRASIL.carriers.contains(com.example.data.model.CarrierType.OI))
        assertTrue(com.example.data.model.CountryRegion.VENEZUELA.carriers.contains(com.example.data.model.CarrierType.CANTV))
        assertTrue(com.example.data.model.CountryRegion.REP_DOMINICANA.carriers.contains(com.example.data.model.CarrierType.WIND))
        assertTrue(com.example.data.model.CountryRegion.PUERTO_RICO.carriers.contains(com.example.data.model.CarrierType.AERONET))
        assertTrue(com.example.data.model.CountryRegion.CUBA.carriers.contains(com.example.data.model.CarrierType.ETECSA_NAUTA))
        assertTrue(com.example.data.model.CountryRegion.ESTADOS_UNIDOS.carriers.contains(com.example.data.model.CarrierType.MINT_MOBILE))
        assertTrue(com.example.data.model.CountryRegion.ESPANA.carriers.contains(com.example.data.model.CarrierType.PEPEPHONE))
        assertEquals(
            "www.claro.com.gt",
            com.example.data.model.CountryRegion.GUATEMALA.regionalSniFor(com.example.data.model.CarrierType.CLARO)
        )
        assertEquals(
            "www.movistar.com.gt",
            com.example.data.model.CountryRegion.GUATEMALA.regionalSniFor(com.example.data.model.CarrierType.MOVISTAR)
        )
        assertEquals(
            "www.cablecolor.hn",
            com.example.data.model.CountryRegion.HONDURAS.regionalSniFor(com.example.data.model.CarrierType.CABLE_COLOR)
        )
        assertEquals(
            "m.tigo.com.sv",
            com.example.data.model.CountryRegion.EL_SALVADOR.regionalSniFor(com.example.data.model.CarrierType.TIGO)
        )
        assertEquals(
            "www.movistar.com.mx",
            com.example.data.model.CountryRegion.MEXICO.regionalSniFor(com.example.data.model.CarrierType.MOVISTAR)
        )
        com.example.data.model.CarrierType.entries.forEach { carrier ->
            assertTrue(com.example.vpn.BhttpPayloadBuilder.defaultPayloadFor(carrier).isNotBlank())
        }
    }

    @Test
    fun `every country enforces 4 companies in FREE and 5 companies in every paid plan`() {
        assertEquals(4, PlanTier.FREE.companyQuotaPerCountry)
        assertEquals(5, PlanTier.PRO.companyQuotaPerCountry)
        assertEquals(5, PlanTier.VIP.companyQuotaPerCountry)
        assertEquals(5, PlanTier.PREMIUM.companyQuotaPerCountry)
        assertEquals(5, PlanTier.SUPER_PRO.companyQuotaPerCountry)

        com.example.data.model.CountryRegion.entries.forEach { region ->
            assertEquals(4, region.carriersForPlan(PlanTier.FREE).size)
            assertEquals(5, region.carriersForPlan(PlanTier.PRO).size)
            assertEquals(5, region.carriersForPlan(PlanTier.VIP).size)
            assertEquals(5, region.carriersForPlan(PlanTier.PREMIUM).size)
            assertEquals(5, region.carriersForPlan(PlanTier.SUPER_PRO).size)
        }

        assertTrue(PlanTier.FREE.unlocksServerTier(PlanTier.FREE))
        assertFalse(PlanTier.FREE.unlocksServerTier(PlanTier.PRO))
        assertFalse(PlanTier.FREE.unlocksServerTier(PlanTier.VIP))
        assertTrue(PlanTier.PRO.unlocksServerTier(PlanTier.PRO))
        assertFalse(PlanTier.PRO.unlocksServerTier(PlanTier.VIP))
        assertTrue(PlanTier.VIP.unlocksServerTier(PlanTier.VIP))
        assertTrue(PlanTier.PREMIUM.unlocksServerTier(PlanTier.PREMIUM))
        assertFalse(PlanTier.PREMIUM.unlocksServerTier(PlanTier.SUPER_PRO))
        assertTrue(PlanTier.SUPER_PRO.unlocksServerTier(PlanTier.PRO))
        assertTrue(PlanTier.SUPER_PRO.unlocksServerTier(PlanTier.VIP))
        assertTrue(PlanTier.SUPER_PRO.unlocksServerTier(PlanTier.PREMIUM))
        assertTrue(PlanTier.SUPER_PRO.unlocksServerTier(PlanTier.SUPER_PRO))
    }
}


