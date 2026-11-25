package com.pivot.banking.customer.application.exception;

import java.util.UUID;

/** Signals that a requested customer resource does not exist. */
public class CustomerNotFoundException extends RuntimeException {

  /** Creates the exception for the specified customer identifier. */
  public CustomerNotFoundException(UUID id) {
    super("Customer not found: " + id);
  }
}
