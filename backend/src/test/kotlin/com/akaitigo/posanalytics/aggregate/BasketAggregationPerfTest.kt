package com.akaitigo.posanalytics.aggregate

import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.jboss.logging.Logger
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * 受け入れ条件（SKU 1,000種・10万件で集計60秒以内）の実測用テスト。
 * データ量が大きく CI では走らせないため、環境変数 RUN_PERF_TEST=true のときだけ有効化する。
 * 実行例: RUN_PERF_TEST=true ./gradlew test --tests '*BasketAggregationPerfTest'
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "RUN_PERF_TEST", matches = "true")
class BasketAggregationPerfTest {
    @Inject
    lateinit var basketAggregationService: BasketAggregationService

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `SKU1000種10万件で60秒以内にセグメント集計が完了する`() {
        seed(skuCount = SKU_COUNT, txCount = TX_COUNT)

        val startNanos = System.nanoTime()
        basketAggregationService.recomputeSegmentedPairs(minPairCount = MIN_PAIR_COUNT)
        val elapsedMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI

        val pairRows = countSegmentedPairs()
        LOG.info("セグメント集計: ${elapsedMs}ms / 生成ペア行数=$pairRows (SKU=$SKU_COUNT, TX=$TX_COUNT)")
        assertTrue(elapsedMs < LIMIT_MS, "集計は60秒以内で完了すべき: ${elapsedMs}ms")
    }

    private fun seed(
        skuCount: Int,
        txCount: Int,
    ) {
        QuarkusTransaction.requiringNew().run {
            listOf("line_items", "item_pair_stats", "transactions").forEach { table ->
                entityManager.createNativeQuery("DELETE FROM $table").executeUpdate()
            }
            entityManager.createNativeQuery(INSERT_TX_SQL).setParameter("txCount", txCount).executeUpdate()
            entityManager.createNativeQuery(INSERT_ITEMS_SQL).setParameter("skuCount", skuCount).executeUpdate()
        }
    }

    private fun countSegmentedPairs(): Long =
        QuarkusTransaction.requiringNew().call {
            (
                entityManager
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM item_pair_stats WHERE time_segment <> 'all'",
                    ).singleResult as Number
            ).toLong()
        }

    companion object {
        private val LOG = Logger.getLogger(BasketAggregationPerfTest::class.java)
        private const val SKU_COUNT = 1_000
        private const val TX_COUNT = 100_000
        private const val MIN_PAIR_COUNT = 2L
        private const val LIMIT_MS = 60_000L
        private const val NANOS_PER_MILLI = 1_000_000L

        // 100k トランザクションを 300 日 × 24 時間に分散させ、時間帯セグメントを広く行き渡らせる。
        private val INSERT_TX_SQL =
            """
            INSERT INTO transactions (source_transaction_id, occurred_at, total_amount, item_count)
            SELECT 'PERF-' || g,
                   TIMESTAMPTZ '2026-01-01 00:00:00+09:00' + ((g * 137) % 7200) * INTERVAL '1 hour',
                   300, 3
            FROM generate_series(1, :txCount) g
            """.trimIndent()

        // 各トランザクションに 3 明細を割り当て、hashtext で 1,000 SKU に決定的に分散させる。
        private val INSERT_ITEMS_SQL =
            """
            INSERT INTO line_items
                (transaction_id, product_code, product_name, category, quantity, unit_price, line_amount)
            SELECT t.id,
                   'SKU-' || LPAD((abs(hashtext(t.source_transaction_id || '#' || k)) % :skuCount)::text, 4, '0'),
                   'perf', 'perf', 1, 100, 100
            FROM transactions t
            CROSS JOIN generate_series(1, 3) k
            """.trimIndent()
    }
}
