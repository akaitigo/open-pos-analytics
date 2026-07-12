package com.akaitigo.posanalytics.cohort

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.aggregate.AggregationService
import com.akaitigo.posanalytics.aggregate.CohortAggregationService
import com.akaitigo.posanalytics.ingest.IngestService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * CohortQueryService の読み取りロジック検証。
 * - 月次コホートのリピート率が手計算と一致（受け入れ条件）
 * - 期間フィルタ・会員データ無しフォールバック
 * - RFM セグメント別サマリーの集計（件数・平均客単価・簡易LTV・segment フィルタ）
 */
@QuarkusTest
class CohortQueryServiceTest {
    @Inject
    lateinit var cohortQueryService: CohortQueryService

    @Inject
    lateinit var aggregationService: AggregationService

    @Inject
    lateinit var cohortAggregationService: CohortAggregationService

    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var entityManager: EntityManager

    private val header =
        "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

    // M-001: 1,2,3月 / M-002: 1,2月 / M-003: 1月 / M-004: 2,3月（初回2月）
    private val cohortCsv =
        listOf(
            header,
            row("TX-J1", "2026-01-05T12:00:00+09:00", "M-001"),
            row("TX-J2", "2026-01-06T12:00:00+09:00", "M-002"),
            row("TX-J3", "2026-01-07T12:00:00+09:00", "M-003"),
            row("TX-F1", "2026-02-05T12:00:00+09:00", "M-001"),
            row("TX-F2", "2026-02-06T12:00:00+09:00", "M-002"),
            row("TX-F3", "2026-02-10T12:00:00+09:00", "M-004"),
            row("TX-M1", "2026-03-05T12:00:00+09:00", "M-001"),
            row("TX-M2", "2026-03-10T12:00:00+09:00", "M-004"),
        )

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
    }

    @Test
    fun `月次コホートのリピート率が手計算と一致する`() {
        ingestAndAggregateCohort()

        val response = cohortQueryService.cohortMatrix(CohortRange.parse("2026-01", "2026-03"))

        assertTrue(response.hasMemberData)
        assertEquals(2, response.maxMonthOffset)

        val jan = response.cohorts.first { it.cohortMonth == "2026-01" }
        assertEquals(3L, jan.cohortSize, "1月コホート = M-001,002,003")
        assertEquals(3L, cell(jan, 0).activeCustomers)
        assertEquals(1.0, cell(jan, 0).retentionRate, EPS, "offset0 = 3/3")
        assertEquals(2L, cell(jan, 1).activeCustomers, "1月→2月 = M-001,002")
        assertEquals(0.667, cell(jan, 1).retentionRate, EPS, "2/3")
        assertEquals(1L, cell(jan, 2).activeCustomers, "1月→3月 = M-001")
        assertEquals(0.333, cell(jan, 2).retentionRate, EPS, "1/3")

        val feb = response.cohorts.first { it.cohortMonth == "2026-02" }
        assertEquals(1L, feb.cohortSize, "2月コホート = M-004")
        assertEquals(1.0, cell(feb, 1).retentionRate, EPS, "2月→3月 = 1/1")
    }

    @Test
    fun `期間フィルタで範囲外のコホートは除外される`() {
        ingestAndAggregateCohort()

        val response = cohortQueryService.cohortMatrix(CohortRange.parse("2026-02", "2026-03"))

        assertTrue(response.cohorts.none { it.cohortMonth == "2026-01" }, "1月コホートは範囲外")
        assertTrue(response.cohorts.any { it.cohortMonth == "2026-02" })
    }

    @Test
    fun `会員データが無い場合はコホートがフォールバック応答になる`() {
        val response = cohortQueryService.cohortMatrix(CohortRange.parse("2026-01", "2026-12"))

        assertFalse(response.hasMemberData)
        assertTrue(response.cohorts.isEmpty())
    }

    @Test
    fun `会員データが無い場合はRFMがフォールバック応答になる`() {
        val response = cohortQueryService.rfmSummary(null)

        assertFalse(response.hasMemberData)
        assertEquals(0L, response.totalCustomers)
        assertTrue(response.segments.isEmpty())
    }

    @Test
    fun `RFMサマリーの件数・平均客単価・簡易LTVが手計算と一致する`() {
        setupRfmNineCustomers()

        val response = cohortQueryService.rfmSummary(null)

        assertTrue(response.hasMemberData)
        assertEquals(9L, response.totalCustomers)
        assertEquals("2026-06-12", response.asOf, "as_of = 最終来店の最大値")

        val dormant = response.segments.first { it.segment == CohortAggregationService.SEGMENT_DORMANT }
        assertEquals(3L, dormant.customerCount, "休眠: D,E,F")
        // 平均客単価 = Σmonetary/Σfrequency = (1000+1100+1200)/(1+2+3) = 3300/6 = 550.00
        assertEquals(BigDecimal("550.00"), dormant.avgOrderValue)
        // 簡易LTV = 客単価 × 想定残存来店回数(休眠=4) = 550.00 × 4 = 2200.00
        assertEquals(BigDecimal("2200.00"), dormant.estimatedLtv)

        val loyal = response.segments.first { it.segment == CohortAggregationService.SEGMENT_LOYAL }
        assertEquals(4L, loyal.customerCount, "優良: A,C,G,I")
    }

    @Test
    fun `RFMサマリーはsegmentフィルタで単一セグメントに絞れる`() {
        setupRfmNineCustomers()

        val response = cohortQueryService.rfmSummary(RfmSegment.AT_RISK)

        assertEquals(1, response.segments.size)
        val atRisk = response.segments.first()
        assertEquals(CohortAggregationService.SEGMENT_AT_RISK, atRisk.segment)
        assertEquals(2L, atRisk.customerCount, "離脱リスク: B,H")
        // (20100+5100)/(51+11) = 25200/62 = 406.45（HALF_UP）
        assertEquals(BigDecimal("406.45"), atRisk.avgOrderValue)
    }

    private fun ingestAndAggregateCohort() {
        ingestService.ingest(cohortCsv.asSequence())
        aggregationService.recomputeCustomerMonthlyCohort()
    }

    private fun setupRfmNineCustomers() {
        QuarkusTransaction.requiringNew().run {
            insertCustomer("hashA", ts(2026, 6, 10), 50, 20000)
            insertCustomer("hashB", ts(2026, 1, 10), 51, 20100)
            insertCustomer("hashC", ts(2026, 3, 10), 52, 20200)
            insertCustomer("hashD", ts(2026, 6, 11), 1, 1000)
            insertCustomer("hashE", ts(2026, 1, 11), 2, 1100)
            insertCustomer("hashF", ts(2026, 3, 11), 3, 1200)
            insertCustomer("hashG", ts(2026, 6, 12), 10, 5000)
            insertCustomer("hashH", ts(2026, 1, 12), 11, 5100)
            insertCustomer("hashI", ts(2026, 3, 12), 12, 5200)
        }
        cohortAggregationService.recomputeRfmSegments()
    }

    private fun insertCustomer(
        hash: String,
        lastSeen: OffsetDateTime,
        visits: Int,
        spent: Int,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO customers (customer_hash, first_seen_at, last_seen_at, visit_count, total_spent)
                VALUES (:hash, :seen, :seen, :visits, :spent)
                """.trimIndent(),
            ).setParameter("hash", hash)
            .setParameter("seen", lastSeen)
            .setParameter("visits", visits)
            .setParameter("spent", BigDecimal(spent).setScale(2))
            .executeUpdate()
    }

    private fun cell(
        row: CohortRow,
        offset: Int,
    ): CohortCell = row.cells.first { it.monthOffset == offset }

    private fun row(
        txId: String,
        occurredAt: String,
        memberId: String,
    ): String = "$txId,$occurredAt,$memberId,P-101,鮭おにぎり,米飯,1,180"

    private fun ts(
        year: Int,
        month: Int,
        day: Int,
    ): OffsetDateTime = OffsetDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.ofHours(9))

    companion object {
        private const val EPS = 1e-9
    }
}
