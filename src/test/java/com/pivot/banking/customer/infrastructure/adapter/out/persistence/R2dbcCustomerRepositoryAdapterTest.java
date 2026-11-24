package com.pivot.banking.customer.infrastructure.adapter.out.persistence;

import com.pivot.banking.customer.application.exception.DuplicateCustomerDocumentException;
import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerStatus;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2dbcCustomerRepositoryAdapterTest {

    @Mock
    private SpringDataCustomerRepository repository;
    @Mock
    private R2dbcEntityTemplate template;

    @InjectMocks
    private R2dbcCustomerRepositoryAdapter adapter;

    @Test
    void savePersistsAndMapsCustomer() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        var entity = CustomerEntity.fromDomain(customer);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class))).thenReturn(Mono.just(entity));

        // Act
        var result = adapter.save(customer);

        // Assert
        TestObserver<Customer> observer = result.test();
        observer.assertComplete();
        assertEquals(customer.id(), observer.values().get(0).id());
        assertEquals(CustomerStatus.ACTIVE, observer.values().get(0).status());
    }

    @Test
    void saveMapsDuplicateDocumentToApplicationException() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new DuplicateKeyException(
                        "duplicate key value violates unique constraint uq_customers_document")));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(DuplicateCustomerDocumentException.class);
    }

    @Test
    void saveUpdatesExistingCustomer() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        var entity = CustomerEntity.fromDomain(customer);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(true));
        when(template.update(any(CustomerEntity.class))).thenReturn(Mono.just(entity));

        // Act
        var result = adapter.save(customer);

        // Assert
        var observer = result.test();
        observer.assertComplete();
        observer.assertValue(saved -> saved.id().equals(customer.id()));
    }

    @Test
    void saveMapsOtherIntegrityViolationToIllegalArgument() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException(
                        "violates check constraint ck_customers_name")));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(IllegalArgumentException.class);
    }

    @Test
    void saveMapsIntegrityViolationWithNoMessageToIllegalArgument() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException(null)));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(IllegalArgumentException.class);
    }

    @Test
    void saveMapsDataIntegrityDuplicateToDuplicateException() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException(
                        "duplicate key uq_customers_document")));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(DuplicateCustomerDocumentException.class);
    }

    @Test
    void saveMapsDuplicateFromNestedConstraintMessage() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        var cause = new IllegalStateException("duplicate document");
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("insert failed", cause)));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(DuplicateCustomerDocumentException.class);
    }

    @Test
    void saveDetectsDuplicateFromNestedDriverMessage() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        var driverFailure = new IllegalStateException("duplicate document key");
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("insert failed", driverFailure)));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(DuplicateCustomerDocumentException.class);
    }

    @Test
    void savePreservesNonIntegrityRuntimeError() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(repository.existsById(customer.id())).thenReturn(Mono.just(false));
        when(template.insert(any(CustomerEntity.class)))
                .thenReturn(Mono.error(new RuntimeException("database unavailable")));

        // Act
        var result = adapter.save(customer);

        // Assert
        result.test().assertError(RuntimeException.class);
    }

    @Test
    void findByIdMapsExistingEntity() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(repository.findById(customer.id()))
                .thenReturn(Mono.just(CustomerEntity.fromDomain(customer)));

        // Act
        var result = adapter.findById(customer.id());

        // Assert
        TestObserver<Customer> observer = result.test();
        observer.assertComplete();
        assertEquals(customer.id(), observer.values().get(0).id());
    }

    @Test
    void findAllDelegatesToEntityTemplateForFilteredPage() {
        // Arrange
        var customer = customer(CustomerStatus.ACTIVE);
        when(template.select(any(Query.class), any(Class.class)))
                .thenReturn(Flux.just(CustomerEntity.fromDomain(customer)));

        // Act
        var result = adapter.findAll(CustomerType.PERSONAL, 1, 25);

        // Assert
        TestSubscriber<Customer> subscriber = result.test();
        subscriber.assertComplete();
        subscriber.assertValueCount(1);
        verify(template).select(any(Query.class), any(Class.class));
    }

    @Test
    void findAllSupportsUnfilteredPage() {
        // Arrange
        when(template.select(any(Query.class), any(Class.class))).thenReturn(Flux.empty());

        // Act
        var result = adapter.findAll(null, 0, 20);

        // Assert
        result.test().assertComplete();
    }

    @Test
    void findAllCanFilterInactiveCustomers() {
        // Arrange
        when(template.select(any(Query.class), any(Class.class))).thenReturn(Flux.empty());

        // Act
        var result = adapter.findAll(null, CustomerStatus.INACTIVE, 0, 20);

        // Assert
        result.test().assertComplete();
    }

    @Test
    void findAllOmitsStatusCriterionForAllAndNullStatus() {
        // Arrange
        when(template.select(any(Query.class), any(Class.class))).thenReturn(Flux.empty());

        // Act
        var allStatuses = adapter.findAll(null, CustomerStatus.ALL, 0, 20);
        var unspecifiedStatus = adapter.findAll(null, null, 0, 20);

        // Assert
        allStatuses.test().assertComplete();
        unspecifiedStatus.test().assertComplete();
        var captor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(template, org.mockito.Mockito.times(2)).select(captor.capture(), any(Class.class));
        captor.getAllValues().forEach(query ->
                org.junit.jupiter.api.Assertions.assertFalse(
                        query.getCriteria().orElseThrow().toString().contains("status")));
    }

    @Test
    void findAllOmitsLifecycleCriterionForAllAndNullStatus() {
        // Arrange
        when(template.select(any(Query.class), any(Class.class))).thenReturn(Flux.empty());

        // Act
        var allStatuses = adapter.findAll(null, CustomerStatus.ALL, 0, 20);
        var unspecifiedStatus = adapter.findAll(null, null, 0, 20);

        // Assert
        allStatuses.test().assertComplete();
        unspecifiedStatus.test().assertComplete();
        var queryCaptor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(template, times(2)).select(queryCaptor.capture(), any(Class.class));
        queryCaptor.getAllValues().forEach(query ->
                org.junit.jupiter.api.Assertions.assertFalse(
                        query.getCriteria().orElseThrow().toString().contains("status")));
    }

    @Test
    void existsByDocumentDelegatesToSpringDataRepository() {
        // Arrange
        when(repository.existsByDocumentTypeAndDocumentNumber("DNI", "12345678"))
                .thenReturn(Mono.just(true));

        // Act
        var result = adapter.existsByDocument(DocumentType.DNI, "12345678");

        // Assert
        result.test().assertResult(true);
    }

    private Customer customer(CustomerStatus status) {
        return new Customer(UUID.randomUUID(), CustomerType.PERSONAL, DocumentType.DNI, "12345678",
                "Ana Perez", null, null, null, status, Instant.parse("2025-01-01T10:00:00Z"));
    }
}
