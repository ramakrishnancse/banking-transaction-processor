package com.example.bank.api;

import com.example.bank.domain.AccountNotFoundException;
import com.example.bank.domain.IdempotencyKeyReuseException;
import com.example.bank.domain.InsufficientFundsException;
import com.example.bank.domain.InvalidRequestException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps domain errors to HTTP problem responses in one place. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail malformedRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed or incomplete request");
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail notFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    ProblemDetail idempotencyConflict(IdempotencyKeyReuseException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Lock timeout or optimistic-lock failure: nothing was applied, the client may retry. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail busy(ConcurrencyFailureException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "The account is busy, please retry");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
