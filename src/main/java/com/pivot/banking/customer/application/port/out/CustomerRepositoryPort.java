package com.pivot.banking.customer.application.port.out;

import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerStatus;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.UUID;

/** Defines the persistence operations required by customer use cases. */
public interface CustomerRepositoryPort {

  /** Stores a customer and returns the persisted representation. */
  Single<Customer> save(Customer customer);

  /** Finds a customer by identifier. */
  Maybe<Customer> findById(UUID id);

  /** Finds an optional customer-type-filtered page of customers. */
  Flowable<Customer> findAll(CustomerType customerType, int page, int size);

  /** Finds an optional customer-type-filtered page of customers by lifecycle status. */
  Flowable<Customer> findAll(CustomerType customerType, CustomerStatus status, int page, int size);

  /** Checks whether a document identity already exists. */
  Single<Boolean> existsByDocument(DocumentType documentType, String documentNumber);
}
