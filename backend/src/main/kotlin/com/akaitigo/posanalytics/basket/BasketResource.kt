package com.akaitigo.posanalytics.basket

import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * 併売分析 API。GET /api/basket/pairs で時間帯セグメント別の併売ペアランキングを返す。
 * バリデーション: sort/segment は enum、limit は 1..100（デフォルト20）。不正値は
 * [BasketBadRequestException] を投げ、[BasketExceptionMapper] が 400 に変換する。
 */
@Path("/api/basket")
class BasketResource(private val repository: BasketPairRepository) {

    @GET
    @Path("/pairs")
    @Produces(MediaType.APPLICATION_JSON)
    fun pairs(
        @QueryParam("sort") @DefaultValue(DEFAULT_SORT) sortParam: String,
        @QueryParam("segment") @DefaultValue(DEFAULT_SEGMENT) segmentParam: String,
        @QueryParam("limit") @DefaultValue(DEFAULT_LIMIT) limitParam: String,
    ): Response {
        val sort = BasketSortKey.fromParam(sortParam)
            ?: throw BasketBadRequestException("sort", SORT_ERROR)
        val segment = BasketSegment.fromParam(segmentParam)
            ?: throw BasketBadRequestException("segment", SEGMENT_ERROR)
        val limit = parseLimit(limitParam)
        val pairs = repository.topPairs(segment, sort, limit)
        val body = BasketPairsResponse(
            segment = segment.value,
            sort = sort.value,
            limit = limit,
            count = pairs.size,
            pairs = pairs,
        )
        return Response.ok(body).build()
    }

    private fun parseLimit(raw: String): Int {
        val limit = raw.toIntOrNull()
            ?: throw BasketBadRequestException("limit", LIMIT_ERROR)
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw BasketBadRequestException("limit", LIMIT_ERROR)
        }
        return limit
    }

    companion object {
        private const val DEFAULT_SORT = "lift"
        private const val DEFAULT_SEGMENT = "all"
        private const val DEFAULT_LIMIT = "20"
        private const val MIN_LIMIT = 1
        private const val MAX_LIMIT = 100
        private const val SORT_ERROR = "sort は lift|confidence|support のいずれかを指定してください"
        private const val SEGMENT_ERROR = "segment は morning|noon|evening|all のいずれかを指定してください"
        private const val LIMIT_ERROR = "limit は 1〜100 の整数を指定してください"
    }
}
