package com.akaitigo.posanalytics.basket

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.aggregate.AggregationService
import com.akaitigo.posanalytics.aggregate.BasketAggregationService
import com.akaitigo.posanalytics.ingest.IngestService
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class BasketResourceTest {
    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var aggregationService: AggregationService

    @Inject
    lateinit var basketAggregationService: BasketAggregationService

    @Inject
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
        seedFixture()
        aggregationService.recomputeItemPairStats(minPairCount = 1)
        basketAggregationService.recomputeSegmentedPairs(minPairCount = 1)
    }

    @Test
    fun `デフォルトは segment=all sort=lift limit=20 でランキングを返す`() {
        given()
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
            .body("segment", equalTo("all"))
            .body("sort", equalTo("lift"))
            .body("limit", equalTo(20))
            .body("pairs.productA", hasItem("P-101"))
    }

    @Test
    fun `sort=support で最頻併売ペアが先頭に来る`() {
        given()
            .queryParam("sort", "support")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
            .body("sort", equalTo("support"))
            .body("pairs[0].productA", equalTo("P-101"))
            .body("pairs[0].productB", equalTo("P-301"))
    }

    @Test
    fun `segment=morning はその時間帯のペアだけを返す`() {
        given()
            .queryParam("segment", "morning")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
            .body("segment", equalTo("morning"))
            .body("pairs.size()", equalTo(1))
            .body("pairs[0].productA", equalTo("P-303"))
            .body("pairs[0].productB", equalTo("P-401"))
    }

    @Test
    fun `segment=evening はその時間帯のペアだけを返す`() {
        given()
            .queryParam("segment", "evening")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
            .body("pairs.size()", equalTo(1))
            .body("pairs[0].productA", equalTo("P-501"))
            .body("pairs[0].productB", equalTo("P-601"))
    }

    @Test
    fun `商品コードに対応する商品名が解決される`() {
        given()
            .queryParam("segment", "noon")
            .queryParam("sort", "support")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
            .body("pairs[0].productNameA", equalTo("鮭おにぎり"))
            .body("pairs[0].productNameB", equalTo("緑茶500ml"))
    }

    @Test
    fun `limit で件数を制限できる`() {
        given()
            .queryParam("limit", "1")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
            .body("limit", equalTo(1))
            .body("pairs.size()", equalTo(1))
    }

    @Test
    fun `limit の境界値 1 と 100 は 200`() {
        given()
            .queryParam("limit", "1")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
        given()
            .queryParam("limit", "100")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(OK)
    }

    @Test
    fun `不正な sort は 400 を返す`() {
        given()
            .queryParam("sort", "price")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(BAD_REQUEST)
            .body("field", equalTo("sort"))
    }

    @Test
    fun `不正な segment は 400 を返す`() {
        given()
            .queryParam("segment", "midnight")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(BAD_REQUEST)
            .body("field", equalTo("segment"))
    }

    @Test
    fun `limit が 0 は 400 を返す`() {
        given()
            .queryParam("limit", "0")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(BAD_REQUEST)
            .body("field", equalTo("limit"))
    }

    @Test
    fun `limit が 101 は 400 を返す`() {
        given()
            .queryParam("limit", "101")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(BAD_REQUEST)
            .body("field", equalTo("limit"))
    }

    @Test
    fun `limit が非整数は 400 を返す`() {
        given()
            .queryParam("limit", "abc")
            .`when`()
            .get(PAIRS)
            .then()
            .statusCode(BAD_REQUEST)
            .body("field", equalTo("limit"))
    }

    private fun seedFixture() {
        val lines = mutableListOf(HEADER)
        repeat(NOON_PAIR_COUNT) { i -> lines += pair("N$i", noonAt(i), "P-101", "P-301") }
        repeat(2) { i -> lines += line("NS$i", noonAt(NOON_PAIR_COUNT + i), "P-101") }
        repeat(3) { i -> lines += pair("MG$i", morningAt(i), "P-401", "P-303") }
        repeat(2) { i -> lines += pair("EV$i", eveningAt(i), "P-501", "P-601") }
        repeat(3) { i -> lines += line("NZ$i", noonAt(NOON_PAIR_COUNT + 2 + i), "P-701") }
        ingestService.ingest(lines.asSequence())
    }

    private fun pair(
        txId: String,
        at: String,
        a: String,
        b: String,
    ): List<String> = listOf(line(txId, at, a), line(txId, at, b))

    private fun line(
        txId: String,
        at: String,
        code: String,
    ): String {
        val (name, category) = PRODUCTS.getValue(code)
        return "$txId,$at,,$code,$name,$category,1,100"
    }

    private fun noonAt(minute: Int): String = "2026-06-01T12:${pad(minute)}:00+09:00"

    private fun morningAt(minute: Int): String = "2026-06-01T06:${pad(minute)}:00+09:00"

    private fun eveningAt(minute: Int): String = "2026-06-01T18:${pad(minute)}:00+09:00"

    private fun pad(value: Int): String = value.toString().padStart(2, '0')

    companion object {
        private const val PAIRS = "/api/basket/pairs"
        private const val OK = 200
        private const val BAD_REQUEST = 400
        private const val NOON_PAIR_COUNT = 10
        private const val HEADER =
            "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

        private val PRODUCTS =
            mapOf(
                "P-101" to ("鮭おにぎり" to "米飯"),
                "P-301" to ("緑茶500ml" to "飲料"),
                "P-401" to ("クロワッサン" to "ベーカリー"),
                "P-303" to ("ドリップコーヒー" to "飲料"),
                "P-501" to ("缶ビール350ml" to "酒類"),
                "P-601" to ("ミックスナッツ" to "菓子"),
                "P-701" to ("牛乳1L" to "日配"),
            )
    }
}
