package com.example.aichatbot.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException, request: HttpServletRequest): ResponseEntity<ApiError> =
        errorResponse(exception.status, exception.message, request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: exception.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "Invalid request"
        return errorResponse(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        val message = exception.constraintViolations.firstOrNull()?.message ?: "Invalid request"
        return errorResponse(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(request: HttpServletRequest): ResponseEntity<ApiError> =
        errorResponse(HttpStatus.BAD_REQUEST, "Invalid request body", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(request: HttpServletRequest): ResponseEntity<ApiError> =
        errorResponse(HttpStatus.BAD_REQUEST, "Invalid request parameter", request)

    // Security filters normally use JsonAuthenticationEntryPoint first; this covers MVC/method-security exceptions.
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(request: HttpServletRequest): ResponseEntity<ApiError> =
        errorResponse(HttpStatus.UNAUTHORIZED, "Missing or invalid token", request)

    // Security filters normally use JsonAccessDeniedHandler first; this covers MVC/method-security exceptions.
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(request: HttpServletRequest): ResponseEntity<ApiError> =
        errorResponse(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(request: HttpServletRequest): ResponseEntity<ApiError> =
        errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request)

    private fun errorResponse(
        status: HttpStatus,
        message: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(
            ApiError(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
            ),
        )
}
