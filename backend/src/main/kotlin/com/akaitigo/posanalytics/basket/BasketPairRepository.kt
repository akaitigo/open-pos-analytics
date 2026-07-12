package com.akaitigo.posanalytics.basket

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import java.math.BigDecimal

/**
 * item_pair_stats から時間帯セグメント別の併売ペアランキングを読み取る。
 * 集計は [com.akaitigo.posanalytics.aggregate.AggregationService] /
 * [com.akaitigo.posanalytics.aggregate.BasketAggregationService] が事前計算し、ここは読み取りのみ。
 */
@ApplicationScoped
class BasketPairRepository(
    private val entityManager: EntityManager,
) {
    fun topPairs(
        segment: BasketSegment,
        sort: BasketSortKey,
        limit: Int,
    ): List<BasketPair> {
        // orderByClause は enum 内の定数（ユーザー入力を含まない）。segment/limit はバインドで渡す。
        val sql = SELECT_TEMPLATE.format(sort.orderByClause)

        @Suppress("UNCHECKED_CAST")
        val rows =
            entityManager
                .createNativeQuery(sql)
                .setParameter("segment", segment.value)
                .setParameter("limit", limit)
                .resultList as List<Array<Any?>>
        return rows.map(::toPair)
    }

    private fun toPair(row: Array<Any?>): BasketPair =
        BasketPair(
            productA = row[COL_PRODUCT_A] as String,
            productB = row[COL_PRODUCT_B] as String,
            productNameA = row[COL_NAME_A] as String,
            productNameB = row[COL_NAME_B] as String,
            pairCount = (row[COL_PAIR_COUNT] as Number).toLong(),
            support = (row[COL_SUPPORT] as BigDecimal).toDouble(),
            confidenceAToB = (row[COL_CONF_A_TO_B] as BigDecimal).toDouble(),
            confidenceBToA = (row[COL_CONF_B_TO_A] as BigDecimal).toDouble(),
            lift = (row[COL_LIFT] as BigDecimal).toDouble(),
        )

    companion object {
        private const val COL_PRODUCT_A = 0
        private const val COL_PRODUCT_B = 1
        private const val COL_NAME_A = 2
        private const val COL_NAME_B = 3
        private const val COL_PAIR_COUNT = 4
        private const val COL_SUPPORT = 5
        private const val COL_CONF_A_TO_B = 6
        private const val COL_CONF_B_TO_A = 7
        private const val COL_LIFT = 8

        // %s には BasketSortKey.orderByClause（enum 定数）のみを差し込む。segment/limit はバインド。
        private val SELECT_TEMPLATE =
            """
            WITH names AS (
                SELECT DISTINCT ON (product_code) product_code, product_name
                FROM line_items
                ORDER BY product_code, id
            )
            SELECT s.product_a, s.product_b,
                   COALESCE(na.product_name, s.product_a) AS name_a,
                   COALESCE(nb.product_name, s.product_b) AS name_b,
                   s.pair_count, s.support, s.confidence_a_to_b, s.confidence_b_to_a, s.lift
            FROM item_pair_stats s
            LEFT JOIN names na ON na.product_code = s.product_a
            LEFT JOIN names nb ON nb.product_code = s.product_b
            WHERE s.time_segment = :segment
            ORDER BY %s, s.product_a, s.product_b
            LIMIT :limit
            """.trimIndent()
    }
}
