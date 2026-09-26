package com.cryptoportfoliohub.error;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ProblemResponseFactory problemResponseFactory;

    public ApiExceptionHandler(ProblemResponseFactory problemResponseFactory) {
        this.problemResponseFactory = problemResponseFactory;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<Map<String, Object>> handleValidation(Exception exception, HttpServletRequest request) {
        Map<String, Object> problem = problemResponseFactory.create(
                HttpStatus.BAD_REQUEST,
                ProblemCodes.VALIDATION_ERROR,
                "One or more request fields are invalid.",
                request);
        problem.put("errors", validationErrors(exception));
        return response(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException exception,
                                                       HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, problemResponseFactory.create(
                HttpStatus.NOT_FOUND, ProblemCodes.RESOURCE_NOT_FOUND, exception.getMessage(), request));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException exception,
                                                              HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, problemResponseFactory.create(
                HttpStatus.UNAUTHORIZED, ProblemCodes.AUTHENTICATION_REQUIRED,
                "Authentication is required to access this resource.", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception,
                                                            HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, problemResponseFactory.create(
                HttpStatus.FORBIDDEN, ProblemCodes.ACCESS_DENIED,
                "You are not allowed to access this resource.", request));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<Map<String, Object>> handleMissingRoute(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, problemResponseFactory.create(
                HttpStatus.NOT_FOUND, ProblemCodes.RESOURCE_NOT_FOUND,
                "The requested resource was not found.", request));
    }

    @ExceptionHandler(ProviderException.class)
    ResponseEntity<Map<String, Object>> handleProvider(ProviderException exception, HttpServletRequest request) {
        HttpStatus status = exception.category().httpStatus();
        Map<String, Object> problem = problemResponseFactory.create(
                status, exception.category().problemCode(), exception.getMessage(), request);
        problem.put("providerCategory", exception.category().name());
        return response(status, problem);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> handlePersistence(DataAccessException exception,
                                                           HttpServletRequest request) {
        log.error("Persistence failure. requestId={} exceptionType={}", requestId(request),
                exception.getClass().getName());
        return internalError(ProblemCodes.PERSISTENCE_ERROR,
                "A persistence operation could not be completed.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure. requestId={} exceptionType={}", requestId(request),
                exception.getClass().getName());
        return internalError(ProblemCodes.INTERNAL_ERROR, "An internal error occurred.", request);
    }

    private ResponseEntity<Map<String, Object>> internalError(
            String code, String detail, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, problemResponseFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR, code, detail, request));
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private ArrayList<Map<String, String>> validationErrors(Exception exception) {
        Map<String, String> errors = new TreeMap<>();
        if (exception instanceof BindException bindException) {
            bindException.getBindingResult().getFieldErrors()
                    .forEach(error -> errors.put(error.getField(), "Invalid value."));
        } else if (exception instanceof ConstraintViolationException violationException) {
            violationException.getConstraintViolations()
                    .forEach(violation -> errors.put(violation.getPropertyPath().toString(), "Invalid value."));
        }
        ArrayList<Map<String, String>> result = new ArrayList<>();
        errors.forEach((field, message) -> result.add(Map.of("field", field, "message", message)));
        return result;
    }

    private Object requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
