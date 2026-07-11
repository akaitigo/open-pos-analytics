package com.akaitigo.posanalytics.cohort

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.aggregate.AggregationService
import com.akaitigo.posanalytics.aggregate.CohortAggregationService
import com.akaitigo.posanalytics.ingest.IngestService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * cohort/RFM REST API のエンドツーエンド検証。
 * - 入力バリデーション（400）
 * - 会員データ無しフォールバック（200）
 * - 正常応答の構造
 * - 会員ハッシュが応答に漏れないこと（ADR-0003）
 */
@QuarkusTest
class CohortResourceTest {

    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var aggregationService: AggregationService

    @Inject
    lateinit var cohortAggregationService: CohortAggregationService

    @Inject
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
    }

    @Test
    fun `cohort は from・to 未指定で400`() {
        given().`when`().get("/api/cohort")
            .then().statusCode(400).body("error", notNullValue())
    }

    @Test
    fun `cohort は不正な月形式で400`() {
        given().`when`().get("/api/cohort?from=2026-3&to=2026-05")
            .then().statusCode(400)
    }

    @Test
    fun `cohort は存在しない月（13月）で400`() {
        given().`when`().get("/api/cohort?from=2026-13&to=2026-05")
            .then().statusCode(400)
    }

    @Test
    fun `cohort は from が to より後で400`() {
        given().`when`().get("/api/cohort?from=2026-05&to=2026-01")
            .then().statusCode(400)
    }

    @Test
    fun `cohort は36ヶ月を超える範囲で400`() {
        given().`when`().get("/api/cohort?from=2020-01&to=2026-01")
            .then().statusCode(400)
    }

    @Test
    fun `cohort は会員データ無しでも200でフォールバック`() {
        given().`when`().get("/api/cohort?from=2026-01&to=2026-06")
            .then().statusCode(200)
            .body("hasMemberData", equalTo(false))
            .body("cohorts.size()", equalTo(0))
    }

    @Test
    fun `cohort はデータありで200と構造を返す`() {
        ingestAndAggregateCohort()

        given().`when`().get("/api/cohort?from=2026-01&to=2026-03")
            .then().statusCode(200)
            .body("hasMemberData", equalTo(true))
            .body("maxMonthOffset", equalTo(2))
            .body("cohorts.cohortMonth", hasItems("2026-01", "2026-02"))
    }

    @Test
    fun `rfm は会員データ無しでも200でフォールバック`() {
        given().`when`().get("/api/rfm")
            .then().statusCode(200)
            .body("hasMemberData", equalTo(false))
            .body("segments.size()", equalTo(0))
    }

    @Test
    fun `rfm は不正な segment で400`() {
        given().`when`().get("/api/rfm?segment=platinum")
            .then().statusCode(400).body("error", notNullValue())
    }

    @Test
    fun `rfm はデータありで3セグメントを返す`() {
        setupRfmCustomers()

        given().`when`().get("/api/rfm")
            .then().statusCode(200)
            .body("hasMemberData", equalTo(true))
            .body("totalCustomers", equalTo(9))
            .body("segments.size()", equalTo(3))
            .body("segments.segment", hasItems("loyal", "at_risk", "dormant"))
    }

    @Test
    fun `rfm は segment フィルタで単一セグメントを返す`() {
        setupRfmCustomers()

        given().`when`().get("/api/rfm?segment=loyal")
            .then().statusCode(200)
            .body("segments.size()", equalTo(1))
            .body("segments[0].segment", equalTo("loyal"))
            .body("segments[0].customerCount", equalTo(4))
    }

    @Test
    fun `rfm 応答に会員ハッシュが含まれない`() {
        setupRfmCustomers()

        val body = given().`when`().get("/api/rfm")
            .then().statusCode(200)
            .body(not(containsString("hashA")))
            .extract().asString()
        assertFalse(body.contains("customer_hash"), "応答にハッシュカラム名を含めない")
        assertFalse(body.contains("hashB"), "応答に会員ハッシュ値を含めない")
    }

    private fun ingestAndAggregateCohort() {
        val header =
            "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"
        val csv = listOf(
            header,
            "TX-J1,2026-01-05T12:00:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
            "TX-J2,2026-01-06T12:00:00+09:00,M-002,P-101,鮭おにぎり,米飯,1,180",
            "TX-J3,2026-01-07T12:00:00+09:00,M-003,P-101,鮭おにぎり,米飯,1,180",
            "TX-F1,2026-02-05T12:00:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
            "TX-F2,2026-02-06T12:00:00+09:00,M-002,P-101,鮭おにぎり,米飯,1,180",
            "TX-F3,2026-02-10T12:00:00+09:00,M-004,P-101,鮭おにぎり,米飯,1,180",
            "TX-M1,2026-03-05T12:00:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
            "TX-M2,2026-03-10T12:00:00+09:00,M-004,P-101,鮭おにぎり,米飯,1,180",
        )
        ingestService.ingest(csv.asSequence())
        aggregationService.recomputeCustomerMonthlyCohort()
    }

    private fun setupRfmCustomers() {
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

    private fun insertCustomer(hash: String, lastSeen: OffsetDateTime, visits: Int, spent: Int) {
        entityManager.createNativeQuery(
            """
            INSERT INTO customers (customer_hash, first_seen_at, last_seen_at, visit_count, total_spent)
            VALUES (:hash, :seen, :seen, :visits, :spent)
            """.trimIndent(),
        )
            .setParameter("hash", hash)
            .setParameter("seen", lastSeen)
            .setParameter("visits", visits)
            .setParameter("spent", BigDecimal(spent).setScale(2))
            .executeUpdate()
    }

    private fun ts(year: Int, month: Int, day: Int): OffsetDateTime =
        OffsetDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.ofHours(9))
}
