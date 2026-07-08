package com.akaitigo.posanalytics

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@QuarkusTest
class MigrationTest {

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `Flywayマイグレーションで全テーブルが作成される`() {
        val tables = entityManager.createNativeQuery(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
        ).resultList.map { it.toString() }.toSet()

        val expected = setOf(
            "transactions", "line_items", "customers",
            "sales_by_hour_dow", "item_pair_stats", "customer_monthly_cohort",
        )
        assertTrue(tables.containsAll(expected), "不足: ${expected - tables}")
    }
}
