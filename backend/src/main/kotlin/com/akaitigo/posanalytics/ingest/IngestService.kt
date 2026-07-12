package com.akaitigo.posanalytics.ingest

import com.akaitigo.posanalytics.domain.LineItemEntity
import com.akaitigo.posanalytics.domain.TransactionEntity
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset

/**
 * open-pos CSV の取込（ADR-0002）。
 * - 冪等: source_transaction_id が既存のトランザクションはスキップ
 * - 会員IDはハッシュ化のみ保存（ADR-0003）
 * - 明細間で occurred_at / member_id が不一致のトランザクションはエラーとして集約
 */
@ApplicationScoped
class IngestService(
    private val hasher: CustomerIdHasher,
    private val entityManager: EntityManager,
) {
    private val parser = CsvTransactionParser()

    fun ingestFile(path: Path): IngestReport = Files.newBufferedReader(path).useLines { ingest(it) }

    /**
     * 取込はチャンク単位の独立トランザクションでコミットする。
     * 全件を単一トランザクションで処理すると、10万件規模で JTA のトランザクション
     * タイムアウト（既定60秒）に到達しリーパーにロールバックされるため（ship時の実走で検出）。
     */
    fun ingest(lines: Sequence<String>): IngestReport {
        val parsed = parser.parse(lines)
        val errors = parsed.errors.toMutableList()
        var importedTransactions = 0
        var importedLineItems = 0
        var duplicates = 0

        val grouped = parsed.rows.groupBy { it.transactionId }
        grouped.entries.chunked(TX_CHUNK_SIZE).forEach { chunk ->
            QuarkusTransaction.requiringNew().run {
                val result = ingestChunk(chunk, errors)
                importedTransactions += result.transactions
                importedLineItems += result.lineItems
                duplicates += result.duplicates
            }
        }
        return IngestReport(
            totalDataRows = parsed.totalDataRows,
            importedTransactions = importedTransactions,
            importedLineItems = importedLineItems,
            skippedDuplicateTransactions = duplicates,
            errors = errors,
        )
    }

    private fun ingestChunk(
        chunk: List<Map.Entry<String, List<ParsedRow>>>,
        errors: MutableList<RowError>,
    ): ChunkResult {
        var transactions = 0
        var lineItems = 0
        var duplicates = 0
        chunk.forEach { (transactionId, rows) ->
            when {
                !isConsistent(rows) -> {
                    errors.add(
                        RowError(rows.first().lineNumber, "明細間で occurred_at / member_id が不一致です: $transactionId"),
                    )
                }

                TransactionEntity.existsBySourceId(transactionId) -> {
                    duplicates++
                }

                else -> {
                    lineItems += persistTransaction(transactionId, rows)
                    transactions++
                }
            }
        }
        return ChunkResult(transactions, lineItems, duplicates)
    }

    private data class ChunkResult(
        val transactions: Int,
        val lineItems: Int,
        val duplicates: Int,
    )

    private fun isConsistent(rows: List<ParsedRow>): Boolean {
        val first = rows.first()
        return rows.all { it.occurredAt == first.occurredAt && it.memberId == first.memberId }
    }

    private fun persistTransaction(
        transactionId: String,
        rows: List<ParsedRow>,
    ): Int {
        val first = rows.first()
        val customerHash = first.memberId?.let(hasher::hash)

        val transaction = TransactionEntity()
        transaction.sourceTransactionId = transactionId
        transaction.occurredAt = first.occurredAt
        transaction.customerHash = customerHash
        transaction.totalAmount = rows.sumOf { it.lineAmount }
        transaction.itemCount = rows.sumOf { it.quantity }
        transaction.persist()

        rows.forEach { row ->
            val item = LineItemEntity()
            item.transaction = transaction
            item.productCode = row.productCode
            item.productName = row.productName
            item.category = row.category
            item.quantity = row.quantity
            item.unitPrice = row.unitPrice
            item.lineAmount = row.lineAmount
            item.persist()
        }
        customerHash?.let { upsertCustomer(it, transaction) }
        return rows.size
    }

    private fun upsertCustomer(
        customerHash: String,
        transaction: TransactionEntity,
    ) {
        val occurredAt = transaction.occurredAt.atOffset(ZoneOffset.UTC)
        entityManager
            .createNativeQuery(UPSERT_CUSTOMER_SQL)
            .setParameter("hash", customerHash)
            .setParameter("occurredAt", occurredAt)
            .setParameter("amount", transaction.totalAmount)
            .executeUpdate()
    }

    companion object {
        /** 1トランザクションでコミットする取引数（JTAタイムアウト回避とリトライ粒度のバランス） */
        private const val TX_CHUNK_SIZE = 500
        private val UPSERT_CUSTOMER_SQL =
            """
            INSERT INTO customers (customer_hash, first_seen_at, last_seen_at, visit_count, total_spent)
            VALUES (:hash, :occurredAt, :occurredAt, 1, :amount)
            ON CONFLICT (customer_hash) DO UPDATE SET
                first_seen_at = LEAST(customers.first_seen_at, EXCLUDED.first_seen_at),
                last_seen_at = GREATEST(customers.last_seen_at, EXCLUDED.last_seen_at),
                visit_count = customers.visit_count + 1,
                total_spent = customers.total_spent + EXCLUDED.total_spent
            """.trimIndent()
    }
}
