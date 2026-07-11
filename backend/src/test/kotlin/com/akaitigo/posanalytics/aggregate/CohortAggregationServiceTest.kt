package com.akaitigo.posanalytics.aggregate

import com.akaitigo.posanalytics.TestDb
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * RFM スコアリング（3分位 NTILE）とセグメント分類が docs/rfm-segmentation.md の定義と整合することを検証する。
 * 各次元でスコア 1/2/3 が3件ずつになるよう会員9名を作り込み、r/f/m スコアとセグメントを手計算値と突き合わせる。
 */
@QuarkusTest
class CohortAggregationServiceTest {

    @Inject
    lateinit var cohortAggregationService: CohortAggregationService

    @Inject
    lateinit var entityManager: EntityManager

    private data class Fixture(
        val hash: String,
        val lastSeen: OffsetDateTime,
        val visits: Int,
        val spent: BigDecimal,
        val expectedR: Int,
        val expectedF: Int,
        val expectedM: Int,
        val expectedSegment: String,
    )

    // last_seen: Jan=r1 / Mar=r2 / Jun=r3、visits: {1,2,3}=f1 {10,11,12}=f2 {50,51,52}=f3、spent も同様に m を作る。
    private val fixtures = listOf(
        Fixture("hashA", ts(2026, 6, 10), 50, money(20000), 3, 3, 3, CohortAggregationService.SEGMENT_LOYAL),
        Fixture("hashB", ts(2026, 1, 10), 51, money(20100), 1, 3, 3, CohortAggregationService.SEGMENT_AT_RISK),
        Fixture("hashC", ts(2026, 3, 10), 52, money(20200), 2, 3, 3, CohortAggregationService.SEGMENT_LOYAL),
        Fixture("hashD", ts(2026, 6, 11), 1, money(1000), 3, 1, 1, CohortAggregationService.SEGMENT_DORMANT),
        Fixture("hashE", ts(2026, 1, 11), 2, money(1100), 1, 1, 1, CohortAggregationService.SEGMENT_DORMANT),
        Fixture("hashF", ts(2026, 3, 11), 3, money(1200), 2, 1, 1, CohortAggregationService.SEGMENT_DORMANT),
        Fixture("hashG", ts(2026, 6, 12), 10, money(5000), 3, 2, 2, CohortAggregationService.SEGMENT_LOYAL),
        Fixture("hashH", ts(2026, 1, 12), 11, money(5100), 1, 2, 2, CohortAggregationService.SEGMENT_AT_RISK),
        Fixture("hashI", ts(2026, 3, 12), 12, money(5200), 2, 2, 2, CohortAggregationService.SEGMENT_LOYAL),
    )

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
        QuarkusTransaction.requiringNew().run {
            fixtures.forEach { insertCustomer(it) }
        }
        cohortAggregationService.recomputeRfmSegments()
    }

    @Test
    fun `R・F・Mスコアが3分位の定義通りに割り当たる`() {
        fixtures.forEach { fixture ->
            val scored = scoresOf(fixture.hash)
            assertEquals(fixture.expectedR, scored.r, "${fixture.hash} の r_score")
            assertEquals(fixture.expectedF, scored.f, "${fixture.hash} の f_score")
            assertEquals(fixture.expectedM, scored.m, "${fixture.hash} の m_score")
        }
    }

    @Test
    fun `セグメント分類がルール（V=f+m と r_score）通りになる`() {
        fixtures.forEach { fixture ->
            assertEquals(fixture.expectedSegment, scoresOf(fixture.hash).segment, "${fixture.hash} のセグメント")
        }
    }

    @Test
    fun `セグメント別の件数が手計算と一致する`() {
        assertEquals(4L, countSegment(CohortAggregationService.SEGMENT_LOYAL), "優良: A,C,G,I")
        assertEquals(2L, countSegment(CohortAggregationService.SEGMENT_AT_RISK), "離脱リスク: B,H")
        assertEquals(3L, countSegment(CohortAggregationService.SEGMENT_DORMANT), "休眠: D,E,F")
    }

    @Test
    fun `会員が0件なら rfm_segments も空になる（フォールバック前提）`() {
        TestDb.cleanAll(entityManager)
        cohortAggregationService.recomputeRfmSegments()
        val count = entityManager.createNativeQuery("SELECT COUNT(*) FROM rfm_segments")
            .singleResult as Number
        assertEquals(0L, count.toLong())
    }

    @Test
    fun `recencyの基準日は最終来店の最大値で最新会員はrecency0になる`() {
        // as_of = MAX(last_seen) = 2026-06-12（hashG）。hashG の recency_days は 0。
        val recency = entityManager.createNativeQuery(
            "SELECT recency_days FROM rfm_segments WHERE customer_hash = 'hashG'",
        ).singleResult as Number
        assertEquals(0, recency.toInt())
    }

    private data class Scored(val r: Int, val f: Int, val m: Int, val segment: String)

    private fun scoresOf(hash: String): Scored {
        val row = entityManager.createNativeQuery(
            "SELECT r_score, f_score, m_score, segment FROM rfm_segments WHERE customer_hash = :h",
        ).setParameter("h", hash).singleResult as Array<*>
        return Scored(
            r = (row[0] as Number).toInt(),
            f = (row[1] as Number).toInt(),
            m = (row[2] as Number).toInt(),
            segment = row[3] as String,
        )
    }

    private fun countSegment(segment: String): Long {
        val count = entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM rfm_segments WHERE segment = :s",
        ).setParameter("s", segment).singleResult as Number
        return count.toLong()
    }

    private fun insertCustomer(fixture: Fixture) {
        entityManager.createNativeQuery(
            """
            INSERT INTO customers (customer_hash, first_seen_at, last_seen_at, visit_count, total_spent)
            VALUES (:hash, :seen, :seen, :visits, :spent)
            """.trimIndent(),
        )
            .setParameter("hash", fixture.hash)
            .setParameter("seen", fixture.lastSeen)
            .setParameter("visits", fixture.visits)
            .setParameter("spent", fixture.spent)
            .executeUpdate()
    }

    private fun ts(year: Int, month: Int, day: Int): OffsetDateTime =
        OffsetDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun money(amount: Int): BigDecimal = BigDecimal(amount).setScale(2)
}
