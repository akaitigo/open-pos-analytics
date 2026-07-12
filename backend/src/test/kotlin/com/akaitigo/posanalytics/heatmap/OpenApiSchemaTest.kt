package com.akaitigo.posanalytics.heatmap

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
class OpenApiSchemaTest {
    @Test
    fun `openapi 文書に heatmap と categories のパスが含まれる`() {
        given()
            .`when`()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .body(containsString("/api/heatmap"))
            .body(containsString("/api/categories"))
    }

    @Test
    fun `openapi JSON に HeatmapResponse スキーマとセル定義が含まれる`() {
        given()
            .queryParam("format", "JSON")
            .`when`()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .body(containsString("HeatmapResponse"))
            .body(containsString("cells"))
    }
}
