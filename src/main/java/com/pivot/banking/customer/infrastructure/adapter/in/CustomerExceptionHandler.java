package com.pivot.banking.customer.infrastructure.adapter.in;

import com.pivot.banking.customer.adapter.in.dto.Problem;
import com.pivot.banking.customer.application.exception.CustomerNotFoundException;
import com.pivot.banking.customer.application.exception.DuplicateCustomerDocumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts application errors to the documented RFC 7807 response format. */
@RestControllerAdvice
public class CustomerExceptionHandler {

  /** Returns 404 when a customer cannot be found. */
  @ExceptionHandler(CustomerNotFoundException.class)
  public ResponseEntity<Problem> notFound(CustomerNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Customer not found", exception.getMessage());
  }

  /** Returns 409 when a customer document is already registered. */
  @ExceptionHandler(DuplicateCustomerDocumentException.class)
  public ResponseEntity<Problem> duplicate(DuplicateCustomerDocumentException exception) {
    return problem(HttpStatus.CONFLICT, "Duplicate customer document", exception.getMessage());
  }

  /** Returns 400 for invalid request values such as pagination parameters. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Problem> badRequest(IllegalArgumentException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
  }

  /** Builds a response body and status for a problem detail. */
  private ResponseEntity<Problem> problem(HttpStatus status, String title, String detail) {
    Problem body = new Problem(title, status.value()).type("about:blank").detail(detail);
    return ResponseEntity.status(status)
        .contentType(MediaType.valueOf("application/problem+json"))
        .body(body);
  }
}
