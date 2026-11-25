package com.pivot.banking.customer.infrastructure.adapter.in;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pivot.banking.customer.adapter.in.dto.Problem;
import com.pivot.banking.customer.application.exception.CustomerNotFoundException;
import com.pivot.banking.customer.application.exception.DuplicateCustomerDocumentException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Unit tests for RFC 7807 customer error mapping. */
class CustomerExceptionHandlerTest {

  private final CustomerExceptionHandler handler = new CustomerExceptionHandler();

  @Test
  void mapsMissingCustomerToNotFoundProblem() {
    // Arrange
    UUID customerId = UUID.randomUUID();

    // Act
    ResponseEntity<Problem> response = handler.notFound(new CustomerNotFoundException(customerId));

    // Assert
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals(
        MediaType.valueOf("application/problem+json"), response.getHeaders().getContentType());
    assertEquals("Customer not found", response.getBody().getTitle());
    assertEquals("Customer not found: " + customerId, response.getBody().getDetail());
  }

  @Test
  void mapsDuplicateDocumentToConflictProblem() {
    // Arrange
    DuplicateCustomerDocumentException exception = new DuplicateCustomerDocumentException();

    // Act
    ResponseEntity<Problem> response = handler.duplicate(exception);

    // Assert
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals("Duplicate customer document", response.getBody().getTitle());
    assertEquals(exception.getMessage(), response.getBody().getDetail());
  }

  @Test
  void mapsInvalidRequestToBadRequestProblem() {
    // Arrange
    IllegalArgumentException exception = new IllegalArgumentException("Invalid page size");

    // Act
    ResponseEntity<Problem> response = handler.badRequest(exception);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Invalid request", response.getBody().getTitle());
    assertEquals("Invalid page size", response.getBody().getDetail());
  }
}
