package com.akaitigo.posanalytics.ingest

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsvTransactionParserTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC)
    private val parser = CsvTransactionParser(fixedClock)

    private val header =
        "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

    @Test
    fun `正常行をパースしヘッダを読み飛ばす`() {
        val result = parser.parse(
            sequenceOf(
                header,
                "TX-1,2026-07-01T12:00:00+09:00,M-001,P-101,鮭おにぎり,米飯,2,180",
                "TX-1,2026-07-01T12:00:00+09:00,M-001,P-301,緑茶500ml,飲料,1,140",
                "TX-2,2026-07-01T18:30:00+09:00,,P-802,唐揚げ,惣菜,1,380",
            ),
        )
        assertEquals(3, result.rows.size)
        assertEquals(0, result.errors.size)
        assertEquals(3, result.totalDataRows)
        assertEquals("M-001", result.rows[0].memberId)
        assertNull(result.rows[2].memberId, "空のmember_idはnullになる")
        assertEquals(360.toBigDecimal(), result.rows[0].lineAmount)
    }

    @Test
    fun `不正行は中断せずエラー集約する`() {
        val result = parser.parse(
            sequenceOf(
                header,
                "TX-1,2026-07-01T12:00:00+09:00,,P-101,鮭おにぎり,米飯,1,180",
                "TX-2,2026-07-01T12:00:00+09:00,,P-101,鮭おにぎり,米飯,0,180",
                "TX-3,2026-07-01T12:00:00+09:00,,P-101,鮭おにぎり,米飯,x,180",
                "TX-4,2026-07-01T12:00:00+09:00,,P-101,鮭おにぎり,米飯,1,-50",
                "TX-5,1999-12-31T23:59:59+09:00,,P-101,鮭おにぎり,米飯,1,180",
                "TX-6,2027-01-01T00:00:00+09:00,,P-101,鮭おにぎり,米飯,1,180",
                "TX-7,not-a-date,,P-101,鮭おにぎり,米飯,1,180",
                "TX-8,2026-07-01T12:00:00+09:00,,P-101,鮭おにぎり,米飯,1",
                "TX-9,2026-07-01T12:00:00+09:00,,,鮭おにぎり,米飯,1,180",
            ),
        )
        assertEquals(1, result.rows.size, "正常行は1行のみ")
        assertEquals(8, result.errors.size)
        assertEquals(9, result.totalDataRows)
        val lines = result.errors.map { it.lineNumber }
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9, 10), lines)
        assertTrue(result.errors.all { it.reason.isNotBlank() })
    }
}
