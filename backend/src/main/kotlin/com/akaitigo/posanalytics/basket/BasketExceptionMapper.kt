package com.akaitigo.posanalytics.basket

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

/** [BasketBadRequestException] を 400 + [ErrorResponse] に変換する。 */
@Provider
class BasketExceptionMapper : ExceptionMapper<BasketBadRequestException> {

    override fun toResponse(exception: BasketBadRequestException): Response =
        Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse(exception.field, exception.userMessage))
            .build()
}
