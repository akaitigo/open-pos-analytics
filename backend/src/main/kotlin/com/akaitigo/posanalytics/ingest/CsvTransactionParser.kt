package com.akaitigo.posanalytics.ingest

import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * open-pos エクスポートCSV（ADR-0002）のパーサ。
 * 期待ヘッダ: transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price
 * MVP はクォート無しCSV前提（ADR-0002）。不正行は中断せず RowError として集約する。
 */
class CsvTransactionParser(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun parse(lines: Sequence<String>): ParseResult {
        val rows = mutableListOf<ParsedRow>()
        val errors = mutableListOf<RowError>()
        var dataRows = 0
        val maxOccurredAt = Instant.now(clock).plus(CLOCK_SKEW_TOLERANCE)

        lines.forEachIndexed { index, raw ->
            val lineNumber = index + 1
            if ((lineNumber == 1 && raw.startsWith(HEADER_PREFIX)) || raw.isBlank()) {
                return@forEachIndexed
            }
            dataRows++
            try {
                rows.add(parseLine(lineNumber, raw, maxOccurredAt))
            } catch (e: IllegalArgumentException) {
                errors.add(RowError(lineNumber, e.message ?: "不明なエラー"))
            }
        }
        return ParseResult(rows = rows, errors = errors, totalDataRows = dataRows)
    }

    private fun parseLine(
        lineNumber: Int,
        raw: String,
        maxOccurredAt: Instant,
    ): ParsedRow {
        val cols = raw.split(',')
        require(cols.size == COLUMN_COUNT) { "列数が不正です（期待 $COLUMN_COUNT, 実際 ${cols.size}）" }

        val transactionId = cols[IDX_TRANSACTION_ID].trim()
        require(transactionId.isNotEmpty()) { "transaction_id が空です" }

        val occurredAt = parseOccurredAt(cols[IDX_OCCURRED_AT].trim(), maxOccurredAt)
        val memberId = cols[IDX_MEMBER_ID].trim().ifEmpty { null }

        val productCode = cols[IDX_PRODUCT_CODE].trim()
        val productName = cols[IDX_PRODUCT_NAME].trim()
        val category = cols[IDX_CATEGORY].trim()
        require(productCode.isNotEmpty()) { "product_code が空です" }
        require(productName.isNotEmpty()) { "product_name が空です" }
        require(category.isNotEmpty()) { "category が空です" }

        val quantity =
            requireNotNull(cols[IDX_QUANTITY].trim().toIntOrNull()) {
                "quantity が整数ではありません: ${cols[IDX_QUANTITY]}"
            }
        require(quantity > 0) { "quantity は正の整数が必要です: $quantity" }

        val unitPrice =
            requireNotNull(cols[IDX_UNIT_PRICE].trim().toBigDecimalOrNull()) {
                "unit_price が数値ではありません: ${cols[IDX_UNIT_PRICE]}"
            }
        require(unitPrice >= BigDecimal.ZERO) { "unit_price は非負が必要です: $unitPrice" }

        return ParsedRow(
            lineNumber = lineNumber,
            transactionId = transactionId,
            occurredAt = occurredAt,
            memberId = memberId,
            productCode = productCode,
            productName = productName,
            category = category,
            quantity = quantity,
            unitPrice = unitPrice,
        )
    }

    private fun parseOccurredAt(
        value: String,
        maxOccurredAt: Instant,
    ): Instant {
        val instant =
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("occurred_at をISO-8601として解釈できません: $value", e)
            }
        require(!instant.isBefore(MIN_OCCURRED_AT)) { "occurred_at が古すぎます（2000年以降のみ受理）: $value" }
        require(!instant.isAfter(maxOccurredAt)) { "occurred_at が未来日時です: $value" }
        return instant
    }

    companion object {
        private const val COLUMN_COUNT = 8
        private const val IDX_TRANSACTION_ID = 0
        private const val IDX_OCCURRED_AT = 1
        private const val IDX_MEMBER_ID = 2
        private const val IDX_PRODUCT_CODE = 3
        private const val IDX_PRODUCT_NAME = 4
        private const val IDX_CATEGORY = 5
        private const val IDX_QUANTITY = 6
        private const val IDX_UNIT_PRICE = 7
        private const val HEADER_PREFIX = "transaction_id,"
        private val MIN_OCCURRED_AT: Instant = Instant.parse("2000-01-01T00:00:00Z")
        private val CLOCK_SKEW_TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}
