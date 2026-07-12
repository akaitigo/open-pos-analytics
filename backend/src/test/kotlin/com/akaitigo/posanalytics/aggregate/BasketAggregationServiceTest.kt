package com.akaitigo.posanalytics.aggregate

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.ingest.IngestService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class BasketAggregationServiceTest {

    @Inject
    lateinit var basketAggregationService: BasketAggregationService

    @Inject
    lateinit var aggregationService: AggregationService

    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
    }

    @Test
    fun `時間帯セグメント別にペアが分割集計される`() {
        ingest(
            pair("TX-M1", "2026-06-01T06:10:00+09:00", "P-101", "P-301"),
            pair("TX-M2", "2026-06-01T07:20:00+09:00", "P-101", "P-301"),
            pair("TX-N1", "2026-06-01T12:10:00+09:00", "P-101", "P-301"),
            pair("TX-N2", "2026-06-01T13:10:00+09:00", "P-101", "P-301"),
            pair("TX-N3", "2026-06-01T14:10:00+09:00", "P-101", "P-301"),
            pair("TX-E1", "2026-06-01T18:10:00+09:00", "P-201", "P-302"),
            pair("TX-E2", "2026-06-01T19:10:00+09:00", "P-201", "P-302"),
        )
        aggregationService.recomputeItemPairStats(minPairCount = 1)
        basketAggregationService.recomputeSegmentedPairs(minPairCount = 1)

        assertEquals(2L, pairCount("P-101", "P-301", "morning"))
        assertEquals(3L, pairCount("P-101", "P-301", "noon"))
        assertNull(pairCountOrNull("P-101", "P-301", "evening"), "夜には P-101×P-301 の共起なし")
        assertEquals(2L, pairCount("P-201", "P-302", "evening"))
        // all との整合: all の共起数 = 各セグメントの共起数の合計
        assertEquals(5L, pairCount("P-101", "P-301", "all"), "all = morning(2) + noon(3)")
    }

    @Test
    fun `時間帯境界がmorning・noon・eveningに正しく割り当てられる`() {
        ingest(
            pair("B-0459", "2026-06-01T04:59:00+09:00", "P-102", "P-303"),
            pair("B-0500", "2026-06-01T05:00:00+09:00", "P-101", "P-301"),
            pair("B-1059", "2026-06-01T10:59:00+09:00", "P-501", "P-601"),
            pair("B-1100", "2026-06-01T11:00:00+09:00", "P-401", "P-403"),
            pair("B-1659", "2026-06-01T16:59:00+09:00", "P-801", "P-803"),
            pair("B-1700", "2026-06-01T17:00:00+09:00", "P-701", "P-703"),
        )
        basketAggregationService.recomputeSegmentedPairs(minPairCount = 1)

        assertEquals("evening", segmentOf("P-102", "P-303"), "04:59 は evening")
        assertEquals("morning", segmentOf("P-101", "P-301"), "05:00 は morning")
        assertEquals("morning", segmentOf("P-501", "P-601"), "10:59 は morning")
        assertEquals("noon", segmentOf("P-401", "P-403"), "11:00 は noon")
        assertEquals("noon", segmentOf("P-801", "P-803"), "16:59 は noon")
        assertEquals("evening", segmentOf("P-701", "P-703"), "17:00 は evening")
    }

    @Test
    fun `セグメント別の支持度・信頼度・リフト値がセグメント内母数で計算される`() {
        ingest(
            pair("TX-1", "2026-06-01T06:00:00+09:00", "P-101", "P-301"),
            pair("TX-2", "2026-06-01T07:00:00+09:00", "P-101", "P-301"),
            single("TX-3", "2026-06-01T08:00:00+09:00", "P-101"),
        )
        basketAggregationService.recomputeSegmentedPairs(minPairCount = 1)

        // morning: n=3 / P-101出現3 / P-301出現2 / 共起2
        val stats = statsOf("P-101", "P-301", "morning")
        assertEquals(2L, stats.pairCount)
        assertEquals(2.0 / 3.0, stats.support, EPS, "support = 2/3")
        assertEquals(2.0 / 3.0, stats.confAToB, EPS, "conf(A→B) = 2/3")
        assertEquals(1.0, stats.confBToA, EPS, "conf(B→A) = 2/2")
        assertEquals(1.0, stats.lift, EPS, "lift = (2*3)/(3*2)")
    }

    @Test
    fun `最小共起回数でセグメント別にプルーニングされる`() {
        ingest(
            pair("TX-1", "2026-06-01T12:00:00+09:00", "P-101", "P-301"),
            pair("TX-2", "2026-06-01T13:00:00+09:00", "P-101", "P-301"),
            pair("TX-3", "2026-06-01T14:00:00+09:00", "P-201", "P-302"),
        )
        basketAggregationService.recomputeSegmentedPairs(minPairCount = 2)

        assertEquals(2L, pairCount("P-101", "P-301", "noon"), "共起2回は残る")
        assertNull(pairCountOrNull("P-201", "P-302", "noon"), "共起1回はプルーニング")
    }

    @Test
    fun `all行はセグメント再計算で保持される`() {
        ingest(pair("TX-1", "2026-06-01T12:00:00+09:00", "P-101", "P-301"))
        aggregationService.recomputeItemPairStats(minPairCount = 1)
        basketAggregationService.recomputeSegmentedPairs(minPairCount = 1)

        assertEquals(1L, pairCount("P-101", "P-301", "all"), "all は seg 再計算後も残る")
        assertEquals(1L, pairCount("P-101", "P-301", "noon"))
    }

    private data class PairStats(
        val pairCount: Long,
        val support: Double,
        val confAToB: Double,
        val confBToA: Double,
        val lift: Double,
    )

    private fun ingest(vararg groups: List<String>) {
        val lines = listOf(HEADER) + groups.flatMap { it }
        ingestService.ingest(lines.asSequence())
    }

    private fun pair(txId: String, at: String, a: String, b: String): List<String> =
        listOf(line(txId, at, a), line(txId, at, b))

    private fun single(txId: String, at: String, code: String): List<String> =
        listOf(line(txId, at, code))

    private fun line(txId: String, at: String, code: String): String {
        val (name, category) = PRODUCTS.getValue(code)
        return "$txId,$at,,$code,$name,$category,1,100"
    }

    private fun pairCount(a: String, b: String, seg: String): Long =
        (pairCountRow(a, b, seg).single() as Number).toLong()

    private fun pairCountOrNull(a: String, b: String, seg: String): Long? =
        pairCountRow(a, b, seg).firstOrNull()?.let { (it as Number).toLong() }

    private fun pairCountRow(a: String, b: String, seg: String): List<*> =
        entityManager.createNativeQuery(
            """
            SELECT pair_count FROM item_pair_stats
            WHERE product_a = :a AND product_b = :b AND time_segment = :seg
            """.trimIndent(),
        ).setParameter("a", a).setParameter("b", b).setParameter("seg", seg).resultList

    private fun segmentOf(a: String, b: String): String =
        entityManager.createNativeQuery(
            """
            SELECT time_segment FROM item_pair_stats
            WHERE product_a = :a AND product_b = :b AND time_segment <> 'all'
            """.trimIndent(),
        ).setParameter("a", a).setParameter("b", b).singleResult as String

    private fun statsOf(a: String, b: String, seg: String): PairStats {
        val row = entityManager.createNativeQuery(
            """
            SELECT pair_count, support, confidence_a_to_b, confidence_b_to_a, lift
            FROM item_pair_stats
            WHERE product_a = :a AND product_b = :b AND time_segment = :seg
            """.trimIndent(),
        ).setParameter("a", a).setParameter("b", b).setParameter("seg", seg).singleResult as Array<*>
        return PairStats(
            pairCount = (row[0] as Number).toLong(),
            support = (row[1] as BigDecimal).toDouble(),
            confAToB = (row[2] as BigDecimal).toDouble(),
            confBToA = (row[3] as BigDecimal).toDouble(),
            lift = (row[4] as BigDecimal).toDouble(),
        )
    }

    companion object {
        private const val EPS = 1e-6
        private const val HEADER =
            "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

        private val PRODUCTS = mapOf(
            "P-101" to ("鮭おにぎり" to "米飯"),
            "P-301" to ("緑茶500ml" to "飲料"),
            "P-201" to ("まぐろ刺身" to "鮮魚"),
            "P-302" to ("本わさびチューブ" to "調味料"),
            "P-102" to ("幕の内弁当" to "米飯"),
            "P-303" to ("ドリップコーヒー" to "飲料"),
            "P-401" to ("クロワッサン" to "ベーカリー"),
            "P-403" to ("メロンパン" to "ベーカリー"),
            "P-501" to ("缶ビール350ml" to "酒類"),
            "P-601" to ("ミックスナッツ" to "菓子"),
            "P-701" to ("牛乳1L" to "日配"),
            "P-703" to ("豆腐" to "日配"),
            "P-801" to ("ポテトサラダ" to "惣菜"),
            "P-803" to ("コロッケ" to "惣菜"),
        )
    }
}
