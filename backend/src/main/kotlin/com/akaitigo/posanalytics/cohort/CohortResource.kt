package com.akaitigo.posanalytics.cohort

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

/**
 * cohort モジュールの読み取り専用 REST API（Issue #11）。
 * - GET /api/cohort?from=YYYY-MM&to=YYYY-MM : 月次コホートのリピート率マトリクス
 * - GET /api/rfm?segment=loyal|at_risk|dormant : RFM セグメント別サマリー（segment 省略で全件）
 *
 * 入力バリデーション（[CohortRange.parse] / [RfmSegment.parseFilter]）に反した場合は 400。
 * 会員データが無い場合はエラーにせず hasMemberData=false の 200 応答を返す。
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
class CohortResource(private val queryService: CohortQueryService) {

    @GET
    @Path("/cohort")
    fun cohort(
        @QueryParam("from") from: String?,
        @QueryParam("to") to: String?,
    ): CohortMatrixResponse = queryService.cohortMatrix(CohortRange.parse(from, to))

    @GET
    @Path("/rfm")
    fun rfm(@QueryParam("segment") segment: String?): RfmResponse =
        queryService.rfmSummary(RfmSegment.parseFilter(segment))
}
