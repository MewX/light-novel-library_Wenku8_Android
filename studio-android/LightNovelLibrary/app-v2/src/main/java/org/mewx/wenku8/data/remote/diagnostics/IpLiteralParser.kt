package org.mewx.wenku8.data.remote.diagnostics

import java.net.InetAddress
import java.util.Locale
import java.util.Random

enum class IpAddressFamily(val wireLabel: String) {
    IPV4("ipv4"),
    IPV6("ipv6");

    companion object {
        fun fromRawBytesOrNull(bytes: ByteArray): IpAddressFamily? = when (bytes.size) {
            4 -> IPV4
            16 -> IPV6
            else -> null
        }

        fun fromWireLabelOrNull(raw: String?): IpAddressFamily? = when (raw?.trim()?.lowercase()) {
            "ipv4" -> IPV4
            "ipv6" -> IPV6
            else -> null
        }
    }
}

/**
 * Represents either a single numeric IP (`/32` IPv4 or `/128` IPv6) or a verified Cloudflare
 * Anycast subnet prefix (`/16..31` IPv4, `/32..127` IPv6) from which clients sample random
 * usable host addresses without touching OS DNS.
 *
 * ### Why Subnet Sampling?
 * Publishing `/24` IPv4 subnets (`254` usable hosts each) and `/48` IPv6 prefixes (`2^80` hosts)
 * instead of static `/32` IPs prevents trivial enumeration/blocking of hardcoded IP pools:
 * - On IPv4 `/16..30`, [sampleCandidateEntry] uniformly samples offsets `1 .. (2^hostBits - 2)`,
 *   strictly avoiding `.0` network and `.255` broadcast addresses.
 * - On IPv6 `/32..127`, [sampleCandidateEntry] randomizes host bits while ensuring at least one
 *   host bit is non-zero (avoiding the RFC 4291 `::0` Subnet-Router Anycast address).
 * - Every sampled address is re-verified through [IpLiteralParser.isPublicRoutableUnicast] so
 *   RFC 1918 private, loopback, link-local, CGNAT (`100.64/10`), documentation (`TEST-NET`),
 *   multicast, and reserved ranges can never be dialed.
 */
data class CandidateAddressSpec(
    val idPrefix: String,
    val networkBytes: ByteArray,
    val prefixLength: Int,
    val family: IpAddressFamily,
    val canonicalSpec: String,
) {
    val isSingleHost: Boolean
        get() = (family == IpAddressFamily.IPV4 && prefixLength == 32) ||
            (family == IpAddressFamily.IPV6 && prefixLength == 128)

    fun sampleCandidateEntry(
        random: Random,
        requirePublicUnicast: Boolean = true,
    ): CandidateIpEntry? {
        if (isSingleHost) {
            val copy = networkBytes.clone()
            if (requirePublicUnicast && !IpLiteralParser.isPublicRoutableUnicast(copy)) {
                return null
            }
            val ipText = IpLiteralParser.formatNumericIpBytes(copy)
            return CandidateIpEntry(
                id = idPrefix,
                ipLiteral = ipText,
                rawIpBytes = copy,
                family = family,
            )
        }

        return when (family) {
            IpAddressFamily.IPV4 -> {
                val hostBits = 32 - prefixLength
                val baseInt = ((networkBytes[0].toInt() and 0xFF) shl 24) or
                    ((networkBytes[1].toInt() and 0xFF) shl 16) or
                    ((networkBytes[2].toInt() and 0xFF) shl 8) or
                    (networkBytes[3].toInt() and 0xFF)
                val hostOffset = if (prefixLength == 31) {
                    random.nextInt(2)
                } else {
                    val totalAddresses = 1 shl hostBits
                    // Strictly pick inside `1 .. totalAddresses - 2` (never `.0` network or broadcast).
                    1 + random.nextInt((totalAddresses - 2).coerceAtLeast(1))
                }
                val sampledInt = baseInt or hostOffset
                val sampledBytes = byteArrayOf(
                    ((sampledInt ushr 24) and 0xFF).toByte(),
                    ((sampledInt ushr 16) and 0xFF).toByte(),
                    ((sampledInt ushr 8) and 0xFF).toByte(),
                    (sampledInt and 0xFF).toByte(),
                )
                if (requirePublicUnicast && !IpLiteralParser.isPublicRoutableUnicast(sampledBytes)) {
                    return null
                }
                val ipText = IpLiteralParser.formatNumericIpBytes(sampledBytes)
                CandidateIpEntry(
                    id = "$idPrefix-$ipText",
                    ipLiteral = ipText,
                    rawIpBytes = sampledBytes,
                    family = IpAddressFamily.IPV4,
                )
            }

            IpAddressFamily.IPV6 -> {
                val sampledBytes = networkBytes.clone()
                val fullPrefixBytes = prefixLength / 8
                val partialBits = prefixLength % 8

                var nonZeroHostBit = false
                if (partialBits > 0 && fullPrefixBytes < 16) {
                    val hostMask = (1 shl (8 - partialBits)) - 1
                    val randBits = random.nextInt(256) and hostMask
                    if (randBits != 0) nonZeroHostBit = true
                    sampledBytes[fullPrefixBytes] =
                        ((sampledBytes[fullPrefixBytes].toInt() and (hostMask.inv() and 0xFF)) or randBits).toByte()
                }

                val firstFullHostByte = if (partialBits > 0) fullPrefixBytes + 1 else fullPrefixBytes
                for (idx in firstFullHostByte until 16) {
                    val randByte = random.nextInt(256)
                    if (randByte != 0) nonZeroHostBit = true
                    sampledBytes[idx] = randByte.toByte()
                }
                if (!nonZeroHostBit) {
                    // Avoid Subnet-Router Anycast all-zeros host identifier (`::0`).
                    sampledBytes[15] = (sampledBytes[15].toInt() or 0x01).toByte()
                }
                if (requirePublicUnicast && !IpLiteralParser.isPublicRoutableUnicast(sampledBytes)) {
                    return null
                }
                val ipText = IpLiteralParser.formatNumericIpBytes(sampledBytes)
                val compactIdSuffix = String.format(
                    Locale.ROOT,
                    "%02x%02x%02x%02x",
                    sampledBytes[12].toInt() and 0xFF,
                    sampledBytes[13].toInt() and 0xFF,
                    sampledBytes[14].toInt() and 0xFF,
                    sampledBytes[15].toInt() and 0xFF,
                )
                CandidateIpEntry(
                    id = "$idPrefix-$compactIdSuffix",
                    ipLiteral = ipText,
                    rawIpBytes = sampledBytes,
                    family = IpAddressFamily.IPV6,
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CandidateAddressSpec) return false
        return idPrefix == other.idPrefix &&
            prefixLength == other.prefixLength &&
            family == other.family &&
            networkBytes.contentEquals(other.networkBytes)
    }

    override fun hashCode(): Int {
        var result = idPrefix.hashCode()
        result = 31 * result + networkBytes.contentHashCode()
        result = 31 * result + prefixLength
        result = 31 * result + family.hashCode()
        return result
    }
}

/**
 * Pure-Kotlin DNS-free numeric IPv4 / IPv6 literal parser and public-address validator.
 *
 * Architectural rationale (`network-connectivity-and-routing-design.md` §Candidate-list service):
 * - `InetAddresses.parseNumericAddress()` requires API 29, while `InetAddress.getByName()` falls
 *   back to OS `getaddrinfo` if handed a hostname or malformed string.
 * - Parsing numeric IPv4/IPv6 strings into `ByteArray(4)` / `ByteArray(16)` in pure JVM code and
 *   wrapping via `InetAddress.getByAddress(relayHostname, rawBytes)` guarantees **zero OS DNS
 *   invocations** while keeping TLS SNI and X.509 hostname verification bound to `relayHostname`.
 * - Simultaneously rejects private, loopback, link-local, multicast, CGNAT, documentation, ULA,
 *   zone-scoped (`%`), CIDR (`/`), bracketed (`[...]`), and port-bearing (`:443` on IPv4) inputs.
 */
object IpLiteralParser {

    const val MIN_IPV4_SUBNET_PREFIX_LEN: Int = 16
    const val MIN_IPV6_SUBNET_PREFIX_LEN: Int = 32

    /**
     * Parses [rawLiteral] strictly as a numeric IPv4 (`ByteArray(4)`) or unbracketed IPv6
     * (`ByteArray(16)`) address without touching OS DNS.
     *
     * When [requirePublicUnicast] is `true` (default for untrusted/remote manifests), non-public
     * ranges (`127.0.0.0/8`, `10.0.0.0/8`, `::1`, `fe80::/10`, `2001:db8::/32`, etc.) return `null`.
     */
    fun parseNumericIpBytes(
        rawLiteral: String,
        requirePublicUnicast: Boolean = true,
    ): ByteArray? {
        val candidate = rawLiteral.trim()
        if (candidate.isEmpty() || candidate.length > 45) return null
        // Reject brackets, zone IDs, CIDR prefixes, URL schemes/paths, and whitespace.
        if (candidate.any { it == '[' || it == ']' || it == '%' || it == '/' || it.isWhitespace() }) {
            return null
        }

        val parsedBytes = if (candidate.contains(':')) {
            parseIpv6Literal(candidate)
        } else {
            parseIpv4Literal(candidate)
        } ?: return null

        if (requirePublicUnicast && !isPublicRoutableUnicast(parsedBytes)) {
            return null
        }
        return parsedBytes
    }

    /**
     * Parses either an exact numeric IP literal (`104.16.132.229`, `2606:4700::6810:84e5`) or a
     * bounded CIDR subnet (`104.18.32.0/24` with `/16..32`, `2606:4700:3030::/48` with `/32..128`)
     * into a DNS-free [CandidateAddressSpec].
     */
    fun parseCandidateSpec(
        idPrefix: String,
        rawIpOrCidr: String,
        requirePublicUnicast: Boolean = true,
    ): CandidateAddressSpec? {
        val trimmed = rawIpOrCidr.trim()
        if (trimmed.isEmpty() || trimmed.length > 49) return null
        if (trimmed.any { it == '[' || it == ']' || it == '%' || it.isWhitespace() }) {
            return null
        }

        val slashIndex = trimmed.indexOf('/')
        if (slashIndex < 0) {
            val ipBytes = parseNumericIpBytes(trimmed, requirePublicUnicast = requirePublicUnicast)
                ?: return null
            val family = IpAddressFamily.fromRawBytesOrNull(ipBytes) ?: return null
            val prefixLen = if (family == IpAddressFamily.IPV4) 32 else 128
            return CandidateAddressSpec(
                idPrefix = idPrefix,
                networkBytes = ipBytes,
                prefixLength = prefixLen,
                family = family,
                canonicalSpec = formatNumericIpBytes(ipBytes),
            )
        }

        val ipPart = trimmed.substring(0, slashIndex)
        val prefixPart = trimmed.substring(slashIndex + 1)
        if (ipPart.isEmpty() || prefixPart.isEmpty() || prefixPart.length > 3 || prefixPart.contains('/')) {
            return null
        }
        if (prefixPart.length > 1 && prefixPart[0] == '0') return null
        val prefixLen = prefixPart.toIntOrNull() ?: return null

        val rawBytes = if (ipPart.contains(':')) {
            parseIpv6Literal(ipPart)
        } else {
            parseIpv4Literal(ipPart)
        } ?: return null

        val family = IpAddressFamily.fromRawBytesOrNull(rawBytes) ?: return null
        when (family) {
            IpAddressFamily.IPV4 -> {
                if (prefixLen !in MIN_IPV4_SUBNET_PREFIX_LEN..32) return null
            }
            IpAddressFamily.IPV6 -> {
                if (prefixLen !in MIN_IPV6_SUBNET_PREFIX_LEN..128) return null
            }
        }

        val maskedNetwork = maskNetworkPrefix(rawBytes, prefixLen)
        if (requirePublicUnicast) {
            // Verify both first and last addresses in the CIDR block stay strictly inside public unicast!
            val broadcastOrLast = fillHostBitsOne(maskedNetwork, prefixLen)
            if (!isPublicRoutableUnicast(maskedNetwork) || !isPublicRoutableUnicast(broadcastOrLast)) {
                return null
            }
        }

        val canonicalIp = formatNumericIpBytes(maskedNetwork)
        val canonicalSpec = if ((family == IpAddressFamily.IPV4 && prefixLen == 32) ||
            (family == IpAddressFamily.IPV6 && prefixLen == 128)
        ) {
            canonicalIp
        } else {
            "$canonicalIp/$prefixLen"
        }
        return CandidateAddressSpec(
            idPrefix = idPrefix,
            networkBytes = maskedNetwork,
            prefixLength = prefixLen,
            family = family,
            canonicalSpec = canonicalSpec,
        )
    }

    fun formatNumericIpBytes(bytes: ByteArray): String {
        return when (bytes.size) {
            4 -> "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}." +
                "${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"
            16 -> {
                val groups = IntArray(8) { i ->
                    ((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)
                }
                // Compress longest run of >= 2 zero hextets per RFC 5952
                var bestStart = -1
                var bestLen = 0
                var curStart = -1
                var curLen = 0
                for (i in 0 until 8) {
                    if (groups[i] == 0) {
                        if (curStart == -1) {
                            curStart = i
                            curLen = 1
                        } else {
                            curLen += 1
                        }
                        if (curLen >= 2 && curLen > bestLen) {
                            bestStart = curStart
                            bestLen = curLen
                        }
                    } else {
                        curStart = -1
                        curLen = 0
                    }
                }
                if (bestStart == -1) {
                    groups.joinToString(":") { it.toString(16) }
                } else {
                    val left = (0 until bestStart).joinToString(":") { groups[it].toString(16) }
                    val right = ((bestStart + bestLen) until 8).joinToString(":") { groups[it].toString(16) }
                    "$left::$right"
                }
            }
            else -> ""
        }
    }

    private fun maskNetworkPrefix(raw: ByteArray, prefixLen: Int): ByteArray {
        val out = raw.clone()
        val fullBytes = prefixLen / 8
        val remBits = prefixLen % 8
        if (remBits > 0 && fullBytes < out.size) {
            val mask = (0xFF shl (8 - remBits)) and 0xFF
            out[fullBytes] = (out[fullBytes].toInt() and mask).toByte()
        }
        val clearStart = if (remBits > 0) fullBytes + 1 else fullBytes
        for (i in clearStart until out.size) {
            out[i] = 0
        }
        return out
    }

    private fun fillHostBitsOne(maskedNetwork: ByteArray, prefixLen: Int): ByteArray {
        val out = maskedNetwork.clone()
        val fullBytes = prefixLen / 8
        val remBits = prefixLen % 8
        if (remBits > 0 && fullBytes < out.size) {
            val hostMask = (1 shl (8 - remBits)) - 1
            out[fullBytes] = (out[fullBytes].toInt() or hostMask).toByte()
        }
        val setStart = if (remBits > 0) fullBytes + 1 else fullBytes
        for (i in setStart until out.size) {
            out[i] = 0xFF.toByte()
        }
        return out
    }

    /**
     * Constructs a DNS-free [InetAddress] whose hostname is [relayHostname] (for TLS SNI & HTTP
     * `:authority`) and whose socket bytes are [rawLiteral]. Returns `null` if invalid or non-public.
     */
    fun toPinnedInetAddress(
        relayHostname: String,
        rawLiteral: String,
        requirePublicUnicast: Boolean = true,
    ): InetAddress? {
        val normalizedHost = relayHostname.trim().trimEnd('.')
        if (normalizedHost.isEmpty()) return null
        val bytes = parseNumericIpBytes(rawLiteral, requirePublicUnicast = requirePublicUnicast)
            ?: return null
        return InetAddress.getByAddress(normalizedHost, bytes)
    }

    /**
     * Strict dotted-quad IPv4 parser (`0..255` per decimal octet, no leading zeros like `010`).
     */
    internal fun parseIpv4Literal(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val result = ByteArray(4)
        for (index in 0 until 4) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part[0] == '0') return null
            var value = 0
            for (ch in part) {
                if (ch !in '0'..'9') return null
                value = value * 10 + (ch - '0')
            }
            if (value > 255) return null
            result[index] = value.toByte()
        }
        return result
    }

    /**
     * RFC 4291 / RFC 5952 unbracketed IPv6 literal parser supporting at most one `::` zero-run
     * compression and an optional trailing dotted-quad IPv4 pair (e.g. `64:ff9b::1.2.3.4`).
     */
    internal fun parseIpv6Literal(text: String): ByteArray? {
        if (text.length < 2) return null
        // Reject triple colons or single leading/trailing colon (`:1` or `1:`)
        if (text.contains(":::")) return null
        if (text.startsWith(':') && !text.startsWith("::")) return null
        if (text.endsWith(':') && !text.endsWith("::")) return null

        val doubleColonSplit = text.split("::", limit = 3)
        if (doubleColonSplit.size > 2) return null // Multiple `::` compressions are invalid

        val hasCompression = doubleColonSplit.size == 2
        val headTokens = if (doubleColonSplit[0].isEmpty()) {
            emptyList()
        } else {
            doubleColonSplit[0].split(':')
        }
        val rawTailTokens = if (hasCompression && doubleColonSplit[1].isNotEmpty()) {
            doubleColonSplit[1].split(':')
        } else {
            emptyList()
        }

        // Handle optional embedded IPv4 dotted-quad in the very last token
        val effectiveHeadTokens: List<String>
        val effectiveTailTokens: List<String>
        var trailingIpv4Bytes: ByteArray? = null

        if (hasCompression) {
            if (rawTailTokens.isNotEmpty() && rawTailTokens.last().contains('.')) {
                trailingIpv4Bytes = parseIpv4Literal(rawTailTokens.last()) ?: return null
                effectiveHeadTokens = headTokens
                effectiveTailTokens = rawTailTokens.dropLast(1)
            } else {
                if (headTokens.any { it.contains('.') } || rawTailTokens.any { it.contains('.') }) {
                    return null
                }
                effectiveHeadTokens = headTokens
                effectiveTailTokens = rawTailTokens
            }
        } else {
            if (headTokens.isNotEmpty() && headTokens.last().contains('.')) {
                trailingIpv4Bytes = parseIpv4Literal(headTokens.last()) ?: return null
                effectiveHeadTokens = headTokens.dropLast(1)
            } else {
                if (headTokens.any { it.contains('.') }) return null
                effectiveHeadTokens = headTokens
            }
            effectiveTailTokens = emptyList()
        }

        val ipv4HextetCount = if (trailingIpv4Bytes != null) 2 else 0
        val explicitHextetCount = effectiveHeadTokens.size + effectiveTailTokens.size + ipv4HextetCount
        if (hasCompression) {
            if (explicitHextetCount >= 8) return null // `::` must compress at least one 16-bit group
        } else {
            if (explicitHextetCount != 8) return null
        }

        val bytes = ByteArray(16)
        var byteCursor = 0

        for (token in effectiveHeadTokens) {
            val hextet = parseHextet(token) ?: return null
            bytes[byteCursor++] = ((hextet ushr 8) and 0xFF).toByte()
            bytes[byteCursor++] = (hextet and 0xFF).toByte()
        }

        if (hasCompression) {
            val compressedZeroGroups = 8 - explicitHextetCount
            byteCursor += compressedZeroGroups * 2
        }

        for (token in effectiveTailTokens) {
            val hextet = parseHextet(token) ?: return null
            bytes[byteCursor++] = ((hextet ushr 8) and 0xFF).toByte()
            bytes[byteCursor++] = (hextet and 0xFF).toByte()
        }

        if (trailingIpv4Bytes != null) {
            System.arraycopy(trailingIpv4Bytes, 0, bytes, byteCursor, 4)
            byteCursor += 4
        }

        return if (byteCursor == 16) bytes else null
    }

    private fun parseHextet(token: String): Int? {
        if (token.isEmpty() || token.length > 4) return null
        var value = 0
        for (ch in token) {
            val digit = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'f' -> ch - 'a' + 10
                in 'A'..'F' -> ch - 'A' + 10
                else -> return null
            }
            value = (value shl 4) or digit
        }
        return value
    }

    /**
     * Enforces IANA / RFC 6890 global-unicast rules so a manifest can never steer relay traffic to
     * loopback, RFC1918 LANs, CGNAT, link-local, multicast, documentation, or IPv4-mapped IPv6.
     */
    fun isPublicRoutableUnicast(bytes: ByteArray): Boolean {
        return when (bytes.size) {
            4 -> isPublicIpv4(bytes)
            16 -> isPublicIpv6(bytes)
            else -> false
        }
    }

    private fun isPublicIpv4(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val b2 = b[2].toInt() and 0xFF

        if (b0 == 0) return false // 0.0.0.0/8 ("This network")
        if (b0 == 10) return false // 10.0.0.0/8 (RFC 1918)
        if (b0 == 100 && (b1 and 0xC0) == 64) return false // 100.64.0.0/10 (CGNAT RFC 6598)
        if (b0 == 127) return false // 127.0.0.0/8 (Loopback)
        if (b0 == 169 && b1 == 254) return false // 169.254.0.0/16 (Link-local)
        if (b0 == 172 && b1 in 16..31) return false // 172.16.0.0/12 (RFC 1918)
        if (b0 == 192 && b1 == 0 && b2 == 0) return false // 192.0.0.0/24 (IETF Protocol Assignments)
        if (b0 == 192 && b1 == 0 && b2 == 2) return false // 192.0.2.0/24 (TEST-NET-1)
        if (b0 == 192 && b1 == 88 && b2 == 99) return false // 192.88.99.0/24 (6to4 Relay Anycast)
        if (b0 == 192 && b1 == 168) return false // 192.168.0.0/16 (RFC 1918)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return false // 198.18.0.0/15 (Benchmarking)
        if (b0 == 198 && b1 == 51 && b2 == 100) return false // 198.51.100.0/24 (TEST-NET-2)
        if (b0 == 203 && b1 == 0 && b2 == 113) return false // 203.0.113.0/24 (TEST-NET-3)
        if (b0 >= 224) return false // 224.0.0.0/4 Multicast + 240.0.0.0/4 Reserved/Broadcast
        return true
    }

    private fun isPublicIpv6(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF

        // Reject unspecified `::` and loopback `::1`
        val allFirst15Zero = (0 until 15).all { b[it].toInt() == 0 }
        if (allFirst15Zero && (b[15].toInt() == 0 || b[15].toInt() == 1)) {
            return false
        }

        // Reject IPv4-mapped `::ffff:0:0/96` and IPv4-compatible `::/96`
        val first10Zero = (0 until 10).all { b[it].toInt() == 0 }
        if (first10Zero) {
            val b10 = b[10].toInt() and 0xFF
            val b11 = b[11].toInt() and 0xFF
            if ((b10 == 0 && b11 == 0) || (b10 == 0xFF && b11 == 0xFF)) {
                return false
            }
        }

        // Reject Multicast `ff00::/8`, Link-local `fe80::/10`, Unique Local `fc00::/7`
        if (b0 == 0xFF) return false
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return false
        if ((b0 and 0xFE) == 0xFC) return false

        // Reject Documentation `2001:db8::/32`
        val b2 = b[2].toInt() and 0xFF
        val b3 = b[3].toInt() and 0xFF
        if (b0 == 0x20 && b1 == 0x01 && b2 == 0x0D && b3 == 0xB8) return false

        // Reject NAT64 Well-Known Prefix `64:ff9b::/96` & `64:ff9b:1::/48` as direct candidate IPs
        if (b0 == 0x00 && b1 == 0x64 && b2 == 0xFF && b3 == 0x9B) return false

        // Require IANA Global Unicast `2000::/3` (`001xxxxx`)
        return (b0 and 0xE0) == 0x20
    }
}
