package com.akaitigo.posanalytics.aggregate

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional

/**
 * 集計テーブル3種の全置換再計算（削除→INSERT SELECT）。
 * 集計ロジックは SQL に置き、Kotlin はオーケストレーションのみ（PRD 技術要件）。
 * タイムゾーンは MVP では Asia/Tokyo 固定（店舗ローカル時刻での分析。設定化は #9 で検討）。
 * item_pair_stats の time_segment は MVP では 'all' のみ（朝/昼/晩の分割は #10）。
 */
@ApplicationScoped
class AggregationService(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun recomputeAll(minPairCount: Long = DEFAULT_MIN_PAIR_COUNT) {
        recomputeSalesByHourDow()
        recomputeItemPairStats(minPairCount)
        recomputeCustomerMonthlyCohort()
    }

    @Transactional
    fun recomputeSalesByHourDow() {
        execute("DELETE FROM sales_by_hour_dow")
        execute(INSERT_SALES_BY_CATEGORY_SQL)
        execute(INSERT_SALES_ALL_SQL)
    }

    @Transactional
    fun recomputeItemPairStats(minPairCount: Long = DEFAULT_MIN_PAIR_COUNT) {
        execute("DELETE FROM item_pair_stats")
        entityManager
            .createNativeQuery(INSERT_ITEM_PAIR_SQL)
            .setParameter("minPairCount", minPairCount)
            .executeUpdate()
    }

    @Transactional
    fun recomputeCustomerMonthlyCohort() {
        execute("DELETE FROM customer_monthly_cohort")
        execute(INSERT_COHORT_SQL)
    }

    private fun execute(sql: String) {
        entityManager.createNativeQuery(sql).executeUpdate()
    }

    companion object {
        const val CATEGORY_ALL = "__ALL__"
        private const val DEFAULT_MIN_PAIR_COUNT = 2L
        private const val TZ = "Asia/Tokyo"

        private val INSERT_SALES_BY_CATEGORY_SQL =
            """
            INSERT INTO sales_by_hour_dow (dow, hour, category, sales_amount, transaction_count, item_count)
            SELECT EXTRACT(DOW FROM t.occurred_at AT TIME ZONE '$TZ')::smallint,
                   EXTRACT(HOUR FROM t.occurred_at AT TIME ZONE '$TZ')::smallint,
                   li.category,
                   SUM(li.line_amount),
                   COUNT(DISTINCT t.id),
                   SUM(li.quantity)
            FROM line_items li
            JOIN transactions t ON t.id = li.transaction_id
            GROUP BY 1, 2, 3
            """.trimIndent()

        private val INSERT_SALES_ALL_SQL =
            """
            INSERT INTO sales_by_hour_dow (dow, hour, category, sales_amount, transaction_count, item_count)
            SELECT EXTRACT(DOW FROM t.occurred_at AT TIME ZONE '$TZ')::smallint,
                   EXTRACT(HOUR FROM t.occurred_at AT TIME ZONE '$TZ')::smallint,
                   '$CATEGORY_ALL',
                   SUM(li.line_amount),
                   COUNT(DISTINCT t.id),
                   SUM(li.quantity)
            FROM line_items li
            JOIN transactions t ON t.id = li.transaction_id
            GROUP BY 1, 2
            """.trimIndent()

        private val INSERT_ITEM_PAIR_SQL =
            """
            WITH tx_products AS (
                SELECT DISTINCT transaction_id, product_code FROM line_items
            ),
            tx_total AS (
                SELECT COUNT(*)::numeric AS n FROM transactions
            ),
            product_tx_counts AS (
                SELECT product_code, COUNT(DISTINCT transaction_id)::numeric AS c
                FROM tx_products GROUP BY product_code
            ),
            pairs AS (
                SELECT a.product_code AS product_a, b.product_code AS product_b, COUNT(*)::numeric AS pair_count
                FROM tx_products a
                JOIN tx_products b
                  ON a.transaction_id = b.transaction_id AND a.product_code < b.product_code
                GROUP BY 1, 2
            )
            INSERT INTO item_pair_stats
                (product_a, product_b, time_segment, pair_count,
                 support, confidence_a_to_b, confidence_b_to_a, lift)
            SELECT p.product_a, p.product_b, 'all', p.pair_count,
                   p.pair_count / tt.n,
                   p.pair_count / ca.c,
                   p.pair_count / cb.c,
                   (p.pair_count * tt.n) / (ca.c * cb.c)
            FROM pairs p
            JOIN product_tx_counts ca ON ca.product_code = p.product_a
            JOIN product_tx_counts cb ON cb.product_code = p.product_b
            CROSS JOIN tx_total tt
            WHERE p.pair_count >= :minPairCount AND tt.n > 0
            """.trimIndent()

        private val INSERT_COHORT_SQL =
            """
            WITH firsts AS (
                SELECT customer_hash,
                       date_trunc('month', MIN(occurred_at AT TIME ZONE '$TZ'))::date AS cohort_month
                FROM transactions
                WHERE customer_hash IS NOT NULL
                GROUP BY customer_hash
            ),
            activity AS (
                SELECT customer_hash,
                       date_trunc('month', occurred_at AT TIME ZONE '$TZ')::date AS activity_month,
                       SUM(total_amount) AS sales
                FROM transactions
                WHERE customer_hash IS NOT NULL
                GROUP BY 1, 2
            )
            INSERT INTO customer_monthly_cohort (cohort_month, activity_month, active_customers, total_sales)
            SELECT f.cohort_month, a.activity_month, COUNT(DISTINCT a.customer_hash), SUM(a.sales)
            FROM activity a
            JOIN firsts f USING (customer_hash)
            GROUP BY 1, 2
            """.trimIndent()
    }
}
