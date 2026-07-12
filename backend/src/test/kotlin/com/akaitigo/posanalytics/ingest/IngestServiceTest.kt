package com.akaitigo.posanalytics.ingest

import com.akaitigo.posanalytics.TestDb
import com.akaitigo.posanalytics.domain.CustomerEntity
import com.akaitigo.posanalytics.domain.LineItemEntity
import com.akaitigo.posanalytics.domain.TransactionEntity
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@QuarkusTest
class IngestServiceTest {
    @Inject
    lateinit var ingestService: IngestService

    @Inject
    lateinit var entityManager: EntityManager

    private val header =
        "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

    private val sampleCsv =
        listOf(
            header,
            "TX-1,2026-06-05T12:10:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
            "TX-1,2026-06-05T12:10:00+09:00,M-001,P-301,緑茶500ml,飲料,1,140",
            "TX-2,2026-06-05T12:40:00+09:00,M-002,P-101,鮭おにぎり,米飯,2,180",
            "TX-3,2026-06-06T18:05:00+09:00,M-001,P-802,唐揚げ,惣菜,1,380",
            "TX-4,2026-07-01T12:15:00+09:00,,P-703,豆腐,日配,1,98",
        )

    @BeforeEach
    fun setUp() {
        TestDb.cleanAll(entityManager)
    }

    @Test
    fun `取込件数と顧客集計が正しい`() {
        val report = ingestService.ingest(sampleCsv.asSequence())

        assertEquals(5, report.totalDataRows)
        assertEquals(4, report.importedTransactions)
        assertEquals(5, report.importedLineItems)
        assertEquals(0, report.skippedDuplicateTransactions)
        assertEquals(0, report.errors.size)

        QuarkusTransaction.requiringNew().run {
            assertEquals(4, TransactionEntity.count())
            assertEquals(5, LineItemEntity.count())
            assertEquals(2, CustomerEntity.count(), "会員2名分のみ（匿名は含まない）")
            val m001 = CustomerEntity.listAll().first { it.visitCount == 2 }
            assertEquals(700.toBigDecimal().setScale(2), m001.totalSpent.setScale(2), "M-001は320+380")
        }
    }

    @Test
    fun `再取込は冪等（重複はスキップ）`() {
        ingestService.ingest(sampleCsv.asSequence())
        val second = ingestService.ingest(sampleCsv.asSequence())

        assertEquals(0, second.importedTransactions)
        assertEquals(4, second.skippedDuplicateTransactions)
        QuarkusTransaction.requiringNew().run {
            assertEquals(4, TransactionEntity.count(), "件数が増えない")
            assertEquals(2, CustomerEntity.count())
        }
    }

    @Test
    fun `生の会員IDとカード情報がDBに存在しない`() {
        ingestService.ingest(sampleCsv.asSequence())

        QuarkusTransaction.requiringNew().run {
            val hashes = TransactionEntity.listAll().mapNotNull { it.customerHash }
            assertTrue(hashes.isNotEmpty())
            assertTrue(hashes.all { Regex("^[0-9a-f]{64}$").matches(it) }, "customer_hashはhex64のみ")

            val rawIdHits =
                entityManager
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM transactions WHERE customer_hash LIKE :probe",
                    ).setParameter("probe", "%M-00%")
                    .singleResult as Number
            assertEquals(0L, rawIdHits.toLong(), "生の会員IDがtransactionsに存在しない")

            val rawIdInCustomers =
                entityManager
                    .createNativeQuery(
                        "SELECT COUNT(*) FROM customers WHERE customer_hash LIKE :probe",
                    ).setParameter("probe", "%M-00%")
                    .singleResult as Number
            assertEquals(0L, rawIdInCustomers.toLong(), "生の会員IDがcustomersに存在しない")

            val cardColumns =
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND (column_name LIKE :card OR column_name LIKE :pan OR column_name LIKE :cvv)
                        """.trimIndent(),
                    ).setParameter("card", "%card%")
                    .setParameter("pan", "%pan%")
                    .setParameter("cvv", "%cvv%")
                    .singleResult as Number
            assertEquals(0L, cardColumns.toLong(), "カード情報のカラムがスキーマに存在しない")
        }
    }

    @Test
    fun `不正行はエラー集約され正常分のみ取込む`() {
        val csv =
            listOf(
                header,
                "TX-10,2026-06-05T12:10:00+09:00,M-009,P-101,鮭おにぎり,米飯,1,180",
                "TX-11,2026-06-05T12:10:00+09:00,M-009,P-101,鮭おにぎり,米飯,-1,180",
                "TX-12,不正な日時,,P-101,鮭おにぎり,米飯,1,180",
            )
        val report = ingestService.ingest(csv.asSequence())

        assertEquals(1, report.importedTransactions)
        assertEquals(2, report.errors.size)
        assertEquals(listOf(3, 4), report.errors.map { it.lineNumber })
    }

    @Test
    fun `明細間で属性が食い違うトランザクションはエラー`() {
        val csv =
            listOf(
                header,
                "TX-20,2026-06-05T12:10:00+09:00,M-001,P-101,鮭おにぎり,米飯,1,180",
                "TX-20,2026-06-05T12:11:00+09:00,M-001,P-301,緑茶500ml,飲料,1,140",
            )
        val report = ingestService.ingest(csv.asSequence())

        assertEquals(0, report.importedTransactions)
        assertEquals(1, report.errors.size)
        QuarkusTransaction.requiringNew().run {
            assertNull(TransactionEntity.find("sourceTransactionId", "TX-20").firstResult())
        }
    }
}
