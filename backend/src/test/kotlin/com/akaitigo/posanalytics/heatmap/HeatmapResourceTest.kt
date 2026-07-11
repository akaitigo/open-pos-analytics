package com.akaitigo.posanalytics.heatmap

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.aggregate.AggregationService
import com.akaitigo.posanalytics.ingest.IngestService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class HeatmapResourceTest {

    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var aggregationService: AggregationService

    @Inject
    lateinit var entityManager: EntityManager

    private val header =
        "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

    // AggregationServiceTest と同じ既知フィクスチャ（米飯/飲料/惣菜の3カテゴリ）
    private val fixture = listOf(
        header,
        "TX-1,2026-06-05T12:10:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
        "TX-1,2026-06-05T12:10:00+09:00,M-001,P-301,緑茶500ml,飲料,1,140",
        "TX-2,2026-06-05T12:40:00+09:00,M-002,P-101,鮭おにぎり,米飯,2,180",
        "TX-3,2026-06-06T18:05:00+09:00,M-001,P-802,唐揚げ,惣菜,1,380",
        "TX-4,2026-07-01T12:15:00+09:00,,P-101,鮭おにぎり,米飯,1,180",
        "TX-4,2026-07-01T12:15:00+09:00,,P-301,緑茶500ml,飲料,1,140",
    )

    private fun pgDow(iso: String): Int = OffsetDateTime.parse(iso).dayOfWeek.value % 7

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
        ingestService.ingest(fixture.asSequence())
        aggregationService.recomputeAll(minPairCount = 1)
    }

    @Test
    fun `heatmap 全カテゴリの集計値が sales_by_hour_dow と一致する`() {
        val response = given()
            .queryParam("metric", "sales")
            .`when`().get("/api/heatmap")
            .then().statusCode(200)
            .body("metric", equalTo("sales"))
            .body("category", equalTo(AggregationService.CATEGORY_ALL))
            .extract().response()

        val actual = response.jsonPath().getList<Map<String, Any>>("cells")
        assertCellsMatch(queryCells(AggregationService.CATEGORY_ALL), actual)

        // 既知セル: 2026-06-05 12時台 ALL = 180+140+360 = 680 / 2件
        val dow = pgDow("2026-06-05T12:10:00+09:00")
        val cell = actual.first { (it["dow"] as Number).toInt() == dow && (it["hour"] as Number).toInt() == 12 }
        assertEquals(0, BigDecimal(cell["sales"].toString()).compareTo(BigDecimal("680.00")))
        assertEquals(2, (cell["count"] as Number).toInt())
    }

    @Test
    fun `category 指定で該当カテゴリのみ返す`() {
        val response = given()
            .queryParam("category", "米飯")
            .`when`().get("/api/heatmap")
            .then().statusCode(200)
            .body("category", equalTo("米飯"))
            .extract().response()

        val actual = response.jsonPath().getList<Map<String, Any>>("cells")
        assertCellsMatch(queryCells("米飯"), actual)

        // 12時台の米飯 = 180+360 = 540
        val dow = pgDow("2026-06-05T12:10:00+09:00")
        val cell = actual.first { (it["dow"] as Number).toInt() == dow && (it["hour"] as Number).toInt() == 12 }
        assertEquals(0, BigDecimal(cell["sales"].toString()).compareTo(BigDecimal("540.00")))
    }

    @Test
    fun `metric=count は 200 で echo される`() {
        given().queryParam("metric", "count")
            .`when`().get("/api/heatmap")
            .then().statusCode(200).body("metric", equalTo("count"))
    }

    @Test
    fun `metric 未指定は sales にフォールバックする`() {
        given().`when`().get("/api/heatmap")
            .then().statusCode(200).body("metric", equalTo("sales"))
    }

    @Test
    fun `metric が enum 外は 400`() {
        given().queryParam("metric", "revenue")
            .`when`().get("/api/heatmap")
            .then().statusCode(400).body("error", notNullValue())
    }

    @Test
    fun `存在しない category は 400`() {
        given().queryParam("category", "存在しないカテゴリ")
            .`when`().get("/api/heatmap")
            .then().statusCode(400).body("error", notNullValue())
    }

    @Test
    fun `100文字を超える category は 400`() {
        given().queryParam("category", "あ".repeat(101))
            .`when`().get("/api/heatmap")
            .then().statusCode(400)
    }

    @Test
    fun `categories は実在カテゴリを返し合算センチネルを含まない`() {
        given().`when`().get("/api/categories")
            .then().statusCode(200)
            .body("categories", hasItem("米飯"))
            .body("categories", hasItem("飲料"))
            .body("categories", hasItem("惣菜"))
            .body("categories", not(hasItem(AggregationService.CATEGORY_ALL)))
    }

    private fun queryCells(category: String): List<Array<*>> {
        val rows = entityManager.createNativeQuery(
            "SELECT dow, hour, sales_amount, transaction_count, item_count " +
                "FROM sales_by_hour_dow WHERE category = :c ORDER BY dow, hour",
        ).setParameter("c", category).resultList
        return rows.map { it as Array<*> }
    }

    private fun assertCellsMatch(expected: List<Array<*>>, actual: List<Map<String, Any>>) {
        assertEquals(expected.size, actual.size, "セル数が一致する")
        expected.forEachIndexed { index, row ->
            val cell = actual[index]
            assertEquals((row[0] as Number).toInt(), (cell["dow"] as Number).toInt(), "dow 一致")
            assertEquals((row[1] as Number).toInt(), (cell["hour"] as Number).toInt(), "hour 一致")
            assertEquals(
                0,
                (row[2] as BigDecimal).compareTo(BigDecimal(cell["sales"].toString())),
                "sales 一致",
            )
            assertEquals((row[3] as Number).toLong(), (cell["count"] as Number).toLong(), "count 一致")
            assertEquals((row[4] as Number).toLong(), (cell["itemCount"] as Number).toLong(), "itemCount 一致")
        }
    }
}
