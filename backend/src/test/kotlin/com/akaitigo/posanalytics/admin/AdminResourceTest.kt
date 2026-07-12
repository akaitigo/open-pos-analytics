package com.akaitigo.posanalytics.admin

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.sample.SampleDataGenerator
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@QuarkusTest
class AdminResourceTest {
    @Inject
    lateinit var entityManager: EntityManager

    private lateinit var tmpDir: Path

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
        tmpDir = Files.createTempDirectory("admin-ingest-test")
    }

    @Test
    fun `CSV取込から3モジュールのAPI閲覧まで一気通貫で成立する`() {
        val csv = tmpDir.resolve("sample.csv")
        SampleDataGenerator().generate(csv, transactionCount = 600, seed = 42L)

        given()
            .contentType(ContentType.JSON)
            .body(mapOf("path" to csv.toAbsolutePath().toString()))
            .`when`()
            .post("/api/admin/ingest")
            .then()
            .statusCode(200)
            .body("ingestedTransactions", greaterThan(0))
            .body("ingestedLineItems", greaterThan(0))
            .body("errorCount", org.hamcrest.Matchers.equalTo(0))
            .body("aggregates.salesByHourDow", greaterThan(0))
            .body("aggregates.itemPairStats", greaterThan(0))
            .body("aggregates.customerMonthlyCohort", greaterThan(0))
            .body("aggregates.rfmSegments", greaterThan(0))

        given()
            .`when`()
            .get("/api/heatmap")
            .then()
            .statusCode(200)
            .body("cells.size()", greaterThan(0))
        given()
            .`when`()
            .get("/api/basket/pairs")
            .then()
            .statusCode(200)
            .body("pairs.size()", greaterThan(0))
        given()
            .`when`()
            .get("/api/rfm")
            .then()
            .statusCode(200)
    }

    @Test
    fun `再取込は冪等でrecomputeも再実行できる`() {
        val csv = tmpDir.resolve("sample.csv")
        SampleDataGenerator().generate(csv, transactionCount = 300, seed = 7L)
        val body = mapOf("path" to csv.toAbsolutePath().toString())

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/admin/ingest")
            .then()
            .statusCode(200)
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/admin/ingest")
            .then()
            .statusCode(200)
            .body("ingestedTransactions", org.hamcrest.Matchers.equalTo(0))
            .body("skippedDuplicates", greaterThan(0))

        given()
            .contentType(ContentType.JSON)
            .body(emptyMap<String, Any>())
            .`when`()
            .post("/api/admin/recompute")
            .then()
            .statusCode(200)
            .body("salesByHourDow", greaterThan(0))
    }

    @Test
    fun `pathバリデーションで400を返す`() {
        postIngestExpecting400(emptyMap<String, Any>(), "path は必須")
        postIngestExpecting400(mapOf("path" to "relative/data.csv"), "絶対パス")
        postIngestExpecting400(mapOf("path" to "${tmpDir.toAbsolutePath()}/data.txt"), ".csv")
        postIngestExpecting400(mapOf("path" to "${tmpDir.toAbsolutePath()}/missing.csv"), "存在しません")
    }

    @Test
    fun `minPairCountが1未満なら400を返す`() {
        given()
            .contentType(ContentType.JSON)
            .body(mapOf("minPairCount" to 0))
            .`when`()
            .post("/api/admin/recompute")
            .then()
            .statusCode(400)
            .body("error", containsString("minPairCount"))
    }

    @Test
    fun `エラー応答に生の会員IDやスタックトレースを含めない`() {
        val response =
            given()
                .contentType(ContentType.JSON)
                .body(mapOf("path" to "/no/such/file.csv"))
                .`when`()
                .post("/api/admin/ingest")
                .then()
                .statusCode(400)
                .body("error", not(containsString("Exception")))
                .extract()
                .asString()
        assert(!response.contains("at com.akaitigo")) { "スタックトレースが露出している" }
    }

    private fun postIngestExpecting400(
        body: Map<String, Any>,
        messagePart: String,
    ) {
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/admin/ingest")
            .then()
            .statusCode(400)
            .body("error", containsString(messagePart))
    }
}
