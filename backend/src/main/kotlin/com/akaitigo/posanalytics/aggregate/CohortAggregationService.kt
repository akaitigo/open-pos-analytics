package com.akaitigo.posanalytics.aggregate

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional

/**
 * cohort/RFM モジュール専用の集計再計算（Issue #11）。
 *
 * #10 との競合回避のため [AggregationService] を改変せず独立クラスとする。
 * 集計ロジックは SQL に置き、Kotlin はオーケストレーションのみ（PRD 技術要件）。
 * 月次コホート（customer_monthly_cohort）は [AggregationService.recomputeCustomerMonthlyCohort] が担当し、
 * 本クラスは RFM セグメント（rfm_segments）の全置換再計算を担当する。
 *
 * R/F/M のスコアリング（3分位）とセグメント分類ルールの定義・根拠は docs/rfm-segmentation.md を参照。
 */
@ApplicationScoped
class CohortAggregationService(
    private val entityManager: EntityManager,
) {
    /**
     * rfm_segments を全置換で再計算する。
     * 会員（customers）が0件の場合は何も挿入しない（会員データなしは API 側で 200 フォールバック）。
     */
    @Transactional
    fun recomputeRfmSegments() {
        entityManager.createNativeQuery("DELETE FROM rfm_segments").executeUpdate()
        entityManager.createNativeQuery(INSERT_RFM_SQL).executeUpdate()
    }

    companion object {
        const val SEGMENT_LOYAL = "loyal"
        const val SEGMENT_AT_RISK = "at_risk"
        const val SEGMENT_DORMANT = "dormant"

        // R/F/M を NTILE(3) で 3分位スコア化（3=最良）し、複合価値 V=f_score+m_score と r_score で分類する。
        // - 優良(loyal):     V>=4 かつ r_score>=2
        // - 離脱リスク(at_risk): V>=4 かつ r_score=1
        // - 休眠(dormant):    V<=3
        // 根拠: docs/rfm-segmentation.md
        private val INSERT_RFM_SQL =
            """
            WITH ref AS (
                SELECT MAX(last_seen_at) AS as_of FROM customers
            ),
            base AS (
                SELECT c.customer_hash,
                       GREATEST(0, (r.as_of::date - c.last_seen_at::date))::int AS recency_days,
                       c.visit_count AS frequency,
                       c.total_spent AS monetary,
                       c.last_seen_at
                FROM customers c
                CROSS JOIN ref r
            ),
            scored AS (
                SELECT customer_hash, recency_days, frequency, monetary, last_seen_at,
                       NTILE(3) OVER (ORDER BY last_seen_at ASC, customer_hash ASC) AS r_score,
                       NTILE(3) OVER (ORDER BY frequency ASC, customer_hash ASC) AS f_score,
                       NTILE(3) OVER (ORDER BY monetary ASC, customer_hash ASC) AS m_score
                FROM base
            )
            INSERT INTO rfm_segments
                (customer_hash, recency_days, frequency, monetary,
                 r_score, f_score, m_score, segment, last_seen_at, computed_at)
            SELECT customer_hash, recency_days, frequency, monetary,
                   r_score, f_score, m_score,
                   CASE
                       WHEN (f_score + m_score) >= 4 AND r_score >= 2 THEN '$SEGMENT_LOYAL'
                       WHEN (f_score + m_score) >= 4 AND r_score = 1 THEN '$SEGMENT_AT_RISK'
                       ELSE '$SEGMENT_DORMANT'
                   END,
                   last_seen_at, now()
            FROM scored
            """.trimIndent()
    }
}
