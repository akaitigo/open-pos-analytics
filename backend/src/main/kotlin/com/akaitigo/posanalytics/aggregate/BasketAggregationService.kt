package com.akaitigo.posanalytics.aggregate

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional

/**
 * 時間帯セグメント別（morning/noon/evening）の併売ペア集計（#10）。
 *
 * 既存の [AggregationService] は time_segment='all' を担当し、本サービスは 'all' 以外を担当する。
 * 同じ item_pair_stats テーブルを共有するが、本サービスは 'all' 行に触れず自分の3セグメントのみ全置換する。
 * ただし AggregationService.recomputeItemPairStats は item_pair_stats を全削除して 'all' を入れ直すため、
 * 両者を再計算するときは「AggregationService（all）→ 本サービス（seg）」の順で呼ぶこと（逆順だと seg 行が消える）。
 *
 * 時間帯境界（Asia/Tokyo, 24時間を漏れなく3分割）:
 *   morning = 05:00–10:59 / noon = 11:00–16:59 / evening = 17:00–翌04:59
 * support/confidence/lift の母数はセグメント内で数える（その時間帯における併売傾向を表す）。
 * 組合せ爆発対策として、セグメント別でも最小共起回数（minPairCount）でプルーニングする。
 */
@ApplicationScoped
class BasketAggregationService(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun recomputeSegmentedPairs(minPairCount: Long = DEFAULT_MIN_PAIR_COUNT) {
        entityManager.createNativeQuery(DELETE_SEGMENTED_SQL).executeUpdate()
        entityManager
            .createNativeQuery(INSERT_SEGMENTED_SQL)
            .setParameter("minPairCount", minPairCount)
            .executeUpdate()
    }

    companion object {
        const val SEGMENT_ALL = "all"
        private const val DEFAULT_MIN_PAIR_COUNT = 2L
        private const val TZ = "Asia/Tokyo"

        // 'all' 以外（morning/noon/evening）のみ全置換する。'all' は AggregationService の管轄。
        private val DELETE_SEGMENTED_SQL =
            "DELETE FROM item_pair_stats WHERE time_segment <> '$SEGMENT_ALL'"

        private val INSERT_SEGMENTED_SQL =
            """
            WITH tx_hour AS (
                SELECT id, EXTRACT(HOUR FROM occurred_at AT TIME ZONE '$TZ')::int AS h
                FROM transactions
            ),
            tx_seg AS (
                SELECT id,
                       CASE WHEN h BETWEEN 5 AND 10 THEN 'morning'
                            WHEN h BETWEEN 11 AND 16 THEN 'noon'
                            ELSE 'evening' END AS seg
                FROM tx_hour
            ),
            tx_products AS (
                SELECT DISTINCT ts.seg, li.transaction_id, li.product_code
                FROM line_items li
                JOIN tx_seg ts ON ts.id = li.transaction_id
            ),
            seg_total AS (
                SELECT seg, COUNT(*)::numeric AS n FROM tx_seg GROUP BY seg
            ),
            product_tx_counts AS (
                SELECT seg, product_code, COUNT(DISTINCT transaction_id)::numeric AS c
                FROM tx_products GROUP BY seg, product_code
            ),
            pairs AS (
                SELECT a.seg, a.product_code AS product_a, b.product_code AS product_b,
                       COUNT(*)::numeric AS pair_count
                FROM tx_products a
                JOIN tx_products b
                  ON a.seg = b.seg AND a.transaction_id = b.transaction_id
                     AND a.product_code < b.product_code
                GROUP BY a.seg, a.product_code, b.product_code
            )
            INSERT INTO item_pair_stats
                (product_a, product_b, time_segment, pair_count,
                 support, confidence_a_to_b, confidence_b_to_a, lift)
            SELECT p.product_a, p.product_b, p.seg, p.pair_count,
                   p.pair_count / st.n,
                   p.pair_count / ca.c,
                   p.pair_count / cb.c,
                   (p.pair_count * st.n) / (ca.c * cb.c)
            FROM pairs p
            JOIN seg_total st ON st.seg = p.seg
            JOIN product_tx_counts ca ON ca.seg = p.seg AND ca.product_code = p.product_a
            JOIN product_tx_counts cb ON cb.seg = p.seg AND cb.product_code = p.product_b
            WHERE p.pair_count >= :minPairCount AND st.n > 0
            """.trimIndent()
    }
}
