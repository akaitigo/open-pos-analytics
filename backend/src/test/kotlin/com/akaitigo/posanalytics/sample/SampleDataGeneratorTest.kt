package com.akaitigo.posanalytics.sample

import com.akaitigo.posanalytics.ingest.CsvTransactionParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SampleDataGeneratorTest {
    @Test
    fun `決定的に妥当なCSVを生成する`(
        @TempDir tmp: Path,
    ) {
        val out = tmp.resolve("sample.csv")
        val summary = SampleDataGenerator().generate(out, transactionCount = 400, seed = 42L)

        assertEquals(400, summary.transactions)
        val lines = Files.readAllLines(out)
        assertEquals(summary.lineItems + 1, lines.size, "ヘッダ + 明細行数")

        val parsed = CsvTransactionParser().parse(lines.asSequence())
        assertEquals(0, parsed.errors.size, "生成データにエラー行が無い: ${parsed.errors.take(3)}")
        assertEquals(summary.lineItems, parsed.rows.size)
    }

    @Test
    fun `リピート会員と併売傾向を含む`(
        @TempDir tmp: Path,
    ) {
        val out = tmp.resolve("sample.csv")
        SampleDataGenerator().generate(out, transactionCount = 600, seed = 7L)
        val rows = CsvTransactionParser().parse(Files.readAllLines(out).asSequence()).rows

        val txToMember = rows.groupBy({ it.transactionId }, { it.memberId }).mapValues { it.value.first() }
        val memberCounts =
            txToMember.values
                .filterNotNull()
                .groupingBy { it }
                .eachCount()
        assertTrue(memberCounts.values.any { it >= 3 }, "3回以上来店するリピート会員が存在する")

        val txProducts = rows.groupBy({ it.transactionId }, { it.productCode }).mapValues { it.value.toSet() }
        val onigiri = txProducts.values.count { "P-101" in it }
        val onigiriWithTea = txProducts.values.count { "P-101" in it && "P-301" in it }
        val teaOverall = txProducts.values.count { "P-301" in it }.toDouble() / txProducts.size
        val teaGivenOnigiri = onigiriWithTea.toDouble() / onigiri
        assertTrue(
            teaGivenOnigiri > teaOverall,
            "おにぎり購入時のお茶率($teaGivenOnigiri)が全体($teaOverall)より高い",
        )
    }
}
