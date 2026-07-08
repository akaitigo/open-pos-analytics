package com.akaitigo.posanalytics.sample

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 分析用サンプルCSVの生成器（ADR-0002 の列定義で出力）。
 * シード固定で決定的に生成する。特徴:
 * - 時間帯偏り（昼12時・夕方18-19時にピーク、深夜は僅少）と週末増
 * - 併売傾向（おにぎり→お茶 65% 等の PAIR_RULES）
 * - リピート会員（会員取引の約6割が「常連」プールに集中）
 */
fun main(args: Array<String>) {
    val out = Path.of(args.getOrElse(0) { "build/sample-data.csv" })
    val transactionCount = args.getOrElse(1) { SampleDataGenerator.DEFAULT_TX_COUNT.toString() }.toInt()
    val seed = args.getOrElse(2) { SampleDataGenerator.DEFAULT_SEED.toString() }.toLong()
    SampleDataGenerator().generate(out, transactionCount, seed)
}

data class GenerationSummary(
    val transactions: Int,
    val lineItems: Int,
    val memberTransactions: Int,
)

data class Product(
    val code: String,
    val name: String,
    val category: String,
    val price: Int,
)

class SampleDataGenerator(
    private val baseDate: LocalDate = DEFAULT_BASE_DATE,
) {

    fun generate(out: Path, transactionCount: Int, seed: Long): GenerationSummary {
        require(transactionCount > 0) { "transactionCount は正の整数が必要です: $transactionCount" }
        val rng = Random(seed)
        val memberPool = buildMemberPool(transactionCount)
        var lineItems = 0
        var memberTransactions = 0

        out.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(out).use { writer ->
            writer.write(HEADER)
            writer.newLine()
            repeat(transactionCount) { index ->
                val txId = "TX-%08d".format(index + 1)
                val occurredAt = randomOccurredAt(rng)
                val memberId = pickMember(rng, memberPool)
                if (memberId != null) {
                    memberTransactions++
                }
                val products = pickProducts(rng)
                products.forEach { product ->
                    val quantity = pickQuantity(rng)
                    writer.write(csvRow(txId, occurredAt, memberId, product, quantity))
                    writer.newLine()
                    lineItems++
                }
            }
        }
        return GenerationSummary(transactionCount, lineItems, memberTransactions)
    }

    private fun csvRow(
        txId: String,
        occurredAt: OffsetDateTime,
        memberId: String?,
        product: Product,
        quantity: Int,
    ): String = listOf(
        txId,
        occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        memberId ?: "",
        product.code,
        product.name,
        product.category,
        quantity.toString(),
        product.price.toString(),
    ).joinToString(",")

    private fun randomOccurredAt(rng: Random): OffsetDateTime {
        var date: LocalDate
        do {
            date = baseDate.minusDays(rng.nextLong(DAYS_SPAN))
        } while (rng.nextDouble() >= DOW_ACCEPTANCE[date.dayOfWeek.value % DOW_ACCEPTANCE.size])
        val hour = pickWeightedIndex(rng, HOUR_WEIGHTS)
        val time = LocalTime.of(hour, rng.nextInt(MINUTES_PER_HOUR), rng.nextInt(SECONDS_PER_MINUTE))
        return OffsetDateTime.of(date, time, ZoneOffset.ofHours(JST_OFFSET_HOURS))
    }

    private fun buildMemberPool(transactionCount: Int): List<String> {
        val size = maxOf(MIN_MEMBER_POOL, transactionCount / TX_PER_MEMBER)
        return (1..size).map { "M-%05d".format(it) }
    }

    private fun pickMember(rng: Random, pool: List<String>): String? {
        if (rng.nextDouble() >= MEMBER_RATIO) {
            return null
        }
        val frequentSize = maxOf(1, pool.size / FREQUENT_POOL_DIVISOR)
        return if (rng.nextDouble() < FREQUENT_PICK_RATIO) {
            pool[rng.nextInt(frequentSize)]
        } else {
            pool[rng.nextInt(pool.size)]
        }
    }

    private fun pickProducts(rng: Random): List<Product> {
        val picked = linkedMapOf<String, Product>()
        val base = CATALOG[rng.nextInt(CATALOG.size)]
        picked[base.code] = base
        PAIR_RULES[base.code]?.let { (companionCode, probability) ->
            if (rng.nextDouble() < probability) {
                val companion = CATALOG.first { it.code == companionCode }
                picked[companion.code] = companion
            }
        }
        if (rng.nextDouble() < EXTRA_ITEM_RATIO) {
            val extra = CATALOG[rng.nextInt(CATALOG.size)]
            picked.putIfAbsent(extra.code, extra)
        }
        return picked.values.toList()
    }

    private fun pickQuantity(rng: Random): Int = pickWeightedIndex(rng, QUANTITY_WEIGHTS) + 1

    private fun pickWeightedIndex(rng: Random, weights: IntArray): Int {
        val total = weights.sum()
        var remaining = rng.nextInt(total)
        weights.forEachIndexed { index, weight ->
            remaining -= weight
            if (remaining < 0) {
                return index
            }
        }
        return weights.lastIndex
    }

    companion object {
        const val DEFAULT_TX_COUNT = 100_000
        const val DEFAULT_SEED = 20_260_709L
        const val HEADER =
            "transaction_id,occurred_at,member_id,product_code,product_name,category,quantity,unit_price"

        /** 決定的生成のための固定基準日（実行日に依存させない）。 */
        val DEFAULT_BASE_DATE: LocalDate = LocalDate.of(2026, 6, 30)

        private const val DAYS_SPAN = 365L
        private const val JST_OFFSET_HOURS = 9
        private const val MINUTES_PER_HOUR = 60
        private const val SECONDS_PER_MINUTE = 60
        private const val MEMBER_RATIO = 0.35
        private const val FREQUENT_PICK_RATIO = 0.6
        private const val FREQUENT_POOL_DIVISOR = 5
        private const val MIN_MEMBER_POOL = 10
        private const val TX_PER_MEMBER = 50
        private const val EXTRA_ITEM_RATIO = 0.35

        /** 月..日(ISO value 1..7 を %7 で 0..6 に写像)の受理確率。金土日を厚めに。 */
        private val DOW_ACCEPTANCE = doubleArrayOf(0.9, 0.6, 0.6, 0.65, 0.7, 0.85, 1.0)

        /** 0-23時の出現重み。昼と夕方にピーク、深夜は僅少。 */
        private val HOUR_WEIGHTS = intArrayOf(
            1, 1, 1, 1, 1, 2, 4, 8, 12, 14, 16, 22,
            30, 22, 14, 12, 14, 20, 28, 26, 18, 12, 6, 2,
        )

        private val QUANTITY_WEIGHTS = intArrayOf(70, 20, 10)

        /** 併売ルール: トリガー商品コード -> (相方コード, 同時購入確率) */
        private val PAIR_RULES = mapOf(
            "P-101" to ("P-301" to 0.65),
            "P-201" to ("P-302" to 0.40),
            "P-401" to ("P-303" to 0.50),
            "P-102" to ("P-301" to 0.45),
            "P-501" to ("P-601" to 0.35),
        )

        private val CATALOG = listOf(
            Product("P-101", "鮭おにぎり", "米飯", 180),
            Product("P-102", "幕の内弁当", "米飯", 580),
            Product("P-103", "梅おにぎり", "米飯", 150),
            Product("P-201", "まぐろ刺身", "鮮魚", 480),
            Product("P-202", "アジ開き", "鮮魚", 320),
            Product("P-301", "緑茶500ml", "飲料", 140),
            Product("P-302", "本わさびチューブ", "調味料", 220),
            Product("P-303", "ドリップコーヒー", "飲料", 160),
            Product("P-304", "オレンジジュース", "飲料", 190),
            Product("P-401", "クロワッサン", "ベーカリー", 210),
            Product("P-402", "食パン6枚切", "ベーカリー", 240),
            Product("P-403", "メロンパン", "ベーカリー", 180),
            Product("P-501", "缶ビール350ml", "酒類", 230),
            Product("P-502", "純米酒720ml", "酒類", 1280),
            Product("P-601", "ミックスナッツ", "菓子", 350),
            Product("P-602", "ポテトチップス", "菓子", 160),
            Product("P-603", "チョコレート", "菓子", 250),
            Product("P-701", "牛乳1L", "日配", 260),
            Product("P-702", "納豆3パック", "日配", 120),
            Product("P-703", "豆腐", "日配", 98),
            Product("P-801", "ポテトサラダ", "惣菜", 280),
            Product("P-802", "唐揚げ", "惣菜", 380),
            Product("P-803", "コロッケ", "惣菜", 120),
            Product("P-901", "ティッシュ5箱", "雑貨", 420),
        )
    }
}
