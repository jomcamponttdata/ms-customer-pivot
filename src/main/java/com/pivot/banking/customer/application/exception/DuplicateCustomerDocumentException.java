package com.pivot.banking.customer.application.exception;

/** Signals that a customer document is already registered. */
public class DuplicateCustomerDocumentException extends RuntimeException {

  /** Creates the duplicate-document exception. */
  public DuplicateCustomerDocumentException() {
    super("A customer with this document is already registered");
  }
}
