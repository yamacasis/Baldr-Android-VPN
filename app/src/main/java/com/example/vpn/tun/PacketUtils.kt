package com.example.vpn.tun

import java.nio.ByteBuffer

object PacketUtils {
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    data class Ipv4Header(
        val version: Int,
        val headerLength: Int,
        val totalLength: Int,
        val protocol: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcIpStr: String,
        val dstIpStr: String
    )

    data class TcpHeader(
        val srcPort: Int,
        val dstPort: Int,
        val seqNum: Long,
        val ackNum: Long,
        val headerLength: Int,
        val flags: Int,
        val windowSize: Int
    )

    data class UdpHeader(
        val srcPort: Int,
        val dstPort: Int,
        val length: Int
    )

    fun parseIpv4Header(packet: ByteArray, length: Int): Ipv4Header? {
        if (length < 20) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null
        val totalLength = u16(packet, 2).coerceAtMost(length)
        val protocol = packet[9].toInt() and 0xFF
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        return Ipv4Header(
            version = version,
            headerLength = ihl,
            totalLength = totalLength,
            protocol = protocol,
            srcIp = srcIp,
            dstIp = dstIp,
            srcIpStr = ipBytesToString(srcIp),
            dstIpStr = ipBytesToString(dstIp)
        )
    }

    fun parseTcpHeader(packet: ByteArray, ipHeaderLen: Int, totalLen: Int): TcpHeader? {
        if (totalLen < ipHeaderLen + 20) return null
        val srcPort = u16(packet, ipHeaderLen)
        val dstPort = u16(packet, ipHeaderLen + 2)
        val seqNum = u32(packet, ipHeaderLen + 4)
        val ackNum = u32(packet, ipHeaderLen + 8)
        val dataOffset = ((packet[ipHeaderLen + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffset < 20 || totalLen < ipHeaderLen + dataOffset) return null
        val flags = packet[ipHeaderLen + 13].toInt() and 0x3F
        val windowSize = u16(packet, ipHeaderLen + 14)
        return TcpHeader(
            srcPort = srcPort,
            dstPort = dstPort,
            seqNum = seqNum,
            ackNum = ackNum,
            headerLength = dataOffset,
            flags = flags,
            windowSize = windowSize
        )
    }

    fun parseUdpHeader(packet: ByteArray, ipHeaderLen: Int, totalLen: Int): UdpHeader? {
        if (totalLen < ipHeaderLen + 8) return null
        val srcPort = u16(packet, ipHeaderLen)
        val dstPort = u16(packet, ipHeaderLen + 2)
        val udpLen = u16(packet, ipHeaderLen + 4)
        return UdpHeader(srcPort = srcPort, dstPort = dstPort, length = udpLen)
    }

    fun buildIpv4TcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        windowSize: Int = 65535,
        payload: ByteArray = ByteArray(0),
        payloadOffset: Int = 0,
        payloadLength: Int = payload.size
    ): ByteArray {
        val ipLen = 20
        val tcpLen = 20
        val totalLen = ipLen + tcpLen + payloadLength
        val buf = ByteBuffer.allocate(totalLen)

        // IPv4 Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0) // ID
        buf.putShort(0x4000.toShort()) // Don't Fragment
        buf.put(64.toByte()) // TTL
        buf.put(PROTO_TCP.toByte())
        buf.putShort(0) // Checksum placeholder
        buf.put(srcIp)
        buf.put(dstIp)

        // TCP Header
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putInt(seqNum.toInt())
        buf.putInt(ackNum.toInt())
        buf.put(0x50.toByte()) // 20 bytes header
        buf.put(flags.toByte())
        buf.putShort(windowSize.coerceIn(1024, 65535).toShort())
        buf.putShort(0) // TCP checksum placeholder
        buf.putShort(0) // Urgent pointer

        if (payloadLength > 0) {
            buf.put(payload, payloadOffset, payloadLength)
        }

        val packet = buf.array()
        val ipChecksum = computeChecksum(packet, 0, ipLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        val tcpChecksum = computeTransportChecksum(
            srcIp = srcIp,
            dstIp = dstIp,
            protocol = PROTO_TCP,
            packet = packet,
            offset = ipLen,
            length = tcpLen + payloadLength
        )
        packet[ipLen + 16] = ((tcpChecksum ushr 8) and 0xFF).toByte()
        packet[ipLen + 17] = (tcpChecksum and 0xFF).toByte()

        return packet
    }

    fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipLen = 20
        val udpLen = 8 + payload.size
        val totalLen = ipLen + udpLen
        val buf = ByteBuffer.allocate(totalLen)

        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0)
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(PROTO_UDP.toByte())
        buf.putShort(0)
        buf.put(srcIp)
        buf.put(dstIp)

        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0) // UDP checksum placeholder
        buf.put(payload)

        val packet = buf.array()
        val ipChecksum = computeChecksum(packet, 0, ipLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        val udpChecksum = computeTransportChecksum(
            srcIp = srcIp,
            dstIp = dstIp,
            protocol = PROTO_UDP,
            packet = packet,
            offset = ipLen,
            length = udpLen
        )
        packet[ipLen + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[ipLen + 7] = (udpChecksum and 0xFF).toByte()
        return packet
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun computeTransportChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        protocol: Int,
        packet: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        var sum = 0L
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += protocol.toLong()
        sum += length.toLong()

        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val res = (sum.inv() and 0xFFFFL).toInt()
        return if (res == 0 && protocol == PROTO_UDP) 0xFFFF else res
    }

    fun ipBytesToString(ip: ByteArray): String =
        "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"

    private fun u16(b: ByteArray, idx: Int): Int =
        ((b[idx].toInt() and 0xFF) shl 8) or (b[idx + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, idx: Int): Long =
        ((b[idx].toLong() and 0xFF) shl 24) or
            ((b[idx + 1].toLong() and 0xFF) shl 16) or
            ((b[idx + 2].toLong() and 0xFF) shl 8) or
            (b[idx + 3].toLong() and 0xFF)
}
