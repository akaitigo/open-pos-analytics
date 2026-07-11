package com.akaitigo.posanalytics.cohort

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.math.BigDecimal
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** API のエラー応答ボディ。 */
data class ApiError(val error: String)

/** コホートマトリクスの1セル（初回購入月からの経過月ごとのリピート実績）。 */
data class CohortCell(
    val monthOffset: Int,
    val activeCustomers: Long,
    val retentionRate: Double,
    val totalSales: BigDecimal,
)

/** 1コホート（初回購入月）の行。cohortSize は経過0ヶ月時点の会員数（リピート率の分母）。 */
data class CohortRow(
    val cohortMonth: String,
    val cohortSize: Long,
    val cells: List<CohortCell>,
)

/** GET /api/cohort の応答。会員データが無い場合は hasMemberData=false・cohorts 空。 */
data class CohortMatrixResponse(
    val hasMemberData: Boolean,
    val from: String,
    val to: String,
    val maxMonthOffset: Int,
    val cohorts: List<CohortRow>,
)

/** RFM セグメント別サマリー。生の会員ID・ハッシュは含めない（ADR-0003）。 */
data class RfmSegmentSummary(
    val segment: String,
    val label: String,
    val description: String,
    val customerCount: Long,
    val avgOrderValue: BigDecimal,
    val avgFrequency: Double,
    val avgMonetary: BigDecimal,
    val avgRecencyDays: Double,
    val lastVisit: String?,
    val estimatedLtv: BigDecimal,
)

/** GET /api/rfm の応答。asOf は Recency の基準日。会員0件時は hasMemberData=false。 */
data class RfmResponse(
    val hasMemberData: Boolean,
    val asOf: String?,
    val totalCustomers: Long,
    val segments: List<RfmSegmentSummary>,
)

/**
 * RFM セグメント定義（優良/離脱リスク/休眠）。
 * 分類ルールと assumedRemainingVisits（簡易LTV用の想定残存来店回数）の根拠は docs/rfm-segmentation.md。
 */
enum class RfmSegment(
    val id: String,
    val label: String,
    val description: String,
    val assumedRemainingVisits: Int,
) {
    LOYAL("loyal", "優良", "最近も来店があり、頻度・金額も高い中核顧客", LOYAL_REMAINING_VISITS),
    AT_RISK("at_risk", "離脱リスク", "以前は高頻度・高金額だが最近の来店が途絶えた顧客（販促の最優先）", AT_RISK_REMAINING_VISITS),
    DORMANT("dormant", "休眠", "来店頻度・金額が低い低活性顧客", DORMANT_REMAINING_VISITS),
    ;

    companion object {
        fun fromId(id: String): RfmSegment? = entries.firstOrNull { it.id == id }

        /** クエリの segment 値を検証する。null/空は「全セグメント」、enum 以外は 400。 */
        fun parseFilter(raw: String?): RfmSegment? {
            if (raw.isNullOrBlank()) {
                return null
            }
            return fromId(raw) ?: throw badRequest(
                "segment は次のいずれかです: ${entries.joinToString(", ") { it.id }}",
            )
        }
    }
}

/**
 * GET /api/cohort の期間パラメータ。YYYY-MM 形式・from<=to・最大36ヶ月を検証する。
 * 不正な入力は 400（[badRequest]）。
 */
data class CohortRange(val from: YearMonth, val to: YearMonth) {

    /** マトリクスの最大経過月インデックス（例: 36ヶ月幅なら 0..35）。 */
    val maxOffset: Int = ChronoUnit.MONTHS.between(from, to).toInt()

    companion object {
        private const val MAX_MONTHS = 36L
        private const val MIN_MONTH = 1
        private const val MONTHS_PER_YEAR = 12
        private val PATTERN = Regex("""^(\d{4})-(\d{2})$""")

        fun parse(from: String?, to: String?): CohortRange {
            val fromYm = parseMonth(from, "from")
            val toYm = parseMonth(to, "to")
            if (fromYm > toYm) {
                throw badRequest("from は to 以前である必要があります: from=$fromYm, to=$toYm")
            }
            val span = ChronoUnit.MONTHS.between(fromYm, toYm) + 1
            if (span > MAX_MONTHS) {
                throw badRequest("期間は最大 $MAX_MONTHS ヶ月です（指定: $span ヶ月）")
            }
            return CohortRange(fromYm, toYm)
        }

        private fun parseMonth(raw: String?, field: String): YearMonth {
            val match = raw?.let { PATTERN.matchEntire(it) }
                ?: throw badRequest("$field は YYYY-MM 形式で指定してください（指定値: ${raw ?: "なし"}）")
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            if (month < MIN_MONTH || month > MONTHS_PER_YEAR) {
                throw badRequest("$field の月は 01〜12 の範囲です: $raw")
            }
            return YearMonth.of(year, month)
        }
    }
}

private const val LOYAL_REMAINING_VISITS = 24
private const val AT_RISK_REMAINING_VISITS = 8
private const val DORMANT_REMAINING_VISITS = 4

/** 400 Bad Request（JSON ボディ付き）を構築する。 */
internal fun badRequest(message: String): WebApplicationException =
    WebApplicationException(
        message,
        Response.status(Response.Status.BAD_REQUEST)
            .entity(ApiError(message))
            .type(MediaType.APPLICATION_JSON)
            .build(),
    )
