package com.pivot.banking.customer.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable customer aggregate used by the business and application layers. */
public record Customer(
    UUID id,
    CustomerType customerType,
    CustomerProfile customerProfile,
    DocumentType documentType,
    String documentNumber,
    String name,
    String email,
    String phone,
    String address,
    CustomerStatus status,
    Instant createdAt) {

  /** Creates a customer without an additional profile for backward compatibility. */
  public Customer(
      UUID id,
      CustomerType customerType,
      DocumentType documentType,
      String documentNumber,
      String name,
      String email,
      String phone,
      String address,
      CustomerStatus status,
      Instant createdAt) {
    this(
        id,
        customerType,
        null,
        documentType,
        documentNumber,
        name,
        email,
        phone,
        address,
        status,
        createdAt);
  }

  /** Returns a copy of this customer marked as inactive. */
  public Customer deactivate() {
    return new Customer(
        id,
        customerType,
        customerProfile,
        documentType,
        documentNumber,
        name,
        email,
        phone,
        address,
        CustomerStatus.INACTIVE,
        createdAt);
  }

  /** Returns a copy of this customer marked as active. */
  public Customer activate() {
    return new Customer(
        id,
        customerType,
        customerProfile,
        documentType,
        documentNumber,
        name,
        email,
        phone,
        address,
        CustomerStatus.ACTIVE,
        createdAt);
  }

  /** Returns a copy with editable identity and contact fields replaced. */
  public Customer update(
      DocumentType updatedDocumentType,
      String updatedDocumentNumber,
      String updatedName,
      String updatedEmail,
      String updatedPhone,
      String updatedAddress) {
    return new Customer(
        id,
        customerType,
        customerProfile,
        updatedDocumentType,
        updatedDocumentNumber,
        updatedName,
        updatedEmail,
        updatedPhone,
        updatedAddress,
        status,
        createdAt);
  }
}
