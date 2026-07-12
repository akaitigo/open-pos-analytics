package com.akaitigo.posanalytics.ingest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomerIdHasherTest {
    private val hasher = CustomerIdHasher("salt-a")

    @Test
    fun `SHA-256の16進64文字を返す`() {
        val hash = hasher.hash("M-00123")
        assertTrue(Regex("^[0-9a-f]{64}$").matches(hash), "hex64ではない: $hash")
    }

    @Test
    fun `同一入力は同一ハッシュ（決定的）`() {
        assertEquals(hasher.hash("M-00123"), hasher.hash("M-00123"))
    }

    @Test
    fun `ソルトが違えばハッシュも違う`() {
        val other = CustomerIdHasher("salt-b")
        assertNotEquals(hasher.hash("M-00123"), other.hash("M-00123"))
    }

    @Test
    fun `ハッシュに生の会員IDを含まない`() {
        assertFalse(hasher.hash("M-00123").contains("M-00123"))
    }

    @Test
    fun `空の会員IDは拒否する`() {
        assertThrows<IllegalArgumentException> { hasher.hash("  ") }
    }
}
