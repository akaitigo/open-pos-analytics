package com.akaitigo.posanalytics

import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.persistence.EntityManager

/** テスト間のDB初期化ヘルパ。FK順に全テーブルを空にする。 */
object TestDb {

    private val TABLES = listOf(
        "line_items",
        "transactions",
        "rfm_segments",
        "customers",
        "sales_by_hour_dow",
        "item_pair_stats",
        "customer_monthly_cohort",
    )

    fun cleanAll(entityManager: EntityManager) {
        QuarkusTransaction.requiringNew().run {
            TABLES.forEach { table ->
                entityManager.createNativeQuery("DELETE FROM $table").executeUpdate()
            }
        }
    }
}
