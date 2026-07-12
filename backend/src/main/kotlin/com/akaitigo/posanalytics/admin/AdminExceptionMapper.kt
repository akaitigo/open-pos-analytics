package com.akaitigo.posanalytics.admin

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class AdminExceptionMapper : ExceptionMapper<AdminBadRequestException> {
    override fun toResponse(exception: AdminBadRequestException): Response =
        Response.status(Response.Status.BAD_REQUEST)
            .entity(AdminError(exception.message ?: "bad request"))
            .build()
}
