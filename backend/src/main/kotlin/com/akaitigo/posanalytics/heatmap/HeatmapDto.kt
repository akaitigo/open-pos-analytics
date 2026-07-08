package com.akaitigo.posanalytics.heatmap

import org.eclipse.microprofile.openapi.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "時間帯×曜日ヒートマップの1セル（事前集計値）")
data class HeatmapCell(
    @field:Schema(description = "曜日 0=日曜〜6=土曜（PostgreSQL EXTRACT(DOW) 準拠）", example = "5")
    val dow: Int,
    @field:Schema(description = "時間帯 0〜23（店舗ローカル時刻 Asia/Tokyo）", example = "18")
    val hour: Int,
    @field:Schema(description = "売上金額", example = "12340.00")
    val sales: BigDecimal,
    @field:Schema(description = "取引数（distinct transaction）", example = "42")
    val count: Long,
    @field:Schema(description = "販売点数の合計", example = "77")
    val itemCount: Long,
)

@Schema(description = "ヒートマップAPIレスポンス")
data class HeatmapResponse(
    @field:Schema(description = "選択された指標", example = "sales")
    val metric: String,
    @field:Schema(description = "対象カテゴリ（__ALL__ は全カテゴリ合算）", example = "__ALL__")
    val category: String,
    @field:Schema(description = "非空セルの一覧（dow, hour 昇順）")
    val cells: List<HeatmapCell>,
)

@Schema(description = "カテゴリ一覧レスポンス")
data class CategoriesResponse(
    @field:Schema(description = "存在するカテゴリID（合算センチネル __ALL__ は含まない）")
    val categories: List<String>,
)

@Schema(description = "エラーレスポンス")
data class ApiError(
    @field:Schema(description = "エラーメッセージ", example = "metric は sales または count を指定してください")
    val error: String,
)
