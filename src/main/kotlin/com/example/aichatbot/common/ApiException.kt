package com.example.aichatbot.common

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    override val message: String,
) : RuntimeException(message)

class BadRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)

class UnauthorizedException(message: String = "Missing or invalid token") :
    ApiException(HttpStatus.UNAUTHORIZED, message)

class ForbiddenException(message: String = "You do not have permission to access this resource") :
    ApiException(HttpStatus.FORBIDDEN, message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)
