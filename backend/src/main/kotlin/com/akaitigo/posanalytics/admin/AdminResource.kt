package com.akaitigo.posanalytics.admin

import com.akaitigo.posanalytics.aggregate.AggregationService
import com.akaitigo.posanalytics.aggregate.BasketAggregationService
import com.akaitigo.posanalytics.aggregate.CohortAggregationService
import com.akaitigo.posanalytics.ingest.IngestService
import jakarta.persistence.EntityManager
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import java.nio.file.Files

/**
 * ローカル運用向けの管理エンドポイント（CSV取込 + 再集計）。
 * MVP では無認証（ADR-0004）。本番デプロイ時は認証必須。
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
class AdminResource(
    private val ingestService: IngestService,
    private val aggregationService: AggregationService,
    private val basketAggregationService: BasketAggregationService,
    private val cohortAggregationService: CohortAggregationService,
    private val entityManager: EntityManager,
) {
    @POST
    @Path("/ingest")
    fun ingest(request: IngestRequest?): IngestResponse {
        val csvPath = validatedCsvPath(request?.path)
        val minPairCount = validatedMinPairCount(request?.minPairCount)

        val report = ingestService.ingestFile(csvPath)
        recomputeAll(minPairCount)
        return IngestResponse(
            ingestedTransactions = report.importedTransactions.toLong(),
            ingestedLineItems = report.importedLineItems.toLong(),
            skippedDuplicates = report.skippedDuplicateTransactions.toLong(),
            errorCount = report.errors.size.toLong(),
            errors = report.errors.take(MAX_ERRORS_IN_RESPONSE).map { "行${it.lineNumber}: ${it.reason}" },
            aggregates = aggregateCounts(),
        )
    }

    @POST
    @Path("/recompute")
    fun recompute(request: IngestRequest?): AggregateCounts {
        recomputeAll(validatedMinPairCount(request?.minPairCount))
        return aggregateCounts()
    }

    private fun recomputeAll(minPairCount: Long?) {
        if (minPairCount == null) {
            aggregationService.recomputeAll()
            basketAggregationService.recomputeSegmentedPairs()
        } else {
            aggregationService.recomputeAll(minPairCount)
            basketAggregationService.recomputeSegmentedPairs(minPairCount)
        }
        cohortAggregationService.recomputeRfmSegments()
    }

    private fun validatedCsvPath(rawPath: String?): java.nio.file.Path {
        val trimmed = rawPath?.trim().orEmpty()
        val syntaxError =
            when {
                trimmed.isEmpty() -> "path は必須です"
                trimmed.length > MAX_PATH_LENGTH -> "path が長すぎます（最大 $MAX_PATH_LENGTH 文字）"
                else -> null
            }
        if (syntaxError != null) {
            throw AdminBadRequestException(syntaxError)
        }
        val csvPath =
            java.nio.file.Path
                .of(trimmed)
        val fileError =
            when {
                !csvPath.isAbsolute -> {
                    "path は絶対パスで指定してください"
                }

                !csvPath.fileName.toString().endsWith(".csv") -> {
                    "拡張子 .csv のファイルのみ取込可能です"
                }

                !Files.isRegularFile(csvPath) || !Files.isReadable(csvPath) -> {
                    "読み取り可能なファイルが存在しません: $csvPath"
                }

                Files.size(csvPath) > MAX_CSV_BYTES -> {
                    "ファイルサイズ上限（${MAX_CSV_BYTES / BYTES_PER_MB}MB）を超えています"
                }

                else -> {
                    null
                }
            }
        if (fileError != null) {
            throw AdminBadRequestException(fileError)
        }
        return csvPath
    }

    private fun validatedMinPairCount(value: Long?): Long? {
        if (value != null && value < 1) {
            throw AdminBadRequestException("minPairCount は 1 以上で指定してください")
        }
        return value
    }

    private fun aggregateCounts(): AggregateCounts =
        AggregateCounts(
            salesByHourDow = countRows("sales_by_hour_dow"),
            itemPairStats = countRows("item_pair_stats"),
            customerMonthlyCohort = countRows("customer_monthly_cohort"),
            rfmSegments = countRows("rfm_segments"),
        )

    private fun countRows(table: String): Long {
        require(table in ALLOWED_TABLES) { "unexpected table: $table" }
        val result = entityManager.createNativeQuery("SELECT COUNT(*) FROM $table").singleResult
        return (result as Number).toLong()
    }

    companion object {
        private const val MAX_PATH_LENGTH = 4096
        private const val MAX_ERRORS_IN_RESPONSE = 20
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val MAX_CSV_BYTES = 100L * BYTES_PER_MB
        private val ALLOWED_TABLES =
            setOf(
                "sales_by_hour_dow",
                "item_pair_stats",
                "customer_monthly_cohort",
                "rfm_segments",
            )
    }
}
