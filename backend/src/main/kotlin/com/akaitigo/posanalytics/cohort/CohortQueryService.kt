package com.akaitigo.posanalytics.cohort

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * cohort/RFM の読み取り専用クエリサービス（Issue #11）。
 * 事前集計テーブル（customer_monthly_cohort・rfm_segments）を読み、API 応答 DTO に整形する。
 * 生の会員ID・ハッシュは応答に含めない（ADR-0003）。
 */
@ApplicationScoped
class CohortQueryService(
    private val entityManager: EntityManager,
) {
    /** 月次コホートのリピート率マトリクスを構築する。会員データが無ければ hasMemberData=false。 */
    @Transactional
    fun cohortMatrix(range: CohortRange): CohortMatrixResponse {
        val hasMemberData = scalarLong("SELECT COUNT(*) FROM customer_monthly_cohort") > 0
        val rows =
            entityManager
                .createNativeQuery(COHORT_SELECT_SQL)
                .setParameter("from", range.from.atDay(1))
                .setParameter("to", range.to.atDay(1))
                .resultList
        val cohorts = buildCohortRows(rows)
        val maxOffset = cohorts.flatMap { it.cells }.maxOfOrNull { it.monthOffset } ?: 0
        return CohortMatrixResponse(
            hasMemberData = hasMemberData,
            from = range.from.toString(),
            to = range.to.toString(),
            maxMonthOffset = maxOffset,
            cohorts = cohorts,
        )
    }

    /** RFM セグメント別サマリーを構築する。filter 指定時はそのセグメントのみ。会員0件なら hasMemberData=false。 */
    @Transactional
    fun rfmSummary(filter: RfmSegment?): RfmResponse {
        val total = scalarLong("SELECT COUNT(*) FROM rfm_segments")
        if (total == 0L) {
            return RfmResponse(hasMemberData = false, asOf = null, totalCustomers = 0L, segments = emptyList())
        }
        val asOf = scalarStringOrNull("SELECT MAX(last_seen_at)::date::text FROM rfm_segments")
        val aggregates = loadRfmAggregates()
        val targets =
            if (filter != null) {
                listOf(filter)
            } else {
                RfmSegment.entries.toList()
            }
        val segments = targets.map { segment -> toSummary(segment, aggregates[segment.id]) }
        return RfmResponse(hasMemberData = true, asOf = asOf, totalCustomers = total, segments = segments)
    }

    private fun buildCohortRows(rows: List<*>): List<CohortRow> {
        val parsed =
            rows.map { it as Array<*> }.map { row ->
                RawCohort(
                    cohort = toYearMonth(row[0]),
                    activity = toYearMonth(row[1]),
                    activeCustomers = (row[2] as Number).toLong(),
                    sales = row[3] as BigDecimal,
                )
            }
        return parsed.groupBy { it.cohort }.entries.sortedBy { it.key }.map { (cohort, entries) ->
            val cohortSize = entries.firstOrNull { it.activity == cohort }?.activeCustomers ?: 0L
            val cells =
                entries.sortedBy { it.activity }.map { raw ->
                    val offset = ChronoUnit.MONTHS.between(cohort, raw.activity).toInt()
                    val rate =
                        if (cohortSize > 0) {
                            raw.activeCustomers.toDouble() / cohortSize.toDouble()
                        } else {
                            0.0
                        }
                    CohortCell(offset, raw.activeCustomers, roundTo(rate, RATE_SCALE), raw.sales.setScale(MONEY_SCALE))
                }
            CohortRow(cohort.toString(), cohortSize, cells)
        }
    }

    private fun loadRfmAggregates(): Map<String, RfmAggregate> {
        val rows = entityManager.createNativeQuery(RFM_AGGREGATE_SQL).resultList
        return rows.map { it as Array<*> }.associate { row ->
            val segment = row[0] as String
            segment to
                RfmAggregate(
                    customerCount = (row[1] as Number).toLong(),
                    sumMonetary = row[2] as BigDecimal,
                    sumFrequency = (row[3] as Number).toLong(),
                    avgFrequency = (row[4] as Number).toDouble(),
                    avgMonetary = row[5] as BigDecimal,
                    avgRecencyDays = (row[6] as Number).toDouble(),
                    lastVisit = row[7] as String?,
                )
        }
    }

    private fun toSummary(
        segment: RfmSegment,
        aggregate: RfmAggregate?,
    ): RfmSegmentSummary {
        if (aggregate == null || aggregate.customerCount == 0L) {
            return RfmSegmentSummary(
                segment = segment.id,
                label = segment.label,
                description = segment.description,
                customerCount = 0L,
                avgOrderValue = BigDecimal.ZERO.setScale(MONEY_SCALE),
                avgFrequency = 0.0,
                avgMonetary = BigDecimal.ZERO.setScale(MONEY_SCALE),
                avgRecencyDays = 0.0,
                lastVisit = null,
                estimatedLtv = BigDecimal.ZERO.setScale(MONEY_SCALE),
            )
        }
        val avgOrderValue =
            aggregate.sumMonetary
                .divide(BigDecimal.valueOf(aggregate.sumFrequency), MONEY_SCALE, RoundingMode.HALF_UP)
        val estimatedLtv =
            avgOrderValue
                .multiply(BigDecimal(segment.assumedRemainingVisits))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        return RfmSegmentSummary(
            segment = segment.id,
            label = segment.label,
            description = segment.description,
            customerCount = aggregate.customerCount,
            avgOrderValue = avgOrderValue,
            avgFrequency = roundTo(aggregate.avgFrequency, RATE_SCALE),
            avgMonetary = aggregate.avgMonetary.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            avgRecencyDays = roundTo(aggregate.avgRecencyDays, RATE_SCALE),
            lastVisit = aggregate.lastVisit,
            estimatedLtv = estimatedLtv,
        )
    }

    private fun scalarLong(sql: String): Long = (entityManager.createNativeQuery(sql).singleResult as Number).toLong()

    private fun scalarStringOrNull(sql: String): String? = entityManager.createNativeQuery(sql).singleResult as String?

    private fun toYearMonth(value: Any?): YearMonth = YearMonth.from(LocalDate.parse(value.toString()))

    private fun roundTo(
        value: Double,
        scale: Int,
    ): Double = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toDouble()

    private data class RawCohort(
        val cohort: YearMonth,
        val activity: YearMonth,
        val activeCustomers: Long,
        val sales: BigDecimal,
    )

    private data class RfmAggregate(
        val customerCount: Long,
        val sumMonetary: BigDecimal,
        val sumFrequency: Long,
        val avgFrequency: Double,
        val avgMonetary: BigDecimal,
        val avgRecencyDays: Double,
        val lastVisit: String?,
    )

    companion object {
        private const val MONEY_SCALE = 2
        private const val RATE_SCALE = 3

        private val COHORT_SELECT_SQL =
            """
            SELECT cohort_month::text, activity_month::text, active_customers, total_sales
            FROM customer_monthly_cohort
            WHERE cohort_month >= :from AND cohort_month <= :to
            ORDER BY cohort_month, activity_month
            """.trimIndent()

        private val RFM_AGGREGATE_SQL =
            """
            SELECT segment,
                   COUNT(*)::bigint,
                   COALESCE(SUM(monetary), 0)::numeric,
                   COALESCE(SUM(frequency), 0)::bigint,
                   COALESCE(AVG(frequency), 0)::double precision,
                   COALESCE(AVG(monetary), 0)::numeric,
                   COALESCE(AVG(recency_days), 0)::double precision,
                   MAX(last_seen_at)::date::text
            FROM rfm_segments
            GROUP BY segment
            """.trimIndent()
    }
}
