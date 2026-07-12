package com.akaitigo.posanalytics.basket

/** 併売ペアランキングの時間帯セグメント。value は API クエリパラメータ表現。 */
enum class BasketSegment(val value: String) {
    MORNING("morning"),
    NOON("noon"),
    EVENING("evening"),
    ALL("all"),
    ;

    companion object {
        fun fromParam(raw: String): BasketSegment? = entries.firstOrNull { it.value == raw }
    }
}

/**
 * ランキングのソート軸。orderByClause は SQL の ORDER BY 句に埋め込む定数。
 * enum 内で完結する固定文字列でありユーザー入力を含まない（SQL インジェクション不可）。
 */
enum class BasketSortKey(val value: String, val orderByClause: String) {
    LIFT("lift", "s.lift DESC"),
    CONFIDENCE("confidence", "GREATEST(s.confidence_a_to_b, s.confidence_b_to_a) DESC"),
    SUPPORT("support", "s.support DESC"),
    ;

    companion object {
        fun fromParam(raw: String): BasketSortKey? = entries.firstOrNull { it.value == raw }
    }
}

/** 併売ペア1件。フィールド名がそのまま JSON キーになる（Jackson）。 */
data class BasketPair(
    val productA: String,
    val productB: String,
    val productNameA: String,
    val productNameB: String,
    val pairCount: Long,
    val support: Double,
    val confidenceAToB: Double,
    val confidenceBToA: Double,
    val lift: Double,
)

/** GET /api/basket/pairs のレスポンス本体。 */
data class BasketPairsResponse(
    val segment: String,
    val sort: String,
    val limit: Int,
    val count: Int,
    val pairs: List<BasketPair>,
)

/** バリデーションエラーのレスポンス本体。 */
data class ErrorResponse(
    val field: String,
    val message: String,
)

/** バリデーション失敗を表す例外。[BasketExceptionMapper] が 400 レスポンスに変換する。 */
class BasketBadRequestException(
    val field: String,
    val userMessage: String,
) : RuntimeException(userMessage)
