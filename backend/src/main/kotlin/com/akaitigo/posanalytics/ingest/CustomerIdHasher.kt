package com.akaitigo.posanalytics.ingest

import jakarta.enterprise.context.ApplicationScoped
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * 会員IDの一方向ハッシュ化（ADR-0003）。
 * SHA-256(salt + ":" + memberId) の16進64文字を返す。生の会員IDは保存しない。
 */
@ApplicationScoped
class CustomerIdHasher(
    @param:ConfigProperty(name = "analytics.customer-salt") private val salt: String,
) {

    fun hash(memberId: String): String {
        require(memberId.isNotBlank()) { "memberId が空です" }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$memberId".toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { HEX_FORMAT.format(it) }
    }

    companion object {
        private const val HEX_FORMAT = "%02x"
    }
}
