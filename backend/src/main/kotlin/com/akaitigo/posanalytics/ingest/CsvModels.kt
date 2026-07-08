package com.akaitigo.posanalytics.ingest

import java.math.BigDecimal
import java.time.Instant

/** CSV 1行（= 1明細）の型付き表現。ADR-0002 の列定義に対応する。 */
data class ParsedRow(
    val lineNumber: Int,
    val transactionId: String,
    val occurredAt: Instant,
    val memberId: String?,
    val productCode: String,
    val productName: String,
    val category: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
) {
    val lineAmount: BigDecimal = unitPrice.multiply(BigDecimal(quantity))
}

/** 不正行の記録。取込は中断せず、行番号と理由を集約する。 */
data class RowError(
    val lineNumber: Int,
    val reason: String,
)

/** 取込結果レポート。 */
data class IngestReport(
    val totalDataRows: Int,
    val importedTransactions: Int,
    val importedLineItems: Int,
    val skippedDuplicateTransactions: Int,
    val errors: List<RowError>,
)

/** パース結果（有効行 + エラー集約）。 */
data class ParseResult(
    val rows: List<ParsedRow>,
    val errors: List<RowError>,
    val totalDataRows: Int,
)
