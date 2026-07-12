package com.akaitigo.posanalytics.heatmap

import com.akaitigo.posanalytics.aggregate.AggregationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import java.math.BigDecimal

/**
 * 集計テーブル sales_by_hour_dow の読み取り専用サービス。
 * API は #8 が事前計算した値を返すのみ（再集計はしない）。SQL はパラメータバインド。
 */
@ApplicationScoped
class HeatmapService(
    private val entityManager: EntityManager,
) {
    /** 合算センチネル（__ALL__）を除いた実在カテゴリを昇順で返す。 */
    fun listCategories(): List<String> {
        val rows =
            entityManager
                .createNativeQuery(CATEGORIES_SQL)
                .setParameter("all", AggregationService.CATEGORY_ALL)
                .resultList
        return rows.map { it as String }
    }

    /** カテゴリが集計テーブルに存在するか（合算センチネルは対象外）。 */
    fun categoryExists(category: String): Boolean {
        val count =
            entityManager
                .createNativeQuery(CATEGORY_EXISTS_SQL)
                .setParameter("category", category)
                .setParameter("all", AggregationService.CATEGORY_ALL)
                .singleResult as Number
        return count.toLong() > 0
    }

    /**
     * 指定カテゴリのヒートマップセルを返す。
     * @param category null/空文字は全カテゴリ合算（__ALL__）
     */
    fun heatmap(
        category: String?,
        metric: Metric,
    ): HeatmapResponse {
        val resolved = category?.takeIf { it.isNotBlank() } ?: AggregationService.CATEGORY_ALL
        val rows =
            entityManager
                .createNativeQuery(CELLS_SQL)
                .setParameter("category", resolved)
                .resultList
        val cells = rows.map { toCell(it) }
        return HeatmapResponse(metric = metric.param, category = resolved, cells = cells)
    }

    private fun toCell(row: Any?): HeatmapCell {
        val columns = row as Array<*>
        return HeatmapCell(
            dow = (columns[0] as Number).toInt(),
            hour = (columns[1] as Number).toInt(),
            sales = columns[2] as BigDecimal,
            count = (columns[3] as Number).toLong(),
            itemCount = (columns[4] as Number).toLong(),
        )
    }

    companion object {
        private const val CATEGORIES_SQL =
            "SELECT DISTINCT category FROM sales_by_hour_dow WHERE category <> :all ORDER BY category"
        private const val CATEGORY_EXISTS_SQL =
            "SELECT COUNT(*) FROM sales_by_hour_dow WHERE category = :category AND category <> :all"
        private const val CELLS_SQL =
            "SELECT dow, hour, sales_amount, transaction_count, item_count " +
                "FROM sales_by_hour_dow WHERE category = :category ORDER BY dow, hour"
    }
}
