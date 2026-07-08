package com.akaitigo.posanalytics.heatmap

/** ヒートマップの集計指標。API クエリ `metric` の enum（sales|count）。 */
enum class Metric(val param: String) {
    SALES("sales"),
    COUNT("count"),
    ;

    companion object {
        /** クエリ値を enum に変換する。未知の値は null（呼び出し側で 400 とする）。 */
        fun fromParam(value: String): Metric? =
            entries.firstOrNull { it.param == value.lowercase() }
    }
}
