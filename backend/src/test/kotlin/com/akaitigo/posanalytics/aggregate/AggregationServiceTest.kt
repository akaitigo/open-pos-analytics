package com.akaitigo.posanalytics.aggregate

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.ingest.IngestService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class AggregationServiceTest {

    @Inject
    lateinit var aggregationService: AggregationService

    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var entityManager: EntityManager

    private val header =
        "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

    // T1/T2: 同日12時台(JST) / T3: 翌日18時台 / T4: 翌月・匿名
    private val fixture = listOf(
        header,
        "TX-1,2026-06-05T12:10:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
        "TX-1,2026-06-05T12:10:00+09:00,M-001,P-301,緑茶500ml,飲料,1,140",
        "TX-2,2026-06-05T12:40:00+09:00,M-002,P-101,鮭おにぎり,米飯,2,180",
        "TX-3,2026-06-06T18:05:00+09:00,M-001,P-802,唐揚げ,惣菜,1,380",
        "TX-4,2026-07-01T12:15:00+09:00,,P-101,鮭おにぎり,米飯,1,180",
        "TX-4,2026-07-01T12:15:00+09:00,,P-301,緑茶500ml,飲料,1,140",
    )

    private fun pgDow(isoDateTime: String): Int =
        OffsetDateTime.parse(isoDateTime).dayOfWeek.value % 7

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
        ingestService.ingest(fixture.asSequence())
        aggregationService.recomputeAll(minPairCount = 1)
    }

    @Test
    fun `時間帯x曜日ヒートマップがSQL手計算と一致する`() {
        val dow = pgDow("2026-06-05T12:10:00+09:00")

        val all = heatmapCell(dow, 12, AggregationService.CATEGORY_ALL)
        assertEquals(BigDecimal("680.00"), all.first, "12時台ALL: 180+140+360")
        assertEquals(2L, all.second, "12時台ALLのトランザクション数")

        val riceOnly = heatmapCell(dow, 12, "米飯")
        assertEquals(BigDecimal("540.00"), riceOnly.first, "12時台の米飯: 180+360")

        val saturday = pgDow("2026-06-06T18:05:00+09:00")
        val souzai = heatmapCell(saturday, 18, "惣菜")
        assertEquals(BigDecimal("380.00"), souzai.first)
    }

    @Test
    fun `カテゴリ別の合計がALL行と一致する`() {
        val dow = pgDow("2026-06-05T12:10:00+09:00")
        val sumOfCategories = entityManager.createNativeQuery(
            """
            SELECT COALESCE(SUM(sales_amount), 0) FROM sales_by_hour_dow
            WHERE dow = :dow AND hour = 12 AND category <> :all
            """.trimIndent(),
        ).setParameter("dow", dow).setParameter("all", AggregationService.CATEGORY_ALL)
            .singleResult as BigDecimal
        val all = heatmapCell(dow, 12, AggregationService.CATEGORY_ALL)
        assertEquals(all.first, sumOfCategories.setScale(2))
    }

    @Test
    fun `併売ペアの支持度・信頼度・リフト値が定義通り`() {
        // (P-101, P-301) 併売: TX-1, TX-4 の2回 / 全4TX / P-101出現3回・P-301出現2回
        val row = entityManager.createNativeQuery(
            """
            SELECT pair_count, support, confidence_a_to_b, confidence_b_to_a, lift
            FROM item_pair_stats
            WHERE product_a = 'P-101' AND product_b = 'P-301' AND time_segment = 'all'
            """.trimIndent(),
        ).singleResult as Array<*>

        assertEquals(2L, (row[0] as Number).toLong())
        assertEquals(0.5, (row[1] as BigDecimal).toDouble(), 1e-6, "support = 2/4")
        assertEquals(2.0 / 3.0, (row[2] as BigDecimal).toDouble(), 1e-6, "conf(A→B) = 2/3")
        assertEquals(1.0, (row[3] as BigDecimal).toDouble(), 1e-6, "conf(B→A) = 2/2（P-301の出現はTX-1,TX-4のみ）")
        assertEquals(4.0 / 3.0, (row[4] as BigDecimal).toDouble(), 1e-6, "lift = (2*4)/(3*2)")
    }

    @Test
    fun `最小ペア数の閾値でプルーニングされる`() {
        aggregationService.recomputeItemPairStats(minPairCount = 3)
        val count = entityManager.createNativeQuery("SELECT COUNT(*) FROM item_pair_stats")
            .singleResult as Number
        assertEquals(0L, count.toLong(), "pair_count>=3 のペアは存在しない")
    }

    @Test
    fun `月次コホートは会員のみを集計する`() {
        val rows = entityManager.createNativeQuery(
            """
            SELECT cohort_month::text, activity_month::text, active_customers, total_sales
            FROM customer_monthly_cohort ORDER BY cohort_month, activity_month
            """.trimIndent(),
        ).resultList

        assertEquals(1, rows.size, "匿名TX-4はコホートに含まれない")
        val row = rows.first() as Array<*>
        assertEquals("2026-06-01", row[0])
        assertEquals("2026-06-01", row[1])
        assertEquals(2L, (row[2] as Number).toLong(), "6月コホートの6月活動 = M-001, M-002")
        assertEquals(BigDecimal("1060.00"), (row[3] as BigDecimal).setScale(2), "320+360+380")
    }

    private fun heatmapCell(dow: Int, hour: Int, category: String): Pair<BigDecimal, Long> {
        val row = entityManager.createNativeQuery(
            """
            SELECT sales_amount, transaction_count FROM sales_by_hour_dow
            WHERE dow = :dow AND hour = :hour AND category = :category
            """.trimIndent(),
        ).setParameter("dow", dow).setParameter("hour", hour).setParameter("category", category)
            .singleResult as Array<*>
        return (row[0] as BigDecimal).setScale(2) to (row[1] as Number).toLong()
    }
}
