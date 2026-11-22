package com.example.vpn

import com.example.data.local.VpnProfileEntity
import com.example.data.model.CarrierType

object BhttpPayloadBuilder {

    const val DEFAULT_TIGO_PAYLOAD =
        "GET / HTTP/1.1[crlf]Host: [sni][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf]X-Online-Host: [sni][crlf]X-VPS-Name: [vps][crlf]X-VPS-Auth: [pass][crlf][crlf]"

    const val DEFAULT_CLARO_PAYLOAD =
        "CONNECT [host_port] HTTP/1.1[crlf]Host: [sni][crlf]Connection: Keep-Alive[crlf]User-Agent: JeffersVPN/1.0[crlf]X-VPS-Name: [vps][crlf]X-VPS-Auth: [pass][crlf][crlf]"

    const val DEFAULT_SSH_CLIENT_ID = "SSH-2.0-JeffersVPN_1.0"

    fun defaultPayloadFor(carrier: CarrierType): String =
        if (carrier.defaultPort == 80) DEFAULT_CLARO_PAYLOAD else DEFAULT_TIGO_PAYLOAD

    fun compile(profile: VpnProfileEntity): String {
        val rawTemplate = profile.bhttpPayload.ifBlank {
            defaultPayloadFor(CarrierType.fromName(profile.carrier))
        }
        val basicAuth = encodeBase64("${profile.username}:${profile.password}".toByteArray(Charsets.UTF_8))
        return rawTemplate
            .replace("[host_port]", "${profile.host}:${profile.port}")
            .replace("[host]", profile.host)
            .replace("[port]", profile.port.toString())
            .replace("[sni]", profile.sniHost.ifBlank { profile.host })
            .replace("[user]", profile.username)
            .replace("[pass]", profile.password)
            .replace("[vps]", profile.vpsName)
            .replace("[carrier]", profile.carrier)
            .replace("[auth_basic]", basicAuth)
            .replace("[crlf]", "\r\n")
            .replace("[lf]", "\n")
            .replace("[cr]", "\r")
            .let { compiled ->
                if (compiled.endsWith("\r\n\r\n")) compiled else "$compiled\r\n\r\n"
            }
    }

    fun previewRedacted(profile: VpnProfileEntity): String {
        val redactedProfile = profile.copy(
            password = if (profile.password.length > 6) {
                "${profile.password.take(4)}••••••••${profile.password.takeLast(4)}"
            } else {
                "••••••••"
            }
        )
        return compile(redactedProfile)
            .replace("\r\n", "\\r\\n\n")
            .trimEnd()
    }

    private fun encodeBase64(input: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = StringBuilder(((input.size + 2) / 3) * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1

            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            out.append(if (b1 >= 0) alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)] else '=')
            out.append(if (b2 >= 0) alphabet[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}
