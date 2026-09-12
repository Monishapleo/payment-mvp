package com.paymentguard.common.exception;

import java.time.Instant;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    record ErrorResponse(Instant timestamp, int status, String message) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(Instant.now(), e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String m = e.getBindingResult().getFieldErrors().stream().map(x -> x.getField() + ": " + x.getDefaultMessage()).collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, m));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> generic(Exception e) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(Instant.now(), 500, "Internal server error"));
    }
}
