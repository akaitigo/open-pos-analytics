package com.akaitigo.posanalytics.admin

/** POST /api/admin/ingest のリクエストボディ。 */
data class IngestRequest(
    val path: String? = null,
    val minPairCount: Long? = null,
)

/** 取込 + 再集計の結果サマリー。 */
data class IngestResponse(
    val ingestedTransactions: Long,
    val ingestedLineItems: Long,
    val skippedDuplicates: Long,
    val errorCount: Long,
    val errors: List<String>,
    val aggregates: AggregateCounts,
)

/** 再集計後の各テーブル行数。 */
data class AggregateCounts(
    val salesByHourDow: Long,
    val itemPairStats: Long,
    val customerMonthlyCohort: Long,
    val rfmSegments: Long,
)

/** 400 応答ボディ。 */
data class AdminError(
    val error: String,
)

/** バリデーション失敗を 400 に変換するための例外。 */
class AdminBadRequestException(
    message: String,
) : RuntimeException(message)
