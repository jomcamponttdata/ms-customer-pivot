package com.pivot.banking.customer.infrastructure.adapter.out.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Spring Data repository for reactive customer persistence operations. */
public interface SpringDataCustomerRepository extends ReactiveCrudRepository<CustomerEntity, UUID> {

    /** Checks whether a document identity is already registered. */
    Mono<Boolean> existsByDocumentTypeAndDocumentNumber(String documentType, String documentNumber);
}
