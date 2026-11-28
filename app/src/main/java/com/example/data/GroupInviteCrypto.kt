package com.example.data

import java.security.SecureRandom

/**
 * Cryptographic high-entropy generators and pure-JVM validators for group IDs
 * and revocable/expirable invite tokens (Spark-tier, zero external dependencies).
 *
 * Entropy design:
 * - Alphabet: 32 unambiguous Base32 characters (5 bits per character; omits 0, 1, I, O).
 * - Group ID: "v2_grp_" + 26 Base32 chars = 130 bits of entropy (decoupled from invites).
 * - Invite Token: "INV-" + 4 groups of 8 Base32 chars (32 chars total) = 160 bits of entropy.
 */
object GroupInviteCrypto {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val GROUP_ID_PREFIX = "v2_grp_"
    private const val GROUP_ID_RANDOM_CHARS = 26 // 130 bits
    private const val INVITE_PREFIX = "INV-"
    private const val INVITE_RANDOM_CHARS = 32   // 160 bits
    private const val MIN_TOKEN_ALNUM_CHARS = 24 // >= 120 bits minimum accepted

    private val secureRandom = SecureRandom()
    private val validTokenRegex = Regex("^INV-[A-Z2-9]{8}-[A-Z2-9]{8}-[A-Z2-9]{8}-[A-Z2-9]{8}$")
    private val legacyCodeRegex = Regex("^(QUEDA|GRUPO)-[A-Z0-9]{3,12}$")

    fun generateHighEntropyGroupId(random: SecureRandom = secureRandom): String {
        val sb = StringBuilder(GROUP_ID_PREFIX.length + GROUP_ID_RANDOM_CHARS)
        sb.append(GROUP_ID_PREFIX)
        repeat(GROUP_ID_RANDOM_CHARS) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    fun generateHighEntropyInviteToken(random: SecureRandom = secureRandom): String {
        val sb = StringBuilder(INVITE_PREFIX.length + INVITE_RANDOM_CHARS + 3)
        sb.append(INVITE_PREFIX)
        for (i in 0 until INVITE_RANDOM_CHARS) {
            if (i > 0 && i % 8 == 0) sb.append('-')
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    /**
     * Normalizes user-pasted invite tokens (handles lowercase, surrounding spaces,
     * optional share prefixes, or dashless 32-char Base32 tokens).
     */
    fun normalizeInviteToken(rawInput: String): String {
        val trimmed = rawInput.trim().uppercase()
        if (trimmed.isEmpty()) return ""

        // Extract canonical INV-... token if user pasted a full shared message
        val embeddedMatch = Regex("INV-[A-Z0-9]{8}-[A-Z0-9]{8}-[A-Z0-9]{8}-[A-Z0-9]{8}").find(trimmed)
        if (embeddedMatch != null) {
            return embeddedMatch.value
        }

        val withoutPrefix = trimmed.removePrefix("INV-").removePrefix("INV")
        val stripped = withoutPrefix.replace(Regex("[\\s\\-_]"), "")
        if (stripped.length == INVITE_RANDOM_CHARS && stripped.all { it in ALPHABET }) {
            return buildString {
                append(INVITE_PREFIX)
                for (i in 0 until INVITE_RANDOM_CHARS) {
                    if (i > 0 && i % 8 == 0) append('-')
                    append(stripped[i])
                }
            }
        }
        return trimmed.replace(Regex("\\s+"), "")
    }

    fun isHighEntropyInviteToken(token: String): Boolean {
        if (validTokenRegex.matches(token)) return true
        val alnumCount = token.count { it.isLetterOrDigit() }
        return token.startsWith(INVITE_PREFIX) &&
            alnumCount >= MIN_TOKEN_ALNUM_CHARS &&
            token.matches(Regex("^[A-Z0-9_\\-]{24,64}$"))
    }

    fun isLegacyGroupCode(rawInput: String): Boolean {
        val clean = rawInput.trim().uppercase().replace(Regex("\\s+"), "")
        if (clean.isEmpty()) return false
        if (isHighEntropyInviteToken(normalizeInviteToken(clean))) return false
        return legacyCodeRegex.matches(clean) || (clean.length in 4..16 && !clean.startsWith(INVITE_PREFIX))
    }

    sealed class InviteValidationResult {
        data object Valid : InviteValidationResult()
        data object AlreadyMember : InviteValidationResult()
        data object InvalidFormat : InviteValidationResult()
        data object Revoked : InviteValidationResult()
        data object Expired : InviteValidationResult()
        data object Exhausted : InviteValidationResult()
    }

    fun validateInviteForRedemption(
        token: String,
        revoked: Boolean,
        expiresAtMillis: Long?,
        useCount: Long,
        maxUses: Long,
        nowMillis: Long,
        isAlreadyMember: Boolean
    ): InviteValidationResult {
        if (!isHighEntropyInviteToken(token)) return InviteValidationResult.InvalidFormat
        if (isAlreadyMember) return InviteValidationResult.AlreadyMember
        if (revoked) return InviteValidationResult.Revoked
        if (expiresAtMillis == null || expiresAtMillis <= nowMillis) return InviteValidationResult.Expired
        if (maxUses <= 0L || useCount >= maxUses) return InviteValidationResult.Exhausted
        return InviteValidationResult.Valid
    }
}
