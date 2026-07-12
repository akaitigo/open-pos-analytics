package com.akaitigo.posanalytics.heatmap

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses

/**
 * ヒートマップ関連 API（読み取り専用）。
 * バリデーション: metric は enum 以外 400 / category は存在チェックで不正 400 / クエリ長上限 100 文字。
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
class HeatmapResource(
    private val heatmapService: HeatmapService,
) {
    @GET
    @Path("/heatmap")
    @Operation(summary = "時間帯×曜日ヒートマップ", description = "事前集計テーブルから 24時間×7曜日 のセルを返す")
    @APIResponses(
        APIResponse(responseCode = "200", description = "ヒートマップデータ"),
        APIResponse(responseCode = "400", description = "パラメータが不正"),
    )
    fun heatmap(
        @QueryParam("category") category: String?,
        @QueryParam("metric") metric: String?,
    ): HeatmapResponse {
        validateLength("category", category)
        validateLength("metric", metric)
        val selectedMetric = resolveMetric(metric)
        if (!category.isNullOrBlank() && !heatmapService.categoryExists(category)) {
            badRequest("category が存在しません: $category")
        }
        return heatmapService.heatmap(category, selectedMetric)
    }

    @GET
    @Path("/categories")
    @Operation(summary = "カテゴリ一覧", description = "ヒートマップで選択可能なカテゴリID一覧（昇順）")
    @APIResponses(
        APIResponse(responseCode = "200", description = "カテゴリ一覧"),
    )
    fun categories(): CategoriesResponse = CategoriesResponse(heatmapService.listCategories())

    private fun resolveMetric(metric: String?): Metric {
        if (metric.isNullOrBlank()) {
            return Metric.SALES
        }
        return Metric.fromParam(metric) ?: badRequest("metric は sales または count を指定してください")
    }

    private fun validateLength(
        name: String,
        value: String?,
    ) {
        if (value != null && value.length > MAX_PARAM_LENGTH) {
            badRequest("$name は $MAX_PARAM_LENGTH 文字以内で指定してください")
        }
    }

    private fun badRequest(message: String): Nothing =
        throw WebApplicationException(
            Response
                .status(Response.Status.BAD_REQUEST)
                .entity(ApiError(message))
                .type(MediaType.APPLICATION_JSON)
                .build(),
        )

    companion object {
        private const val MAX_PARAM_LENGTH = 100
    }
}
