package com.akaitigo.posanalytics

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
class HealthCheckTest {

    @Test
    fun `health endpoint returns UP`() {
        given()
            .`when`().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }
}
